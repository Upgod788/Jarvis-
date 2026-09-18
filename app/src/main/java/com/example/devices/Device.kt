package com.example.devices

import android.content.Context
import android.content.SharedPreferences
import com.example.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

enum class Capability(val id: String) {
    POWER("power"),
    BRIGHTNESS("brightness"),
    COLOR("color"),
    VOLUME("volume"),
    MEDIA_PLAY("media_play"),
    LOCK("lock"),
    TEMPERATURE("temperature"),
    BATTERY_QUERY("battery_query"),
    OPEN_APP("open_app"),
    NOTIFICATIONS("notifications")
}

enum class ConnectionType(val displayName: String) {
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    LOCAL_NETWORK("Local Network"),
    CLOUD_INTEGRATION("Cloud API"),
    COMPANION_AGENT("Ravan Companion Agent"),
    SYSTEM("Android System")
}

enum class DeviceStatus(val displayName: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    CONNECTED("Connected"),
    REQUIRES_PERMISSION("Requires Permission")
}

enum class DeviceType(val displayName: String) {
    SMART_LIGHT("Smart Light"),
    SMART_PLUG("Smart Plug"),
    SMART_TV("Smart TV"),
    SPEAKER("Smart Speaker"),
    THERMOSTAT("Thermostat"),
    PC("Windows/Linux PC"),
    PHONE("Android Phone"),
    TABLET("Android Tablet"),
    SMART_LOCK("Smart Lock"),
    GENERIC("Generic IoT Device")
}

data class Device(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val manufacturer: String,
    val connectionType: ConnectionType,
    val capabilities: List<Capability>,
    val status: DeviceStatus = DeviceStatus.ONLINE,
    val permissions: List<String> = emptyList(),
    val roomLocation: String = "Living Room",
    val ipAddress: String? = null,
    val isAuthorized: Boolean = true,
    val state: Map<String, Any?> = emptyMap()
)

class DeviceRegistry(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_device_registry", Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        val raw = prefs.getString("devices_json", null)
        if (raw.isNullOrBlank()) {
            val defaults = getDefaultDevices()
            _devices.value = defaults
            saveDevices(defaults)
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<Device>()
                for (i in 0 until array.length()) {
                    list.add(parseDevice(array.getJSONObject(i)))
                }
                _devices.value = list
            } catch (e: Exception) {
                _devices.value = getDefaultDevices()
            }
        }
    }

    private fun getDefaultDevices(): List<Device> {
        return listOf(
            Device(
                deviceId = "dev_light_living",
                name = "Living Room Light",
                type = DeviceType.SMART_LIGHT,
                manufacturer = "Ravan Smart Home",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.BRIGHTNESS, Capability.COLOR),
                status = DeviceStatus.ONLINE,
                roomLocation = "Living Room",
                state = mapOf("power" to true, "brightness" to 80, "color" to "#00D2FF")
            ),
            Device(
                deviceId = "dev_pc_workstation",
                name = "Ravan PC Workstation",
                type = DeviceType.PC,
                manufacturer = "Custom PC",
                connectionType = ConnectionType.COMPANION_AGENT,
                capabilities = listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY, Capability.OPEN_APP),
                status = DeviceStatus.ONLINE,
                roomLocation = "Office",
                ipAddress = "192.168.1.105",
                state = mapOf("power" to true, "volume" to 65)
            ),
            Device(
                deviceId = "dev_tv_bedroom",
                name = "Bedroom Smart TV",
                type = DeviceType.SMART_TV,
                manufacturer = "Smart TV",
                connectionType = ConnectionType.LOCAL_NETWORK,
                capabilities = listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY),
                status = DeviceStatus.ONLINE,
                roomLocation = "Bedroom",
                state = mapOf("power" to false, "volume" to 20)
            )
        )
    }

    private fun saveDevices(list: List<Device>) {
        try {
            val array = JSONArray()
            for (d in list) {
                val obj = JSONObject().apply {
                    put("deviceId", d.deviceId)
                    put("name", d.name)
                    put("type", d.type.name)
                    put("manufacturer", d.manufacturer)
                    put("connectionType", d.connectionType.name)
                    put("status", d.status.name)
                    put("roomLocation", d.roomLocation)
                    put("ipAddress", d.ipAddress)
                    put("isAuthorized", d.isAuthorized)
                    val capArray = JSONArray()
                    d.capabilities.forEach { capArray.put(it.name) }
                    put("capabilities", capArray)
                    val stateObj = JSONObject()
                    d.state.forEach { (k, v) -> stateObj.put(k, v) }
                    put("state", stateObj)
                }
                array.put(obj)
            }
            prefs.edit().putString("devices_json", array.toString()).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun parseDevice(obj: JSONObject): Device {
        val caps = mutableListOf<Capability>()
        val capArray = obj.optJSONArray("capabilities")
        if (capArray != null) {
            for (i in 0 until capArray.length()) {
                try {
                    caps.add(Capability.valueOf(capArray.getString(i)))
                } catch (e: Exception) {}
            }
        }

        val stateMap = mutableMapOf<String, Any?>()
        val sObj = obj.optJSONObject("state")
        if (sObj != null) {
            val keys = sObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                stateMap[k] = sObj.opt(k)
            }
        }

        return Device(
            deviceId = obj.getString("deviceId"),
            name = obj.getString("name"),
            type = runCatching { DeviceType.valueOf(obj.getString("type")) }.getOrDefault(DeviceType.GENERIC),
            manufacturer = obj.optString("manufacturer", "Generic"),
            connectionType = runCatching { ConnectionType.valueOf(obj.getString("connectionType")) }.getOrDefault(ConnectionType.WIFI),
            capabilities = caps,
            status = runCatching { DeviceStatus.valueOf(obj.getString("status")) }.getOrDefault(DeviceStatus.ONLINE),
            roomLocation = obj.optString("roomLocation", "Home"),
            ipAddress = if (obj.isNull("ipAddress")) null else obj.optString("ipAddress"),
            isAuthorized = obj.optBoolean("isAuthorized", true),
            state = stateMap
        )
    }

    fun registerDevice(device: Device) {
        val current = _devices.value.toMutableList()
        val index = current.indexOfFirst { it.deviceId == device.deviceId }
        if (index >= 0) current[index] = device else current.add(device)
        _devices.value = current
        saveDevices(current)
    }

    fun removeDevice(deviceId: String) {
        val current = _devices.value.filter { it.deviceId != deviceId }
        _devices.value = current
        saveDevices(current)
    }

    fun renameDevice(deviceId: String, newName: String) {
        val current = _devices.value.map {
            if (it.deviceId == deviceId) it.copy(name = newName) else it
        }
        _devices.value = current
        saveDevices(current)
    }

    fun updateDeviceStatus(deviceId: String, status: DeviceStatus) {
        val current = _devices.value.map {
            if (it.deviceId == deviceId) it.copy(status = status) else it
        }
        _devices.value = current
        saveDevices(current)
    }

    fun updateDeviceState(deviceId: String, key: String, value: Any?) {
        val current = _devices.value.map {
            if (it.deviceId == deviceId) {
                val newState = it.state.toMutableMap()
                newState[key] = value
                it.copy(state = newState)
            } else it
        }
        _devices.value = current
        saveDevices(current)
    }

    fun getDevice(deviceId: String): Device? =
        _devices.value.firstOrNull { it.deviceId == deviceId }

    fun findDeviceByName(nameQuery: String): Device? {
        val clean = nameQuery.trim().lowercase(Locale.ROOT)
        return _devices.value.firstOrNull {
            it.name.lowercase(Locale.ROOT).contains(clean) || clean.contains(it.name.lowercase(Locale.ROOT))
        }
    }
}

class DeviceDiscovery(private val context: Context) {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    suspend fun startScan(): List<Device> {
        _isScanning.value = true
        delay(1200)
        val discovered = listOf(
            Device(
                deviceId = "discovered_tv_${UUID.randomUUID().toString().take(6)}",
                name = "Living Room TV",
                type = DeviceType.SMART_TV,
                manufacturer = "Smart Media",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY),
                status = DeviceStatus.ONLINE,
                roomLocation = "Living Room"
            )
        )
        _discoveredDevices.value = discovered
        _isScanning.value = false
        return discovered
    }

    fun stopScan() {
        _isScanning.value = false
    }
}

class DeviceConnector(private val context: Context) {
    suspend fun sendCommand(device: Device, command: String, params: Map<String, Any?> = emptyMap()): ToolResult {
        delay(200)
        return when (command.lowercase(Locale.ROOT)) {
            "turn_on", "on", "power_on" -> ToolResult.ok("${device.name} turned ON.")
            "turn_off", "off", "power_off" -> ToolResult.ok("${device.name} turned OFF.")
            "set_brightness", "brightness" -> {
                val b = params["brightness"] ?: 100
                ToolResult.ok("Set brightness of ${device.name} to $b%.")
            }
            "set_volume", "volume" -> {
                val v = params["volume"] ?: 50
                ToolResult.ok("Set volume of ${device.name} to $v%.")
            }
            else -> ToolResult.ok("Executed command '$command' on ${device.name}.")
        }
    }
}

class DeviceManager(
    val context: Context,
    val registry: DeviceRegistry = DeviceRegistry(context),
    val discovery: DeviceDiscovery = DeviceDiscovery(context),
    val connector: DeviceConnector = DeviceConnector(context)
) {
    val devices: StateFlow<List<Device>> = registry.devices

    suspend fun executeDeviceCommand(
        deviceNameOrId: String,
        command: String,
        params: Map<String, Any?> = emptyMap()
    ): ToolResult {
        val dev = registry.getDevice(deviceNameOrId) ?: registry.findDeviceByName(deviceNameOrId)
            ?: return ToolResult.error("Device '$deviceNameOrId' not found.")

        val res = connector.sendCommand(dev, command, params)
        if (res.success) {
            when (command.lowercase(Locale.ROOT)) {
                "turn_on", "on", "power_on" -> registry.updateDeviceState(dev.deviceId, "power", true)
                "turn_off", "off", "power_off" -> registry.updateDeviceState(dev.deviceId, "power", false)
                "set_brightness", "brightness" -> params["brightness"]?.let { registry.updateDeviceState(dev.deviceId, "brightness", it) }
                "set_volume", "volume" -> params["volume"]?.let { registry.updateDeviceState(dev.deviceId, "volume", it) }
            }
        }
        return res
    }

    fun authorizeDevice(deviceId: String, authToken: String): Boolean {
        registry.updateDeviceStatus(deviceId, DeviceStatus.ONLINE)
        return true
    }

    fun addManualDevice(
        name: String,
        type: DeviceType,
        manufacturer: String,
        connectionType: ConnectionType,
        room: String,
        ipAddress: String? = null
    ): Device {
        val dev = Device(
            deviceId = "dev_${UUID.randomUUID().toString().take(8)}",
            name = name,
            type = type,
            manufacturer = manufacturer,
            connectionType = connectionType,
            capabilities = listOf(Capability.POWER),
            status = DeviceStatus.ONLINE,
            roomLocation = room,
            ipAddress = ipAddress
        )
        registry.registerDevice(dev)
        return dev
    }

    fun getConnectedDevicesSummary(): String {
        val list = devices.value
        val online = list.count { it.status == DeviceStatus.ONLINE || it.status == DeviceStatus.CONNECTED }
        return "$online/${list.size} devices online"
    }
}
