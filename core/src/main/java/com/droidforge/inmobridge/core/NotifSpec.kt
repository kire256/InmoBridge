package com.droidforge.inmobridge.core

import org.json.JSONObject

/**
 * One mirrored notification, phone -> glasses.
 *
 * The phone classifies the notification (app + Android category) and resolves
 * the display theme / sound / vibration pattern, so the glasses stay dumb:
 * render [theme], play [sound], buzz [vibrate].
 */
data class NotifSpec(
    val app: String,          // app label, e.g. "WhatsApp"
    val pkg: String,          // source package
    val title: String,
    val text: String,
    val category: String,     // message | call | mail | calendar | nav | media | system | other
    val theme: String,        // theme id from NotifThemes
    val sound: String,        // sound id (none|ping|chime|ring|alert)
    val vibrate: String,      // vibration id (none|tick|pulse|ring)
    val timeoutMs: Long = 6000,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("app", app).put("pkg", pkg).put("title", title).put("text", text)
        .put("category", category).put("theme", theme).put("sound", sound)
        .put("vibrate", vibrate).put("timeout", timeoutMs)

    companion object {
        fun fromJson(o: JSONObject): NotifSpec? = runCatching {
            NotifSpec(
                app = o.optString("app"),
                pkg = o.optString("pkg"),
                title = o.optString("title"),
                text = o.optString("text"),
                category = o.optString("category", "other"),
                theme = o.optString("theme", "teal"),
                sound = o.optString("sound", "ping"),
                vibrate = o.optString("vibrate", "tick"),
                timeoutMs = o.optLong("timeout", 6000L),
            )
        }.getOrNull()
    }
}

/**
 * Overlay themes for the glasses card. Each theme = accent color + label.
 * Mapped per notification type on the phone (RelayConfig).
 */
object NotifThemes {
    data class Theme(val id: String, val label: String, val accent: Int)

    val ALL = listOf(
        Theme("teal", "Teal", 0xFF39D2C0.toInt()),
        Theme("blue", "Blue", 0xFF2E7CF6.toInt()),
        Theme("purple", "Purple", 0xFF9C6DE8.toInt()),
        Theme("amber", "Amber", 0xFFE8A03C.toInt()),
        Theme("red", "Red", 0xFFE85D5D.toInt()),
        Theme("green", "Green", 0xFF22A85C.toInt()),
    )

    fun byId(id: String): Theme = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** Built-in earcon ids the glasses can play (ToneGenerator tones). */
    val SOUNDS = listOf("none", "ping", "chime", "ring", "alert")

    /** Built-in vibration waveform ids. */
    val VIBRATIONS = listOf("none", "tick", "pulse", "ring")
}
