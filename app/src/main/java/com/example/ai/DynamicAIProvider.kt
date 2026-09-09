package com.example.ai

import android.content.Context
import android.util.Log

/**
 * Dynamic AI Provider that coordinates between:
 * 1. Default Gemini AI (Firebase AI SDK & Gemini REST with built-in default key - zero input needed)
 * 2. OpenRouter AI (custom user API key and model)
 *
 * Persists user configuration across app restarts via [AISettingsManager].
 * Defaults strictly to Gemini so the user never has to repeatedly enter credentials.
 */
class DynamicAIProvider(
    private val context: Context,
    val settingsManager: AISettingsManager = AISettingsManager(context)
) : AIProvider {

    companion object {
        private const val TAG = "DynamicAIProvider"
    }

    val geminiProvider: FirebaseAIProvider = FirebaseAIProvider(context)
    val openRouterProvider: OpenRouterAIProvider = OpenRouterAIProvider(
        apiKey = settingsManager.settings.value.openRouterApiKey,
        modelName = settingsManager.settings.value.openRouterModel,
        fallbackProvider = geminiProvider // Seamless fallback to Gemini if OpenRouter fails
    )

    init {
        // Apply saved custom Gemini key if present
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

    /**
     * Backwards-compatible method to update the Gemini API key.
     */
    fun updateApiKey(key: String?) {
        val safeKey = key ?: ""
        settingsManager.saveGeminiCustomKey(safeKey)
        geminiProvider.updateApiKey(safeKey.ifBlank { null })
    }

    fun updateOpenRouterConfig(apiKey: String, model: String) {
        settingsManager.saveOpenRouterConfig(apiKey, model)
        openRouterProvider.updateConfig(apiKey, model)
    }

    suspend fun testOpenRouterConnection(apiKey: String, model: String): Pair<Boolean, String> {
        return openRouterProvider.testConnection(apiKey, model)
    }

    override suspend fun processCommand(request: AIRequest): AIResponse {
        val currentSettings = settingsManager.settings.value

        return when (currentSettings.providerType) {
            AIProviderType.OPENROUTER -> {
                if (currentSettings.openRouterApiKey.isNotBlank()) {
                    Log.d(TAG, "Routing command through OpenRouter (${currentSettings.openRouterModel})")
                    openRouterProvider.processCommand(request)
                } else {
                    Log.d(TAG, "OpenRouter selected but API key is blank. Defaulting seamlessly to Gemini.")
                    geminiProvider.processCommand(request)
                }
            }
            AIProviderType.GEMINI -> {
                Log.d(TAG, "Routing command through default Gemini AI.")
                geminiProvider.processCommand(request)
            }
        }
    }
}
