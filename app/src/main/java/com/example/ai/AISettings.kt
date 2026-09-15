package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AIProviderType(val id: String, val displayName: String) {
    GEMINI("gemini", "Gemini AI (Default)"),
    OPENROUTER("openrouter", "OpenRouter (Custom)")
}

data class AISettings(
    val providerType: AIProviderType = AIProviderType.GEMINI,
    val geminiCustomApiKey: String = "",
    val openRouterApiKey: String = "",
    val openRouterModel: String = DEFAULT_OPENROUTER_MODEL
) {
    companion object {
        const val DEFAULT_OPENROUTER_MODEL = "google/gemini-2.5-flash"
    }
}

class AISettingsManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "jarvis_ai_settings"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_GEMINI_KEY = "gemini_custom_api_key"
        private const val KEY_OPENROUTER_KEY = "openrouter_api_key"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AISettings> = _settings.asStateFlow()

    private fun loadSettings(): AISettings {
        val providerStr = prefs.getString(KEY_PROVIDER, AIProviderType.GEMINI.id) ?: AIProviderType.GEMINI.id
        val provider = if (providerStr == AIProviderType.OPENROUTER.id) AIProviderType.OPENROUTER else AIProviderType.GEMINI
        val geminiKey = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        val openRouterKey = prefs.getString(KEY_OPENROUTER_KEY, "") ?: ""
        val openRouterModel = prefs.getString(KEY_OPENROUTER_MODEL, AISettings.DEFAULT_OPENROUTER_MODEL) ?: AISettings.DEFAULT_OPENROUTER_MODEL

        return AISettings(
            providerType = provider,
            geminiCustomApiKey = geminiKey,
            openRouterApiKey = openRouterKey,
            openRouterModel = if (openRouterModel.isBlank()) AISettings.DEFAULT_OPENROUTER_MODEL else openRouterModel
        )
    }

    fun saveProvider(providerType: AIProviderType) {
        prefs.edit().putString(KEY_PROVIDER, providerType.id).apply()
        _settings.value = _settings.value.copy(providerType = providerType)
    }

    fun saveGeminiCustomKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_GEMINI_KEY, trimmed).apply()
        _settings.value = _settings.value.copy(geminiCustomApiKey = trimmed)
    }

    fun saveOpenRouterConfig(apiKey: String, model: String) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { AISettings.DEFAULT_OPENROUTER_MODEL }
        prefs.edit().putString(KEY_OPENROUTER_KEY, cleanKey).putString(KEY_OPENROUTER_MODEL, cleanModel).apply()
        _settings.value = _settings.value.copy(openRouterApiKey = cleanKey, openRouterModel = cleanModel)
    }
}
