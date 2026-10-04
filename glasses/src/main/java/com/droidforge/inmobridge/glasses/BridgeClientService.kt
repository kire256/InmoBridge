package com.droidforge.inmobridge.glasses

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.Envelope

/**
 * Foreground service holding the phone connection. The overlay, sound and
 * vibration live here too, so a notification can light the display up even
 * when nothing else is running.
 */
class BridgeClientService : Service() {

    private var client: BridgeClient? = null

    private val onEnvelope = { env: Envelope ->
        when (env.type) {
            BridgeMessage.TYPE_NOTIF -> NotifOverlay.onEnvelope(env)
            BridgeMessage.TYPE_APK_BEGIN, BridgeMessage.TYPE_APK_CHUNK, BridgeMessage.TYPE_APK_END ->
                ApkReceiver.onEnvelope(env, ::sendEvent)
            BridgeMessage.TYPE_CONFIG -> {
                // config.enabled == false -> overlay could hide; MVP ignores.
            }
        }
    }

    /** Send an event envelope back to the phone. */
    private fun sendEvent(env: Envelope) {
        client?.send(env)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bridge link", NotificationManager.IMPORTANCE_LOW)
        )
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("InmoBridge")
            .setContentText("Connected to phone relay")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)

        NotifOverlay.init(this)
        ApkReceiver.AppHolder.ctx = this

        val pairing = PairingStore.load(this)
        val host = pairing?.host ?: "192.168.68.51"
        val port = pairing?.port ?: 8899
        client = BridgeClient(host, port, onEnvelope,
            token = pairing?.token, deviceName = android.os.Build.MODEL ?: "glasses")
        client?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-pairing: restart the client with the new address/token.
        if (intent?.getBooleanExtra("repair", false) == true) {
            client?.stop()
            val pairing = PairingStore.load(this)
            client = BridgeClient(
                pairing?.host ?: "192.168.68.51", pairing?.port ?: 8899, onEnvelope,
                token = pairing?.token, deviceName = android.os.Build.MODEL ?: "glasses")
            client?.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "bridge_client"
        const val NOTIF_ID = 41
    }
}
