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

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "bridge-client", isDaemon = true) {
            while (running.get()) {
                try {
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
                        Envelope.decode(line)?.let(onEnvelope)
                    }
                } catch (_: Exception) {
                    // reconnect loop
                } finally {
                    runCatching { socket?.close() }
                    socket = null
                    writer = null
                }
                if (running.get()) Thread.sleep(RECONNECT_DELAY_MS)
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

    companion object {
        private const val RECONNECT_DELAY_MS = 2000L
    }
}
