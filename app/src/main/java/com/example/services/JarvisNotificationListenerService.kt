package com.example.services

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return enabledListeners.contains(component)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            val bundle = sbn.notification.extras
            val title = bundle.getString("android.title") ?: ""
            val text = bundle.getCharSequence("android.text")?.toString() ?: ""

            if (title.isNotBlank() || text.isNotBlank()) {
                recentNotifications.add(0, JarvisNotification(sbn.id, sbn.packageName, title, text, sbn.postTime))
                if (recentNotifications.size > 50) {
                    recentNotifications.removeAt(recentNotifications.size - 1)
                }
            }
        }
    }
}
