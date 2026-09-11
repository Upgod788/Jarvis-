package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Events emitted during the lifecycle of a speech recognition session.
 */
sealed interface SpeechEvent {
    /** Speech recognizer is ready and actively listening for user speech. */
    data object Ready : SpeechEvent

    /** Speech input has begun (user started talking). */
    data object BeginningOfSpeech : SpeechEvent

    /** Sound input has ended (user paused or finished talking). */
    data object EndOfSpeech : SpeechEvent

    /** Intermediate partial transcript captured in real-time while user is still speaking. */
    data class PartialResult(val text: String) : SpeechEvent

    /** Final high-confidence speech transcription converted into a text string. */
    data class FinalResult(
        val text: String,
        val confidence: Float = 1.0f,
        val alternatives: List<String> = emptyList()
    ) : SpeechEvent

    /** Real-time microphone audio volume in decibels (-2.0 to 10.0+ dB). */
    data class RmsChanged(val rmsdB: Float, val normalizedLevel: Float) : SpeechEvent

    /** Recognition encountered an error or timed out. */
    data class Error(val message: String, val errorCode: Int) : SpeechEvent
}

/**
 * Robust, thread-safe manager for Android's [SpeechRecognizer] API.
 * Features:
 * - Direct language tag integration from [VoiceSettingsManager]
 * - Audio focus management during recording
 * - Pre-execution RECORD_AUDIO permission safety checks
 * - Concurrent session collision prevention
 * - Partial and final result buffering
 * - Infinite retry loop avoidance
 */
class SpeechRecognizerManager(
    private val context: Context,
    val voiceSettingsManager: VoiceSettingsManager? = null
) {

    companion object {
        private const val TAG = "SpeechRecognizerManager"

        // Extended error codes for Android API 31+
        const val ERROR_TOO_MANY_REQUESTS = 10
        const val ERROR_SERVER_DISCONNECTED = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
        const val ERROR_CANNOT_CHECK_SUPPORT = 14
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    /**
     * Checks whether speech recognition services are supported and installed on this device.
     */
    fun isAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Initiates voice recognition on the main thread.
     *
     * @param language Optional BCP-47 language tag (e.g., "hi-IN", "en-US"). If null, uses the active language from VoiceSettingsManager.
     * @param onEvent Callback receiving speech recognition lifecycle events.
     */
    fun startListening(
        language: String? = null,
        onEvent: (SpeechEvent) -> Unit
    ) {
        mainHandler.post {
            // Clean up any existing session prior to creating a new one
            cleanupInternal()

            // 1. Verify Audio Permission
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "RECORD_AUDIO permission is not granted.")
                onEvent(
                    SpeechEvent.Error(
                        "Microphone permission is required to listen.",
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    )
                )
                return@post
            }

            // 2. Verify SpeechRecognizer availability
            if (!isAvailable()) {
                Log.w(TAG, "SpeechRecognizer is not available on this device.")
                onEvent(SpeechEvent.Error("Speech recognition is not available on this device.", -1))
                return@post
            }

            // 3. Resolve active recognition language
            val activeLang = voiceSettingsManager?.getCurrentLanguage()
            val resolvedLangTag = language?.ifBlank { null }
                ?: activeLang?.sttLanguageTag
                ?: Locale.getDefault().toLanguageTag()

            try {
                requestAudioFocus()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer.apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(TAG, "SpeechRecognizer ready for speech (lang: $resolvedLangTag)")
                            _isListening.value = true
                            _lastRecognizedText.value = ""
                            onEvent(SpeechEvent.Ready)
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d(TAG, "Speech input began")
                            onEvent(SpeechEvent.BeginningOfSpeech)
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val clamped = rmsdB.coerceIn(0f, 10f)
                            val normalized = clamped / 10f
                            _rmsLevel.value = normalized
                            onEvent(SpeechEvent.RmsChanged(rmsdB, normalized))
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            Log.d(TAG, "Speech input ended")
                            _isListening.value = false
                            _rmsLevel.value = 0f
                            onEvent(SpeechEvent.EndOfSpeech)
                        }

                        override fun onError(error: Int) {
                            Log.w(TAG, "SpeechRecognizer error code: $error")
                            _isListening.value = false
                            _rmsLevel.value = 0f
                            releaseAudioFocus()

                            // If partial speech was captured, deliver it instead of failing on silence timeout
                            val partialCaptured = _lastRecognizedText.value.trim()
                            if (partialCaptured.isNotBlank() && partialCaptured.length >= 2) {
                                Log.i(TAG, "Recovered speech command from partial transcript: \"$partialCaptured\" despite error $error")
                                onEvent(SpeechEvent.FinalResult(partialCaptured, 0.9f, emptyList()))
                                cleanupInternal()
                                return
                            }

                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Tap to retry."
                                SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer notice. Tap to retry."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                                SpeechRecognizer.ERROR_NETWORK -> "Network issue with speech recognition. Tap to retry."
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition connection timed out."
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap the mic to speak."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Resetting..."
                                SpeechRecognizer.ERROR_SERVER -> "Speech service temporarily unavailable. Tap to retry."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and speak."
                                ERROR_TOO_MANY_REQUESTS -> "Too many voice requests. Please try again shortly."
                                ERROR_SERVER_DISCONNECTED -> "Voice connection reset. Tap to speak again."
                                ERROR_LANGUAGE_NOT_SUPPORTED -> "Selected language ($resolvedLangTag) not supported on device."
                                ERROR_LANGUAGE_UNAVAILABLE -> "Selected language is currently unavailable."
                                ERROR_CANNOT_CHECK_SUPPORT -> "Unable to verify speech recognizer status."
                                else -> "Speech recognition notice ($error)"
                            }

                            onEvent(SpeechEvent.Error(errorMsg, error))
                            cleanupInternal()
                        }

                        override fun onResults(results: Bundle?) {
                            Log.d(TAG, "SpeechRecognizer results received")
                            _isListening.value = false
                            _rmsLevel.value = 0f
                            releaseAudioFocus()

                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                            var bestMatch = matches?.firstOrNull()?.trim() ?: ""
                            if (bestMatch.isBlank() && _lastRecognizedText.value.isNotBlank()) {
                                bestMatch = _lastRecognizedText.value.trim()
                            }

                            val confidence = scores?.firstOrNull() ?: 1.0f
                            val alternatives = if ((matches?.size ?: 0) > 1) {
                                matches!!.subList(1, matches.size)
                            } else {
                                emptyList()
                            }

                            if (bestMatch.isNotBlank()) {
                                _lastRecognizedText.value = bestMatch
                                Log.i(TAG, "Recognized command: \"$bestMatch\" (confidence: $confidence)")
                                onEvent(SpeechEvent.FinalResult(bestMatch, confidence, alternatives))
                            } else {
                                onEvent(SpeechEvent.Error("No clear speech heard.", SpeechRecognizer.ERROR_NO_MATCH))
                            }

                            cleanupInternal()
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partialText = matches?.firstOrNull()?.trim() ?: ""
                            if (partialText.isNotBlank()) {
                                _lastRecognizedText.value = partialText
                                onEvent(SpeechEvent.PartialResult(partialText))
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedLangTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, resolvedLangTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                    // Multilingual additional language support tailored to the chosen language
                    val additionalLangs = activeLang?.additionalLangs ?: arrayOf("en-US", "en-IN", "hi-IN")
                    putExtra("android.speech.extra.ADDITIONAL_LANGUAGES", additionalLangs)

                    // Generous silence thresholds for natural pausing
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognizer", e)
                _isListening.value = false
                _rmsLevel.value = 0f
                releaseAudioFocus()
                cleanupInternal()
                onEvent(SpeechEvent.Error("Failed to start speech recognition: ${e.localizedMessage}", -1))
            }
        }
    }

    /**
     * Gracefully stops the active speech recognition session.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping SpeechRecognizer", e)
            } finally {
                _isListening.value = false
                _rmsLevel.value = 0f
                releaseAudioFocus()
            }
        }
    }

    /**
     * Cancels recognition immediately without waiting for results.
     */
    fun cancel() {
        mainHandler.post {
            cleanupInternal()
        }
    }

    private fun cleanupInternal() {
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying SpeechRecognizer", e)
        } finally {
            speechRecognizer = null
            _isListening.value = false
            _rmsLevel.value = 0f
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
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener { /* handle focus changes */ }
                        .build()
                }
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring audio focus for recording", e)
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
            Log.w(TAG, "Error releasing audio focus for recording", e)
        }
    }
}
