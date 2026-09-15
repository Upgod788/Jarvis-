package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.permissions.PermissionManager
import com.example.ui.AppNavDestination
import com.example.ui.JarvisViewModel
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JarvisApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    var currentDestination by remember { mutableStateOf(AppNavDestination.HOME) }
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val aiSettings by viewModel.aiSettings.collectAsState()
    val voiceSettings by viewModel.voiceSettings.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    val confirmationSettings by viewModel.confirmationSettings.collectAsState()
    val updateSettings by viewModel.updateSettings.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val personalityStyle by viewModel.personalityStyle.collectAsState()
    val isMemoryEnabled by viewModel.isMemoryEnabled.collectAsState()
    val isMemoryPaused by viewModel.isMemoryPaused.collectAsState()
    val memoryEvents by viewModel.memoryEvents.collectAsState()
    val pendingMemorySuggestion by viewModel.pendingMemorySuggestion.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppNavDestination.values().forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)

        when (currentDestination) {
            AppNavDestination.HOME -> {
                HomeScreen(
                    uiState = uiState,
                    onStartListening = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStopListening = { viewModel.stopListening() },
                    onSubmitCommand = { cmd -> viewModel.processCommand(cmd) },
                    onToggleMute = { viewModel.toggleVoiceMute() },
                    onSpeakResponse = { viewModel.speakCurrentResponse() },
                    modifier = screenModifier
                )
            }
            AppNavDestination.HISTORY -> {
                HistoryScreen(
                    conversations = conversations,
                    onDeleteItem = { id -> viewModel.deleteHistoryItem(id) },
                    onClearAll = { viewModel.clearHistory() },
                    modifier = screenModifier
                )
            }
            AppNavDestination.MEMORY -> {
                MemoryScreen(
                    memories = memories,
                    isMemoryEnabled = isMemoryEnabled,
                    isMemoryPaused = isMemoryPaused,
                    memoryEvents = memoryEvents,
                    onToggleMemoryEnabled = { viewModel.setMemoryEnabled(it) },
                    onToggleMemoryPaused = { viewModel.setMemoryPaused(it) },
                    onSaveMemory = { k, v, c, imp -> viewModel.saveMemory(k, v, c, imp) },
                    onUpdateMemory = { m -> viewModel.updateMemory(m) },
                    onDeleteMemory = { m -> viewModel.deleteMemory(m) },
                    onClearAllMemories = { viewModel.clearAllMemories() },
                    onClearConversations = { viewModel.clearConversationMemories() },
                    onExportJson = { cb -> viewModel.exportMemoriesJson(cb) },
                    onImportJson = { json, cb -> viewModel.importMemoriesJson(json, cb) },
                    modifier = screenModifier
                )
            }
            AppNavDestination.AUTOMATION -> {
                AutomationScreen(
                    routines = routines,
                    onRunRoutine = { id -> viewModel.runRoutine(id) },
                    onToggleRoutine = { id, enabled -> viewModel.toggleRoutine(id, enabled) },
                    modifier = screenModifier
                )
            }
            AppNavDestination.SETTINGS -> {
                SettingsScreen(
                    aiSettings = aiSettings,
                    voiceSettings = voiceSettings,
                    availableVoices = availableVoices,
                    confirmationSettings = confirmationSettings,
                    updateSettings = updateSettings,
                    updateStatus = updateStatus,
                    themeMode = themeMode,
                    personalityStyle = personalityStyle,
                    isMemoryEnabled = isMemoryEnabled,
                    isMemoryPaused = isMemoryPaused,
                    onSetPersonalityStyle = { s -> viewModel.setPersonalityStyle(s) },
                    onSetMemoryEnabled = { e -> viewModel.setMemoryEnabled(e) },
                    onSetMemoryPaused = { p -> viewModel.setMemoryPaused(p) },
                    onSelectAIProvider = { p -> viewModel.selectAIProvider(p) },
                    onUpdateApiKey = { p, k -> viewModel.updateCustomApiKey(p, k) },
                    onUpdateOpenRouterConfig = { k, m -> viewModel.updateOpenRouterConfig(k, m) },
                    onSetLanguage = { l -> viewModel.setVoiceLanguage(l) },
                    onSetVoice = { v -> viewModel.setVoice(v) },
                    onSetSpeechRate = { r -> viewModel.setSpeechRate(r) },
                    onSetPitch = { p -> viewModel.setPitch(p) },
                    onTestVoice = { viewModel.testVoice() },
                    onSetConfirmCalls = { c -> viewModel.setConfirmCalls(c) },
                    onSetConfirmSms = { s -> viewModel.setConfirmSms(s) },
                    onSetConfirmWhatsApp = { w -> viewModel.setConfirmWhatsApp(w) },
                    onCheckForUpdates = { viewModel.checkForUpdates(true) },
                    onStartDownloadUpdate = { m -> viewModel.startDownloadUpdate(m) },
                    onInstallUpdate = { viewModel.installUpdate() },
                    onSetAutoUpdate = { a -> viewModel.setAutoUpdate(a) },
                    onSetWifiOnly = { w -> viewModel.setWifiOnly(w) },
                    onSetSimulationMode = { s -> viewModel.setSimulationMode(s) },
                    onSetThemeMode = { t -> viewModel.setThemeMode(t) },
                    modifier = screenModifier
                )
            }
        }

        // Memory Suggestion Dialog
        pendingMemorySuggestion?.let { suggestion ->
            AlertDialog(
                onDismissRequest = { viewModel.rejectMemorySuggestion() },
                title = { Text("Remember This Fact?") },
                text = {
                    Column {
                        Text("JARVIS noticed: \"${suggestion.content}\"")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Would you like JARVIS to remember this for future conversations?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.approveMemorySuggestion() }) {
                        Text("Remember")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.rejectMemorySuggestion() }) {
                        Text("Don't Remember")
                    }
                }
            )
        }

        // Action Confirmation Dialog
        uiState.pendingConfirmation?.let { request ->
            AlertDialog(
                onDismissRequest = { viewModel.cancelPendingAction() },
                title = { Text(request.title) },
                text = { Text(request.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmPendingAction() }) {
                        Text("Authorize")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelPendingAction() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
