package com.example.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Represents the current resolution state of the microphone permission.
 */
sealed class AudioPermissionUiStatus {
    /** Permission is granted; microphone can be used immediately. */
    object Granted : AudioPermissionUiStatus()

    /** Permission is not granted, but rationale should be displayed to explain why it's needed. */
    data class RequiresRationale(val explanation: String) : AudioPermissionUiStatus()

    /** Permission was permanently denied (Don't Ask Again). User must navigate to App Settings. */
    object PermanentlyDenied : AudioPermissionUiStatus()

    /** Initial state before any request has been made. */
    object NotRequested : AudioPermissionUiStatus()
}

/**
 * Structured state container for audio permissions.
 */
data class StructuredAudioPermissionState(
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val isPermanentlyDenied: Boolean,
    val requestPermission: () -> Unit,
    val openSettings: () -> Unit
)

/**
 * Utility helper to launch the app details settings screen where the user can manually
 * toggle permissions.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
