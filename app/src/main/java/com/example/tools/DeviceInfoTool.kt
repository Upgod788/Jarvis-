package com.example.tools

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BatteryTool : Tool {
    override val name = "BatteryTool"
    override val description = "Checks battery percentage, charging state, and power source."
    override val parameters = emptyList<ToolParameter>()
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult.error("Unable to read battery state.")

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Unplugged"
        }

        val statusText = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "fully charged (100%)"
            isCharging -> "$percentage% and currently charging via $plugSource"
            else -> "$percentage% and discharging"
        }

        return ToolResult.ok(
            message = "Your battery is at $statusText.",
            data = mapOf(
                "percentage" to percentage,
                "isCharging" to isCharging,
                "plugged" to plugSource
            )
        )
    }
}

class DeviceInfoTool : Tool {
    override val name = "DeviceInfoTool"
    override val description = "Provides hardware model, Android OS version, storage capacity, and connection states."
    override val parameters = listOf(
        ToolParameter(name = "queryType", type = "string", description = "'all', 'storage', 'model', 'wifi', or 'bluetooth'", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val queryType = params["queryType"]?.toString()?.lowercase() ?: "all"

        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        // Storage calculation
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        val bytesTotal = stat.blockCountLong * stat.blockSizeLong
        val gbAvailable = String.format(Locale.US, "%.1f", bytesAvailable / (1024.0 * 1024.0 * 1024.0))
        val gbTotal = String.format(Locale.US, "%.1f", bytesTotal / (1024.0 * 1024.0 * 1024.0))

        // Wi-Fi status
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val isWifiEnabled = wifiManager?.isWifiEnabled ?: false

        // Bluetooth status
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothEnabled = bluetoothAdapter?.isEnabled ?: false

        return when (queryType) {
            "storage" -> ToolResult.ok("You have ${gbAvailable} GB free out of ${gbTotal} GB total storage.")
            "model" -> ToolResult.ok("You are using a $model running $androidVersion.")
            "wifi" -> ToolResult.ok("Wi-Fi is currently ${if (isWifiEnabled) "enabled" else "disabled"}.")
            "bluetooth" -> ToolResult.ok("Bluetooth is currently ${if (isBluetoothEnabled) "enabled" else "disabled"}.")
            else -> {
                val summary = "Device: $model running $androidVersion. Storage: ${gbAvailable} GB free of ${gbTotal} GB. Wi-Fi: ${if (isWifiEnabled) "ON" else "OFF"}, Bluetooth: ${if (isBluetoothEnabled) "ON" else "OFF"}."
                ToolResult.ok(
                    message = summary,
                    data = mapOf(
                        "model" to model,
                        "androidVersion" to androidVersion,
                        "storageFreeGB" to gbAvailable,
                        "storageTotalGB" to gbTotal,
                        "wifiEnabled" to isWifiEnabled,
                        "bluetoothEnabled" to isBluetoothEnabled
                    )
                )
            }
        }
    }
}

class CurrentTimeTool : Tool {
    override val name = "CurrentTimeTool"
    override val description = "Provides the current local time, date, day of the week, and timezone."
    override val parameters = emptyList<ToolParameter>()
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val now = Date()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val tz = TimeZone.getDefault().displayName

        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)

        return ToolResult.ok(
            message = "It is currently $timeStr on $dateStr ($tz).",
            data = mapOf("time" to timeStr, "date" to dateStr, "timezone" to tz)
        )
    }
}
