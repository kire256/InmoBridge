package com.droidforge.inmobridge.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RelayProtocolTest {

    @Test
    fun `notif spec round trips through json`() {
        val spec = NotifSpec(
            app = "WhatsApp", pkg = "com.whatsapp", title = "Mom",
            text = "Dinner at 7?", category = "message",
            theme = "teal", sound = "ping", vibrate = "tick", timeoutMs = 5000,
        )
        val parsed = NotifSpec.fromJson(spec.toJson())
        assertEquals(spec, parsed)
    }

    @Test
    fun `notif envelope encodes and decodes`() {
        val spec = NotifSpec("Gmail", "com.gmail", "Alert", "2 new", "mail", "blue", "chime", "tick")
        val env = BridgeMessage.notif(spec, id = 42)
        val line = env.encode()
        val back = Envelope.decode(line)
        assertNotNull(back)
        assertEquals("notif", back!!.type)
        val parsed = NotifSpec.fromJson(back.payload)
        assertEquals("Gmail", parsed!!.app)
        assertEquals("blue", parsed.theme)
    }

    @Test
    fun `config envelope carries timeout and enabled`() {
        val env = BridgeMessage.config(timeoutMs = 8000, enabled = true, id = 1)
        val back = Envelope.decode(env.encode())!!
        assertEquals(8000L, back.payload.optLong("timeout"))
        assertEquals(true, back.payload.optBoolean("enabled"))
    }

    @Test
    fun `reject envelope round trips`() {
        val env = BridgeMessage.reject("bad token", id = 7)
        val back = Envelope.decode(env.encode())!!
        assertEquals("bad token", back.payload.optString("reason"))
    }

    @Test
    fun `qr payload round trips and rejects junk`() {
        val s = QrPayload.encode("192.168.68.51", 8899, "tok123", "phone")
        val p = QrPayload.decode(s)!!
        assertEquals("192.168.68.51", p.host)
        assertEquals(8899, p.port)
        assertEquals("tok123", p.token)
        assertNull(QrPayload.decode("not json"))
        assertNull(QrPayload.decode(JSONObject().put("v", 99).toString()))
    }

    @Test
    fun `theme lookup falls back to first`() {
        assertEquals("teal", NotifThemes.byId("teal").id)
        assertEquals("teal", NotifThemes.byId("nonexistent").id)
    }
}
