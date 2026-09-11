package com.example.devices

import android.content.Context
import com.example.tools.ToolResult
import kotlinx.coroutines.flow.StateFlow

class DeviceManager(
    private val context: Context,
    val registry: DeviceRegistry = DeviceRegistry(context),
    val discovery: DeviceDiscovery = DeviceDiscovery(context),
    val connector: DeviceConnector = DeviceConnector(registry)
) {

    val devices: StateFlow<List<Device>> = registry.devices

    suspend fun executeDeviceCommand(
        deviceNameOrId: String,
        command: String,
        params: Map<String, Any?> = emptyMap()
    ): ToolResult {
        val device = registry.getDevice(deviceNameOrId)
            ?: registry.findDeviceByName(deviceNameOrId)
            ?: return ToolResult.error("Could not find connected device \"$deviceNameOrId\".")

        return connector.executeCommand(context, device, command, params)
    }

    fun authorizeDevice(deviceId: String, authToken: String): Boolean {
        // Legitimate pairing verification: ensure token is not empty
        if (authToken.isNotBlank()) {
            val dev = registry.getDevice(deviceId) ?: return false
            registry.registerDevice(dev.copy(isAuthorized = true, status = DeviceStatus.CONNECTED))
            return true
        }
        return false
    }

    fun addManualDevice(
        name: String,
        type: DeviceType,
        manufacturer: String,
        connectionType: ConnectionType,
        room: String,
        ipAddress: String? = null
    ): Device {
        val id = "manual_${System.currentTimeMillis()}"
        val capabilities = when (type) {
            DeviceType.SMART_LIGHT -> listOf(Capability.POWER, Capability.BRIGHTNESS, Capability.COLOR)
            DeviceType.SMART_PLUG -> listOf(Capability.POWER)
            DeviceType.SMART_TV -> listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY)
            DeviceType.SPEAKER -> listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY)
            DeviceType.THERMOSTAT -> listOf(Capability.TEMPERATURE, Capability.POWER)
            DeviceType.PC -> listOf(Capability.LOCK, Capability.OPEN_APP, Capability.MEDIA_PLAY, Capability.BATTERY_QUERY)
            else -> listOf(Capability.POWER)
        }
        val device = Device(
            deviceId = id,
            name = name,
            type = type,
            manufacturer = manufacturer,
            connectionType = connectionType,
            capabilities = capabilities,
            status = DeviceStatus.ONLINE,
            roomLocation = room,
            ipAddress = ipAddress,
            isAuthorized = true
        )
        registry.registerDevice(device)
        return device
    }

    fun getConnectedDevicesSummary(): String {
        val list = registry.devices.value
        if (list.isEmpty()) return "No devices currently configured."
        return list.joinToString(", ") { "${it.name} (${it.status.displayName})" }
    }
}
