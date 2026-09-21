package com.droidforge.inmobridge.glasses

import com.droidforge.inmobridge.core.Envelope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Glasses-side TCP client for the phone bridge. Newline-delimited JSON over a
 * persistent socket with auto-reconnect (the connection must survive the phone
 * screen locking — the bridge runs as a foreground service).
 *
 * Connection state is published to [BridgeState]: CONNECTING while dialing,
 * CONNECTED once the phone accepts the token handshake (first inbound frame),
 * REJECTED if the server sends a reject (stale token).
 */
class BridgeClient(
    private val host: String,
    private val port: Int,
    private val onEnvelope: (Envelope) -> Unit,
    private val token: String? = null,
    private val deviceName: String = "glasses",
) {
    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var connectAttempts = 0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        BridgeState.set(BridgeState.CONNECTING)
        thread(name = "bridge-client", isDaemon = true) {
            while (running.get()) {
                try {
                    BridgeState.set(BridgeState.CONNECTING)
                    val s = Socket(host, port)
                    socket = s
                    s.tcpNoDelay = true
                    writer = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                    // Authenticate first thing on every (re)connect
                    val hello = com.droidforge.inmobridge.core.BridgeMessage
                        .hello("glasses", deviceName, System.nanoTime(), token)
                    synchronized(writer!!) {
                        writer!!.write(hello.encode() + "\n")
                        writer!!.flush()
                    }
                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        // The server only speaks to token-authenticated clients,
                        // so any inbound frame means the pairing was ACCEPTED.
                        if (BridgeState.status != BridgeState.CONNECTED) {
                            connectAttempts = 0
                            BridgeState.set(BridgeState.CONNECTED)
                        }
                        val env = Envelope.decode(line)
                        if (env == null) continue
                        if (env.type == com.droidforge.inmobridge.core.BridgeMessage.TYPE_REJECT) {
                            BridgeState.set(BridgeState.REJECTED)
                            break // stale token won't fix itself; re-pairing relaunches
                        }
                        onEnvelope(env)
                    }
                } catch (_: Exception) {
                    // reconnect loop
                } finally {
                    runCatching { socket?.close() }
                    socket = null
                    writer = null
                }
                if (running.get()) {
                    if (BridgeState.status == BridgeState.CONNECTED) {
                        BridgeState.set(BridgeState.CONNECTING)
                    }
                    connectAttempts = (connectAttempts + 1).coerceAtMost(8)
                    Thread.sleep(1000L * (1L shl connectAttempts).coerceAtMost(16))
                }
            }
            if (BridgeState.status != BridgeState.REJECTED) {
                BridgeState.set(BridgeState.DISCONNECTED)
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
    }

    fun send(env: Envelope) {
        val w = writer ?: return
        thread(isDaemon = true) {
            runCatching {
                synchronized(w) {
                    w.write(env.encode() + "\n")
                    w.flush()
                }
            }
        }
    }
}
