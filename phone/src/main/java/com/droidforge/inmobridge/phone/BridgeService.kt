package com.droidforge.inmobridge.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.droidforge.inmobridge.core.BridgeMessage

/**
 * Foreground service owning the bridge server. Relays phone notifications to
 * the glasses (see RelayService); survives screen-off via foreground priority.
 */
class BridgeService : Service() {

    private var server: BridgeServer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        running = true
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Glasses relay", NotificationManager.IMPORTANCE_LOW)
        )
        val notif: Notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("InmoBridge")
                .setContentText("Relaying notifications to glasses")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()
        startForeground(NOTIF_ID, notif)

        server = BridgeServer(
            PORT,
            onClientConnected = { s ->
                // Push current display config on every (re)connect.
                s.send(BridgeMessage.config(displayTimeoutMs(), relayEnabled(), s.nextId(), RelayConfig.maxLines(this), RelayConfig.autoScrollMs(this)))
            },
            expectedToken = PairingManager.token(this),
            onEvent = { env -> onGlassesEvent(env) },
        ).also { it.start() }
    }

    override fun onDestroy() {
        server?.stop()
        running = false
        instance = null
        super.onDestroy()
    }

    /** Send an envelope to the connected glasses. */
    fun sendToGlasses(env: com.droidforge.inmobridge.core.Envelope): Boolean =
        server?.let { runCatching { it.send(env); true }.getOrDefault(false) } == true

    /** Fire a sample notification through the full relay path to the glasses. */
    fun sendTestNotification(): Boolean {
        val spec = com.droidforge.inmobridge.core.NotifSpec(
            app = "InmoBridge", pkg = packageName,
            title = "Test notification",
            text = "If you can read this, the relay works!",
            category = RelayConfig.MSG, theme = "teal", sound = "ping", vibrate = "tick",
            timeoutMs = 8000,
        )
        return sendToGlasses(BridgeMessage.notif(spec, server?.nextId() ?: 0L))
    }

    /**
     * Stream an APK to the glasses (apk_begin/chunks/end). Returns false
     * immediately if no client is attached; install result arrives as an
     * apk_result event.
     */
    fun sendApk(apk: java.io.File, displayName: String): Boolean {
        val s = server ?: return false
        if (!s.hasClient) return false
        Thread {
            try {
                val size = apk.length()
                s.send(BridgeMessage.apkBegin(displayName, size, s.nextId()))
                val buf = ByteArray(48 * 1024)
                var index = 0
                apk.inputStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        val chunk = if (n == buf.size) buf else buf.copyOf(n)
                        s.send(BridgeMessage.apkChunk(index, chunk, s.nextId()))
                        index++
                        Thread.sleep(15) // keep the glasses' reader from choking
                    }
                }
                s.send(BridgeMessage.apkEnd(index, s.nextId()))
            } catch (_: Exception) {
            }
        }.start()
        return true
    }

    /** Glasses->phone events (install progress/results) for the UI. */
    private fun onGlassesEvent(env: com.droidforge.inmobridge.core.Envelope) {
        Companion.dispatch(env)
    }

    companion object {
        const val PORT = 8899
        const val CHANNEL_ID = "bridge"
        const val NOTIF_ID = 42

        @Volatile var instance: BridgeService? = null
            private set
        @Volatile var running: Boolean = false
            private set

        private val eventListeners = java.util.concurrent.CopyOnWriteArrayList<(com.droidforge.inmobridge.core.Envelope) -> Unit>()

        fun addEventListener(l: (com.droidforge.inmobridge.core.Envelope) -> Unit) { eventListeners.add(l) }
        fun removeEventListener(l: (com.droidforge.inmobridge.core.Envelope) -> Unit) { eventListeners.remove(l) }
        fun dispatch(env: com.droidforge.inmobridge.core.Envelope) { eventListeners.forEach { runCatching { it(env) } } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("cmd") == "push_config") {
            pushConfig()
        }
        return START_STICKY
    }

    private fun relayEnabled(): Boolean = RelayConfig.relayEnabled(this)
    private fun displayTimeoutMs(): Long =
        getSharedPreferences("display", MODE_PRIVATE).getLong("timeout", 6000L)

    /** Push current display config to the glasses (settings changed). */
    fun pushConfig() {
        server?.let {
            it.send(BridgeMessage.config(displayTimeoutMs(), relayEnabled(), it.nextId(), RelayConfig.maxLines(this), RelayConfig.autoScrollMs(this)))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
