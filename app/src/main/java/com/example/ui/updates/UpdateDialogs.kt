package com.example.ui.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.update.UpdateManifest
import java.util.Locale

@Composable
fun UpdatePromptDialog(
    manifest: UpdateManifest,
    isCritical: Boolean,
    onConfirmUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!isCritical) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isCritical,
            dismissOnClickOutside = !isCritical
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(
                    1.5.dp,
                    if (isCritical) JarvisError else JarvisCyanBright,
                    RoundedCornerShape(18.dp)
                ),
            color = JarvisDarkSurface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCritical) Icons.Default.Dangerous else Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = if (isCritical) JarvisError else JarvisCyanBright,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isCritical) "CRITICAL UPDATE REQUIRED" else "JARVIS UPDATE AVAILABLE",
                            color = if (isCritical) JarvisError else JarvisCyanBright,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${manifest.latestVersionName} • ${(manifest.fileSize / (1024 * 1024f)).formatSize()} MB",
                            color = JarvisTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(color = JarvisCardBorder, thickness = 0.5.dp)

                if (isCritical) {
                    Text(
                        text = "This version is no longer supported. Please update JARVIS to continue.",
                        color = JarvisError,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "A new official release of JARVIS is ready to install.",
                        color = JarvisTextPrimary,
                        fontSize = 13.sp
                    )
                }

                // Release notes
                if (manifest.releaseNotes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(JarvisCardSurface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "What's New:",
                            color = JarvisCyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        manifest.releaseNotes.take(3).forEach { note ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", color = JarvisCyanPrimary, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                                Text(note, color = JarvisTextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isCritical) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                        ) {
                            Text("Later", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onConfirmUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dialog_update_now_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCritical) JarvisError else JarvisCyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UPDATE NOW", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun Float.formatSize(): String = "%.1f".format(Locale.US, this)
