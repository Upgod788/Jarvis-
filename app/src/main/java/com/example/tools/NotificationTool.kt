package com.example.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.services.JarvisNotificationListenerService

class NotificationTool : Tool {
    override val name = "NotificationTool"
    override val description = "Reads recent notifications from installed apps if notification access is granted by user."
    override val parameters = listOf(
        ToolParameter(name = "appName", type = "string", description = "Filter by app name (e.g. 'whatsapp')", required = false),
        ToolParameter(name = "clear", type = "boolean", description = "true to clear stored notifications", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        if (params["clear"] == true) {
            JarvisNotificationListenerService.clearNotifications()
            return ToolResult.ok("Notification history cleared.")
        }

        val isGranted = JarvisNotificationListenerService.isNotificationAccessGranted(context)
        if (!isGranted) {
            // Prompt to open settings
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
            return ToolResult.error(
                message = "Notification access is not enabled. Please enable JARVIS in Notification Access settings to read incoming alerts.",
                errorCode = "NOTIFICATION_ACCESS_REQUIRED"
            )
        }

        val appName = params["appName"]?.toString()?.trim()?.lowercase()
        val notifs = if (appName != null && appName.isNotBlank()) {
            JarvisNotificationListenerService.getForPackage(appName)
        } else {
            JarvisNotificationListenerService.getRecent(5)
        }

        if (notifs.isEmpty()) {
            val filterMsg = if (!appName.isNullOrBlank()) " from $appName" else ""
            return ToolResult.ok("You have no new notifications$filterMsg.")
        }

        val formatted = notifs.take(4).mapIndexed { idx, it ->
            val pkg = it.packageName.substringAfterLast(".")
            "${idx + 1}. [$pkg] ${it.title}: ${it.text}"
        }.joinToString("\n")

        return ToolResult.ok(
            message = "Here are your recent notifications:\n$formatted",
            data = mapOf("count" to notifs.size)
        )
    }
}
