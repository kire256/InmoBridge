package com.droidforge.inmobridge.phone

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.NotifSpec

/**
 * Captures phone notifications, applies the per-app allowlist, classifies and
 * styles them (RelayConfig), and mirrors matching ones to the glasses.
 *
 * Requires the user to grant Notification access once (Settings screen has a
 * shortcut). This is the heart of the relay.
 */
class RelayService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        INSTANCE = this
        connected = true
        // Android replays every ACTIVE notification to a (re)connecting listener.
        // Snapshot them so onNotificationPosted skips the replay burst; only
        // notifications that arrive after connect get mirrored.
        replayGuard.clear()
        runCatching {
            activeNotifications?.forEach { replayGuard.add(it.key) }
        }
    }

    private val replayGuard = HashSet<String>()
    private var lastMirrorHash = 0
    private var lastMirrorAt = 0L

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        INSTANCE = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return                      // never mirror ourselves
        if (!RelayConfig.relayEnabled(this)) return
        if (!RelayConfig.isAppAllowed(this, pkg)) return    // per-app allowlist
        if (replayGuard.remove(sbn.key)) return             // active-at-connect replay, not new
        if (n.extras == null) return

        // Skip silent/progress notifications (downloads, media progress bars)
        val flags = n.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        if (extras.get(Notification.EXTRA_PROGRESS_MAX) != null &&
            (extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0)) return

        val type = classify(pkg, extras, n.category)

        // Suppress double-post bursts of identical content (3s window), so a
        // single message never re-mirrors — but genuine repeats still get through.
        val h = listOf(pkg, title, text).hashCode()
        val now = System.currentTimeMillis()
        if (h == lastMirrorHash && now - lastMirrorAt < 3000) return
        lastMirrorHash = h; lastMirrorAt = now

        val (theme, sound, vibrate) = RelayConfig.ruleFor(this, type)
        val spec = NotifSpec(
            app = appLabel(pkg),
            pkg = pkg,
            title = title.ifBlank { appLabel(pkg) },
            text = text,
            category = type,
            theme = theme,
            sound = sound,
            vibrate = vibrate,
        )
        BridgeService.instance?.sendToGlasses(BridgeMessage.notif(spec, nextId()))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // MVP: overlays auto-dismiss on a timer; no removal mirroring yet.
    }

    // ---- classification ----

    private fun classify(pkg: String, extras: Bundle, notifCategory: String?): String {
        val c = notifCategory.orEmpty()
        return when {
            c == Notification.CATEGORY_CALL -> RelayConfig.CALL
            c == Notification.CATEGORY_MESSAGE -> RelayConfig.MSG
            c == Notification.CATEGORY_EMAIL -> RelayConfig.MAIL
            c == Notification.CATEGORY_EVENT -> RelayConfig.CALENDAR
            c == Notification.CATEGORY_NAVIGATION -> RelayConfig.NAV
            c == Notification.CATEGORY_TRANSPORT -> RelayConfig.NAV
            c == Notification.CATEGORY_PROGRESS -> RelayConfig.MEDIA
            c == Notification.CATEGORY_SOCIAL -> RelayConfig.MSG
            else -> byPackage(pkg)
        }
    }

    private fun byPackage(pkg: String): String = when {
        pkg.contains("mail", true) || pkg.contains("gmail", true) -> RelayConfig.MAIL
        pkg.contains("message", true) || pkg.contains("whatsapp", true) ||
            pkg.contains("telegram", true) || pkg.contains("signal", true) ||
            pkg.contains("messenger", true) || pkg.contains("sms", true) -> RelayConfig.MSG
        pkg.contains("dialer", true) || pkg.contains("phone", true) -> RelayConfig.CALL
        pkg.contains("calendar", true) -> RelayConfig.CALENDAR
        pkg.contains("maps", true) || pkg.contains("waze", true) -> RelayConfig.NAV
        pkg.contains("music", true) || pkg.contains("spotify", true) ||
            pkg.contains("youtube", true) || pkg.contains("podcast", true) -> RelayConfig.MEDIA
        pkg.contains("settings", true) || pkg.contains("system", true) ||
            pkg.contains("android", true) -> RelayConfig.SYSTEM
        else -> RelayConfig.OTHER
    }

    private val labelCache = HashMap<String, String>()
    private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)
    }

    private fun nextId(): Long = System.nanoTime()

    companion object {
        @Volatile var INSTANCE: RelayService? = null
            private set
        @Volatile var connected: Boolean = false
            private set

        /** True when the user granted notification access. */
        fun isListenerConnected(): Boolean = connected

        /** Open the system notification-access settings. */
        fun openListenerSettings(ctx: Context) {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
