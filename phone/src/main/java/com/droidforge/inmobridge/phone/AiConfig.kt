package com.droidforge.inmobridge.phone

import android.content.Context
import com.droidforge.inmobridge.core.CardSpec

/**
 * AI configuration (phone-side). One OpenAI-compatible client covers cloud
 * providers and local Ollama/LM Studio — they differ only by base URL, model,
 * and whether an API key is sent.
 *
 * Defaults point at a LAN Ollama instance. The API key lives in device-side
 * SharedPreferences only — never in git. Local HTTP (cleartext) is permitted
 * for private-network models; see the manifest note.
 */
data class AiConfig(
    val kind: Kind = Kind.COMPAT,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
) {
    enum class Kind { STUB, COMPAT }

    fun toProvider(): CommandRouter.AiProvider = when (kind) {
        Kind.STUB -> StubAiProvider()
        Kind.COMPAT -> OpenAiCompatProvider(baseUrl, apiKey.ifBlank { null }, model)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("kind", kind.name)
            .putString("baseUrl", baseUrl)
            .putString("apiKey", apiKey)
            .putString("model", model)
            .apply()
    }

    companion object {
        const val PREFS = "ai_config"

        /** LAN/Tailscale Ollama (OpenAI-compatible endpoint). */
        const val DEFAULT_BASE_URL = "http://192.168.68.51:11434/v1"
        const val DEFAULT_MODEL = "qwen3-coder:30b"

        /** Cloud presets for the UI. */
        val PRESET_OPENAI = "https://api.openai.com/v1" to "gpt-4o-mini"
        val PRESET_GEMINI = "https://generativelanguage.googleapis.com/v1beta/openai" to "gemini-2.0-flash"

        fun load(context: Context): AiConfig {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val kind = Kind.entries.firstOrNull { it.name == sp.getString("kind", null) }
                ?: Kind.COMPAT
            return AiConfig(
                kind = kind,
                baseUrl = sp.getString("baseUrl", null) ?: DEFAULT_BASE_URL,
                apiKey = sp.getString("apiKey", null) ?: "",
                model = sp.getString("model", null) ?: DEFAULT_MODEL,
            )
        }
    }
}

/** v0 echo provider — offline placeholder. */
class StubAiProvider : CommandRouter.AiProvider {
    override fun ask(prompt: String): CardSpec =
        CardSpec("AI (stub)", listOf("You said: $prompt", "Switch provider in the phone app."))
}
