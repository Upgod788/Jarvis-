package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agent.ConfirmationSettings
import com.example.ai.AIProviderType
import com.example.ai.AISettings
import com.example.ui.theme.ThemeMode
import com.example.update.UpdateManifest
import com.example.update.UpdateSettings
import com.example.update.UpdateStatus
import com.example.voice.VoiceLanguageRegistry
import com.example.voice.VoiceOption
import com.example.voice.VoiceSettings

import com.example.personality.CommunicationStyle
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    aiSettings: AISettings,
    voiceSettings: VoiceSettings,
    availableVoices: List<VoiceOption>,
    confirmationSettings: ConfirmationSettings,
    updateSettings: UpdateSettings,
    updateStatus: UpdateStatus,
    themeMode: ThemeMode,
    personalityStyle: CommunicationStyle = CommunicationStyle.FRIENDLY,
    isMemoryEnabled: Boolean = true,
    isMemoryPaused: Boolean = false,
    onSetPersonalityStyle: (CommunicationStyle) -> Unit = {},
    onSetMemoryEnabled: (Boolean) -> Unit = {},
    onSetMemoryPaused: (Boolean) -> Unit = {},
    onSelectAIProvider: (AIProviderType) -> Unit,
    onUpdateApiKey: (AIProviderType, String) -> Unit,
    onUpdateOpenRouterConfig: (String, String) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetVoice: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onSetConfirmCalls: (Boolean) -> Unit,
    onSetConfirmSms: (Boolean) -> Unit,
    onSetConfirmWhatsApp: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onStartDownloadUpdate: (UpdateManifest) -> Unit,
    onInstallUpdate: () -> Unit,
    onSetAutoUpdate: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetSimulationMode: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var geminiKeyInput by remember(aiSettings.geminiCustomApiKey) {
        mutableStateOf(aiSettings.geminiCustomApiKey)
    }
    var openRouterKeyInput by remember(aiSettings.openRouterApiKey) {
        mutableStateOf(aiSettings.openRouterApiKey)
    }
    var openRouterModelInput by remember(aiSettings.openRouterModel) {
        mutableStateOf(aiSettings.openRouterModel)
    }

    var speechRate by remember(voiceSettings.speechRate) { mutableFloatStateOf(voiceSettings.speechRate) }
    var pitch by remember(voiceSettings.pitch) { mutableFloatStateOf(voiceSettings.pitch) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Core Configuration",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "System settings, AI backends, and update channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section: AI Provider
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("AI Provider Engine", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = aiSettings.providerType == AIProviderType.GEMINI,
                            onClick = { onSelectAIProvider(AIProviderType.GEMINI) },
                            label = { Text("Gemini AI") },
                            leadingIcon = if (aiSettings.providerType == AIProviderType.GEMINI) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = aiSettings.providerType == AIProviderType.OPENROUTER,
                            onClick = { onSelectAIProvider(AIProviderType.OPENROUTER) },
                            label = { Text("OpenRouter") },
                            leadingIcon = if (aiSettings.providerType == AIProviderType.OPENROUTER) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (aiSettings.providerType == AIProviderType.GEMINI) {
                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = {
                                geminiKeyInput = it
                                onUpdateApiKey(AIProviderType.GEMINI, it)
                            },
                            label = { Text("Custom Gemini API Key (Optional)") },
                            placeholder = { Text("Uses pre-configured key if empty") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = openRouterKeyInput,
                            onValueChange = {
                                openRouterKeyInput = it
                                onUpdateOpenRouterConfig(it, openRouterModelInput)
                            },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("sk-or-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = openRouterModelInput,
                            onValueChange = {
                                openRouterModelInput = it
                                onUpdateOpenRouterConfig(openRouterKeyInput, it)
                            },
                            label = { Text("Model ID") },
                            placeholder = { Text("e.g. google/gemini-2.5-flash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Section: Voice & Speech Synthesizer
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Voice & Speech", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        IconButton(onClick = onTestVoice) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Test Voice", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Speech Rate (${String.format("%.1f", speechRate)}x)", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = speechRate,
                        onValueChange = {
                            speechRate = it
                            onSetSpeechRate(it)
                        },
                        valueRange = 0.5f..2.0f
                    )

                    Text("Pitch (${String.format("%.1f", pitch)})", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = pitch,
                        onValueChange = {
                            pitch = it
                            onSetPitch(it)
                        },
                        valueRange = 0.6f..1.6f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Language: ${voiceSettings.language.displayName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Section: JARVIS Personality & Communication Style
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("JARVIS Personality", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Customize JARVIS's conversational attitude and demeanor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val styles = listOf(
                        CommunicationStyle.PROFESSIONAL,
                        CommunicationStyle.FRIENDLY,
                        CommunicationStyle.CASUAL,
                        CommunicationStyle.FUNNY,
                        CommunicationStyle.MINIMAL,
                        CommunicationStyle.MOTIVATIONAL
                    )

                    styles.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = personalityStyle == style,
                                onClick = { onSetPersonalityStyle(style) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = style.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Long-Term Memory Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Long-Term Memory & Privacy", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "JARVIS stores your preferences and routines 100% locally on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Long-Term Memory", style = MaterialTheme.typography.titleSmall)
                            Text("Persist knowledge between sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isMemoryEnabled,
                            onCheckedChange = onSetMemoryEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pause Memory Recording", style = MaterialTheme.typography.titleSmall)
                            Text("Temporarily halt remembering new facts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isMemoryPaused,
                            onCheckedChange = onSetMemoryPaused
                        )
                    }
                }
            }
        }

        // Section: Action Confirmation & Security
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Confirmation Guardrails", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Confirm Phone Calls", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = confirmationSettings.requireCallConfirmation,
                            onCheckedChange = onSetConfirmCalls,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Confirm SMS Messages", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = confirmationSettings.requireSmsConfirmation,
                            onCheckedChange = onSetConfirmSms,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Confirm WhatsApp Messages", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = confirmationSettings.confirmWhatsAppMessages,
                            onCheckedChange = onSetConfirmWhatsApp,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        // Section: System OTA Updates
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("System Updates", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        IconButton(onClick = onCheckForUpdates) {
                            Icon(Icons.Default.Refresh, contentDescription = "Check for Updates", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (updateStatus) {
                        is UpdateStatus.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Checking for new releases...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is UpdateStatus.Available -> {
                            val manifest = updateStatus.manifest
                            Text(
                                "Update v${manifest.latestVersionName} Available!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                manifest.changelog.firstOrNull() ?: "Performance improvements and bug fixes.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Button(
                                onClick = { onStartDownloadUpdate(manifest) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Download Update")
                            }
                        }
                        is UpdateStatus.Downloading -> {
                            Text("Downloading update: ${(updateStatus.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { updateStatus.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is UpdateStatus.ReadyToInstall -> {
                            Text("Update downloaded and verified.", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF00E676)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onInstallUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Text("Install Now")
                            }
                        }
                        is UpdateStatus.UpToDate -> {
                            Text("System is up to date (v${updateStatus.versionName})", style = MaterialTheme.typography.bodySmall)
                        }
                        is UpdateStatus.Error -> {
                            Text("Update check error: ${updateStatus.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        else -> {
                            Text("No pending updates.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Check Updates", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = updateSettings.autoUpdateEnabled,
                            onCheckedChange = onSetAutoUpdate,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Download Wi-Fi Only", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = updateSettings.wifiOnly,
                            onCheckedChange = onSetWifiOnly,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Update Simulation Mode", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = updateSettings.simulationMode,
                            onCheckedChange = onSetSimulationMode,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
