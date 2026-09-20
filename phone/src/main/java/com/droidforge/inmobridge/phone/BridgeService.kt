package com.droidforge.inmobridge.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.droidforge.inmobridge.core.CardSpec

/**
 * Foreground service owning the bridge server. The connection must survive
 * screen-off and Doze — hence foreground + partial wakelock-free design
 * (socket reads keep the process alive without holding a wakelock).
 */
class BridgeService : Service() {

    private var server: BridgeServer? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Glasses bridge",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notif: Notification =
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("InmoBridge")
                .setContentText("Glasses bridge running")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()
        startForeground(NOTIF_ID, notif)

        val provider: CommandRouter.AiProvider = AiConfig.load(this).toProvider()
        server = BridgeServer(PORT, CommandRouter(provider)).also { it.start() }
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PORT = 8899
        const val CHANNEL_ID = "bridge"
        const val NOTIF_ID = 42
    }
}
