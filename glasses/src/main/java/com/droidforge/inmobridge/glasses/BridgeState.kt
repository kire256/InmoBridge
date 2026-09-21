package com.droidforge.inmobridge.glasses

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Live bridge connection state, shared between the launcher, the pairing
 * screen and the TCP client.
 *
 * CONNECTED means the phone ACCEPTED our pairing token (the glasses received
 * an envelope on the current socket — the server only talks to authenticated
 * clients). REJECTED means the server answered with a reject envelope (stale
 * token: re-scan the QR).
 */
object BridgeState {
    const val DISCONNECTED = 0
    const val CONNECTING = 1
    const val CONNECTED = 2
    const val REJECTED = 3

    @Volatile
    var status: Int = DISCONNECTED
        private set

    private val listeners = CopyOnWriteArrayList<(Int) -> Unit>()

    /** Register a listener; it fires immediately with the current status. */
    fun addListener(l: (Int) -> Unit) {
        listeners.add(l)
        l(status)
    }

    fun removeListener(l: (Int) -> Unit) {
        listeners.remove(l)
    }

    fun set(s: Int) {
        if (status == s) return
        status = s
        listeners.forEach { runCatching { it(s) } }
    }
}
