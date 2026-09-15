package com.example.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class StructuredAudioPermissionState(
    val isGranted: Boolean,
    val shouldShowRationale: Boolean,
    val isPermanentlyDenied: Boolean,
    val requestPermission: () -> Unit,
    val openSettings: () -> Unit
)

@Composable
fun rememberAudioPermissionState(
    onPermissionGranted: () -> Unit = {}
): StructuredAudioPermissionState {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(PermissionManager.hasPermission(context, android.Manifest.permission.RECORD_AUDIO))
    }
    var hasRequestedBefore by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (granted) {
            onPermissionGranted()
        } else {
            showPermanentlyDeniedDialog = true
        }
    }

    val openSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val requestPermission = {
        if (isGranted) {
            onPermissionGranted()
        } else if (hasRequestedBefore && !isGranted) {
            showPermanentlyDeniedDialog = true
        } else {
            hasRequestedBefore = true
            launcher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    if (showPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermanentlyDeniedDialog = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            title = { Text("Microphone Permission Required") },
            text = { Text("JARVIS requires microphone access to listen to voice commands and provide speech recognition. Please enable it in App Settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermanentlyDeniedDialog = false
                        openSettings()
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermanentlyDeniedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    return StructuredAudioPermissionState(
        isGranted = isGranted,
        shouldShowRationale = showRationaleDialog,
        isPermanentlyDenied = showPermanentlyDeniedDialog,
        requestPermission = requestPermission,
        openSettings = openSettings
    )
}
