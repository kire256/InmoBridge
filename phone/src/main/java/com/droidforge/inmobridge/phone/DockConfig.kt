package com.droidforge.inmobridge.phone

import android.content.Context
import com.droidforge.inmobridge.core.DockItem
import org.json.JSONArray

/**
 * Phone-side layout store: which features sit in the glasses dock vs the app
 * container. Persisted as JSON in app-private prefs; pushed to the glasses as
 * a `config` envelope on (re)connect.
 *
 * Dock items: quick-access icons (≤6 sensible). App items: the fuller list.
 */
object DockConfig {

    private const val PREFS = "dock_config"
    private const val KEY = "layout"

    val DEFAULT_DOCK = listOf(
        DockItem("nav", "Maps", "◈", "intent", "inmo, navigate home"),
        DockItem("ai", "AI", "✦", "toggle", "ai"),
        DockItem("media", "Media", "▶", "toggle", "media"),
        DockItem("voice", "Voice", "◉", "toggle", "voice"),
    )

    val DEFAULT_APPS = listOf(
        DockItem("app_maps", "Maps", "◈", "intent", "inmo, navigate home"),
        DockItem("app_email", "Email", "✉", "intent", "inmo, open email"),
        DockItem("app_msgs", "Messages", "✽", "intent", "inmo, open messages"),
        DockItem("app_ai", "AI Assistant", "✦", "toggle", "ai"),
        DockItem("app_media", "Media Player", "▶", "toggle", "media"),
        DockItem("app_video", "Video Stream", "▣", "toggle", "video"),
    )

    fun load(context: Context): Pair<List<DockItem>, List<DockItem>> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, null) ?: return DEFAULT_DOCK to DEFAULT_APPS
        return runCatching {
            val o = org.json.JSONObject(raw)
            fun arr(name: String): List<DockItem> {
                val a = o.optJSONArray(name) ?: JSONArray()
                return (0 until a.length()).mapNotNull { DockItem.fromJson(a.getJSONObject(it)) }
            }
            arr("dock") to arr("apps")
        }.getOrDefault(DEFAULT_DOCK to DEFAULT_APPS)
    }

    fun save(context: Context, dock: List<DockItem>, apps: List<DockItem>) {
        val o = org.json.JSONObject()
            .put("dock", JSONArray(dock.map { it.toJson() }))
            .put("apps", JSONArray(apps.map { it.toJson() }))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, o.toString())
            .apply()
    }
}
