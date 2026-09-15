package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceSettingsManager(private val context: Context? = null) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    private val _availableVoices = MutableStateFlow(
        listOf(VoiceOption("default", "Default (System Recommended)", "en-US", false, 300))
    )
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private fun loadSettings(): VoiceSettings {
        if (prefs == null) return VoiceSettings()
        return VoiceSettings(
            languageCode = prefs.getString(KEY_LANGUAGE, "en") ?: "en",
            voiceId = prefs.getString(KEY_VOICE_ID, "default") ?: "default",
            speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f),
            pitch = prefs.getFloat(KEY_PITCH, 1.0f)
        )
    }

    fun setLanguage(langCode: String) {
        val supported = VoiceLanguageRegistry.findByCode(langCode)
        val currentVoiceId = _settings.value.voiceId
        val isCompatible = _availableVoices.value.any { voice ->
            voice.id == currentVoiceId && (voice.id == "default" || voice.localeTag.startsWith(supported.ttsLocale.language, ignoreCase = true))
        }
        val effectiveVoiceId = if (isCompatible) currentVoiceId else "default"

        prefs?.edit()
            ?.putString(KEY_LANGUAGE, supported.code)
            ?.putString(KEY_VOICE_ID, effectiveVoiceId)
            ?.apply()

        _settings.value = _settings.value.copy(
            languageCode = supported.code,
            voiceId = effectiveVoiceId
        )
    }

    fun setVoice(voiceId: String) {
        val safeVoiceId = if (voiceId.isBlank()) "default" else voiceId
        prefs?.edit()?.putString(KEY_VOICE_ID, safeVoiceId)?.apply()
        _settings.value = _settings.value.copy(voiceId = safeVoiceId)
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        prefs?.edit()?.putFloat(KEY_SPEECH_RATE, clamped)?.apply()
        _settings.value = _settings.value.copy(speechRate = clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.6f, 1.6f)
        prefs?.edit()?.putFloat(KEY_PITCH, clamped)?.apply()
        _settings.value = _settings.value.copy(pitch = clamped)
    }

    fun updateAvailableVoices(voices: List<VoiceOption>) {
        _availableVoices.value = voices
    }

    fun getCurrentLanguage(): SupportedLanguage =
        VoiceLanguageRegistry.findByCode(_settings.value.languageCode)

    fun getCurrentAiInstruction(): String =
        getCurrentLanguage().aiPromptInstruction

    fun getCurrentSttLanguageTag(): String =
        getCurrentLanguage().sttLanguageTag

    companion object {
        const val PREFS_NAME = "jarvis_voice_settings"
        const val KEY_LANGUAGE = "pref_voice_language"
        const val KEY_VOICE_ID = "pref_voice_id"
        const val KEY_SPEECH_RATE = "pref_speech_rate"
        const val KEY_PITCH = "pref_pitch"
    }
}
