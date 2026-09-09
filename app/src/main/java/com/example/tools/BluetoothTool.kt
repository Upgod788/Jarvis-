package com.example.tools

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * System control tool for toggling or opening Bluetooth controls.
 * Supports direct toggling where supported by Android APIs and permissions,
 * and provides seamless 1-tap fallback to the system Bluetooth settings.
 */
class BluetoothTool : Tool {
    override val name = "BluetoothTool"
    override val description = "Toggles Bluetooth state or opens the Bluetooth settings screen."
    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "'toggle', 'on', 'off', 'status', or 'settings'",
            required = false
        )
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase()?.trim() ?: "toggle"
        val appContext = context.applicationContext
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        @Suppress("DEPRECATION")
        val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            return ToolResult.error("Bluetooth is not supported on this device.")
        }

        val isCurrentlyEnabled = try {
            bluetoothAdapter.isEnabled
        } catch (_: SecurityException) {
            false
        }

        if (action == "status") {
            return ToolResult.ok(
                message = "Bluetooth is currently ${if (isCurrentlyEnabled) "ON" else "OFF"}.",
                data = mapOf("bluetoothEnabled" to isCurrentlyEnabled)
            )
        }

        val targetEnabled = when (action) {
            "on", "enable", "turn_on" -> true
            "off", "disable", "turn_off" -> false
            "toggle" -> !isCurrentlyEnabled
            else -> null
        }

        // On Android 12 and below, direct enable/disable can be attempted
        if (targetEnabled != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try {
                @Suppress("DEPRECATION")
                val success = if (targetEnabled) bluetoothAdapter.enable() else bluetoothAdapter.disable()
                if (success) {
                    return ToolResult.ok(
                        message = "Bluetooth turned ${if (targetEnabled) "ON" else "OFF"}.",
                        data = mapOf("bluetoothEnabled" to targetEnabled)
                    )
                }
            } catch (_: Throwable) {
                // Fallback to opening system settings
            }
        }

        // Modern Android (API 33+) or permission-restricted environments: open system Bluetooth settings
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            val desiredText = when (targetEnabled) {
                true -> "turn ON"
                false -> "turn OFF"
                else -> "toggle"
            }
            ToolResult.ok(
                message = "Opened Bluetooth settings to $desiredText. Bluetooth is currently ${if (isCurrentlyEnabled) "ON" else "OFF"}.",
                data = mapOf(
                    "bluetoothEnabled" to isCurrentlyEnabled,
                    "settingsOpened" to true
                )
            )
        } catch (e: Exception) {
            ToolResult.error("Unable to open Bluetooth settings: ${e.localizedMessage}")
        }
    }
}
