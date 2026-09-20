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
 *  - hello    : handshake (side, device name, protocol version)
 *  - config   : phone → glasses: full dock + container assignment (authoritative)
 *  - intent   : glasses → phone: a voice/text command awaiting routing
 *  - reply    : phone → glasses: a rendered card in response to an intent (corr. by "replyTo")
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
    const val TYPE_CONFIG = "config"
    const val TYPE_INTENT = "intent"
    const val TYPE_REPLY = "reply"
    const val TYPE_EVENT = "event"

    fun hello(side: String, deviceName: String, id: Long): Envelope = Envelope(
        id, TYPE_HELLO,
        JSONObject().put("side", side).put("device", deviceName)
    )

    fun config(items: List<DockItem>, id: Long): Envelope = Envelope(
        id, TYPE_CONFIG,
        JSONObject().put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    )

    fun intent(text: String, source: String, id: Long): Envelope = Envelope(
        id, TYPE_INTENT,
        JSONObject().put("text", text).put("source", source)
    )

    fun reply(replyTo: Long, card: CardSpec, id: Long): Envelope = Envelope(
        id, TYPE_REPLY,
        card.toJson().put("replyTo", replyTo)
    )

    fun event(name: String, data: JSONObject = JSONObject(), id: Long): Envelope =
        Envelope(id, TYPE_EVENT, JSONObject().put("name", name).put("data", data))
}

/** One feature tile assignable from the phone. */
data class DockItem(
    val id: String,
    val label: String,
    val icon: String,          // glyph name resolved on the glasses
    val action: String,        // "nav", "view", "toggle", "launch", "intent"
    val arg: String = "",      // e.g. a URI to launch, or an intent phrase
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("label", label).put("icon", icon)
        .put("action", action).put("arg", arg)

    companion object {
        fun fromJson(o: JSONObject): DockItem? = runCatching {
            DockItem(
                id = o.getString("id"),
                label = o.getString("label"),
                icon = o.optString("icon", "◇"),
                action = o.optString("action", "view"),
                arg = o.optString("arg", ""),
            )
        }.getOrNull()
    }
}

/**
 * HUD response card — deliberately minimal: a title, up to ~4 body lines,
 * a style, and an on-screen timeout. No lists, no images, no scrolling:
 * the glasses render text fast and the user reads at a glance.
 */
data class CardSpec(
    val title: String,
    val body: List<String>,
    val style: String = STYLE_INFO,   // info | ai | warn
    val timeoutMs: Long = 6000,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("body", JSONArray(body))
        .put("style", style)
        .put("timeout", timeoutMs)

    companion object {
        const val STYLE_INFO = "info"
        const val STYLE_AI = "ai"
        const val STYLE_WARN = "warn"

        fun fromJson(o: JSONObject): CardSpec? = runCatching {
            val bodyArr = o.optJSONArray("body") ?: JSONArray()
            val body = ArrayList<String>(bodyArr.length())
            for (i in 0 until bodyArr.length()) body.add(bodyArr.getString(i))
            CardSpec(
                title = o.getString("title"),
                body = body,
                style = o.optString("style", STYLE_INFO),
                timeoutMs = o.optLong("timeout", 6000),
            )
        }.getOrNull()
    }
}
