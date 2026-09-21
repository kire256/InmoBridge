package com.droidforge.inmobridge.glasses

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.CardSpec
import com.droidforge.inmobridge.core.DockItem
import com.droidforge.inmobridge.core.Envelope

/**
 * Glasses-side launcher. Transparent, fullscreen, keeps the screen on, owns the
 * D-pad focus machine and the bridge connection lifecycle.
 *
 * Layout: a bottom dock strip — feature tiles plus an Apps tab and a Pair tab.
 * Pressing UP on a dock item opens THAT item's panel above: a submenu of its
 * actions, the installed-apps grid for the Apps tab, or pairing help for the
 * Pair tab. Pressing UP again from any panel's top row shows the pairing
 * overlay; DOWN/BACK closes one level at a time.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var view: LauncherView
    private lateinit var focus: LauncherFocus

    private var bridge: BridgeClient? = null
    private var cardDismiss: Runnable? = null

    /** Panels per dock item id; the Apps tab is special (PackageManager grid). */
    private val submenus = HashMap<String, List<DockItem>>()

    /** Grid cells for the Apps tab (real installed apps). */
    private var appGrid: List<DockItem> = emptyList()

    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                runOnUiThread { relaunch() }
            }
        }
    }

    // Paired via PairingActivity (QR scan / manual). Loaded in onCreate so the
    // context is attached. Falls back to the old hardcode only when nothing is
    // paired yet.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pairing = PairingStore.load(this)
        val phoneHost = pairing?.host ?: "192.168.68.51"
        val phonePort = pairing?.port ?: 8899
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        view = LauncherView(this)
        setContentView(view)

        // Seed with sensible offline defaults until the phone pushes a config
        view.dockItems = defaultDock()
        buildSubmenus()
        appGrid = loadInstalledApps()

        focus = LauncherFocus(view.dockItems.size, ::currentRowSizes) { st ->
            view.focusState = st
            syncPanel()
        }
        syncPanel()

        ContextCompat.registerReceiver(
            this, homeReceiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        bridge = BridgeClient(phoneHost, phonePort, ::onEnvelope,
            token = pairing?.token, deviceName = android.os.Build.MODEL ?: "glasses")
        bridge?.start()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(homeReceiver) }
        bridge?.stop()
        super.onDestroy()
    }

    private fun defaultDock() = listOf(
        DockItem("nav", "Maps", "◈", "intent", "inmo, navigate home"),
        DockItem("ai", "AI", "✦", "intent", "inmo ai"),
        DockItem("media", "Media", "▶", "intent", "inmo, play music"),
        DockItem("voice", "Voice", "◉", "intent", "voice"),
        DockItem("apps_tab", "Apps", "▦", "apps", ""),
        DockItem("pair_tab", "Pair", "⌘", "pair", ""),
    )

    /** One submenu row per dock feature tile. */
    private fun buildSubmenus() {
        submenus["nav"] = listOf(
            DockItem("nav_home", "Home", "⌂", "intent", "inmo, navigate home"),
            DockItem("nav_work", "Work", "⌗", "intent", "inmo, navigate to work"),
        )
        submenus["ai"] = listOf(
            DockItem("ai_ask", "Ask AI", "✦", "intent", "inmo ai"),
            DockItem("ai_time", "Time", "◷", "intent", "inmo, what time is it"),
            DockItem("ai_batt", "Battery", "▮", "intent", "inmo, battery"),
        )
        submenus["media"] = listOf(
            DockItem("media_play", "Play", "▶", "intent", "inmo, play music"),
            DockItem("media_pause", "Pause", "⏸", "intent", "inmo, pause"),
            DockItem("media_next", "Next", "⏭", "intent", "inmo, next song"),
        )
        submenus["voice"] = listOf(
            DockItem("voice_start", "Start voice", "◉", "intent", "voice"),
        )
    }

    /** Enumerate launchable apps for the Apps tab grid. */
    private fun loadInstalledApps(): List<DockItem> {
        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        val self = packageName
        return launchables.asSequence()
            .filter { it.activityInfo.packageName != self }
            .distinctBy { it.activityInfo.packageName }
            .take(MAX_APPS)
            .map { info ->
                DockItem(
                    id = "pkg_${info.activityInfo.packageName}",
                    label = info.loadLabel(pm).toString(),
                    icon = "▣",
                    action = "app",
                    arg = info.activityInfo.packageName,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Re-render whatever panel the current focus state implies. */
    private fun syncPanel() {
        val st = focus.state
        view.pairVisible = st.zone == LauncherFocus.Zone.PAIR
        view.panel = when {
            st.zone != LauncherFocus.Zone.PANEL -> null
            isAppsTabFocused() -> PanelUi.Grid(appGrid)
            else -> {
                val dockItem = view.dockItems.getOrNull(st.dockIndex)
                val items = dockItem?.let { submenus[it.id] } ?: emptyList()
                PanelUi.Row(dockItem?.label ?: "", items)
            }
        }
    }

    private fun isAppsTabFocused(): Boolean =
        view.dockItems.getOrNull(focus.state.dockIndex)?.action == "apps"

    /**
     * Row-size provider for the focus machine: how many cells each row of the
     * currently open panel holds. Grid = chunks of [LauncherFocus.GRID_COLS];
     * submenu = one row; closed = single dummy row.
     */
    private fun currentRowSizes(): IntArray {
        if (focus.state.zone != LauncherFocus.Zone.PANEL) return intArrayOf(1)
        return if (isAppsTabFocused()) {
            val n = appGrid.size
            if (n == 0) intArrayOf(1) else IntArray((n + LauncherFocus.GRID_COLS - 1) / LauncherFocus.GRID_COLS) { r ->
                minOf(LauncherFocus.GRID_COLS, n - r * LauncherFocus.GRID_COLS)
            }
        } else {
            val n = view.dockItems.getOrNull(focus.state.dockIndex)
                ?.let { submenus[it.id] }?.size ?: 0
            intArrayOf(n.coerceAtLeast(1))
        }
    }

    private fun onEnvelope(env: Envelope) {
        when (env.type) {
            BridgeMessage.TYPE_CONFIG -> {
                val arr = env.payload.optJSONArray("items") ?: return
                val items = ArrayList<DockItem>(arr.length())
                for (i in 0 until arr.length()) {
                    DockItem.fromJson(arr.getJSONObject(i))?.let { items.add(it) }
                }
                runOnUiThread { onConfig(items) }
            }
            BridgeMessage.TYPE_REPLY -> {
                CardSpec.fromJson(env.payload)?.let { spec ->
                    runOnUiThread { showCard(spec) }
                }
            }
        }
    }

    /**
     * The phone pushes feature tiles (dock + app_* ids). Keep OUR dock (it owns
     * the Apps/Pair tabs and submenu wiring); phone app_* items that aren't
     * already real installed apps are appended to the Apps grid.
     */
    private fun onConfig(items: List<DockItem>) {
        val knownIds = appGrid.mapTo(HashSet()) { it.id }
        val extras = items.filter { it.id.startsWith("app_") && it.id !in knownIds }
        if (extras.isNotEmpty()) {
            appGrid = (appGrid + extras).distinctBy { it.id }.take(MAX_APPS)
        }
        syncPanel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Inside a panel, UP/DOWN move between grid rows first; UP from the top
        // row climbs to the pairing overlay, DOWN from the bottom closes.
        if (focus.state.zone == LauncherFocus.Zone.PANEL &&
            (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        ) {
            val dir = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
            if (focus.panelVertical(dir)) return true
            return if (dir < 0) { focus.up(); true } else { focus.down(); true }
        }
        if (focus.onKey(keyCode)) {
            if (keyCode == LauncherFocus.KEY_ENTER) executeFocused()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun executeFocused() {
        when (focus.state.zone) {
            LauncherFocus.Zone.PAIR -> openPairing()
            LauncherFocus.Zone.DOCK -> focus.up() // any dock item opens its panel
            LauncherFocus.Zone.PANEL -> {
                val item = if (isAppsTabFocused()) {
                    appGrid.getOrNull(focus.focusedIndex())
                } else {
                    view.dockItems.getOrNull(focus.state.dockIndex)
                        ?.let { submenus[it.id]?.getOrNull(focus.state.panelCol) }
                }
                if (item != null) executePanelItem(item) else showCard(CardSpec("Empty", listOf("Nothing here yet")))
            }
        }
    }

    private fun executePanelItem(item: DockItem) {
        when (item.action) {
            "app" -> launchApp(item.arg)
            "pair" -> openPairing()
            "intent" -> {
                bridge?.send(BridgeMessage.intent(item.arg, "dock", nextId()))
                showCard(CardSpec(item.label, listOf("Sent to phone…"), timeoutMs = 2500))
            }
            "toggle" -> showCard(CardSpec("Coming soon", listOf(item.label)))
            else -> showCard(CardSpec(item.label, listOf("Not configured")))
        }
    }

    private fun launchApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addCategory(Intent.CATEGORY_LAUNCHER)
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                startActivity(launch)
            } catch (_: ActivityNotFoundException) {
                showCard(CardSpec("Can't open", listOf("App missing")))
            }
        } else {
            showCard(CardSpec("Can't open", listOf("No launcher activity")))
        }
    }

    private fun openPairing() {
        startActivity(Intent(this, PairingActivity::class.java))
    }

    /** HOME pressed while we were front: drop back to the dock, close overlays. */
    private fun relaunch() {
        view.card = null
        while (focus.state.zone != LauncherFocus.Zone.DOCK) focus.down()
    }

    private fun showCard(spec: CardSpec) {
        view.card = spec
        cardDismiss?.let { view.removeCallbacks(it) }
        val run = Runnable { view.card = null }
        cardDismiss = run
        view.postDelayed(run, spec.timeoutMs)
    }

    private var idCounter = System.nanoTime()
    private fun nextId() = idCounter++

    companion object {
        private const val MAX_APPS = 24
    }
}
