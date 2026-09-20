package com.droidforge.inmobridge.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeProtocolTest {

    @Test
    fun `envelope round trip`() {
        val env = BridgeMessage.intent("inmo, navigate home", "voice", 42L)
        val decoded = Envelope.decode(env.encode())
        assertNotNull(decoded)
        assertEquals(42L, decoded!!.id)
        assertEquals(BridgeMessage.TYPE_INTENT, decoded.type)
        assertEquals("inmo, navigate home", decoded.payload.getString("text"))
    }

    @Test
    fun `version mismatch rejects`() {
        val bad = JSONObject().put("v", 99).put("id", 1L).put("type", "hello").toString()
        assertNull(Envelope.decode(bad))
        assertNull(Envelope.decode("not json"))
    }

    @Test
    fun `card round trip preserves style and timeout`() {
        val card = CardSpec("Time", listOf("3:04 PM"), style = CardSpec.STYLE_AI, timeoutMs = 2500)
        val decoded = CardSpec.fromJson(JSONObject(card.toJson().toString()))
        assertEquals(card, decoded)
    }

    @Test
    fun `config message carries dock items`() {
        val items = listOf(
            DockItem("nav", "Maps", "◈", "intent", "inmo, navigate home"),
            DockItem("ai", "AI", "✦", "toggle", "ai"),
        )
        val env = BridgeMessage.config(items, 7L)
        val decoded = Envelope.decode(env.encode())!!
        val arr = decoded.payload.getJSONArray("items")
        assertEquals(2, arr.length())
        val back = DockItem.fromJson(arr.getJSONObject(1))
        assertEquals("ai", back!!.id)
        assertEquals("toggle", back.action)
    }
}
