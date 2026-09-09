package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/**
 * System tool for toggling or opening Wi-Fi controls.
 * Supports direct toggling on Android 9 and below, and interactive
 * fast-panel overlays (Settings.Panel.ACTION_WIFI) on Android 10+.
 */
class WifiTool : Tool {
    override val name = "WifiTool"
    override val description = "Toggles Wi-Fi state or opens the Wi-Fi quick panel/settings screen."
    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "'toggle', 'on', 'off', 'panel', or 'settings'",
            required = false
        )
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase()?.trim() ?: "toggle"
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return ToolResult.error("Wi-Fi service is not available on this device.")

        val isCurrentlyEnabled = wifiManager.isWifiEnabled

        if (action == "status") {
            return ToolResult.ok(
                message = "Wi-Fi is currently ${if (isCurrentlyEnabled) "ON" else "OFF"}.",
                data = mapOf("wifiEnabled" to isCurrentlyEnabled)
            )
        }

        val targetEnabled = when (action) {
            "on", "enable", "turn_on" -> true
            "off", "disable", "turn_off" -> false
            "toggle" -> !isCurrentlyEnabled
            else -> null
        }

        // On Android 9 (API 28) and below, direct toggle is supported
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && targetEnabled != null) {
            @Suppress("DEPRECATION")
            val success = wifiManager.setWifiEnabled(targetEnabled)
            return if (success) {
                ToolResult.ok(
                    message = "Wi-Fi turned ${if (targetEnabled) "ON" else "OFF"}.",
                    data = mapOf("wifiEnabled" to targetEnabled)
                )
            } else {
                // Fallback to opening Wi-Fi settings
                openWifiSettings(appContext)
                ToolResult.ok("Opened Wi-Fi settings to switch state.")
            }
        }

        // On Android 10+ (Q, API 29+), opening the native bottom-sheet Wi-Fi panel
        // gives the user instant 1-tap toggle access without full screen disruption
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(panelIntent)
                val desiredText = when (targetEnabled) {
                    true -> "turn ON"
                    false -> "turn OFF"
                    else -> "toggle"
                }
                ToolResult.ok(
                    message = "Opened Wi-Fi quick panel to $desiredText. Current Wi-Fi state is ${if (isCurrentlyEnabled) "ON" else "OFF"}.",
                    data = mapOf(
                        "wifiEnabled" to isCurrentlyEnabled,
                        "panelOpened" to true
                    )
                )
            } catch (e: Exception) {
                openWifiSettings(appContext)
                ToolResult.ok("Opened Wi-Fi settings.")
            }
        } else {
            openWifiSettings(appContext)
            ToolResult.ok("Opened Wi-Fi settings.")
        }
    }

    private fun openWifiSettings(context: Context) {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
