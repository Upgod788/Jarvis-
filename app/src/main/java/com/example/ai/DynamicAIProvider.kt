package com.example.ai

import android.content.Context
import android.util.Log

class DynamicAIProvider(
    private val context: Context,
    val settingsManager: AISettingsManager = AISettingsManager(context)
) : AIProvider {
    companion object {
        private const val TAG = "DynamicAIProvider"
    }

    val geminiProvider = FirebaseAIProvider(context)
    val openRouterProvider = OpenRouterAIProvider(
        apiKey = settingsManager.settings.value.openRouterApiKey,
        modelName = settingsManager.settings.value.openRouterModel,
        fallbackProvider = geminiProvider
    )

    init {
        val savedGeminiKey = settingsManager.settings.value.geminiCustomApiKey
        if (savedGeminiKey.isNotBlank()) {
            geminiProvider.updateApiKey(savedGeminiKey)
        }
    }

    override val providerName: String
        get() = when (settingsManager.settings.value.providerType) {
            AIProviderType.GEMINI -> "Gemini AI (Default Built-in)"
            AIProviderType.OPENROUTER -> openRouterProvider.providerName
        }

    fun getActiveProviderType(): AIProviderType = settingsManager.settings.value.providerType

    fun setProviderType(type: AIProviderType) {
        settingsManager.saveProvider(type)
    }

    fun updateApiKey(key: String?) {
        val safe = key.orEmpty()
        settingsManager.saveGeminiCustomKey(safe)
        geminiProvider.updateApiKey(safe.ifBlank { null })
    }

    fun updateOpenRouterConfig(apiKey: String, model: String) {
        settingsManager.saveOpenRouterConfig(apiKey, model)
        openRouterProvider.updateConfig(apiKey, model)
    }

    suspend fun testOpenRouterConnection(apiKey: String, model: String): Pair<Boolean, String> {
        return openRouterProvider.testConnection(apiKey, model)
    }

    override suspend fun processCommand(request: AIRequest): AIResponse {
        val current = settingsManager.settings.value
        return when (current.providerType) {
            AIProviderType.GEMINI -> {
                Log.d(TAG, "Routing command through default Gemini AI.")
                geminiProvider.processCommand(request)
            }
            AIProviderType.OPENROUTER -> {
                if (current.openRouterApiKey.isNotBlank()) {
                    Log.d(TAG, "Routing command through OpenRouter (${current.openRouterModel})")
                    openRouterProvider.processCommand(request)
                } else {
                    Log.d(TAG, "OpenRouter selected but API key is blank. Defaulting to Gemini.")
                    geminiProvider.processCommand(request)
                }
            }
        }
    }
}
