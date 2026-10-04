package com.droidforge.inmobridge.core

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `apk envelopes round trip with binary payload`() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val begin = BridgeMessage.apkBegin("test.apk", bytes.size.toLong(), 1)
        val chunks = bytes.toList().chunked(48_000).map { it.toByteArray() }
        val chunkEnvs = chunks.mapIndexed { i, c -> BridgeMessage.apkChunk(i, c, 2L + i) }
        val end = BridgeMessage.apkEnd(chunks.size, 99)

        val b = Envelope.decode(begin.encode())!!
        assertEquals("apk_begin", b.type)
        assertEquals(bytes.size.toLong(), b.payload.optLong("size"))

        // decode every chunk line and reassemble
        val out = ArrayList<Byte>()
        chunkEnvs.forEach { env ->
            val back = Envelope.decode(env.encode())!!
            assertEquals("apk_chunk", back.type)
            assertEquals(back.id, env.id)
            val data = java.util.Base64.getDecoder().decode(back.payload.optString("b64"))
            out.addAll(data.toList())
        }
        assertArrayEquals(bytes, out.toByteArray())

        val e = Envelope.decode(end.encode())!!
        assertEquals(chunks.size, e.payload.optInt("chunks"))
    }

    @Test
    fun `deduper suppresses identical reposts but allows new content`() {
        val d = NotifDeduper(windowMs = 86_400_000L)
        val t0 = 1_000_000L
        assertTrue(d.shouldMirror("tg:1", "Telegram", "New login from Firefox", t0))
        // Telegram re-posts the same alert 30 min later -> suppressed (24h window)
        assertTrue(!d.shouldMirror("tg:1", "Telegram", "New login from Firefox", t0 + 1_800_000))
        // same key, NEW content (chat grew) -> mirrors
        assertTrue(d.shouldMirror("tg:1", "Telegram", "New login from Firefox\nAlice: hi", t0 + 1_800_500))
        // window off -> everything mirrors
        val dOff = NotifDeduper(windowMs = 0)
        assertTrue(dOff.shouldMirror("k", "a", "b", 0))
        assertTrue(dOff.shouldMirror("k", "a", "b", 1))
        // after the window expires the identical alert mirrors again
        val d2 = NotifDeduper(windowMs = 60_000)
        assertTrue(d2.shouldMirror("k", "a", "b", 0))
        assertTrue(!d2.shouldMirror("k", "a", "b", 30_000))
        assertTrue(d2.shouldMirror("k", "a", "b", 61_000))
    }

    @Test
    fun `theme lookup falls back to first`() {
        assertEquals("teal", NotifThemes.byId("teal").id)
        assertEquals("teal", NotifThemes.byId("nonexistent").id)
    }
}
