package com.example.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WakeWordManager {
    private val _isWakeWordListening = MutableStateFlow(false)
    val isWakeWordListening: StateFlow<Boolean> = _isWakeWordListening.asStateFlow()

    var wakeWordPhrase: String = "Hey JARVIS"
    var isEnabled: Boolean = false

    fun checkSpeechForWakeWord(text: String): Boolean {
        val clean = text.trim().lowercase()
        return clean.contains("hey jarvis") || clean.contains("jarvis")
    }

    fun setListening(active: Boolean) {
        _isWakeWordListening.value = active
    }
}
