package com.example.emotion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmotionManager(
    private val detector: EmotionDetector = EmotionDetector()
) {
    private val _currentState = MutableStateFlow(EmotionalState.CALM)
    val currentState: StateFlow<EmotionalState> = _currentState.asStateFlow()

    fun updateFromUserInput(input: String): EmotionalState {
        val detected = detector.detectEmotion(input)
        _currentState.value = detected
        return detected
    }

    fun resetToDefault() {
        _currentState.value = EmotionalState.CALM
    }

    fun getCurrentPromptGuideline(): String = _currentState.value.promptGuideline

    fun getVoicePitchMultiplier(): Float = _currentState.value.pitchMultiplier

    fun getVoiceRateMultiplier(): Float = _currentState.value.speechRateMultiplier
}
