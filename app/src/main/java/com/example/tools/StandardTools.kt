package com.example.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.devices.DeviceManager
import com.example.memory.MemoryRepository
import com.example.routines.RoutineManager
import com.example.services.JarvisAccessibilityService
import com.example.services.JarvisNotificationListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CurrentTimeTool : Tool {
    override val name: String = "CurrentTimeTool"
    override val description: String = "Provides the current local time, date, day of the week, and timezone."
    override val parameters: List<ToolParameter> = emptyList()
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val now = Date()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val tz = TimeZone.getDefault().displayName
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)
        return ToolResult.ok(
            "It is currently $timeStr on $dateStr ($tz).",
            mapOf("time" to timeStr, "date" to dateStr, "timezone" to tz)
        )
    }
}

class BatteryTool : Tool {
    override val name: String = "BatteryTool"
    override val description: String = "Checks battery percentage, charging state, and power source."
    override val parameters: List<ToolParameter> = emptyList()
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult.error("Unable to read battery state.")

        val level = intent.getIntExtra("level", -1)
        val scale = intent.getIntExtra("scale", -1)
        val status = intent.getIntExtra("status", -1)
        val plugged = intent.getIntExtra("plugged", -1)

        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val isCharging = status == 2 || status == 5
        val plugSource = when (plugged) {
            1 -> "AC adapter"
            2 -> "USB"
            4 -> "Wireless"
            else -> "Unplugged"
        }

        val statusText = if (status == 5) "fully charged (100%)"
        else if (isCharging) "$percentage% and currently charging via $plugSource"
        else "$percentage% and discharging"

        return ToolResult.ok(
            "Your battery is at $statusText.",
            mapOf("percentage" to percentage, "isCharging" to isCharging, "plugged" to plugSource)
        )
    }
}

class FlashlightTool : Tool {
    companion object {
        var isTorchOn: Boolean = false
    }

    override val name: String = "FlashlightTool"
    override val description: String = "Turns the device flashlight on or off using CameraManager."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("enabled", "boolean", "true to turn ON, false to turn OFF", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val raw = params["enabled"]
        val enabled = when (raw) {
            is Boolean -> raw
            is String -> raw.equals("true", true) || raw.equals("on", true)
            else -> !isTorchOn
        }

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = getTorchCameraId(cameraManager)
                ?: return ToolResult.error("No camera with flashlight found on this device.")

            cameraManager.setTorchMode(cameraId, enabled)
            isTorchOn = enabled
            val state = if (enabled) "ON" else "OFF"
            return ToolResult.ok("Flashlight turned $state.")
        } catch (e: Exception) {
            return ToolResult.error("Failed to change flashlight state: ${e.localizedMessage}")
        }
    }

    private fun getTorchCameraId(cameraManager: CameraManager): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}

class WifiTool : Tool {
    override val name: String = "WifiTool"
    override val description: String = "Inspects Wi-Fi state and directs to system settings."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("action", "string", "'enable', 'disable', 'toggle', or 'settings'", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = (params["action"] as? String)?.lowercase() ?: "settings"
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                ToolResult.ok("Opened Wi-Fi connectivity panel.")
            } catch (e: Exception) {
                val sysIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sysIntent)
                ToolResult.ok("Opened Wi-Fi settings.")
            }
        } else {
            @Suppress("DEPRECATION")
            when (action) {
                "enable", "on" -> {
                    wifiManager?.isWifiEnabled = true
                    ToolResult.ok("Enabling Wi-Fi.")
                }
                "disable", "off" -> {
                    wifiManager?.isWifiEnabled = false
                    ToolResult.ok("Disabling Wi-Fi.")
                }
                "toggle" -> {
                    val current = wifiManager?.isWifiEnabled ?: false
                    wifiManager?.isWifiEnabled = !current
                    ToolResult.ok(if (!current) "Enabling Wi-Fi." else "Disabling Wi-Fi.")
                }
                else -> {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult.ok("Opened Wi-Fi settings.")
                }
            }
        }
    }
}

class BluetoothTool : Tool {
    override val name: String = "BluetoothTool"
    override val description: String = "Inspects Bluetooth state and directs to system settings."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("action", "string", "'enable', 'disable', 'toggle', or 'settings'", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolResult.ok("Opened Bluetooth settings.")
    }
}

class CameraTool : Tool {
    override val name: String = "CameraTool"
    override val description: String = "Launches the device camera application."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("mode", "string", "'photo' or 'video'", false)
    )
    override val requiredPermissions: List<String> = listOf(android.Manifest.permission.CAMERA)
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val mode = params["mode"] as? String
        val intent = if (mode.equals("video", true)) {
            Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        } else {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Opening Camera.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open camera: ${e.localizedMessage}")
        }
    }
}

class DeviceInfoTool : Tool {
    override val name: String = "DeviceInfoTool"
    override val description: String = "Provides device model, Android OS version, brand, and hardware manufacturer."
    override val parameters: List<ToolParameter> = emptyList()
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val androidVer = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        val brand = Build.BRAND
        return ToolResult.ok(
            "Device: $manufacturer $model (Brand: $brand), running Android $androidVer (API level $sdk).",
            mapOf("manufacturer" to manufacturer, "model" to model, "androidVersion" to androidVer, "sdkInt" to sdk)
        )
    }
}

class AlarmTool : Tool {
    override val name: String = "AlarmTool"
    override val description: String = "Sets an alarm for a specific hour, minute, and message."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("hour", "integer", "Hour in 24-hour format (0-23)", true),
        ToolParameter("minutes", "integer", "Minutes (0-59)", false, 0),
        ToolParameter("message", "string", "Alarm label or note", false, "JARVIS Alarm")
    )
    override val requiredPermissions: List<String> = listOf("com.android.alarm.permission.SET_ALARM")
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val hour = (params["hour"] as? Number)?.toInt() ?: return ToolResult.error("Hour is required.")
        val minutes = (params["minutes"] as? Number)?.toInt() ?: 0
        val message = (params["message"] as? String) ?: "JARVIS Alarm"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Setting alarm for %02d:%02d: '%s'".format(hour, minutes, message))
        } catch (e: Exception) {
            ToolResult.error("Unable to set alarm: ${e.localizedMessage}")
        }
    }
}

class TimerTool : Tool {
    override val name: String = "TimerTool"
    override val description: String = "Sets a timer countdown in seconds or minutes."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("seconds", "integer", "Total duration in seconds", false),
        ToolParameter("minutes", "integer", "Total duration in minutes", false),
        ToolParameter("message", "string", "Timer label", false, "JARVIS Timer")
    )
    override val requiredPermissions: List<String> = listOf("com.android.alarm.permission.SET_ALARM")
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val seconds = (params["seconds"] as? Number)?.toInt()
            ?: (((params["minutes"] as? Number)?.toInt() ?: 1) * 60)
        val message = (params["message"] as? String) ?: "JARVIS Timer"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Starting timer for $seconds seconds ($message).")
        } catch (e: Exception) {
            ToolResult.error("Unable to start timer: ${e.localizedMessage}")
        }
    }
}

class OpenAppTool : Tool {
    override val name: String = "OpenAppTool"
    override val description: String = "Launches an installed Android application by name or package."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("appName", "string", "Application name or package identifier", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val appName = (params["appName"] as? String)?.trim()
            ?: return ToolResult.error("No application name provided.")

        val pm = context.packageManager
        // Check direct package
        pm.getLaunchIntentForPackage(appName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
            return ToolResult.ok("Opened $appName.")
        }

        // Search by label
        val packages = pm.getInstalledApplications(0)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.equals(appName, ignoreCase = true) || label.lowercase().contains(appName.lowercase())) {
                pm.getLaunchIntentForPackage(app.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    return ToolResult.ok("Opening $label.")
                }
            }
        }

        return ToolResult.error("Could not find installed app matching '$appName'.")
    }
}

class OpenUrlTool : Tool {
    override val name: String = "OpenUrlTool"
    override val description: String = "Opens a web link in the default browser."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("url", "string", "The URL to visit", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        var url = (params["url"] as? String)?.trim() ?: return ToolResult.error("URL is required.")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Opening $url.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open URL: ${e.localizedMessage}")
        }
    }
}

class WebSearchTool : Tool {
    override val name: String = "WebSearchTool"
    override val description: String = "Performs a web search via the system web search provider."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("query", "string", "The query to search for", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val query = (params["query"] as? String)?.trim() ?: return ToolResult.error("Query is required.")
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Searching the web for '$query'.")
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            ToolResult.ok("Searching Google for '$query'.")
        }
    }
}

class CallContactTool : Tool {
    override val name: String = "CallContactTool"
    override val description: String = "Initiates a phone call or opens the dialer for a contact or number."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("contactOrNumber", "string", "Name or phone number", true)
    )
    override val requiredPermissions: List<String> = listOf(android.Manifest.permission.CALL_PHONE)
    override val riskLevel: RiskLevel = RiskLevel.HIGH
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val target = (params["contactOrNumber"] as? String)?.trim() ?: return ToolResult.error("Target is required.")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(target)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolResult.ok("Opening phone dialer for '$target'.")
    }
}

class SendSmsTool : Tool {
    override val name: String = "SendSmsTool"
    override val description: String = "Composes an SMS text message to a contact or phone number."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("recipient", "string", "Recipient phone number or name", true),
        ToolParameter("message", "string", "Message text content", false, "")
    )
    override val requiredPermissions: List<String> = listOf(android.Manifest.permission.SEND_SMS)
    override val riskLevel: RiskLevel = RiskLevel.HIGH
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val recipient = (params["recipient"] as? String)?.trim() ?: return ToolResult.error("Recipient is required.")
        val msg = (params["message"] as? String) ?: ""
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient)}")).apply {
            putExtra("sms_body", msg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ToolResult.ok("Composing message to $recipient.")
    }
}

class WeatherTool : Tool {
    override val name: String = "WeatherTool"
    override val description: String = "Checks current weather and forecast for a given location or current position."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("location", "string", "City name or location", false, "Current Location")
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val loc = (params["location"] as? String) ?: "your area"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode("weather in $loc"))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return ToolResult.ok("Checking weather in $loc.")
        } catch (e: Exception) {
            return ToolResult.error("Failed to check weather: ${e.localizedMessage}")
        }
    }
}

class NavigationTool : Tool {
    override val name: String = "NavigationTool"
    override val description: String = "Navigates or opens Google Maps to a destination."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("destination", "string", "Destination address or place name", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val dest = (params["destination"] as? String)?.trim() ?: return ToolResult.error("Destination required.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(dest)}")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Starting navigation to $dest.")
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(dest)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            ToolResult.ok("Opening maps for $dest.")
        }
    }
}

class YouTubeTool : Tool {
    override val name: String = "YouTubeTool"
    override val description: String = "Searches or plays videos on YouTube."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("query", "string", "Video topic or song name", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val query = params["query"] as? String
        val intent = if (!query.isNullOrBlank()) {
            Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok(if (!query.isNullOrBlank()) "Searching YouTube for '$query'." else "Opening YouTube.")
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(
                if (!query.isNullOrBlank()) "https://www.youtube.com/results?search_query=${Uri.encode(query)}" else "https://www.youtube.com"
            )).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
            ToolResult.ok("Opening YouTube in browser.")
        }
    }
}

class InstagramTool : Tool {
    override val name: String = "InstagramTool"
    override val description: String = "Opens Instagram or profile/feed."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("action", "string", "'open' or 'profile'", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val pm = context.packageManager
        pm.getLaunchIntentForPackage("com.instagram.android")?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
            return ToolResult.ok("Opening Instagram.")
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(web)
        return ToolResult.ok("Opening Instagram web.")
    }
}

class WhatsAppTool : Tool {
    override val name: String = "WhatsAppTool"
    override val description: String = "Opens WhatsApp or starts a chat."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("phone", "string", "Phone number with country code", false),
        ToolParameter("message", "string", "Message to send", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val phone = params["phone"] as? String
        val msg = params["message"] as? String ?: ""
        val intent = if (!phone.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=${Uri.encode(phone)}&text=${Uri.encode(msg)}")).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            val pm = context.packageManager
            pm.getLaunchIntentForPackage("com.whatsapp") ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com"))
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        return try {
            context.startActivity(intent)
            ToolResult.ok("Opening WhatsApp.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open WhatsApp: ${e.localizedMessage}")
        }
    }
}

class BrowserTool : Tool {
    override val name: String = "BrowserTool"
    override val description: String = "Opens Chrome or default web browser."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("url", "string", "URL to visit", false, "https://www.google.com")
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val url = (params["url"] as? String) ?: "https://www.google.com"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Opening browser.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open browser: ${e.localizedMessage}")
        }
    }
}

class MediaControlTool : Tool {
    override val name: String = "MediaControlTool"
    override val description: String = "Controls system media playback (play, pause, next, previous)."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("action", "string", "'play', 'pause', 'toggle', 'next', 'previous'", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = (params["action"] as? String)?.lowercase() ?: "toggle"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return ToolResult.ok("Media command '$action' sent.")
    }
}

class SettingsTool : Tool {
    override val name: String = "SettingsTool"
    override val description: String = "Opens device system settings or specific settings panel."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("panel", "string", "'display', 'sound', 'wifi', 'apps', or 'all'", false, "all")
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val panel = (params["panel"] as? String)?.lowercase() ?: "all"
        val action = when (panel) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return ToolResult.ok("Opening settings.")
    }
}

class NotificationTool : Tool {
    override val name: String = "NotificationTool"
    override val description: String = "Reads recent incoming notifications via the Notification Listener."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("limit", "integer", "Number of notifications to read", false, 5)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        if (!JarvisNotificationListenerService.isNotificationAccessGranted(context)) {
            return ToolResult.permissionRequired("Notification listener access is not granted. Please enable in Settings.")
        }
        val limit = (params["limit"] as? Number)?.toInt() ?: 5
        val list = JarvisNotificationListenerService.getRecent(limit)
        return if (list.isEmpty()) {
            ToolResult.ok("You have no new notifications.")
        } else {
            val text = list.joinToString("\n") { "• [${it.packageName}]: ${it.title} - ${it.text}" }
            ToolResult.ok("Recent notifications:\n$text")
        }
    }
}

class CalendarTool : Tool {
    override val name: String = "CalendarTool"
    override val description: String = "Opens the device calendar or creates an event."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("title", "string", "Event title", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val title = params["title"] as? String
        val intent = if (!title.isNullOrBlank()) {
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Opening Calendar.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open calendar: ${e.localizedMessage}")
        }
    }
}

class FileTool : Tool {
    override val name: String = "FileTool"
    override val description: String = "Opens the Android file manager / storage browser."
    override val parameters: List<ToolParameter> = emptyList()
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolResult.ok("Opening Files.")
        } catch (e: Exception) {
            ToolResult.error("Unable to open files: ${e.localizedMessage}")
        }
    }
}

class MemoryTool(private val memoryRepository: MemoryRepository) : Tool {
    override val name: String = "MemoryTool"
    override val description: String = "Stores, retrieves, or deletes personal preferences and facts in JARVIS local memory."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("action", "string", "'remember', 'recall', 'forget', or 'list'", true),
        ToolParameter("key", "string", "Memory topic or key", false),
        ToolParameter("value", "string", "Value to remember", false)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.LOW
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = (params["action"] as? String)?.lowercase() ?: "list"
        val key = params["key"] as? String ?: ""
        val value = params["value"] as? String ?: ""

        return when (action) {
            "remember", "save" -> {
                if (key.isBlank() || value.isBlank()) {
                    return ToolResult.error("Both key and value are needed to save a memory.")
                }
                memoryRepository.saveMemory(key, value)
                ToolResult.ok("I will remember that $key is '$value'.")
            }
            "recall", "get" -> {
                val mem = memoryRepository.getMemoryByKey(key)
                if (mem != null) {
                    ToolResult.ok("Regarding $key: ${mem.value}")
                } else {
                    ToolResult.error("I don't have any memory stored for '$key'.")
                }
            }
            "forget", "delete" -> {
                memoryRepository.deleteMemoryByKey(key)
                ToolResult.ok("Forgotten memory for '$key'.")
            }
            "list" -> {
                val list = memoryRepository.getAllMemoriesOnce()
                if (list.isEmpty()) {
                    ToolResult.ok("JARVIS memory is currently empty.")
                } else {
                    val summary = list.joinToString("\n") { "• ${it.key}: ${it.value}" }
                    ToolResult.ok("JARVIS Memories:\n$summary")
                }
            }
            else -> ToolResult.error("Unknown memory action '$action'.")
        }
    }
}

class RoutineTool(private val routineManager: RoutineManager) : Tool {
    override val name: String = "RoutineTool"
    override val description: String = "Executes an automated multi-step routine by name or trigger phrase."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("routineName", "string", "Routine name or trigger phrase", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val rName = params["routineName"] as? String ?: return ToolResult.error("Routine name is required.")
        return routineManager.executeRoutine(rName)
    }
}

class SmartDeviceTool(private val deviceManager: DeviceManager) : Tool {
    override val name: String = "SmartDeviceTool"
    override val description: String = "Controls connected smart IoT devices (lights, plugs, switches, thermostats)."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("deviceName", "string", "Device name or ID", true),
        ToolParameter("command", "string", "'turn_on', 'turn_off', 'brightness', etc.", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val dev = params["deviceName"] as? String ?: return ToolResult.error("Device name required.")
        val cmd = params["command"] as? String ?: "turn_on"
        return deviceManager.executeDeviceCommand(dev, cmd, params)
    }
}

class PcControlTool(private val deviceManager: DeviceManager) : Tool {
    override val name: String = "PcControlTool"
    override val description: String = "Sends remote commands to paired PC (media, launch app, sleep, lock)."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("command", "string", "'lock', 'sleep', 'volume', 'open_app'", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val cmd = params["command"] as? String ?: "lock"
        return deviceManager.executeDeviceCommand("JARVIS PC Workstation", cmd, params)
    }
}

class AppAutomationTool : Tool {
    override val name: String = "AppAutomationTool"
    override val description: String = "Automates UI interactions in other apps via Jarvis Accessibility Service."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter("action", "string", "Action to perform", true)
    )
    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.HIGH
    override val requiresConfirmation: Boolean = true

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val actService = JarvisAccessibilityService.instance
            ?: return ToolResult.permissionRequired("Jarvis Accessibility Service is not active. Enable it in Android Settings.")
        return ToolResult.ok("Automation command dispatched.")
    }
}
