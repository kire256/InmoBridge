package com.droidforge.inmobridge.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire protocol between the phone (server) and the glasses (client).
 *
 * Transport: TCP socket, UTF-8, one JSON object per line (newline-delimited).
 * Every message is an envelope: { "v": 1, "id": <monotonic>, "type": "...", ... }
 *
 * Message types:
 *  - hello    : handshake (side, device name, token)
 *  - reject   : server -> glasses: bad token, closing
 *  - config   : phone -> glasses: display settings (timeout, quiet hours)
 *  - notif    : phone -> glasses: one mirrored notification (NotifSpec)
 *  - event    : either way: status pings, battery, connection health
 */

data class Envelope(
    val id: Long,
    val type: String,
    val payload: JSONObject,
) {
    fun encode(): String = JSONObject()
        .put("v", PROTOCOL_VERSION)
        .put("id", id)
        .put("type", type)
        .put("payload", payload)
        .toString()

    companion object {
        const val PROTOCOL_VERSION = 1

        fun decode(line: String): Envelope? = runCatching {
            val o = JSONObject(line)
            val v = o.optInt("v", 0)
            if (v != PROTOCOL_VERSION) return null
            Envelope(
                id = o.getLong("id"),
                type = o.getString("type"),
                payload = o.optJSONObject("payload") ?: JSONObject(),
            )
        }.getOrNull()
    }
}

/** Factory + helpers for the well-known message types. */
object BridgeMessage {
    const val TYPE_HELLO = "hello"
    const val TYPE_REJECT = "reject"
    const val TYPE_CONFIG = "config"
    const val TYPE_NOTIF = "notif"
    const val TYPE_EVENT = "event"
    const val TYPE_APK_BEGIN = "apk_begin"
    const val TYPE_APK_CHUNK = "apk_chunk"
    const val TYPE_APK_END = "apk_end"

    /** Start an APK transfer to the glasses (name is a display label). */
    fun apkBegin(name: String, size: Long, id: Long): Envelope =
        Envelope(id, TYPE_APK_BEGIN, JSONObject().put("name", name).put("size", size))

    /** One 48KB-ish chunk, base64-encoded. */
    fun apkChunk(index: Int, bytes: ByteArray, id: Long): Envelope = Envelope(
        id, TYPE_APK_CHUNK,
        JSONObject().put("index", index)
            .put("b64", java.util.Base64.getEncoder().encodeToString(bytes)),
    )

    /** Finish transfer; glasses verify and install. */
    fun apkEnd(chunks: Int, id: Long): Envelope =
        Envelope(id, TYPE_APK_END, JSONObject().put("chunks", chunks))

    fun hello(side: String, deviceName: String, id: Long, token: String? = null): Envelope =
        Envelope(
            id, TYPE_HELLO,
            JSONObject().put("side", side).put("device", deviceName).apply {
                if (token != null) put("token", token)
            }
        )

    fun reject(reason: String, id: Long): Envelope =
        Envelope(id, TYPE_REJECT, JSONObject().put("reason", reason))

    /** Display settings push: overlay timeout + master switch + text cap. */
    fun config(timeoutMs: Long, enabled: Boolean, id: Long, maxLines: Int = 3, autoScrollMs: Long = 4000L): Envelope = Envelope(
        id, TYPE_CONFIG,
        JSONObject().put("timeout", timeoutMs).put("enabled", enabled)
            .put("maxLines", maxLines).put("autoScrollMs", autoScrollMs),
    )

    fun notif(spec: NotifSpec, id: Long): Envelope =
        Envelope(id, TYPE_NOTIF, spec.toJson())

    fun event(name: String, data: JSONObject = JSONObject(), id: Long): Envelope =
        Envelope(id, TYPE_EVENT, JSONObject().put("name", name).put("data", data))
}

/**
 * QR pairing payload shown by the phone and scanned by the glasses.
 * Format: compact JSON with v, host, port, token.
 */
object QrPayload {
    const val VERSION = 1

    data class Parsed(val host: String, val port: Int, val token: String, val name: String?)

    fun encode(host: String, port: Int, token: String, name: String? = null): String =
        JSONObject()
            .put("v", VERSION)
            .put("h", host)
            .put("p", port)
            .put("t", token)
            .apply { if (name != null) put("n", name) }
            .toString()

    fun decode(s: String): Parsed? = runCatching {
        val o = JSONObject(s)
        if (o.optInt("v") != VERSION) return null
        val host = o.optString("h")
        val port = o.optInt("p")
        val token = o.optString("t")
        if (host.isEmpty() || port <= 0 || token.isEmpty()) return null
        Parsed(host, port, token, o.optString("n").takeIf { it.isNotEmpty() })
    }.getOrNull()
}
