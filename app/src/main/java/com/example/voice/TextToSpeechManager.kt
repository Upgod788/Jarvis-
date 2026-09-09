package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var isVoiceEnabled: Boolean = true
    var speechRate: Float = 1.0f
    var pitch: Float = 1.0f
    var preferredLanguage: String = "en"

    private var onSpeakingStateChangeListener: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            updateLanguageAndPitch()
            setupProgressListener()
        }
    }

    fun setSpeakingStateListener(listener: (Boolean) -> Unit) {
        this.onSpeakingStateChangeListener = listener
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                onSpeakingStateChangeListener?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeakingStateChangeListener?.invoke(false)
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeakingStateChangeListener?.invoke(false)
            }
        })
    }

    fun updateLanguageAndPitch() {
        if (!isInitialized) return
        tts?.let { engine ->
            val targetLocale = when (preferredLanguage) {
                "hi" -> Locale("hi", "IN")
                "en-in" -> Locale("en", "IN")
                else -> Locale.US
            }
            if (engine.isLanguageAvailable(targetLocale) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
            } else {
                engine.language = Locale.US
            }
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isVoiceEnabled || text.isBlank()) {
            onComplete?.invoke()
            return
        }

        if (!isInitialized) {
            return
        }

        updateLanguageAndPitch()

        val params = Bundle()
        val utteranceId = "jarvis_${System.currentTimeMillis()}"

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        onSpeakingStateChangeListener?.invoke(false)
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
