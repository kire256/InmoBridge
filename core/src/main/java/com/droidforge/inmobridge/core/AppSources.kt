package com.droidforge.inmobridge.core

/**
 * Builds the phone Apps-tab list: launchable apps (discovery surface) merged
 * with packages that have actually posted a notification (observed by the
 * relay, including background-only apps without a launcher icon).
 * Observed notifiers sort first within each group alphabetical.
 */
data class AppEntry(val label: String, val pkg: String, val observed: Boolean)

object AppSources {
    fun merge(launchables: List<Pair<String, String>>, observed: Set<String>): List<AppEntry> {
        val byPkg = LinkedHashMap<String, AppEntry>()
        launchables.forEach { (label, pkg) ->
            byPkg[pkg] = AppEntry(label, pkg, observed.contains(pkg))
        }
        observed.filter { it !in byPkg }.forEach { pkg ->
            byPkg[pkg] = AppEntry(pkg, pkg, true)
        }
        return byPkg.values.sortedWith(
            compareByDescending<AppEntry> { it.observed }.thenBy { it.label.lowercase() }
        )
    }
}
