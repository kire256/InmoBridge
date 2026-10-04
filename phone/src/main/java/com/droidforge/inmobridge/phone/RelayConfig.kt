package com.droidforge.inmobridge.phone

import android.content.Context
import com.droidforge.inmobridge.core.NotifThemes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Relay configuration: which apps mirror to the glasses, and how each
 * notification type looks/sounds/feels on the glasses.
 *
 * Per-app: { "pkg": true|false } (default OFF for unknown apps).
 * Per-type: category -> { theme, sound, vibrate }.
 */
object RelayConfig {

    const val MSG = "message"; const val CALL = "call"; const val MAIL = "mail"
    const val CALENDAR = "calendar"; const val NAV = "nav"; const val MEDIA = "media"
    const val SYSTEM = "system"; const val OTHER = "other"

    val TYPES = listOf(MSG, CALL, MAIL, CALENDAR, NAV, MEDIA, SYSTEM, OTHER)

    val TYPE_LABELS = mapOf(
        MSG to "Messages", CALL to "Calls", MAIL to "Email",
        CALENDAR to "Calendar", NAV to "Navigation", MEDIA to "Media",
        SYSTEM to "System", OTHER to "Other",
    )

    private const val PREFS = "relay_config"
    private const val KEY_APPS = "apps"
    private const val KEY_TYPES = "types"
    private const val KEY_ENABLED = "enabled"

    // ---- Glasses display settings (mirrored via config envelope) ----
    fun maxLines(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("maxLines", 3)

    fun setMaxLines(ctx: Context, lines: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("maxLines", lines.coerceIn(1, 8)).apply()
    }

    /** Auto-scroll long text after this many ms (0 = off). */
    fun autoScrollMs(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("autoScrollMs", 4000L)

    fun setAutoScrollMs(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("autoScrollMs", ms).apply()
    }

    /** Duplicate-notification filter window (ms). 0 = off. Default 24h. */
    fun dedupeWindowMs(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("dedupeMs", 86_400_000L)

    fun setDedupeWindowMs(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("dedupeMs", ms).apply()
    }

    /** Default per-type presentation. */
    fun defaultTypeRules(): Map<String, Triple<String, String, String>> = mapOf(
        MSG to Triple("teal", "ping", "tick"),
        CALL to Triple("green", "ring", "ring"),
        MAIL to Triple("blue", "chime", "tick"),
        CALENDAR to Triple("amber", "chime", "pulse"),
        NAV to Triple("blue", "none", "none"),
        MEDIA to Triple("purple", "none", "none"),
        SYSTEM to Triple("amber", "alert", "tick"),
        OTHER to Triple("teal", "ping", "tick"),
    )

    fun relayEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setRelayEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun isAppAllowed(ctx: Context, pkg: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("app_$pkg", false)

    fun setAppAllowed(ctx: Context, pkg: String, allowed: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("app_$pkg", allowed).apply()
    }

    /** Rules for a type: (theme, sound, vibrate). */
    fun ruleFor(ctx: Context, type: String): Triple<String, String, String> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_TYPES, null) ?: return defaultTypeRules()[type]
            ?: Triple("teal", "ping", "tick")
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return defaultTypeRules()[type]
            ?: Triple("teal", "ping", "tick")
        val t = o.optJSONObject(type) ?: return defaultTypeRules()[type]
            ?: Triple("teal", "ping", "tick")
        return Triple(
            t.optString("theme", "teal"),
            t.optString("sound", "ping"),
            t.optString("vibrate", "tick"),
        )
    }

    fun setRule(ctx: Context, type: String, theme: String, sound: String, vibrate: String) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = runCatching {
            sp.getString(KEY_TYPES, null)?.let { JSONObject(it) } ?: JSONObject()
        }.getOrDefault(JSONObject())
        o.put(type, JSONObject().put("theme", theme).put("sound", sound).put("vibrate", vibrate))
        sp.edit().putString(KEY_TYPES, o.toString()).apply()
    }

    /** Apps currently allowed (for config push / debug). */
    fun allowedPackages(ctx: Context): List<String> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = sp.all
        return all.entries.filter { it.key.startsWith("app_") && it.value == true }
            .map { it.key.removePrefix("app_") }
    }

    fun soundIds() = NotifThemes.SOUNDS
    fun vibrationIds() = NotifThemes.VIBRATIONS
}
