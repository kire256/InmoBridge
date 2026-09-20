package com.droidforge.inmobridge.core

/**
 * Deterministic, offline-first voice command router.
 *
 * Rules:
 *  - A wake word ("inmo", configurable) must lead the utterance; anything after
 *    the comma (or the wake word itself) is the command body.
 *  - The command body is matched against LOCAL intents first (exact phrase,
 *    then fuzzy prefix). Local intents never touch the network.
 *  - Anything that doesn't match a local intent is open-ended → routed to the
 *    phone's AI pipeline (LLM or local model) by the caller.
 */
class IntentRouter(private val wakeWord: String = "inmo") {

    sealed interface Routing {
        /** Not addressed to us at all (no wake word). */
        data object NotAddressed : Routing

        /** Matched a local intent. */
        data class Local(val intentId: String, val arg: String = "") : Routing

        /** Open-ended — send to the AI pipeline. */
        data class Ai(val prompt: String) : Routing
    }

    data class LocalIntent(
        val id: String,
        val phrases: List<String>,
    )

    private val localIntents = mutableListOf(
        LocalIntent("navigate_home", listOf("navigate home", "take me home", "drive home")),
        LocalIntent("navigate_work", listOf("navigate to work", "drive to work")),
        LocalIntent("open_email", listOf("open email", "show email", "read email")),
        LocalIntent("open_messages", listOf("open messages", "show messages", "read messages")),
        LocalIntent("what_time", listOf("what time is it", "the time", "what's the time")),
        LocalIntent("status_battery", listOf("battery", "battery status", "how much battery")),
        LocalIntent("media_pause", listOf("pause", "pause music", "pause media")),
        LocalIntent("media_play", listOf("play", "play music", "resume")),
        LocalIntent("media_next", listOf("next song", "next track", "skip")),
        LocalIntent("dock_toggle", listOf("open apps", "show apps", "app list", "toggle apps")),
    )

    fun route(utterance: String): Routing {
        val cleaned = utterance.trim().lowercase().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return Routing.NotAddressed

        // Wake word detection: "inmo, ..." / "inmo ..." / bare "inmo"
        val wake = wakeWord.lowercase()
        var body: String? = null
        if (cleaned == wake) {
            body = ""
        } else if (cleaned.startsWith("$wake ") || cleaned.startsWith("$wake,")) {
            body = cleaned.removePrefix("$wake").dropWhile { it == ' ' || it == ',' }.trim()
        }
        // Unknown-nothing after wake word → treat as open AI prompt? No: bare wake
        // word alone is a no-op (usually a false trigger).
        if (body == null) return Routing.NotAddressed
        if (body.isEmpty()) return Routing.NotAddressed

        // Exact local match
        for (intent in localIntents) {
            for (phrase in intent.phrases) {
                if (body == phrase) return Routing.Local(intent.id)
            }
        }
        // Prefix match ("inmo navigate home now") — command followed by extra words
        for (intent in localIntents) {
            for (phrase in intent.phrases) {
                if (body.startsWith("$phrase ") || body.startsWith("$phrase,")) {
                    return Routing.Local(intent.id)
                }
            }
        }
        // Open-ended → AI
        return Routing.Ai(body)
    }
}
