package com.example.voice

import java.util.Locale

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val sttLanguageTag: String,
    val ttsLocale: Locale,
    val sampleText: String,
    val aiPromptInstruction: String,
    val additionalLangs: Array<String> = emptyArray()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SupportedLanguage) return false
        return code == other.code
    }

    override fun hashCode(): Int = code.hashCode()
}

data class VoiceOption(
    val id: String,
    val displayName: String,
    val localeTag: String,
    val isNetworkRequired: Boolean = false,
    val quality: Int = 300
) {
    val name: String get() = displayName
}

object VoiceLanguageRegistry {
    val allLanguages: List<SupportedLanguage> = listOf(
        SupportedLanguage(
            code = "en",
            displayName = "English (US)",
            nativeName = "English (US)",
            sttLanguageTag = "en-US",
            ttsLocale = Locale.US,
            sampleText = "Hello! I am Ravan, your personal Android AI assistant. How may I help you today?",
            aiPromptInstruction = "The user has selected English. Respond in natural, clear, and concise English.",
            additionalLangs = arrayOf("en-US", "en-IN")
        ),
        SupportedLanguage(
            code = "en-in",
            displayName = "English (India)",
            nativeName = "Indian English",
            sttLanguageTag = "en-IN",
            ttsLocale = Locale("en", "IN"),
            sampleText = "Hello! I am Ravan. Systems are online and ready for your command.",
            aiPromptInstruction = "The user has selected Indian English. Respond in natural, polite Indian English.",
            additionalLangs = arrayOf("en-IN", "en-US", "hi-IN")
        ),
        SupportedLanguage(
            code = "hi",
            displayName = "Hindi",
            nativeName = "हिन्दी",
            sttLanguageTag = "hi-IN",
            ttsLocale = Locale("hi", "IN"),
            sampleText = "नमस्ते! मैं रावण हूँ। मैं आपकी किस प्रकार सहायता कर सकता हूँ?",
            aiPromptInstruction = "The user has selected Hindi. Respond entirely in natural, respectful Hindi (Devanagari script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("hi-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "hinglish",
            displayName = "Hinglish (Hindi + English)",
            nativeName = "Hinglish",
            sttLanguageTag = "hi-IN",
            ttsLocale = Locale("en", "IN"),
            sampleText = "Hello sir! Main Ravan hoon. Main aapki kya madad kar sakta hoon?",
            aiPromptInstruction = "The user has selected Hinglish (Hindi-English). Respond naturally in conversational Hinglish using Roman script (for example: 'Haan sir, main aapki call connect kar raha hoon.') unless the user explicitly requests otherwise.",
            additionalLangs = arrayOf("hi-IN", "en-IN", "en-US")
        ),
        SupportedLanguage(
            code = "bn",
            displayName = "Bengali",
            nativeName = "বাংলা",
            sttLanguageTag = "bn-IN",
            ttsLocale = Locale("bn", "IN"),
            sampleText = "নমস্কার! আমি রাবণ। আমি আপনাকে কীভাবে সাহায্য করতে পারি?",
            aiPromptInstruction = "The user has selected Bengali. Respond entirely in natural, polite Bengali (Bengali script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("bn-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "ta",
            displayName = "Tamil",
            nativeName = "தமிழ்",
            sttLanguageTag = "ta-IN",
            ttsLocale = Locale("ta", "IN"),
            sampleText = "வணக்கம்! நான் ராவணன். நான் உங்களுக்கு எவ்வாறு உதவ முடியும்?",
            aiPromptInstruction = "The user has selected Tamil. Respond entirely in natural, polite Tamil (Tamil script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("ta-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "te",
            displayName = "Telugu",
            nativeName = "తెలుగు",
            sttLanguageTag = "te-IN",
            ttsLocale = Locale("te", "IN"),
            sampleText = "నమస్కారం! నేను రావణన్. నేను మీకు ఎలా సహాయపడగలను?",
            aiPromptInstruction = "The user has selected Telugu. Respond entirely in natural, polite Telugu (Telugu script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("te-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "mr",
            displayName = "Marathi",
            nativeName = "मराठी",
            sttLanguageTag = "mr-IN",
            ttsLocale = Locale("mr", "IN"),
            sampleText = "नमस्कार! मी रावण आहे. मी तुम्हाला कशी मदत करू शकतो?",
            aiPromptInstruction = "The user has selected Marathi. Respond entirely in natural, polite Marathi (Devanagari script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("mr-IN", "hi-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "gu",
            displayName = "Gujarati",
            nativeName = "ગુજરાતી",
            sttLanguageTag = "gu-IN",
            ttsLocale = Locale("gu", "IN"),
            sampleText = "નમસ્તે! હું રાવણ છું. હું તમને કેવી રીતે મદદ કરી શકું?",
            aiPromptInstruction = "The user has selected Gujarati. Respond entirely in natural, polite Gujarati (Gujarati script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("gu-IN", "hi-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "kn",
            displayName = "Kannada",
            nativeName = "ಕನ್ನಡ",
            sttLanguageTag = "kn-IN",
            ttsLocale = Locale("kn", "IN"),
            sampleText = "ನಮಸ್ಕಾರ! ನಾನು ರಾವಣ. ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಬಹುದು?",
            aiPromptInstruction = "The user has selected Kannada. Respond entirely in natural, polite Kannada (Kannada script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("kn-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "ml",
            displayName = "Malayalam",
            nativeName = "മലയാളം",
            sttLanguageTag = "ml-IN",
            ttsLocale = Locale("ml", "IN"),
            sampleText = "നമസ്കാരം! ഞാൻ രാവണൻ ആണ്. ഞാൻ നിങ്ങളെ എങ്ങനെ സഹായിക്കാം?",
            aiPromptInstruction = "The user has selected Malayalam. Respond entirely in natural, polite Malayalam (Malayalam script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("ml-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "pa",
            displayName = "Punjabi",
            nativeName = "ਪੰਜਾਬੀ",
            sttLanguageTag = "pa-IN",
            ttsLocale = Locale("pa", "IN"),
            sampleText = "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ! ਮੈਂ ਰਾਵਣ ਹਾਂ। ਮੈਂ ਤੁਹਾਡੀ ਕਿਵੇਂ ਮਦਦ ਕਰ ਸਕਦਾ ਹਾਂ?",
            aiPromptInstruction = "The user has selected Punjabi. Respond entirely in natural, polite Punjabi (Gurmukhi script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("pa-IN", "hi-IN", "en-IN")
        ),
        SupportedLanguage(
            code = "ur",
            displayName = "Urdu",
            nativeName = "اردو",
            sttLanguageTag = "ur-IN",
            ttsLocale = Locale("ur", "IN"),
            sampleText = "السلام علیکم! میں راون ہوں۔ میں آپ کی کیا مدد کر سکتا ہوں؟",
            aiPromptInstruction = "The user has selected Urdu. Respond entirely in natural, polite Urdu (Urdu script) unless the user explicitly asks for another language.",
            additionalLangs = arrayOf("ur-IN", "en-IN")
        )
    )

    fun findByCode(code: String): SupportedLanguage {
        val normalized = code.trim().lowercase(Locale.ROOT)
        allLanguages.firstOrNull { it.code == normalized }?.let { return it }
        allLanguages.firstOrNull { it.sttLanguageTag.equals(normalized, ignoreCase = true) }?.let { return it }
        return allLanguages.first()
    }

    fun getSampleText(code: String): String = findByCode(code).sampleText
    fun getAiInstruction(code: String): String = findByCode(code).aiPromptInstruction
}
