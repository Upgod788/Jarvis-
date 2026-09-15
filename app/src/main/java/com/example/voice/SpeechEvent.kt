package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface SpeechEvent {
    object Ready : SpeechEvent
    object BeginningOfSpeech : SpeechEvent
    object EndOfSpeech : SpeechEvent
    data class PartialResult(val text: String) : SpeechEvent
    data class FinalResult(val text: String, val alternatives: List<String> = emptyList()) : SpeechEvent
    data class RmsChanged(val rmsdB: Float) : SpeechEvent
    data class Error(val errorCode: Int, val message: String) : SpeechEvent
}

class WakeWordManager {
    var isEnabled: Boolean = false
    private val _isWakeWordListening = MutableStateFlow(false)
    val isWakeWordListening: StateFlow<Boolean> = _isWakeWordListening.asStateFlow()
    var wakeWordPhrase: String = "Hey JARVIS"

    fun checkSpeechForWakeWord(text: String): Boolean {
        val clean = text.trim().lowercase(Locale.ROOT)
        return clean.contains("hey jarvis") || clean.contains("jarvis")
    }

    fun setListening(active: Boolean) {
        _isWakeWordListening.value = active
    }
}
