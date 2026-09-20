package com.droidforge.inmobridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadTest {

    @Test
    fun `round trip keeps host port token name`() {
        val s = QrPayload.encode("100.101.102.103", 8899, "aabbccddeeff0011", "Erik phone")
        val p = QrPayload.decode(s)
        assertNotNull(p)
        assertEquals("100.101.102.103", p!!.host)
        assertEquals(8899, p.port)
        assertEquals("aabbccddeeff0011", p.token)
        assertEquals("Erik phone", p.name)
    }

    @Test
    fun `name optional`() {
        val p = QrPayload.decode(QrPayload.encode("192.168.68.51", 8899, "tok"))
        assertNotNull(p)
        assertNull(p!!.name)
    }

    @Test
    fun `wrong version or garbage rejects`() {
        assertNull(QrPayload.decode("{\"v\":99,\"h\":\"x\",\"p\":1,\"t\":\"y\"}"))
        assertNull(QrPayload.decode("not json"))
        assertNull(QrPayload.decode(QrPayload.encode("", 8899, "t")))
        assertNull(QrPayload.decode(QrPayload.encode("h", 0, "t")))
        assertNull(QrPayload.decode(QrPayload.encode("h", 8899, "")))
    }

    @Test
    fun `compact for low-density qr`() {
        val s = QrPayload.encode("100.101.102.103", 8899, "aabbccddeeff0011")
        // Must stay small: dense QRs fail on cheap glasses cameras
        assert(s.length < 120) { "payload too big: ${s.length}" }
    }
}
