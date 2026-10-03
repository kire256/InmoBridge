package com.droidforge.inmobridge.phone

import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.Envelope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Phone-side bridge: TCP server (port 8899) owning the glasses connection.
 * One client at a time; the last authenticated connection wins.
 * Token auth happens on the first hello; anything else is rejected.
 */
class BridgeServer(
    private val port: Int = 8899,
    private val onClientConnected: ((BridgeServer) -> Unit)? = null,
    private val expectedToken: String? = null,
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var clientWriter: OutputStreamWriter? = null

    /** True while a token-authenticated glasses client is attached. */
    @Volatile
    var hasClient: Boolean = false
        private set

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
        hasClient = false
    }

    private fun handle(s: Socket) {
        try {
            s.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            var authenticated = expectedToken == null
            val w = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
            if (authenticated) attach(w)
            while (running.get()) {
                val line = reader.readLine() ?: break
                val env = Envelope.decode(line) ?: continue
                if (!authenticated) {
                    if (env.type == BridgeMessage.TYPE_HELLO &&
                        env.payload.optString("token") == expectedToken
                    ) {
                        authenticated = true
                        attach(w)
                        onClientConnected?.invoke(this)
                    } else {
                        runCatching {
                            synchronized(w) {
                                w.write(BridgeMessage.reject("bad token", env.id).encode() + "\n")
                                w.flush()
                            }
                        }
                        break
                    }
                    continue
                }
                // Glasses->phone messages (events) are logged and dropped for MVP.
            }
        } catch (_: Exception) {
        } finally {
            runCatching { s.close() }
            if (clientWriter === writerOf(s)) {
                clientWriter = null
                hasClient = false
            }
        }
    }

    private var currentSocket: Socket? = null
    private fun attach(w: OutputStreamWriter) {
        clientWriter = w
        hasClient = true
    }
    private fun writerOf(s: Socket): OutputStreamWriter? = clientWriter // single-client v0

    /** Push a message to the glasses (notif/config). */
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
