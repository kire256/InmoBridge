package com.droidforge.inmobridge.phone

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.droidforge.inmobridge.core.BridgeMessage

/**
 * Mirrors Google Maps turn-by-turn notifications to the glasses as nav cards.
 * Keyless: listens to the LOCAL Maps notifications (user grants Notification
 * access once) and forwards the maneuver title + distance. Nothing is sent to
 * any cloud service.
 */
class NavMirrorService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (sbn.packageName != MAPS_PKG) return
        if (n.extras == null) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        // Maps navigation notifications are ongoing; ignore silent/other posts.
        val isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!isOngoing && title.contains("Google Maps", ignoreCase = true)) return

        // Heuristic: title = maneuver ("Turn right"), text = street + distance
        // ("onto Main St · 400 ft"). Split distance if we can.
        val (street, distance) = splitDistance(text)
        BridgeService.instance?.sendToGlasses(
            BridgeMessage.nav(title, if (street.isBlank()) distance else "$street · $distance",
                id = System.nanoTime())
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == MAPS_PKG && sbn.notification != null) {
            // Navigation ended — clear the glasses HUD.
            BridgeService.instance?.sendToGlasses(
                BridgeMessage.nav("", "", id = System.nanoTime())
            )
        }
    }

    private fun splitDistance(text: String): Pair<String, String> {
        val idx = text.lastIndexOf('·')
        return if (idx > 0) {
            text.substring(0, idx).trim() to text.substring(idx + 1).trim()
        } else {
            // Fall back to "300 ft" at the end
            val m = Regex("(\\d+\\s*(?:ft|m|km|mi))\\s*$").find(text)
            if (m != null) {
                text.substring(0, m.range.first).trim() to m.value.trim()
            } else {
                text to ""
            }
        }
    }

    companion object {
        const val MAPS_PKG = "com.google.android.apps.maps"

        /** Open the system Notification-access settings screen. */
        fun openListenerSettings(ctx: Context) {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
