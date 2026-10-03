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
                s.send(BridgeMessage.config(displayTimeoutMs(), relayEnabled(), s.nextId()))
            },
            expectedToken = PairingManager.token(this),
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("cmd") == "push_config") {
            server?.let {
                it.send(BridgeMessage.config(displayTimeoutMs(), relayEnabled(), it.nextId()))
            }
        }
        return START_STICKY
    }

    private fun relayEnabled(): Boolean = RelayConfig.relayEnabled(this)
    private fun displayTimeoutMs(): Long =
        getSharedPreferences("display", MODE_PRIVATE).getLong("timeout", 6000L)

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PORT = 8899
        const val CHANNEL_ID = "bridge"
        const val NOTIF_ID = 42

        @Volatile var instance: BridgeService? = null
            private set
        @Volatile var running: Boolean = false
            private set
    }
}
