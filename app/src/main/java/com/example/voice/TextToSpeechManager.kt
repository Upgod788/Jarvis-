package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class TextToSpeechManager(
    private val context: Context,
    val voiceSettingsManager: VoiceSettingsManager? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val utteranceCounter = AtomicLong(0L)
    private var activeUtteranceId: String? = null
    var isVoiceEnabled: Boolean = true

    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    var preferredLanguage: String = "en"
        set(value) {
            field = value
            updateLanguageAndPitch()
        }

    private var onSpeakingStateChangeListener: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            refreshAvailableVoices()
            applyVoiceSettings()
            Log.d(TAG, "TextToSpeech successfully initialized")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with code $status")
        }
    }

    fun setSpeakingStateListener(listener: (Boolean) -> Unit) {
        onSpeakingStateChangeListener = listener
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
                releaseAudioFocus()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeakingStateChangeListener?.invoke(false)
                releaseAudioFocus()
            }
        })
    }

    fun refreshAvailableVoices() {
        val currentTts = tts ?: return
        try {
            val systemVoices = currentTts.voices
            if (!systemVoices.isNullOrEmpty()) {
                val mapped = systemVoices.map { voice ->
                    VoiceOption(
                        id = voice.name,
                        displayName = "${voice.locale.displayName} (${voice.name.takeLast(6)})",
                        localeTag = voice.locale.toLanguageTag(),
                        isNetworkRequired = voice.isNetworkConnectionRequired,
                        quality = voice.quality
                    )
                }
                voiceSettingsManager?.updateAvailableVoices(
                    listOf(VoiceOption("default", "Default (System Recommended)", "en-US", false, 300)) + mapped
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch TTS voices: ${e.message}")
        }
    }

    fun applyVoiceSettings() {
        if (!isInitialized) return
        val currentSettings = voiceSettingsManager?.settings?.value ?: return
        val lang = VoiceLanguageRegistry.findByCode(currentSettings.languageCode)

        tts?.language = lang.ttsLocale
        tts?.setSpeechRate(currentSettings.speechRate)
        tts?.setPitch(currentSettings.pitch)

        if (currentSettings.voiceId != "default") {
            try {
                val matchingVoice = tts?.voices?.firstOrNull { it.name == currentSettings.voiceId }
                if (matchingVoice != null) {
                    tts?.voice = matchingVoice
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun updateLanguageAndPitch() {
        if (!isInitialized) return
        val lang = VoiceLanguageRegistry.findByCode(preferredLanguage)
        tts?.language = lang.ttsLocale
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isVoiceEnabled || text.isBlank()) {
            onComplete?.invoke()
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "TTS requested but not yet initialized")
            onComplete?.invoke()
            return
        }

        applyVoiceSettings()
        requestAudioFocus()

        val utteranceId = "jarvis_utt_${utteranceCounter.incrementAndGet()}"
        activeUtteranceId = utteranceId

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun testVoice(sampleText: String? = null) {
        val sample = sampleText ?: voiceSettingsManager?.getCurrentLanguage()?.sampleText
            ?: "Systems online. Ravan is ready."
        speak(sample)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        _isSpeaking.value = false
        onSpeakingStateChangeListener?.invoke(false)
        releaseAudioFocus()
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                audioManager?.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "TextToSpeechManager"
    }
}
