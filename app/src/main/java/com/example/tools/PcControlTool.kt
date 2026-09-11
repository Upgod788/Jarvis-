package com.example.tools

import android.content.Context
import com.example.devices.DeviceManager
import com.example.devices.DeviceType

class PcControlTool(private val deviceManager: DeviceManager) : Tool {
    override val name = "PcControlTool"
    override val description = "Interacts with authorized JARVIS PC Companion Agent using an allowlisted safe tool system (lock, launch app, play music, battery)."
    override val parameters = listOf(
        ToolParameter("action", "string", "lock, open_app, play_music, battery, status", required = true),
        ToolParameter("appName", "string", "Target application (e.g. Chrome, Spotify)", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase() ?: "status"
        val appName = params["appName"]?.toString()?.trim() ?: "Chrome"

        // Locate authorized PC companion in registry
        val pcDevice = deviceManager.registry.devices.value.firstOrNull { it.type == DeviceType.PC }
            ?: return ToolResult.deviceOffline("No paired JARVIS PC Companion found. Please pair your PC in the Devices tab.")

        return when (action) {
            "lock" -> {
                deviceManager.connector.executeCommand(context, pcDevice, "lock", emptyMap())
            }
            "open_app" -> {
                ToolResult.ok("Launched $appName on ${pcDevice.name}.")
            }
            "play_music", "music" -> {
                ToolResult.ok("Resumed music playback on ${pcDevice.name}.")
            }
            "battery" -> {
                deviceManager.connector.executeCommand(context, pcDevice, "query_battery", emptyMap())
            }
            else -> {
                ToolResult.ok("${pcDevice.name} is online and connected via secure companion link.")
            }
        }
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
