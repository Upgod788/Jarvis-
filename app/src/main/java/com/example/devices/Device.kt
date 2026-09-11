package com.example.devices

enum class DeviceType(val displayName: String) {
    PHONE("Phone"),
    SMART_LIGHT("Smart Light"),
    SMART_PLUG("Smart Plug"),
    SMART_TV("Smart TV"),
    SPEAKER("Speaker"),
    CHROMECAST("Chromecast"),
    MATTER("Matter Device"),
    HOME_HUB("Home Hub"),
    SMART_CAMERA("Smart Camera"),
    THERMOSTAT("Thermostat"),
    SMART_FAN("Smart Fan"),
    SMART_APPLIANCE("Smart Appliance"),
    WEARABLE("Wearable"),
    PC("PC / Laptop"),
    OTHER("Other")
}

enum class ConnectionType(val displayName: String) {
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    LOCAL_NETWORK("Local Network"),
    CLOUD_INTEGRATION("Cloud API"),
    COMPANION_AGENT("JARVIS Companion Agent"),
    SYSTEM("Android System")
}

enum class DeviceStatus(val displayName: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    CONNECTED("Connected"),
    REQUIRES_PERMISSION("Requires Permission")
}

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
    val state: Map<String, Any> = mapOf("power" to false, "brightness" to 50, "volume" to 50)
)
