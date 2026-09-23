package com.droidforge.inmobridge.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.droidforge.inmobridge.core.BridgeMessage
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
        instance = this
        running = true
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
        val (dock, apps) = DockConfig.load(this)
        server = BridgeServer(
            PORT,
            CommandRouter(provider),
            onClientConnected = { _ -> pushLayout() },
            expectedToken = PairingManager.token(this),
        ).also { it.start() }
        // Keep the authoritative copy in memory so pushes are consistent
        currentDock = dock
        currentApps = apps
    }

    override fun onDestroy() {
        server?.stop()
        running = false
        instance = null
        super.onDestroy()
    }

    /** Send an envelope to the connected glasses. False when no server/link. */
    fun sendToGlasses(env: com.droidforge.inmobridge.core.Envelope): Boolean =
        server?.let { runCatching { it.send(env); true }.getOrDefault(false) } == true

    private var currentDock: List<com.droidforge.inmobridge.core.DockItem> = DockConfig.DEFAULT_DOCK
    private var currentApps: List<com.droidforge.inmobridge.core.DockItem> = DockConfig.DEFAULT_APPS

    /** Send the current layout to a connected glasses client. */
    fun pushLayout() {
        val target = server ?: return
        val items = currentDock + currentApps
        target.send(BridgeMessage.config(items, target.nextId()))
    }

    /** Called by the config UI after a save. */
    fun updateLayout(dock: List<com.droidforge.inmobridge.core.DockItem>,
                     apps: List<com.droidforge.inmobridge.core.DockItem>) {
        currentDock = dock
        currentApps = apps
        pushLayout()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("cmd") == "push_layout") {
            val (dock, apps) = DockConfig.load(this)
            updateLayout(dock, apps)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val PORT = 8899
        const val CHANNEL_ID = "bridge"
        const val NOTIF_ID = 42

        /** Static handle so activities can push envelopes to the glasses. */
        @Volatile
        var instance: BridgeService? = null
            private set

        /** True while the foreground service (and TCP server) is alive. */
        @Volatile
        var running: Boolean = false
            private set
    }
}
