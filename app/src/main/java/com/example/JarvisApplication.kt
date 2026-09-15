package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.agent.ConfirmationManager
import com.example.agent.JarvisAgent
import com.example.agent.ToolRegistry
import com.example.ai.DynamicAIProvider
import com.example.database.JarvisDatabase
import com.example.database.UserCommandPreferenceRepository
import com.example.devices.DeviceManager
import com.example.history.ConversationRepository
import com.example.memory.MemoryRepository
import com.example.routines.RoutineManager
import com.example.ui.theme.ThemeManager
import com.example.update.UpdateManager
import com.example.voice.SpeechRecognizerManager
import com.example.voice.TextToSpeechManager
import com.example.voice.VoiceSettingsManager
import com.example.voice.WakeWordManager

class JarvisApplication : Application(), Configuration.Provider {

    lateinit var database: JarvisDatabase
        private set
    lateinit var memoryManager: com.example.memory.MemoryManager
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var conversationRepository: ConversationRepository
        private set
    lateinit var userCommandPreferenceRepository: UserCommandPreferenceRepository
        private set
    lateinit var emotionManager: com.example.emotion.EmotionManager
        private set
    lateinit var personalityManager: com.example.personality.PersonalityManager
        private set
    lateinit var responseStyleManager: com.example.personality.ResponseStyleManager
        private set
    lateinit var deviceManager: DeviceManager
        private set
    lateinit var routineManager: RoutineManager
        private set
    lateinit var confirmationManager: ConfirmationManager
        private set
    lateinit var themeManager: ThemeManager
        private set
    lateinit var voiceSettingsManager: VoiceSettingsManager
        private set
    lateinit var aiProvider: DynamicAIProvider
        private set
    lateinit var speechRecognizerManager: SpeechRecognizerManager
        private set
    lateinit var textToSpeechManager: TextToSpeechManager
        private set
    lateinit var wakeWordManager: WakeWordManager
        private set
    lateinit var updateManager: UpdateManager
        private set
    lateinit var toolRegistry: ToolRegistry
        private set
    lateinit var agent: JarvisAgent
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        database = JarvisDatabase.getInstance(this)
        memoryManager = com.example.memory.MemoryManager(this, database)
        memoryRepository = memoryManager.repository
        conversationRepository = ConversationRepository(database.conversationDao())
        userCommandPreferenceRepository = UserCommandPreferenceRepository(database.userCommandPreferenceDao())
        emotionManager = com.example.emotion.EmotionManager()
        personalityManager = com.example.personality.PersonalityManager(this)
        responseStyleManager = com.example.personality.ResponseStyleManager(personalityManager, emotionManager)
        deviceManager = DeviceManager(this)
        updateManager = UpdateManager(this)
        routineManager = RoutineManager(this) { toolRegistry }
        toolRegistry = ToolRegistry(
            memoryRepository = memoryRepository,
            deviceManager = deviceManager,
            routineManager = routineManager,
            updateManagerProvider = { updateManager }
        )
        confirmationManager = ConfirmationManager(this)
        themeManager = ThemeManager(this)
        voiceSettingsManager = VoiceSettingsManager(this)
        aiProvider = DynamicAIProvider(this)
        speechRecognizerManager = SpeechRecognizerManager(this, voiceSettingsManager)
        textToSpeechManager = TextToSpeechManager(this, voiceSettingsManager)
        wakeWordManager = WakeWordManager()
        agent = JarvisAgent(
            context = this,
            toolRegistry = toolRegistry,
            confirmationManager = confirmationManager,
            aiProvider = aiProvider,
            conversationRepository = conversationRepository,
            voiceSettingsManager = voiceSettingsManager,
            memoryManager = memoryManager,
            emotionManager = emotionManager,
            personalityManager = personalityManager,
            responseStyleManager = responseStyleManager
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        textToSpeechManager.shutdown()
        speechRecognizerManager.stopListening()
    }
}
