package com.droidforge.inmobridge.glasses

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.CardSpec
import com.droidforge.inmobridge.core.DockItem
import com.droidforge.inmobridge.core.Envelope

/**
 * Glasses-side launcher. Transparent, fullscreen, keeps the screen on, owns the
 * D-pad focus machine and the bridge connection lifecycle.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var view: LauncherView
    private lateinit var focus: LauncherFocus

    private var bridge: BridgeClient? = null

    /** TODO: real service discovery (mDNS / QR-paired IP). Stubbed for v0. */
    private val phoneHost = "192.168.68.51"
    private val phonePort = 8899

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        view = LauncherView(this)
        setContentView(view)

        // Seed with sensible offline defaults until the phone pushes a config
        val dock = listOf(
            DockItem("nav", "Maps", "◈", "intent", "inmo, navigate home"),
            DockItem("ai", "AI", "✦", "toggle", "ai"),
            DockItem("media", "Media", "▶", "toggle", "media"),
            DockItem("voice", "Voice", "◉", "toggle", "voice"),
        )
        val apps = listOf(
            DockItem("maps", "Maps", "◈", "intent", "inmo, navigate home"),
            DockItem("email", "Email", "✉", "intent", "inmo, open email"),
            DockItem("msgs", "Messages", "✉", "intent", "inmo, open messages"),
            DockItem("ai", "AI", "✦", "toggle", "ai"),
            DockItem("media", "Media Player", "▶", "toggle", "media"),
            DockItem("video", "Video Stream", "▣", "toggle", "video"),
        )
        view.dockItems = dock
        view.appItems = apps

        focus = LauncherFocus(dock.size, apps.size) { st -> view.focusState = st }
        view.focusState = focus.state

        bridge = BridgeClient(phoneHost, phonePort, ::onEnvelope)
        bridge?.start()
    }

    override fun onDestroy() {
        bridge?.stop()
        super.onDestroy()
    }

    private fun onEnvelope(env: Envelope) {
        when (env.type) {
            BridgeMessage.TYPE_CONFIG -> {
                val arr = env.payload.optJSONArray("items") ?: return
                val items = ArrayList<DockItem>(arr.length())
                for (i in 0 until arr.length()) {
                    DockItem.fromJson(arr.getJSONObject(i))?.let { items.add(it) }
                }
                runOnUiThread {
                    view.dockItems = items.filter { !it.id.startsWith("app_") }
                    view.appItems = items.filter { it.id.startsWith("app_") }
                    focus.updateCounts(view.dockItems.size, view.appItems.size)
                }
            }
            BridgeMessage.TYPE_REPLY -> {
                CardSpec.fromJson(env.payload)?.let { spec ->
                    runOnUiThread { view.card = spec }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (focus.onKey(keyCode)) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) executeFocused()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun executeFocused() {
        val items = if (focus.state.zone == LauncherFocus.Zone.DOCK) view.dockItems else view.appItems
        val idx = focus.focusedIndex()
        val item = items.getOrNull(idx) ?: return
        when (item.action) {
            "intent" -> bridge?.send(BridgeMessage.intent(item.arg, "dock", nextId()))
            "toggle" -> runOnUiThread { view.card = CardSpec("Coming soon", listOf(item.label)) }
            else -> runOnUiThread { view.card = CardSpec(item.label, listOf("Not configured")) }
        }
    }

    private var idCounter = System.nanoTime()
    private fun nextId() = idCounter++
}
