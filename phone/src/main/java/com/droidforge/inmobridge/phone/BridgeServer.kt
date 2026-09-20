package com.droidforge.inmobridge.phone

import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.CardSpec
import com.droidforge.inmobridge.core.Envelope
import com.droidforge.inmobridge.core.IntentRouter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Phone-side bridge: foreground service component that owns a TCP server
 * (port 8899) and routes intents. One glasses client at a time (v0); the last
 * connection wins.
 */
class BridgeServer(
    private val port: Int = 8899,
    private val router: CommandRouter,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    /** Currently connected glasses writer (null until hello). */
    @Volatile
    private var clientWriter: OutputStreamWriter? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "bridge-server", isDaemon = true) {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                while (running.get()) {
                    val s = ss.accept()
                    thread(name = "bridge-conn", isDaemon = true) { handle(s) }
                }
            } catch (_: Exception) {
                // service stopped
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        clientWriter = null
    }

    private fun handle(s: Socket) {
        try {
            s.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val w = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
            clientWriter = w
            while (running.get()) {
                val line = reader.readLine() ?: break
                val env = Envelope.decode(line) ?: continue
                when (env.type) {
                    BridgeMessage.TYPE_INTENT -> router.handle(env) { reply -> send(reply) }
                    else -> Unit
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { s.close() }
            clientWriter = null
        }
    }

    /** Push a message to the glasses (config updates, async replies). */
    fun send(env: Envelope) {
        val w = clientWriter ?: return
        thread(isDaemon = true) {
            runCatching {
                synchronized(w) {
                    w.write(env.encode() + "\n")
                    w.flush()
                }
            }
        }
    }

    private var idCounter = System.nanoTime()
    fun nextId(): Long = idCounter++
}
