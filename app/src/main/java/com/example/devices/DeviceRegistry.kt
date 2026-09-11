package com.example.devices

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class DeviceRegistry(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_device_registry", Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        val jsonString = prefs.getString("registered_devices", null)
        if (jsonString != null) {
            try {
                val list = mutableListOf<Device>()
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(parseDevice(obj))
                }
                _devices.value = list
                return
            } catch (e: Exception) {
                // fallback to default demo devices
            }
        }

        // Initialize with default authorized smart home devices & phone
        val initialList = listOf(
            Device(
                deviceId = "device_phone_local",
                name = "My Android Phone",
                type = DeviceType.PHONE,
                manufacturer = "Android",
                connectionType = ConnectionType.SYSTEM,
                capabilities = listOf(Capability.BATTERY_QUERY, Capability.NOTIFICATIONS, Capability.OPEN_APP, Capability.VOLUME),
                status = DeviceStatus.CONNECTED,
                roomLocation = "Personal",
                isAuthorized = true,
                state = mapOf("battery" to 85, "volume" to 60)
            ),
            Device(
                deviceId = "device_light_bedroom",
                name = "Bedroom Light",
                type = DeviceType.SMART_LIGHT,
                manufacturer = "Philips Hue",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.BRIGHTNESS, Capability.COLOR),
                status = DeviceStatus.ONLINE,
                roomLocation = "Bedroom",
                isAuthorized = true,
                state = mapOf("power" to false, "brightness" to 80)
            ),
            Device(
                deviceId = "device_light_living_room",
                name = "Living Room Light",
                type = DeviceType.SMART_LIGHT,
                manufacturer = "LIFX",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.BRIGHTNESS, Capability.COLOR),
                status = DeviceStatus.ONLINE,
                roomLocation = "Living Room",
                isAuthorized = true,
                state = mapOf("power" to false, "brightness" to 70)
            ),
            Device(
                deviceId = "device_tv_living_room",
                name = "Living Room TV",
                type = DeviceType.SMART_TV,
                manufacturer = "Samsung SmartThings",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY),
                status = DeviceStatus.ONLINE,
                roomLocation = "Living Room",
                isAuthorized = true,
                state = mapOf("power" to false, "volume" to 30)
            ),
            Device(
                deviceId = "device_speaker_bedroom",
                name = "Bedroom Speaker",
                type = DeviceType.SPEAKER,
                manufacturer = "Google Nest",
                connectionType = ConnectionType.WIFI,
                capabilities = listOf(Capability.POWER, Capability.VOLUME, Capability.MEDIA_PLAY),
                status = DeviceStatus.ONLINE,
                roomLocation = "Bedroom",
                isAuthorized = true,
                state = mapOf("power" to true, "volume" to 40)
            ),
            Device(
                deviceId = "device_pc_companion",
                name = "Harshit's Work PC",
                type = DeviceType.PC,
                manufacturer = "JARVIS Companion",
                connectionType = ConnectionType.COMPANION_AGENT,
                capabilities = listOf(Capability.LOCK, Capability.OPEN_APP, Capability.MEDIA_PLAY, Capability.BATTERY_QUERY),
                status = DeviceStatus.ONLINE,
                roomLocation = "Office",
                isAuthorized = true,
                state = mapOf("power" to true, "locked" to false, "battery" to 92)
            )
        )
        _devices.value = initialList
        saveDevices(initialList)
    }

    @Synchronized
    fun registerDevice(device: Device) {
        val current = _devices.value.toMutableList()
        current.removeAll { it.deviceId == device.deviceId }
        current.add(device)
        _devices.value = current
        saveDevices(current)
    }

    @Synchronized
    fun removeDevice(deviceId: String) {
        val current = _devices.value.filter { it.deviceId != deviceId }
        _devices.value = current
        saveDevices(current)
    }

    @Synchronized
    fun renameDevice(deviceId: String, newName: String) {
        val current = _devices.value.map {
            if (it.deviceId == deviceId) it.copy(name = newName) else it
        }
        _devices.value = current
        saveDevices(current)
    }

    @Synchronized
    fun updateDeviceStatus(deviceId: String, status: DeviceStatus) {
        val current = _devices.value.map {
            if (it.deviceId == deviceId) it.copy(status = status) else it
        }
        _devices.value = current
        saveDevices(current)
    }

    @Synchronized
    fun updateDeviceState(deviceId: String, key: String, value: Any) {
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

    fun getDevice(deviceId: String): Device? {
        return _devices.value.firstOrNull { it.deviceId == deviceId }
    }

    fun findDeviceByName(nameQuery: String): Device? {
        val clean = nameQuery.trim().lowercase()
        return _devices.value.firstOrNull {
            it.name.lowercase() == clean ||
            it.name.lowercase().contains(clean) ||
            clean.contains(it.name.lowercase())
        }
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
                    put("isAuthorized", d.isAuthorized)
                    val caps = JSONArray()
                    d.capabilities.forEach { caps.put(it.name) }
                    put("capabilities", caps)
                    val stateObj = JSONObject()
                    d.state.forEach { (k, v) -> stateObj.put(k, v) }
                    put("state", stateObj)
                }
                array.put(obj)
            }
            prefs.edit().putString("registered_devices", array.toString()).apply()
        } catch (e: Exception) {
            // Log or ignore
        }
    }

    private fun parseDevice(obj: JSONObject): Device {
        val deviceId = obj.getString("deviceId")
        val name = obj.getString("name")
        val type = DeviceType.valueOf(obj.optString("type", "OTHER"))
        val manufacturer = obj.optString("manufacturer", "Unknown")
        val connectionType = ConnectionType.valueOf(obj.optString("connectionType", "WIFI"))
        val status = DeviceStatus.valueOf(obj.optString("status", "ONLINE"))
        val roomLocation = obj.optString("roomLocation", "Home")
        val isAuthorized = obj.optBoolean("isAuthorized", true)

        val capsList = mutableListOf<Capability>()
        val capsArr = obj.optJSONArray("capabilities")
        if (capsArr != null) {
            for (i in 0 until capsArr.length()) {
                try {
                    capsList.add(Capability.valueOf(capsArr.getString(i)))
                } catch (e: Exception) {}
            }
        }

        val stateMap = mutableMapOf<String, Any>()
        val stateObj = obj.optJSONObject("state")
        if (stateObj != null) {
            val keys = stateObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                stateMap[k] = stateObj.get(k)
            }
        }

        return Device(
            deviceId = deviceId,
            name = name,
            type = type,
            manufacturer = manufacturer,
            connectionType = connectionType,
            capabilities = capsList,
            status = status,
            roomLocation = roomLocation,
            isAuthorized = isAuthorized,
            state = stateMap
        )
    }
}
