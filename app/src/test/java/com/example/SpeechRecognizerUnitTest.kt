package com.example

import android.speech.SpeechRecognizer
import com.example.voice.SpeechEvent
import org.junit.Assert.*
import org.junit.Test

class SpeechRecognizerUnitTest {

    @Test
    fun testSpeechEventFinalResult() {
        val event = SpeechEvent.FinalResult(
            text = "turn on flashlight",
            confidence = 0.95f,
            alternatives = listOf("turn on flash", "turn flashlight on")
        )

        assertEquals("turn on flashlight", event.text)
        assertEquals(0.95f, event.confidence, 0.01f)
        assertEquals(2, event.alternatives.size)
    }

    @Test
    fun testSpeechEventPartialResult() {
        val event = SpeechEvent.PartialResult(text = "what's the")
        assertEquals("what's the", event.text)
    }

    @Test
    fun testSpeechEventRmsNormalization() {
        val rmsdB = 6.0f
        val clamped = rmsdB.coerceIn(0f, 10f)
        val normalized = clamped / 10f

        val event = SpeechEvent.RmsChanged(rmsdB = rmsdB, normalizedLevel = normalized)
        assertEquals(6.0f, event.rmsdB, 0.01f)
        assertEquals(0.6f, event.normalizedLevel, 0.01f)
    }

    @Test
    fun testSpeechEventErrorHandling() {
        val errorNoMatch = SpeechEvent.Error("No speech recognized", SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(SpeechRecognizer.ERROR_NO_MATCH, errorNoMatch.errorCode)
        assertTrue(errorNoMatch.message.contains("No speech"))

        val errorTimeout = SpeechEvent.Error("No speech input detected", SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, errorTimeout.errorCode)

        val errorServerDisconnected = SpeechEvent.Error(
            "Speech recognition server disconnected. Resetting...",
            com.example.voice.SpeechRecognizerManager.ERROR_SERVER_DISCONNECTED
        )
        assertEquals(11, errorServerDisconnected.errorCode)
        assertTrue(errorServerDisconnected.message.contains("disconnected"))
    }
}
