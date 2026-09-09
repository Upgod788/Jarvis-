package com.example

import android.app.Application
import com.example.agent.ConfirmationManager
import com.example.agent.JarvisAgent
import com.example.agent.ToolRegistry
import com.example.ai.DynamicAIProvider
import com.example.database.JarvisDatabase
import com.example.history.ConversationRepository
import com.example.memory.MemoryRepository
import com.example.voice.SpeechRecognizerManager
import com.example.voice.TextToSpeechManager
import com.example.voice.WakeWordManager

class JarvisApplication : Application() {

    lateinit var database: JarvisDatabase
        private set

    lateinit var memoryRepository: MemoryRepository
        private set

    lateinit var conversationRepository: ConversationRepository
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var confirmationManager: ConfirmationManager
        private set

    lateinit var themeManager: com.example.ui.theme.ThemeManager
        private set

    lateinit var aiProvider: DynamicAIProvider
        private set

    lateinit var speechRecognizerManager: SpeechRecognizerManager
        private set

    lateinit var textToSpeechManager: TextToSpeechManager
        private set

    lateinit var wakeWordManager: WakeWordManager
        private set

    lateinit var agent: JarvisAgent
        private set

    override fun onCreate() {
        super.onCreate()
        database = JarvisDatabase.getInstance(this)
        memoryRepository = MemoryRepository(database.memoryDao())
        conversationRepository = ConversationRepository(database.conversationDao())
        toolRegistry = ToolRegistry(memoryRepository)
        confirmationManager = ConfirmationManager(this)
        themeManager = com.example.ui.theme.ThemeManager(this)
        aiProvider = DynamicAIProvider(this)
        speechRecognizerManager = SpeechRecognizerManager(this)
        textToSpeechManager = TextToSpeechManager(this)
        wakeWordManager = WakeWordManager()

        agent = JarvisAgent(
            context = this,
            toolRegistry = toolRegistry,
            confirmationManager = confirmationManager,
            aiProvider = aiProvider,
            conversationRepository = conversationRepository
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        textToSpeechManager.shutdown()
        speechRecognizerManager.stopListening()
    }
}
