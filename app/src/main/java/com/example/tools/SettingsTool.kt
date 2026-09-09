package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class SettingsTool : Tool {
    override val name = "SettingsTool"
    override val description = "Navigates to safe system settings screens (Wi-Fi, Bluetooth, Apps, Notifications, Accessibility, Display)."
    override val parameters = listOf(
        ToolParameter(name = "settingType", type = "string", description = "'wifi', 'bluetooth', 'app', 'notifications', 'accessibility', 'display', or 'all'")
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val type = params["settingType"]?.toString()?.lowercase() ?: "all"

        val intent = when {
            type.contains("wifi") || type.contains("wi-fi") ->
                Intent(Settings.ACTION_WIFI_SETTINGS)
            type.contains("bluetooth") ->
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            type.contains("app") -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            type.contains("notif") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                }
            }
            type.contains("access") ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            type.contains("display") ->
                Intent(Settings.ACTION_DISPLAY_SETTINGS)
            type.contains("sound") ->
                Intent(Settings.ACTION_SOUND_SETTINGS)
            else ->
                Intent(Settings.ACTION_SETTINGS)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            ToolResult.ok("Opened ${type.replaceFirstChar { it.uppercase() }} Settings.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open settings: ${e.localizedMessage}")
        }
    }
}
