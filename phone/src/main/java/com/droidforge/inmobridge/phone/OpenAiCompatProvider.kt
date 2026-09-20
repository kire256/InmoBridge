package com.droidforge.inmobridge.phone

import com.droidforge.inmobridge.core.CardSpec
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat-completions client. Works with:
 *  - Ollama on the LAN/Tailscale (http://host:11434/v1, no key needed)
 *  - OpenAI (https://api.openai.com/v1 + key)
 *  - Gemini's OpenAI-compat endpoint
 *  - LM Studio / llama.cpp server / vLLM — anything speaking the dialect
 *
 * The system prompt constrains replies to HUD shape: ≤4 short lines, plain text.
 */
class OpenAiCompatProvider(
    private val baseUrl: String,
    private val apiKey: String?,
    private val model: String,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 30_000,
) : CommandRouter.AiProvider {

    override fun ask(prompt: String): CardSpec {
        val conn = (URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (!apiKey.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", prompt))
            )
            .put("max_tokens", 300)
            .put("temperature", 0.4)

        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.use { it?.readBytes()?.toString(Charsets.UTF_8).orEmpty() }
            if (code !in 200..299) {
                CardSpec(
                    "AI error (HTTP $code)",
                    listOf(text.take(180).ifBlank { "no body" }),
                    style = CardSpec.STYLE_WARN,
                    timeoutMs = 9000,
                )
            } else {
                formatResponse(JSONObject(text))
            }
        } catch (e: Exception) {
            CardSpec(
                "AI unreachable",
                listOf((e.message ?: e.javaClass.simpleName).take(120)),
                style = CardSpec.STYLE_WARN,
                timeoutMs = 9000,
            )
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SYSTEM_PROMPT =
            "You are an assistant answering on a smart-glasses heads-up display. " +
                "Reply with at most 4 short lines, each under 40 characters. " +
                "Plain text only — no markdown, no lists, no emoji. Be direct and brief."

        /** choices[0].message.content → HUD card (split/chunked to ≤4 lines). */
        fun formatResponse(o: JSONObject): CardSpec {
            val content = o.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
            if (content.isEmpty()) {
                return CardSpec("AI", listOf("(empty response)"), style = CardSpec.STYLE_WARN)
            }
            val lines = content.split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .flatMap { line ->
                    if (line.length <= 44) listOf(line)
                    else line.chunked(42).map { it.trim() }
                }
                .take(4)
            return CardSpec("AI", lines, style = CardSpec.STYLE_AI, timeoutMs = 9000)
        }
    }
}
