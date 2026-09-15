package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.JarvisApplication
import com.example.agent.AgentExecutionState
import com.example.agent.ConfirmationRequest
import com.example.agent.ConfirmationSettings
import com.example.ai.AIProviderType
import com.example.ai.AISettings
import com.example.database.UpdateHistoryEntity
import com.example.devices.ConnectionType
import com.example.devices.Device
import com.example.devices.DeviceManager
import com.example.devices.DeviceType
import com.example.history.ConversationEntity
import com.example.history.ConversationRepository
import com.example.memory.MemoryEntity
import com.example.memory.MemoryRepository
import com.example.routines.Routine
import com.example.routines.RoutineManager
import com.example.ui.theme.ThemeMode
import com.example.update.InstallResult
import com.example.update.RemoteConfig
import com.example.update.UpdateManager
import com.example.update.UpdateManifest
import com.example.update.UpdateSettings
import com.example.update.UpdateStatus
import com.example.voice.SpeechEvent
import com.example.voice.SpeechRecognizerManager
import com.example.voice.TextToSpeechManager
import com.example.voice.VoiceOption
import com.example.voice.VoiceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    val app = application as JarvisApplication

    val agent = app.agent
    val speechRecognizer: SpeechRecognizerManager = app.speechRecognizerManager
    val tts: TextToSpeechManager = app.textToSpeechManager
    val conversationRepo: ConversationRepository = app.conversationRepository
    val memoryRepo: MemoryRepository = app.memoryRepository
    val memoryManager: com.example.memory.MemoryManager = app.memoryManager
    val personalityManager: com.example.personality.PersonalityManager = app.personalityManager
    val emotionManager: com.example.emotion.EmotionManager = app.emotionManager

    val deviceManager: DeviceManager = app.deviceManager
    val routineManager: RoutineManager = app.routineManager
    val updateManager: UpdateManager = app.updateManager

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private val _pendingMemorySuggestion = MutableStateFlow<com.example.memory.model.MemoryCandidate?>(null)
    val pendingMemorySuggestion: StateFlow<com.example.memory.model.MemoryCandidate?> = _pendingMemorySuggestion.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> =
        conversationRepo.allConversations.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val memories: StateFlow<List<MemoryEntity>> =
        memoryRepo.allMemories.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val personalityStyle: StateFlow<com.example.personality.CommunicationStyle> =
        personalityManager.style

    val isMemoryEnabled: StateFlow<Boolean> =
        memoryManager.isMemoryEnabled

    val isMemoryPaused: StateFlow<Boolean> =
        memoryManager.isMemoryPaused

    val memoryEvents: StateFlow<List<com.example.memory.MemoryEventEntity>> =
        memoryManager.recentEvents.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val conversationSummaries: StateFlow<List<com.example.memory.ConversationSummaryEntity>> =
        memoryManager.conversationSummaries.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val aiSettings: StateFlow<AISettings> =
        app.aiProvider.settingsManager.settings

    val themeMode: StateFlow<ThemeMode> =
        app.themeManager.themeMode

    val confirmationSettings: StateFlow<ConfirmationSettings> =
        app.confirmationManager.settings

    val voiceSettings: StateFlow<VoiceSettings> =
        app.voiceSettingsManager.settings

    val availableVoices: StateFlow<List<VoiceOption>> =
        app.voiceSettingsManager.availableVoices

    val devices: StateFlow<List<Device>> =
        deviceManager.devices

    val routines: StateFlow<List<Routine>> =
        routineManager.routines

    val updateStatus: StateFlow<UpdateStatus> =
        updateManager.status

    val updateSettings: StateFlow<UpdateSettings> =
        updateManager.preferences.settings

    val remoteConfig: StateFlow<RemoteConfig> =
        updateManager.remoteConfigManager.config

    val updateHistory: StateFlow<List<UpdateHistoryEntity>> =
        app.database.updateHistoryDao().getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        agent.onMemorySuggestion = { candidate ->
            _pendingMemorySuggestion.value = candidate
        }
        viewModelScope.launch {
            tts.isSpeaking.collect { speaking ->
                _uiState.value = _uiState.value.copy(
                    isTtsSpeaking = speaking,
                    assistantState = if (speaking) AssistantState.SPEAKING else if (_uiState.value.assistantState == AssistantState.SPEAKING) AssistantState.IDLE else _uiState.value.assistantState
                )
            }
        }
        viewModelScope.launch {
            speechRecognizer.rmsLevel.collect { rms ->
                _uiState.value = _uiState.value.copy(audioRmsLevel = rms)
            }
        }
    }

    fun startListening() {
        _uiState.value = _uiState.value.copy(
            assistantState = AssistantState.LISTENING,
            currentCommand = ""
        )
        speechRecognizer.startListening { event ->
            when (event) {
                is SpeechEvent.PartialResult -> {
                    _uiState.value = _uiState.value.copy(currentCommand = event.text)
                }
                is SpeechEvent.FinalResult -> {
                    _uiState.value = _uiState.value.copy(
                        currentCommand = event.text,
                        assistantState = AssistantState.THINKING
                    )
                    processCommand(event.text)
                }
                is SpeechEvent.Error -> {
                    _uiState.value = _uiState.value.copy(
                        assistantState = AssistantState.IDLE
                    )
                }
                is SpeechEvent.RmsChanged -> {
                    _uiState.value = _uiState.value.copy(audioRmsLevel = event.rmsdB)
                }
                else -> Unit
            }
        }
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        if (_uiState.value.assistantState == AssistantState.LISTENING) {
            _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
        }
    }

    fun submitVoiceCommand(spokenText: String) {
        processCommand(spokenText)
    }

    fun processCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            currentCommand = trimmed,
            assistantState = AssistantState.THINKING
        )

        viewModelScope.launch {
            agent.executeCommand(
                rawCommand = trimmed,
                languageInstruction = app.voiceSettingsManager.getCurrentAiInstruction(),
                onStateChange = { state ->
                    when (state) {
                        is AgentExecutionState.Thinking -> {
                            _uiState.value = _uiState.value.copy(assistantState = AssistantState.THINKING)
                        }
                        is AgentExecutionState.Listening -> {
                            _uiState.value = _uiState.value.copy(assistantState = AssistantState.LISTENING)
                        }
                        is AgentExecutionState.Executing -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.EXECUTING,
                                activeToolName = state.toolName
                            )
                        }
                        is AgentExecutionState.AwaitingConfirmation -> {
                            _uiState.value = _uiState.value.copy(
                                pendingConfirmation = state.request
                            )
                        }
                        is AgentExecutionState.Speaking -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.SPEAKING,
                                assistantResponse = state.response,
                                activeToolName = state.toolName,
                                activeToolResult = state.result
                            )
                        }
                        is AgentExecutionState.Error -> {
                            _uiState.value = _uiState.value.copy(
                                assistantState = AssistantState.ERROR,
                                assistantResponse = state.message
                            )
                        }
                        is AgentExecutionState.Idle -> {
                            _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
                        }
                    }
                },
                onFinished = { responseText, toolResult ->
                    _uiState.value = _uiState.value.copy(
                        assistantResponse = responseText,
                        activeToolResult = toolResult,
                        assistantState = if (_uiState.value.isVoiceMuted) AssistantState.IDLE else AssistantState.SPEAKING
                    )
                    if (!_uiState.value.isVoiceMuted && responseText.isNotBlank()) {
                        tts.speak(responseText) {
                            _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(assistantState = AssistantState.IDLE)
                    }
                }
            )
        }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        viewModelScope.launch {
            pending.onConfirm()
        }
    }

    fun cancelPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        pending.onCancel()
    }

    fun toggleVoiceMute() {
        val newMuted = !_uiState.value.isVoiceMuted
        _uiState.value = _uiState.value.copy(isVoiceMuted = newMuted)
        tts.isVoiceEnabled = !newMuted
        if (newMuted) {
            tts.stop()
        }
    }

    fun speakCurrentResponse() {
        val resp = _uiState.value.assistantResponse
        if (resp.isNotBlank()) {
            tts.speak(resp)
        }
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

    fun setPersonalityStyle(style: com.example.personality.CommunicationStyle) {
        personalityManager.setStyle(style)
    }

    fun setMemoryEnabled(enabled: Boolean) {
        memoryManager.setMemoryEnabled(enabled)
    }

    fun setMemoryPaused(paused: Boolean) {
        memoryManager.setMemoryPaused(paused)
    }

    fun approveMemorySuggestion() {
        viewModelScope.launch {
            val candidate = _pendingMemorySuggestion.value
            if (candidate != null) {
                memoryManager.storeCandidate(candidate)
                _pendingMemorySuggestion.value = null
            }
        }
    }

    fun rejectMemorySuggestion() {
        _pendingMemorySuggestion.value = null
    }

    fun saveMemory(
        key: String,
        value: String,
        category: String = "general",
        importance: Float = 0.5f
    ) {
        viewModelScope.launch {
            memoryRepo.saveMemory(
                key = key,
                value = value,
                category = category,
                importance = importance
            )
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepo.updateMemory(memory)
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepo.deleteMemory(memory)
        }
    }

    fun deleteMemoryById(memoryId: String) {
        viewModelScope.launch {
            memoryRepo.deleteMemoryById(memoryId)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryManager.clearAll()
        }
    }

    fun clearConversationMemories() {
        viewModelScope.launch {
            memoryManager.clearConversations()
        }
    }

    fun exportMemoriesJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = memoryManager.exportJson()
            onResult(json)
        }
    }

    fun importMemoriesJson(json: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = memoryManager.importJson(json)
            onResult(count)
        }
    }

    fun setVoiceLanguage(langCode: String) {
        app.voiceSettingsManager.setLanguage(langCode)
    }

    fun setVoice(voiceId: String) {
        app.voiceSettingsManager.setVoice(voiceId)
    }

    fun setSpeechRate(rate: Float) {
        app.voiceSettingsManager.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        app.voiceSettingsManager.setPitch(pitch)
    }

    fun testVoice() {
        tts.testVoice()
    }

    fun setPreferredLanguage(lang: String) {
        tts.preferredLanguage = lang
    }

    fun updateCustomApiKey(providerType: AIProviderType, apiKey: String) {
        when (providerType) {
            AIProviderType.GEMINI -> app.aiProvider.updateApiKey(apiKey)
            AIProviderType.OPENROUTER -> {
                val currentModel = app.aiProvider.settingsManager.settings.value.openRouterModel
                app.aiProvider.updateOpenRouterConfig(apiKey, currentModel)
            }
        }
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
            routineManager.executeRoutine(routineId)
        }
    }

    fun toggleRoutine(routineId: String, enabled: Boolean) {
        routineManager.toggleRoutine(routineId)
    }

    fun saveRoutine(routine: Routine) {
        routineManager.saveRoutine(routine)
    }

    fun deleteRoutine(routineId: String) {
        routineManager.deleteRoutine(routineId)
    }

    fun triggerDeviceAction(deviceId: String, action: String, params: Map<String, Any?> = emptyMap()) {
        viewModelScope.launch {
            deviceManager.executeDeviceCommand(deviceId, action, params)
        }
    }

    fun authorizeDevice(deviceId: String, token: String) {
        deviceManager.authorizeDevice(deviceId, token)
    }

    fun addManualDevice(name: String, type: DeviceType, manufacturer: String, conn: ConnectionType, room: String) {
        deviceManager.addManualDevice(name, type, manufacturer, conn, room)
    }

    fun removeDevice(deviceId: String) {
        deviceManager.registry.removeDevice(deviceId)
    }

    fun renameDevice(deviceId: String, newName: String) {
        deviceManager.registry.renameDevice(deviceId, newName)
    }

    // Update methods
    fun checkForUpdates(isUserInitiated: Boolean = true) {
        updateManager.checkForUpdates(isUserInitiated)
    }

    fun startDownloadUpdate(manifest: UpdateManifest) {
        updateManager.startDownload(manifest)
    }

    fun cancelUpdateDownload() {
        updateManager.cancelDownload()
    }

    fun installUpdate(onResult: (InstallResult) -> Unit = {}) {
        viewModelScope.launch {
            val res = updateManager.installDownloadedApk()
            onResult(res)
        }
    }

    fun dismissUpdate(versionCode: Int) {
        updateManager.preferences.setDismissedVersion(versionCode)
    }

    fun setAutoUpdate(enabled: Boolean) {
        updateManager.preferences.setAutoUpdateEnabled(enabled)
    }

    fun setWifiOnly(enabled: Boolean) {
        updateManager.preferences.setWifiOnly(enabled)
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
        // simulation maintenance toggle if needed
    }

    fun clearUpdateCache() {
        updateManager.downloader.cleanCache()
    }

    fun clearUpdateHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            app.database.updateHistoryDao().clearAll()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
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

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.stopListening()
    }
}
