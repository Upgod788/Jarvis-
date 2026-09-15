package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class UpdateNotificationManager(private val context: Context) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateConfig.NOTIFICATION_CHANNEL_ID,
                UpdateConfig.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new version of JARVIS is available"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showUpdateAvailableNotification(manifest: UpdateManifest, isCritical: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "updates")
            putExtra("target_version", manifest.latestVersionName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            UpdateConfig.NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isCritical) {
            "CRITICAL: JARVIS Update Required (${manifest.latestVersionName})"
        } else {
            "JARVIS Update Available (${manifest.latestVersionName})"
        }

        val previewNotes = manifest.releaseNotes.take(2).joinToString("\n• ", prefix = "• ")
        val notification = NotificationCompat.Builder(context, UpdateConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Version ${manifest.latestVersionName} is available for installation.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Version ${manifest.latestVersionName}:\n$previewNotes"))
            .setPriority(if (isCritical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(UpdateConfig.NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notifications permission not granted
        }
    }

    fun dismissNotification() {
        notificationManager.cancel(UpdateConfig.NOTIFICATION_ID)
    }
}
