package com.droidforge.inmobridge.core

/**
 * Suppresses re-mirrored notifications with identical content for the same
 * notification key. Apps like Telegram re-post security alerts ("new login")
 * periodically; each repost fires a fresh onNotificationPosted with the same
 * key and text. Within [windowMs] of mirroring a (key, content) pair, repeats
 * are dropped. New text on the same key still mirrors (chat threads grew).
 */
class NotifDeduper(
    @Volatile var windowMs: Long = 86_400_000L, // default: 24h
    private val capacity: Int = 96,
) {
    private val seen = LinkedHashMap<String, Pair<Int, Long>>(32, 0.75f, true)

    @Synchronized
    fun shouldMirror(key: String, title: String, text: String, now: Long): Boolean {
        if (windowMs <= 0) return true // filter off
        val h = 31 * title.hashCode() + text.hashCode()
        val prev = seen[key]
        return if (prev != null && prev.first == h && now - prev.second < windowMs) {
            false
        } else {
            seen[key] = Pair(h, now)
            if (seen.size > capacity) {
                seen.remove(seen.keys.first())
            }
            true
        }
    }

    @Synchronized
    fun clear() = seen.clear()
}
