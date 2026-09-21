package com.droidforge.inmobridge.glasses

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.droidforge.inmobridge.core.AppCategories
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.CardSpec
import com.droidforge.inmobridge.core.DockItem
import com.droidforge.inmobridge.core.Envelope

/**
 * Glasses-side launcher. Opaque fullscreen, keeps the screen on, owns the
 * D-pad focus machine and the bridge connection lifecycle.
 *
 * Bottom dock strip: feature tiles + Apps tab + Pair tab. UP opens the focused
 * item's panel. The Apps tab has TWO levels: UP first opens the CATEGORY row
 * (All / Games / Media / Tools / System / Other) — selecting one opens that
 * category's horizontal 4-row app strip with real icons; DOWN/BACK from the
 * strip returns to categories. Pairing status shows as a text pill top-right.
 */
class LauncherActivity : AppCompatActivity() {

    /** One launchable app with its classification + icon. */
    private data class AppEntry(
        val item: DockItem,
        val category: String,
        val icon: Drawable?,
    )

    private lateinit var view: LauncherView
    private lateinit var focus: LauncherFocus

    private var bridge: BridgeClient? = null
    private var cardDismiss: Runnable? = null

    /** Panels per dock item id; the Apps tab is special. */
    private val submenus = HashMap<String, List<DockItem>>()

    /** Alphabetical master app list with icons + categories. */
    private var allApps: List<AppEntry> = emptyList()

    /** Currently selected apps category ("all" = everything). */
    private var activeCategory: String = "all"

    /** True while the Apps panel is showing the category picker row. */
    private var categoryLevel: Boolean = true

    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                runOnUiThread { relaunch() }
            }
        }
    }

    private val bridgeListener = { s: Int ->
        runOnUiThread { onBridgeState(s) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pairing = PairingStore.load(this)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        view = LauncherView(this)
        setContentView(view)
        // Touch-tap anywhere launches the focused item (covers INMO touchpads
        // that deliver taps as MotionEvents instead of DPAD_CENTER/ENTER).
        view.setOnClickListener { if (::focus.isInitialized) executeFocused() }

        // Seed with sensible offline defaults until the phone pushes a config
        view.dockItems = defaultDock()
        buildSubmenus()
        loadInstalledApps()
        recomputeCategory("all")

        focus = LauncherFocus(view.dockItems.size) { st ->
            view.focusState = st
            syncPanel()
        }
        syncPanel()

        // Pairing status pill: only meaningful once a pairing exists.
        BridgeState.addListener(bridgeListener)
        if (pairing == null) view.bridgeStatus = null

        ContextCompat.registerReceiver(
            this, homeReceiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (pairing != null) {
            bridge = BridgeClient(pairing.host, pairing.port, ::onEnvelope,
                token = pairing.token, deviceName = android.os.Build.MODEL ?: "glasses")
            bridge?.start()
        }
    }

    override fun onDestroy() {
        BridgeState.removeListener(bridgeListener)
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

    /** One submenu row per dock feature tile. Pair gets real actions. */
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
        submenus["pair_tab"] = listOf(
            DockItem("pair_open", "Pair with phone", "⌘", "pair", ""),
            DockItem("pair_help", "How to pair", "?", "pair", ""),
        )
    }

    /** Alphabetical master app list; icons drawn from PackageManager. */
    private fun loadInstalledApps() {
        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        val self = packageName
        allApps = launchables.asSequence()
            .filter { it.activityInfo.packageName != self }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val label = info.loadLabel(pm).toString()
                AppEntry(
                    item = DockItem(
                        id = "pkg_${info.activityInfo.packageName}",
                        label = label,
                        icon = "▣",
                        action = "app",
                        arg = info.activityInfo.packageName,
                    ),
                    category = AppCategories.bucket(info.activityInfo.applicationInfo, label),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull(),
                )
            }
            .sortedBy { it.item.label.lowercase() }
            .take(MAX_APPS)
            .toList()
    }

    /** Push the selected category's items + the icon map into the view. */
    private fun recomputeCategory(cat: String) {
        activeCategory = cat
        view.appsPanel = if (cat == "all") {
            allApps.map { it.item }
        } else {
            allApps.filter { it.category == cat }.map { it.item }
        }
        view.appIcons = HashMap<String, Drawable?>().also { m ->
            allApps.forEach { m[it.item.id] = it.icon }
        }
    }

    /** Categories that actually contain apps (category picker content). */
    private fun categoryRow(): List<DockItem> =
        AppCategories.ORDER
            .filter { cat -> cat == "all" || allApps.any { it.category == cat } }
            .map { cat ->
                DockItem("cat_$cat", AppCategories.LABELS[cat] ?: cat,
                    AppCategories.ICONS[cat] ?: "▦", "category", cat)
            }

    /** Re-render whatever panel the current focus state implies + push geometry. */
    private fun syncPanel() {
        val st = focus.state
        if (st.zone != LauncherFocus.Zone.PANEL) categoryLevel = true
        view.panel = when {
            st.zone != LauncherFocus.Zone.PANEL -> null
            isAppsTabFocused() -> {
                if (categoryLevel) PanelUi.Row("Categories", categoryRow())
                else PanelUi.Grid(view.appsPanel)
            }
            else -> {
                val dockItem = view.dockItems.getOrNull(st.dockIndex)
                val items = dockItem?.let { submenus[it.id] } ?: emptyList()
                PanelUi.Row(dockItem?.label ?: "", items)
            }
        }
        when (val p = view.panel) {
            is PanelUi.Grid -> focus.setPanelGeometry(
                LauncherFocus.GRID_ROWS,
                ((p.items.size + LauncherFocus.GRID_ROWS - 1) / LauncherFocus.GRID_ROWS).coerceAtLeast(1),
                p.items.size,
            )
            is PanelUi.Row -> focus.setPanelGeometry(1, p.items.size.coerceAtLeast(1), p.items.size)
            null -> Unit
        }
    }

    private fun isAppsTabFocused(): Boolean =
        view.dockItems.getOrNull(focus.state.dockIndex)?.action == "apps"

    private fun onBridgeState(s: Int) {
        view.bridgeStatus = when (s) {
            BridgeState.CONNECTED -> "Connected"
            BridgeState.CONNECTING -> "Connecting…"
            BridgeState.REJECTED -> "Rejected — re-pair"
            else -> "Disconnected"
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

    private fun onConfig(items: List<DockItem>) {
        syncPanel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Apps strip: DOWN at the bottom edge (or BACK) returns to the category
        // picker instead of closing the panel entirely.
        if (focus.state.zone == LauncherFocus.Zone.PANEL &&
            isAppsTabFocused() && !categoryLevel &&
            (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK)
        ) {
            if (keyCode == KeyEvent.KEYCODE_BACK || !focus.panelVertical(1)) {
                categoryLevel = true
                syncPanel()
            }
            return true
        }
        if (focus.onKey(keyCode)) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                executeFocused()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun executeFocused() {
        when (focus.state.zone) {
            LauncherFocus.Zone.DOCK -> focus.up() // any dock item opens its panel
            LauncherFocus.Zone.PANEL -> {
                val item = if (isAppsTabFocused()) {
                    if (categoryLevel) {
                        categoryRow().getOrNull(focus.focusedSubmenuIndex())
                    } else {
                        view.appsPanel.getOrNull(focus.focusedGridIndex())
                    }
                } else {
                    view.dockItems.getOrNull(focus.state.dockIndex)
                        ?.let { submenus[it.id]?.getOrNull(focus.focusedSubmenuIndex()) }
                }
                when {
                    item == null -> showCard(CardSpec("Empty", listOf("Nothing here yet")))
                    item.action == "category" -> {
                        recomputeCategory(item.arg)
                        categoryLevel = false
                        syncPanel()
                    }
                    else -> executePanelItem(item)
                }
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
        startActivityForResult(Intent(this, PairingActivity::class.java), REQ_PAIR)
    }

    /** HOME pressed while we were front: drop back to the dock, close panels. */
    private fun relaunch() {
        view.card = null
        if (focus.state.zone != LauncherFocus.Zone.DOCK) focus.down()
        // Fresh pairing? Restart the client against the new address/token.
        restartBridgeIfPairingChanged()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PAIR) {
            restartBridgeIfPairingChanged()
        }
    }

    private var lastPairingKey: String? = PairingStoreSnapshot()

    private fun PairingStoreSnapshot(): String? =
        PairingStore.load(this)?.let { "${it.host}:${it.port}/${it.token}" }

    private fun restartBridgeIfPairingChanged() {
        val key = PairingStoreSnapshot()
        if (key == lastPairingKey) return
        lastPairingKey = key
        bridge?.stop()
        val pairing = PairingStore.load(this) ?: return
        bridge = BridgeClient(pairing.host, pairing.port, ::onEnvelope,
            token = pairing.token, deviceName = android.os.Build.MODEL ?: "glasses")
        bridge?.start()
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
        private const val MAX_APPS = 48
        private const val REQ_PAIR = 7001
    }
}
