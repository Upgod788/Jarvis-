package com.example.devices

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class DeviceDiscovery(private val context: Context) {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    suspend fun startScan(): List<Device> {
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        // Simulate local network & Bluetooth discovery of nearby smart peripherals
        delay(1200L)

        val found = listOf(
            Device(
                deviceId = "disc_smart_plug_${UUID.randomUUID().toString().take(4)}",
                name = "TP-Link Kasa Plug",
                type = DeviceType.SMART_PLUG,
                manufacturer = "TP-Link",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER),
                status = DeviceStatus.ONLINE,
                roomLocation = "Kitchen",
                isAuthorized = false
            ),
            Device(
                deviceId = "disc_smart_thermostat_${UUID.randomUUID().toString().take(4)}",
                name = "Ecobee Smart Thermostat",
                type = DeviceType.THERMOSTAT,
                manufacturer = "Ecobee",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.TEMPERATURE, Capability.POWER),
                status = DeviceStatus.ONLINE,
                roomLocation = "Hallway",
                isAuthorized = false,
                state = mapOf("temperature" to 72, "power" to true)
            ),
            Device(
                deviceId = "disc_smart_fan_${UUID.randomUUID().toString().take(4)}",
                name = "Atomberg Smart Fan",
                type = DeviceType.SMART_FAN,
                manufacturer = "Atomberg",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.VOLUME),
                status = DeviceStatus.ONLINE,
                roomLocation = "Bedroom",
                isAuthorized = false
            )
        )

        _discoveredDevices.value = found
        _isScanning.value = false
        return found
    }

    fun stopScan() {
        _isScanning.value = false
    }
}
