package com.example.permissions

import android.Manifest
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.google.accompanist.permissions.*

/**
 * Controller holder for structured Accompanist RECORD_AUDIO permission management.
 */
@OptIn(ExperimentalPermissionsApi::class)
class AudioPermissionController(
    val context: Context,
    val permissionState: PermissionState,
    private val onPermissionGrantedCallback: () -> Unit,
    val showRationaleDialog: MutableState<Boolean>,
    val showPermanentlyDeniedDialog: MutableState<Boolean>,
    val hasRequestedBefore: MutableState<Boolean>
) {
    val isGranted: Boolean
        get() = permissionState.status.isGranted || PermissionManager.hasPermission(context, Manifest.permission.RECORD_AUDIO)

    val shouldShowRationale: Boolean
        get() = permissionState.status.shouldShowRationale && !isGranted

    val isPermanentlyDenied: Boolean
        get() = !isGranted && !shouldShowRationale && hasRequestedBefore.value

    /**
     * Gracefully executes voice action or routes through appropriate Accompanist
     * permission flow based on current system status.
     */
    fun onVoiceActionRequested() {
        if (isGranted) {
            onPermissionGrantedCallback()
            return
        }

        if (isPermanentlyDenied) {
            showPermanentlyDeniedDialog.value = true
        } else if (shouldShowRationale) {
            showRationaleDialog.value = true
        } else {
            hasRequestedBefore.value = true
            permissionState.launchPermissionRequest()
        }
    }

    fun launchDirectRequest() {
        hasRequestedBefore.value = true
        showRationaleDialog.value = false
        permissionState.launchPermissionRequest()
    }
}

/**
 * Remembers an Accompanist-backed structured audio permission controller.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberStructuredAudioPermissionController(
    onPermissionGranted: () -> Unit
): AudioPermissionController {
    val context = LocalContext.current
    val showRationaleDialog = rememberSaveable { mutableStateOf(false) }
    val showPermanentlyDeniedDialog = rememberSaveable { mutableStateOf(false) }
    val hasRequestedBefore = rememberSaveable { mutableStateOf(false) }

    var permissionStateRef by remember { mutableStateOf<PermissionState?>(null) }

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    ) { granted ->
        hasRequestedBefore.value = true
        if (granted) {
            showRationaleDialog.value = false
            showPermanentlyDeniedDialog.value = false
            onPermissionGranted()
        } else {
            // Evaluated upon denial
            if (permissionStateRef?.status?.shouldShowRationale == false) {
                showPermanentlyDeniedDialog.value = true
            }
        }
    }
    permissionStateRef = permissionState

    return remember(permissionState, context) {
        AudioPermissionController(
            context = context,
            permissionState = permissionState,
            onPermissionGrantedCallback = onPermissionGranted,
            showRationaleDialog = showRationaleDialog,
            showPermanentlyDeniedDialog = showPermanentlyDeniedDialog,
            hasRequestedBefore = hasRequestedBefore
        )
    }
}

/**
 * Renders all required structured Accompanist dialogs for the RECORD_AUDIO permission.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AudioPermissionDialogs(
    controller: AudioPermissionController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 1. Rationale Dialog
    if (controller.showRationaleDialog.value) {
        AlertDialog(
            onDismissRequest = { controller.showRationaleDialog.value = false },
            modifier = modifier.testTag("audio_permission_rationale_dialog"),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(JarvisCyanPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = JarvisCyanBright,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(id = R.string.mic_permission_title),
                    color = JarvisTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.mic_permission_rationale),
                        color = JarvisTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Surface(
                        color = JarvisDarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = JarvisCyanPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your voice audio is processed solely for executing your requested device actions.",
                                color = JarvisTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { controller.launchDirectRequest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyanPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("audio_permission_grant_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.mic_permission_grant),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { controller.showRationaleDialog.value = false },
                    modifier = Modifier.testTag("audio_permission_cancel_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.mic_permission_cancel),
                        color = JarvisTextSecondary
                    )
                }
            },
            containerColor = JarvisCardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. Permanently Denied Dialog (Directing to App Settings)
    if (controller.showPermanentlyDeniedDialog.value) {
        AlertDialog(
            onDismissRequest = { controller.showPermanentlyDeniedDialog.value = false },
            modifier = modifier.testTag("audio_permission_settings_dialog"),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(JarvisWarning.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Microphone Disabled",
                        tint = JarvisWarning,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Microphone Permission Blocked",
                    color = JarvisTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(id = R.string.mic_permission_permanently_denied),
                        color = JarvisTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "1. Tap 'Open Settings'\n2. Select 'Permissions'\n3. Enable 'Microphone'",
                        color = JarvisCyanBright,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .background(JarvisDarkBackground, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.showPermanentlyDeniedDialog.value = false
                        openAppSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyanPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("audio_permission_open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.mic_permission_open_settings),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { controller.showPermanentlyDeniedDialog.value = false },
                    modifier = Modifier.testTag("audio_permission_dismiss_settings_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.mic_permission_cancel),
                        color = JarvisTextSecondary
                    )
                }
            },
            containerColor = JarvisCardSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Elegant inline permission banner shown on the home dashboard when audio permission
 * is not yet granted, giving users immediate context and single-tap activation.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AudioPermissionBanner(
    controller: AudioPermissionController,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !controller.isGranted,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, JarvisWarning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { controller.onVoiceActionRequested() }
                .testTag("audio_permission_banner"),
            color = JarvisCardSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(JarvisWarning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = null,
                        tint = JarvisWarning,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice Input Disabled",
                        color = JarvisWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap to grant microphone permission for hands-free voice commands.",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = { controller.onVoiceActionRequested() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = JarvisWarning.copy(alpha = 0.2f),
                        contentColor = JarvisWarning
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("banner_enable_mic_button")
                ) {
                    Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Small badge showing live microphone permission readiness.
 */
@Composable
fun AudioPermissionStatusBadge(
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isGranted) JarvisSuccess.copy(alpha = 0.15f) else JarvisWarning.copy(alpha = 0.15f))
            .border(
                0.5.dp,
                if (isGranted) JarvisSuccess.copy(alpha = 0.4f) else JarvisWarning.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = null,
            tint = if (isGranted) JarvisSuccess else JarvisWarning,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isGranted) "MIC READY" else "MIC MUTED",
            color = if (isGranted) JarvisSuccess else JarvisWarning,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
