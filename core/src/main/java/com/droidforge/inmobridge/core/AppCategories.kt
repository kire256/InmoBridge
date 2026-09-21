package com.droidforge.inmobridge.core

/**
 * Classifies installed apps into fixed launcher categories (pure logic, shared
 * by phone + glasses). Classification: Google's `ApplicationInfo.category`
 * first (declared by the app itself), then name heuristics, then a
 * system/other fallback via [bucket].
 */
object AppCategories {

    /** Fixed category order on the category row. */
    val ORDER = listOf("all", "games", "media", "tools", "system", "other")

    val LABELS = mapOf(
        "all" to "All",
        "games" to "Games",
        "media" to "Media",
        "tools" to "Tools",
        "system" to "System",
        "other" to "Other",
    )

    val ICONS = mapOf(
        "all" to "▦",
        "games" to "◉",
        "media" to "▶",
        "tools" to "⚡",
        "system" to "⚙",
        "other" to "✎",
    )

    /** True when a package is a system component rather than a user app. */
    fun isSystemApp(info: android.content.pm.ApplicationInfo): Boolean =
        (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    /**
     * Category id for one app, or null when nothing matches (caller picks the
     * fallback — usually via [bucket]).
     */
    fun classify(info: android.content.pm.ApplicationInfo, label: String): String? {
        when (info.category) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> return "games"
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> return "media"
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> return "media"
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> return "media"
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> return "media"
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> return "other"
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> return "tools"
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "tools"
        }
        return heuristic(label)
    }

    /** Final bucket for an app: classify, then system, then other. */
    fun bucket(info: android.content.pm.ApplicationInfo, label: String): String =
        classify(info, label) ?: if (isSystemApp(info)) "system" else "other"

    private fun heuristic(label: String): String? {
        val l = label.lowercase()
        return when {
            GAME_WORDS.any { l.contains(it) } -> "games"
            TOOLS_WORDS.any { l.contains(it) } -> "tools"
            MEDIA_WORDS.any { l.contains(it) } -> "media"
            SYSTEM_WORDS.any { l.contains(it) } -> "system"
            else -> null
        }
    }

    private val GAME_WORDS = listOf("game", "quest", "puzzle", "emulator", "emu.")
    private val TOOLS_WORDS = listOf("tool", "mapper", "store", "browser", "keyboard", "backup", "file", "scanner")
    private val MEDIA_WORDS = listOf("music", "video", "radio", "podcast", "player", "camera", "photo")
    private val SYSTEM_WORDS = listOf("system", "settings", "services", "android", "setup")
}
