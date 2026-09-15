package com.example.voice

data class VoiceSettings(
    val languageCode: String = "en",
    val voiceId: String = "default",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
) {
    val language: SupportedLanguage
        get() = VoiceLanguageRegistry.findByCode(languageCode)
}



