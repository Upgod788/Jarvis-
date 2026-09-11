package com.example.tools

import android.content.Context
import com.example.devices.DeviceManager

class SmartDeviceTool(private val deviceManager: DeviceManager) : Tool {
    override val name = "SmartDeviceTool"
    override val description = "Controls authorized smart home devices including lights, plugs, TVs, speakers, and thermostats, or discovers available devices."
    override val parameters = listOf(
        ToolParameter("deviceName", "string", "Target device name or room (e.g. 'bedroom light', 'tv', 'living room light')", required = false),
        ToolParameter("action", "string", "Action to perform: power, brightness, volume, status, query_devices", required = true),
        ToolParameter("state", "string", "State parameter: on, off, or numeric percentage 0-100", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase() ?: "power"
        val deviceName = params["deviceName"]?.toString()?.trim() ?: ""
        val state = params["state"]?.toString()?.trim()

        if (action == "query_devices" || action == "list" || deviceName.isBlank()) {
            val summary = deviceManager.getConnectedDevicesSummary()
            return ToolResult.ok("Connected devices: $summary", mapOf("summary" to summary))
        }

        val cmd = when {
            action.contains("bright") -> "set_brightness"
            action.contains("vol") -> "set_volume"
            else -> "set_power"
        }

        val execParams = mutableMapOf<String, Any?>()
        if (state != null) {
            val num = state.replace("%", "").toIntOrNull()
            if (num != null) {
                execParams["level"] = num
                execParams["brightness"] = num
                execParams["volume"] = num
            }
            execParams["power"] = state in listOf("on", "true", "chalu", "enable")
        }

        return deviceManager.executeDeviceCommand(deviceName, cmd, execParams)
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
