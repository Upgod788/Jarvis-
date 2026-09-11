package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.JarvisApplication
import com.example.agent.AgentExecutionState
import com.example.ai.AIProviderType
import com.example.ai.AISettings
import com.example.history.ConversationEntity
import com.example.memory.MemoryEntity
import com.example.voice.SpeechEvent
import com.example.voice.SpeechRecognizerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as JarvisApplication
    private val agent = app.agent
    private val speechRecognizer = app.speechRecognizerManager
    private val tts = app.textToSpeechManager
    private val conversationRepo = app.conversationRepository
    private val memoryRepo = app.memoryRepository

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = conversationRepo.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = memoryRepo.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiSettings: StateFlow<AISettings> = app.aiProvider.settingsManager.settings

    val themeMode: StateFlow<com.example.ui.theme.ThemeMode> = app.themeManager.themeMode

    val confirmationSettings: StateFlow<com.example.agent.ConfirmationSettings> = app.confirmationManager.settings

    val voiceSettings: StateFlow<com.example.voice.VoiceSettings> = app.voiceSettingsManager.settings
    val availableVoices: StateFlow<List<com.example.voice.VoiceOption>> = app.voiceSettingsManager.availableVoices

    val deviceManager = app.deviceManager
    val devices: StateFlow<List<com.example.devices.Device>> = deviceManager.devices

    val routineManager = app.routineManager
    val routines: StateFlow<List<com.example.routines.Routine>> = routineManager.routines

    val updateManager = app.updateManager
    val updateStatus: StateFlow<com.example.update.UpdateStatus> = updateManager.status
    val updateSettings: StateFlow<com.example.update.UpdateSettings> = updateManager.preferences.settings
    val remoteConfig: StateFlow<com.example.update.RemoteConfig> = updateManager.remoteConfigManager.config
    val updateHistory: StateFlow<List<com.example.database.UpdateHistoryEntity>> = app.database.updateHistoryDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun checkForUpdates(isUserInitiated: Boolean = true) {
        updateManager.checkForUpdates(isUserInitiated = isUserInitiated)
    }

    fun startDownloadUpdate(manifest: com.example.update.UpdateManifest? = null) {
        updateManager.startDownload(manifest)
    }

    fun cancelUpdateDownload() {
        updateManager.cancelDownload()
    }

    fun installUpdate(onResult: (com.example.update.InstallResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = updateManager.installDownloadedApk()
            onResult(result)
        }
    }

    fun dismissUpdate(versionCode: Int) {
        updateManager.dismissUpdate(versionCode)
    }

    fun setAutoUpdate(enabled: Boolean) {
        updateManager.preferences.setAutoUpdate(enabled)
        if (enabled) {
            com.example.update.BackgroundUpdateWorker.schedulePeriodicCheck(app, updateManager.preferences.settings.value.wifiOnly)
        } else {
            com.example.update.BackgroundUpdateWorker.cancelPeriodicCheck(app)
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        updateManager.preferences.setWifiOnly(enabled)
        if (updateManager.preferences.settings.value.autoUpdateEnabled) {
            com.example.update.BackgroundUpdateWorker.schedulePeriodicCheck(app, enabled)
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        updateManager.preferences.setAutoDownload(enabled)
    }

    fun setUpdateOverMobileData(enabled: Boolean) {
        updateManager.preferences.setUpdateOverMobileData(enabled)
    }

    fun setIncludeBetaUpdates(enabled: Boolean) {
        updateManager.preferences.setIncludeBetaUpdates(enabled)
    }

    fun setUpdateChannel(channel: String) {
        updateManager.preferences.setUpdateChannel(channel)
    }

    fun setCustomServerUrl(url: String) {
        updateManager.preferences.setCustomServerUrl(url)
    }

    fun setSimulationMode(enabled: Boolean) {
        updateManager.preferences.setSimulationMode(enabled)
    }

    fun setSimulatedTargetVersion(version: String) {
        updateManager.preferences.setSimulatedTargetVersion(version)
    }

    fun setSimulatedMandatory(mandatory: Boolean) {
        updateManager.preferences.setSimulatedIsMandatory(mandatory)
    }

    fun setSimulatedMaintenance(enabled: Boolean) {
        updateManager.remoteConfigManager.setSimulatedMaintenance(enabled)
    }

    fun clearUpdateCache() {
        updateManager.clearCache()
    }

    fun clearUpdateHistory() {
        viewModelScope.launch {
            app.database.updateHistoryDao().clearAll()
        }
    }

    fun setThemeMode(mode: com.example.ui.theme.ThemeMode) {
        app.themeManager.setThemeMode(mode)
    }

    fun setConfirmCalls(enabled: Boolean) {
        app.confirmationManager.setCallConfirmation(enabled)
    }

    fun setConfirmSms(enabled: Boolean) {
        app.confirmationManager.setSmsConfirmation(enabled)
    }

    fun setConfirmWhatsApp(enabled: Boolean) {
        app.confirmationManager.setWhatsAppConfirmation(enabled)
    }

    init {
        // Collect speech recognizer listening state
        viewModelScope.launch {
            speechRecognizer.isListening.collect { listening ->
                if (listening && _uiState.value.assistantState != AssistantState.LISTENING) {
                    _uiState.value = _uiState.value.copy(assistantState = AssistantState.LISTENING)
                } else if (!listening && _uiState.value.assistantState == AssistantState.LISTENING) {
                    _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
                }
            }
        }

        // Collect TTS speaking state
        viewModelScope.launch {
            tts.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(isTtsSpeaking = speaking)
                if (!speaking && _uiState.value.assistantState == AssistantState.SPEAKING) {
                    _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
                }
            }
        }

        // Collect recent conversations for Home screen preview
        viewModelScope.launch {
            conversationRepo.getRecent(3).collect { recents ->
                _uiState.value = _uiState.value.copy(recentConversations = recents)
            }
        }

        // Automatic update check on app launch
        viewModelScope.launch {
            if (updateManager.preferences.settings.value.autoUpdateEnabled) {
                updateManager.checkForUpdates(isUserInitiated = false)
            }
        }
    }

    fun startListening() {
        tts.stop()
        _uiState.value = _uiState.value.copy(
            assistantState = AssistantState.LISTENING,
            currentCommand = "",
            pendingConfirmation = null
        )

        val recognitionLanguage = app.voiceSettingsManager.getCurrentSttLanguageTag()

        speechRecognizer.startListening(
            language = recognitionLanguage
        ) { event ->
            when (event) {
                is SpeechEvent.Ready -> {
                    _uiState.value = _uiState.value.copy(
                        assistantState = AssistantState.LISTENING,
                        assistantResponse = "Listening... Speak your command now."
                    )
                }
                is SpeechEvent.BeginningOfSpeech -> {
                    _uiState.value = _uiState.value.copy(
                        assistantState = AssistantState.LISTENING
                    )
                }
                is SpeechEvent.EndOfSpeech -> {
                    _uiState.value = _uiState.value.copy(
                        assistantState = AssistantState.THINKING,
                        audioRmsLevel = 0f
                    )
                }
                is SpeechEvent.RmsChanged -> {
                    _uiState.value = _uiState.value.copy(audioRmsLevel = event.normalizedLevel)
                }
                is SpeechEvent.PartialResult -> {
                    _uiState.value = _uiState.value.copy(currentCommand = event.text)
                }
                is SpeechEvent.FinalResult -> {
                    _uiState.value = _uiState.value.copy(
                        currentCommand = event.text,
                        audioRmsLevel = 0f
                    )
                    processCommand(event.text)
                }
                is SpeechEvent.Error -> {
                    // Check if any speech was partially captured before the timeout or error
                    val fallbackText = _uiState.value.currentCommand.trim()
                    if (fallbackText.isNotBlank() && fallbackText.length >= 2) {
                        _uiState.value = _uiState.value.copy(
                            audioRmsLevel = 0f
                        )
                        processCommand(fallbackText)
                        return@startListening
                    }

                    if (event.errorCode == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                        event.errorCode == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        // Normal voice silence or timeout - return to IDLE with helpful prompt
                        _uiState.value = _uiState.value.copy(
                            assistantState = AssistantState.IDLE,
                            assistantResponse = "I didn't catch that. Tap the mic to speak again, or type below."
                        )
                    } else if (event.errorCode == SpeechRecognizerManager.ERROR_SERVER_DISCONNECTED ||
                        event.errorCode == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        event.errorCode == android.speech.SpeechRecognizer.ERROR_CLIENT
                    ) {
                        // Transient connection reset - reset engine and return to IDLE without locking screen
                        _uiState.value = _uiState.value.copy(
                            assistantState = AssistantState.IDLE,
                            assistantResponse = "Voice engine ready. Tap the mic or enter your command below."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            assistantState = AssistantState.ERROR,
                            assistantResponse = event.message
                        )
                    }
                }
            }
        }
    }

    fun submitVoiceCommand(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                currentCommand = trimmed,
                assistantState = AssistantState.THINKING,
                audioRmsLevel = 0f
            )
            processCommand(trimmed)
        }
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        if (_uiState.value.assistantState == AssistantState.LISTENING) {
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
        }
    }

    fun processCommand(command: String) {
        if (command.isBlank()) return
        stopListening()
        tts.stop()

        _uiState.value = _uiState.value.copy(
            currentCommand = command,
            assistantState = AssistantState.THINKING
        )

        viewModelScope.launch {
            agent.executeCommand(
                rawCommand = command,
                onStateChange = { agentState ->
                    when (agentState) {
                        is AgentExecutionState.Idle -> {
                            _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
                        }
                        is AgentExecutionState.Listening -> {
                            _uiState.value = _uiState.value.copy(assistantState = AssistantState.LISTENING)
                        }
                        is AgentExecutionState.Thinking -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.THINKING,
                                currentCommand = agentState.command
                            )
                        }
                        is AgentExecutionState.Executing -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.EXECUTING,
                                activeToolName = agentState.toolName
                            )
                        }
                        is AgentExecutionState.AwaitingConfirmation -> {
                            _uiState.value = _uiState.value.copy(
                                pendingConfirmation = agentState.request
                            )
                        }
                        is AgentExecutionState.MissingPermission -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.ERROR,
                                missingPermissions = agentState.permissions
                            )
                        }
                        is AgentExecutionState.Speaking -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.SPEAKING,
                                assistantResponse = agentState.response,
                                activeToolName = agentState.toolName,
                                activeToolResult = agentState.result
                            )
                            tts.speak(agentState.response)
                        }
                        is AgentExecutionState.Error -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.ERROR,
                                assistantResponse = agentState.message
                            )
                        }
                    }
                },
                onFinished = { response, toolResult ->
                    _uiState.value = _uiState.value.copy(
                        assistantResponse = response,
                        activeToolResult = toolResult
                    )
                }
            )
        }
    }

    fun confirmPendingAction() {
        val request = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        viewModelScope.launch {
            request.onConfirm()
        }
    }

    fun cancelPendingAction() {
        val request = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        request.onCancel()
    }

    fun toggleVoiceMute() {
        val newMuted = !_uiState.value.isVoiceMuted
        tts.isVoiceEnabled = !newMuted
        if (newMuted) tts.stop()
        _uiState.value = _uiState.value.copy(isVoiceMuted = newMuted)
    }

    fun speakCurrentResponse() {
        tts.speak(_uiState.value.assistantResponse)
    }

    fun clearHistory() {
        viewModelScope.launch {
            conversationRepo.clearAll()
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            conversationRepo.deleteById(id)
        }
    }

    fun saveMemory(key: String, value: String) {
        viewModelScope.launch {
            memoryRepo.saveMemory(key, value)
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepo.deleteMemory(memory)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepo.clearAll()
        }
    }

    fun setVoiceLanguage(langCode: String) {
        tts.stop()
        app.voiceSettingsManager.setLanguage(langCode)
        tts.refreshAvailableVoices()
        tts.applyVoiceSettings()
    }

    fun setVoice(voiceId: String) {
        app.voiceSettingsManager.setVoice(voiceId)
        tts.applyVoiceSettings()
    }

    fun setSpeechRate(rate: Float) {
        app.voiceSettingsManager.setSpeechRate(rate)
        tts.applyVoiceSettings()
    }

    fun setPitch(pitch: Float) {
        app.voiceSettingsManager.setPitch(pitch)
        tts.applyVoiceSettings()
    }

    fun testVoice() {
        tts.testVoice()
    }

    fun setPreferredLanguage(lang: String) {
        setVoiceLanguage(lang)
    }

    fun updateCustomApiKey(key: String) {
        app.aiProvider.updateApiKey(key)
    }

    fun selectAIProvider(providerType: AIProviderType) {
        app.aiProvider.setProviderType(providerType)
    }

    fun updateOpenRouterConfig(apiKey: String, model: String) {
        app.aiProvider.updateOpenRouterConfig(apiKey, model)
    }

    suspend fun testOpenRouterConnection(apiKey: String, model: String): Pair<Boolean, String> {
        return app.aiProvider.testOpenRouterConnection(apiKey, model)
    }

    fun clearMissingPermissions() {
        _uiState.value = _uiState.value.copy(missingPermissions = emptyList())
    }

    fun runRoutine(routineId: String) {
        viewModelScope.launch {
            val result = routineManager.executeRoutine(routineId)
            _uiState.value = _uiState.value.copy(
                assistantResponse = result.message,
                activeToolResult = result
            )
        }
    }

    fun toggleRoutine(routineId: String) {
        routineManager.toggleRoutine(routineId)
    }

    fun saveRoutine(routine: com.example.routines.Routine) {
        routineManager.saveRoutine(routine)
    }

    fun deleteRoutine(routineId: String) {
        routineManager.deleteRoutine(routineId)
    }

    fun triggerDeviceAction(deviceId: String, command: String, params: Map<String, Any?> = emptyMap()) {
        viewModelScope.launch {
            val result = deviceManager.executeDeviceCommand(deviceId, command, params)
            _uiState.value = _uiState.value.copy(
                assistantResponse = result.message,
                activeToolResult = result
            )
        }
    }

    fun authorizeDevice(deviceId: String, token: String): Boolean {
        return deviceManager.authorizeDevice(deviceId, token)
    }

    fun addManualDevice(
        name: String,
        type: com.example.devices.DeviceType,
        manufacturer: String,
        conn: com.example.devices.ConnectionType,
        room: String
    ) {
        deviceManager.addManualDevice(name, type, manufacturer, conn, room)
    }

    fun removeDevice(deviceId: String) {
        deviceManager.registry.removeDevice(deviceId)
    }

    fun renameDevice(deviceId: String, newName: String) {
        deviceManager.registry.renameDevice(deviceId, newName)
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.stopListening()
        tts.stop()
    }
}
