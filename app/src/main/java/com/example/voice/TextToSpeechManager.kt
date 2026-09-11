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

/**
 * Enterprise-grade TextToSpeech manager with support for:
 * - Dynamic device voice enumeration and selection
 * - Multilingual locale switching (English, Hindi, Hinglish, Bengali, Tamil, etc.)
 * - Proper audio focus acquisition and release
 * - Rate, pitch, and voice persistence
 * - Speech queue control and duplicate callback suppression
 */
class TextToSpeechManager(
    private val context: Context,
    val voiceSettingsManager: VoiceSettingsManager = VoiceSettingsManager(context)
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val utteranceCounter = AtomicLong(0)
    private var activeUtteranceId: String? = null

    var isVoiceEnabled: Boolean = true

    // Legacy property getters/setters for compatibility with any older references
    var speechRate: Float
        get() = voiceSettingsManager.settings.value.speechRate
        set(value) {
            voiceSettingsManager.setSpeechRate(value)
            applyVoiceSettings()
        }

    var pitch: Float
        get() = voiceSettingsManager.settings.value.pitch
        set(value) {
            voiceSettingsManager.setPitch(value)
            applyVoiceSettings()
        }

    var preferredLanguage: String
        get() = voiceSettingsManager.settings.value.languageCode
        set(value) {
            voiceSettingsManager.setLanguage(value)
            refreshAvailableVoices()
            applyVoiceSettings()
        }

    private var onSpeakingStateChangeListener: ((Boolean) -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech engine", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            refreshAvailableVoices()
            applyVoiceSettings()
            Log.i(TAG, "TextToSpeech engine successfully initialized")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            isInitialized = false
        }
    }

    fun setSpeakingStateListener(listener: (Boolean) -> Unit) {
        this.onSpeakingStateChangeListener = listener
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != activeUtteranceId) return
                _isSpeaking.value = true
                onSpeakingStateChangeListener?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != activeUtteranceId) return
                _isSpeaking.value = false
                onSpeakingStateChangeListener?.invoke(false)
                releaseAudioFocus()
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != activeUtteranceId) return
                _isSpeaking.value = false
                onSpeakingStateChangeListener?.invoke(false)
                releaseAudioFocus()
            }
        })
    }

    /**
     * Enumerates device TTS voices and filters those compatible with the currently selected language.
     */
    fun refreshAvailableVoices() {
        if (!isInitialized) return
        val currentLang = voiceSettingsManager.getCurrentLanguage()
        val targetLocale = currentLang.ttsLocale

        try {
            val allVoices: Set<Voice>? = tts?.voices
            val compatibleVoices = mutableListOf<VoiceOption>()

            // Add default fallback option at index 0
            compatibleVoices.add(
                VoiceOption(
                    id = "default",
                    displayName = "Default (${currentLang.displayName})",
                    localeTag = targetLocale.toLanguageTag()
                )
            )

            if (!allVoices.isNullOrEmpty()) {
                val targetLangCode = targetLocale.language.lowercase()

                val filtered = allVoices.filter { voice ->
                    val voiceLang = voice.locale.language.lowercase()
                    voiceLang == targetLangCode
                }.sortedWith(
                    compareBy<Voice> { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.name }
                )

                filtered.forEachIndexed { index, voice ->
                    val isLocal = !voice.isNetworkConnectionRequired
                    val display = buildString {
                        append("Voice ${index + 1}")
                        if (voice.name.contains("male", ignoreCase = true) && !voice.name.contains("female", ignoreCase = true)) {
                            append(" (Male")
                        } else if (voice.name.contains("female", ignoreCase = true)) {
                            append(" (Female")
                        } else {
                            append(" (")
                        }
                        append(if (isLocal) ", Offline)" else ", Online)")
                    }

                    compatibleVoices.add(
                        VoiceOption(
                            id = voice.name,
                            displayName = display,
                            localeTag = voice.locale.toLanguageTag(),
                            isNetworkRequired = voice.isNetworkConnectionRequired,
                            quality = voice.quality
                        )
                    )
                }
            }

            voiceSettingsManager.updateAvailableVoices(compatibleVoices)
        } catch (e: Exception) {
            Log.w(TAG, "Error enumerating TTS voices", e)
        }
    }

    /**
     * Applies the configured language, voice, speech rate, and pitch to the active TTS engine.
     */
    fun applyVoiceSettings() {
        if (!isInitialized) return
        val engine = tts ?: return
        val settings = voiceSettingsManager.settings.value
        val currentLang = voiceSettingsManager.getCurrentLanguage()
        val targetLocale = currentLang.ttsLocale

        try {
            // Set locale
            if (engine.isLanguageAvailable(targetLocale) >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
            } else {
                Log.w(TAG, "Target locale $targetLocale not fully supported, attempting fallback")
                engine.language = Locale.US
            }

            // Set voice if a specific non-default voice was selected
            if (settings.voiceId != "default" && settings.voiceId.isNotBlank()) {
                try {
                    val matchingVoice = engine.voices?.find { it.name == settings.voiceId }
                    if (matchingVoice != null && matchingVoice.locale.language.equals(targetLocale.language, ignoreCase = true)) {
                        engine.voice = matchingVoice
                    } else {
                        // Incompatible or missing voice - fall back cleanly
                        Log.i(TAG, "Voice ${settings.voiceId} unavailable, falling back to default voice")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting TTS voice", e)
                }
            }

            // Set rate and pitch
            engine.setSpeechRate(settings.speechRate)
            engine.setPitch(settings.pitch)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying voice settings", e)
        }
    }

    /**
     * Backward-compatible alias for applyVoiceSettings.
     */
    fun updateLanguageAndPitch() {
        applyVoiceSettings()
    }

    /**
     * Synthesizes speech for the provided text using the active voice and settings.
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isVoiceEnabled || text.isBlank()) {
            onComplete?.invoke()
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "Cannot speak: TextToSpeech is not yet initialized")
            onComplete?.invoke()
            return
        }

        stop()
        applyVoiceSettings()
        requestAudioFocus()

        val params = Bundle()
        val utteranceId = "jarvis_${System.currentTimeMillis()}_${utteranceCounter.incrementAndGet()}"
        activeUtteranceId = utteranceId

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS speak failed with code: $result")
            _isSpeaking.value = false
            releaseAudioFocus()
            onComplete?.invoke()
        }
    }

    /**
     * Plays a voice test sample in the current selected language with active voice, speed, and pitch.
     */
    fun testVoice(sampleText: String? = null) {
        val textToSpeak = sampleText ?: voiceSettingsManager.getCurrentLanguage().sampleText
        speak(textToSpeak)
    }

    /**
     * Halts any active speech and releases audio focus.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        } finally {
            activeUtteranceId = null
            _isSpeaking.value = false
            onSpeakingStateChangeListener?.invoke(false)
            releaseAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener { /* handle focus changes if needed */ }
                        .build()
                }
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring audio focus", e)
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
            Log.w(TAG, "Error releasing audio focus", e)
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
