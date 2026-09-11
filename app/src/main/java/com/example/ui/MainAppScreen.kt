package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.automation.AutomationScreen
import com.example.ui.components.ConfirmationDialog
import com.example.ui.devices.DevicesScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.memory.MemoryScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.*

enum class AppNavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.SmartToy),
    DEVICES("Devices", Icons.Default.Devices),
    HISTORY("History", Icons.Default.History),
    MEMORY("Memory", Icons.Default.Psychology),
    AUTOMATION("Automation", Icons.Default.AutoMode),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainAppScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val confirmationSettings by viewModel.confirmationSettings.collectAsStateWithLifecycle()
    val voiceSettings by viewModel.voiceSettings.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppNavDestination.HOME) }

    // Confirmation dialog for consequential actions
    uiState.pendingConfirmation?.let { request ->
        ConfirmationDialog(
            request = request,
            onConfirm = { viewModel.confirmPendingAction() },
            onDismiss = { viewModel.cancelPendingAction() }
        )
    }

    // Missing permissions notification dialog
    if (uiState.missingPermissions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMissingPermissions() },
            title = { Text("Permission Required", color = JarvisTextPrimary) },
            text = {
                Text(
                    text = "This action requires permissions that are not currently granted. Please enable them in Settings > Android Permissions to continue.",
                    color = JarvisTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearMissingPermissions()
                        currentDestination = AppNavDestination.SETTINGS
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyanPrimary, contentColor = Color.Black)
                ) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearMissingPermissions() }) {
                    Text("Dismiss", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisCardSurface
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisDarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = JarvisDarkSurface,
                tonalElevation = 6.dp,
                modifier = Modifier.border(0.5.dp, JarvisCardBorder, androidx.compose.ui.graphics.RectangleShape)
            ) {
                AppNavDestination.values().forEach { destination ->
                    val selected = currentDestination == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                tint = if (selected) JarvisCyanBright else JarvisTextMuted
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                color = if (selected) JarvisCyanBright else JarvisTextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = JarvisCyanPrimary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${destination.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition",
            modifier = Modifier.padding(innerPadding)
        ) { destination ->
            when (destination) {
                AppNavDestination.HOME -> {
                    HomeScreen(
                        uiState = uiState,
                        onStartListening = { viewModel.startListening() },
                        onStopListening = { viewModel.stopListening() },
                        onSubmitCommand = { cmd -> viewModel.processCommand(cmd) },
                        onSpeakResponse = { viewModel.speakCurrentResponse() },
                        onToggleMute = { viewModel.toggleVoiceMute() },
                        sttLanguageTag = voiceSettings.language.sttLanguageTag
                    )
                }
                AppNavDestination.DEVICES -> {
                    DevicesScreen(viewModel = viewModel)
                }
                AppNavDestination.HISTORY -> {
                    HistoryScreen(
                        conversations = conversations,
                        onDeleteConversation = { id -> viewModel.deleteHistoryItem(id) },
                        onClearAllHistory = { viewModel.clearHistory() }
                    )
                }
                AppNavDestination.MEMORY -> {
                    MemoryScreen(
                        memories = memories,
                        onSaveMemory = { k, v -> viewModel.saveMemory(k, v) },
                        onDeleteMemory = { m -> viewModel.deleteMemory(m) },
                        onClearAllMemories = { viewModel.clearAllMemories() }
                    )
                }
                AppNavDestination.AUTOMATION -> {
                    AutomationScreen(viewModel = viewModel)
                }
                AppNavDestination.SETTINGS -> {
                    SettingsScreen(
                        speechRate = voiceSettings.speechRate,
                        pitch = voiceSettings.pitch,
                        preferredLanguage = voiceSettings.languageCode,
                        selectedVoiceId = voiceSettings.voiceId,
                        availableVoices = availableVoices,
                        aiSettings = aiSettings,
                        themeMode = themeMode,
                        onThemeModeChange = { viewModel.setThemeMode(it) },
                        onSpeechRateChange = { viewModel.setSpeechRate(it) },
                        onPitchChange = { viewModel.setPitch(it) },
                        onLanguageChange = { viewModel.setVoiceLanguage(it) },
                        onVoiceChange = { viewModel.setVoice(it) },
                        onTestVoice = { viewModel.testVoice() },
                        onSelectProvider = { providerType -> viewModel.selectAIProvider(providerType) },
                        onUpdateApiKey = { key -> viewModel.updateCustomApiKey(key) },
                        onUpdateOpenRouterConfig = { key, model -> viewModel.updateOpenRouterConfig(key, model) },
                        onTestOpenRouterConnection = { key, model -> viewModel.testOpenRouterConnection(key, model) },
                        onClearHistory = { viewModel.clearHistory() },
                        onClearMemories = { viewModel.clearAllMemories() },
                        confirmCalls = confirmationSettings.requireCallConfirmation,
                        confirmSms = confirmationSettings.requireSmsConfirmation,
                        confirmWhatsApp = confirmationSettings.confirmWhatsAppMessages,
                        onConfirmCallsChange = { viewModel.setConfirmCalls(it) },
                        onConfirmSmsChange = { viewModel.setConfirmSms(it) },
                        onConfirmWhatsAppChange = { viewModel.setConfirmWhatsApp(it) }
                    )
                }
            }
        }
    }
}
