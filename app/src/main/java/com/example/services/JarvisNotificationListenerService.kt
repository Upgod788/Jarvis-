package com.example.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

data class JarvisNotification(
    val id: Int,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

class JarvisNotificationListenerService : NotificationListenerService() {

    companion object {
        private val recentNotifications = CopyOnWriteArrayList<JarvisNotification>()

        fun getRecent(limit: Int = 10): List<JarvisNotification> {
            return recentNotifications.take(limit)
        }

        fun getForPackage(pkgQuery: String): List<JarvisNotification> {
            return recentNotifications.filter { it.packageName.contains(pkgQuery, ignoreCase = true) }
        }

        fun clearNotifications() {
            recentNotifications.clear()
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            val component = ComponentName(context, JarvisNotificationListenerService::class.java).flattenToString()
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.contains(component)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val extras = it.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (title.isNotBlank() || text.isNotBlank()) {
                val notif = JarvisNotification(
                    id = it.id,
                    packageName = it.packageName,
                    title = title,
                    text = text,
                    postTime = it.postTime
                )
                // Add to head
                recentNotifications.add(0, notif)
                if (recentNotifications.size > 50) {
                    recentNotifications.removeAt(recentNotifications.size - 1)
                }
            }
        }
    }
}
