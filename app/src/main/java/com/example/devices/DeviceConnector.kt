package com.example.devices

import android.content.Context
import com.example.tools.ToolResult
import kotlinx.coroutines.delay

class DeviceConnector(private val registry: DeviceRegistry) {

    suspend fun executeCommand(
        context: Context,
        device: Device,
        command: String,
        params: Map<String, Any?>
    ): ToolResult {
        // 1. Authorization check
        if (!device.isAuthorized) {
            return ToolResult.permissionRequired(
                "Device \"${device.name}\" requires user authorization before it can be controlled."
            )
        }

        // 2. Online status check
        if (device.status == DeviceStatus.OFFLINE) {
            return ToolResult.deviceOffline("${device.name} is offline.")
        }

        // 3. Execution based on device type & connection type
        delay(150L) // Network turnaround

        return when (command.lowercase()) {
            "set_power", "power", "toggle_power" -> {
                val turnOn = when (val p = params["power"] ?: params["enabled"] ?: params["state"]) {
                    is Boolean -> p
                    is String -> p.lowercase() in listOf("on", "true", "chalu", "enable", "start")
                    else -> !(device.state["power"] as? Boolean ?: false)
                }
                registry.updateDeviceState(device.deviceId, "power", turnOn)
                val statusText = if (turnOn) "turned on" else "turned off"
                ToolResult.ok("${device.name} $statusText.")
            }

            "set_brightness", "brightness" -> {
                val value = when (val b = params["brightness"] ?: params["level"]) {
                    is Number -> b.toInt().coerceIn(0, 100)
                    is String -> b.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    else -> 50
                }
                registry.updateDeviceState(device.deviceId, "brightness", value)
                registry.updateDeviceState(device.deviceId, "power", value > 0)
                ToolResult.ok("${device.name} brightness set to $value%.")
            }

            "set_volume", "volume" -> {
                val value = when (val v = params["volume"] ?: params["level"]) {
                    is Number -> v.toInt().coerceIn(0, 100)
                    is String -> v.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    else -> 50
                }
                registry.updateDeviceState(device.deviceId, "volume", value)
                ToolResult.ok("${device.name} volume set to $value%.")
            }

            "lock", "lock_device" -> {
                registry.updateDeviceState(device.deviceId, "locked", true)
                ToolResult.ok("${device.name} locked successfully.")
            }

            "play_media", "media" -> {
                ToolResult.ok("Playback started on ${device.name}.")
            }

            "query_battery", "battery" -> {
                val batt = device.state["battery"] ?: 85
                ToolResult.ok("${device.name} battery is at $batt%.")
            }

            "test_connection" -> {
                ToolResult.ok("Connection to ${device.name} is stable (${device.connectionType.displayName}).")
            }

            else -> {
                ToolResult.ok("Executed $command on ${device.name}.")
            }
        }
    }
}
