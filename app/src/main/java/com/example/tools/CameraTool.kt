package com.example.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.MediaStore

class CameraTool : Tool {
    override val name = "CameraTool"
    override val description = "Opens the device camera to take a photo or video."
    override val parameters = listOf(
        ToolParameter(name = "mode", type = "string", description = "'photo' or 'video'", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val mode = params["mode"]?.toString()?.lowercase() ?: "photo"
        val action = if (mode == "video") {
            MediaStore.ACTION_VIDEO_CAPTURE
        } else {
            MediaStore.ACTION_IMAGE_CAPTURE
        }

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.ok("Camera opened.")
            } else {
                // Fallback to general camera app launch
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage("com.android.camera")
                    ?: pm.getLaunchIntentForPackage("com.google.android.GoogleCamera")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ToolResult.ok("Camera opened.")
                } else {
                    ToolResult.error("No camera app found on this device.")
                }
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to open camera: ${e.localizedMessage}")
        }
    }
}

class FlashlightTool : Tool {
    override val name = "FlashlightTool"
    override val description = "Turns the device flashlight on or off using CameraManager."
    override val parameters = listOf(
        ToolParameter(name = "enabled", type = "boolean", description = "true to turn ON, false to turn OFF")
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    companion object {
        var isTorchOn: Boolean = false
            private set
    }

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val rawEnabled = params["enabled"]
        val enabled = when (rawEnabled) {
            is Boolean -> rawEnabled
            is String -> rawEnabled.equals("true", ignoreCase = true) || rawEnabled.equals("on", ignoreCase = true)
            else -> !isTorchOn // Toggle if unspecified
        }

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = getTorchCameraId(cameraManager)
                ?: return ToolResult.error("No camera with flashlight found on this device.")

            cameraManager.setTorchMode(cameraId, enabled)
            isTorchOn = enabled
            val state = if (enabled) "ON" else "OFF"
            ToolResult.ok("Flashlight turned $state.")
        } catch (e: Exception) {
            ToolResult.error("Failed to change flashlight state: ${e.localizedMessage}")
        }
    }

    private fun getTorchCameraId(cameraManager: CameraManager): String? {
        return try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
