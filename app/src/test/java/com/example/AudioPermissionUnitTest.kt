package com.example

import android.Manifest
import com.example.permissions.AudioPermissionUiStatus
import com.example.permissions.PermissionManager
import org.junit.Assert.*
import org.junit.Test

class AudioPermissionUnitTest {

    @Test
    fun testAudioPermissionLabel() {
        val label = PermissionManager.getPermissionLabel(Manifest.permission.RECORD_AUDIO)
        assertEquals("Microphone", label)
    }

    @Test
    fun testAudioPermissionUiStates() {
        val granted: AudioPermissionUiStatus = AudioPermissionUiStatus.Granted
        val rationale: AudioPermissionUiStatus = AudioPermissionUiStatus.RequiresRationale("Voice access required")
        val permanentlyDenied: AudioPermissionUiStatus = AudioPermissionUiStatus.PermanentlyDenied
        val notRequested: AudioPermissionUiStatus = AudioPermissionUiStatus.NotRequested

        assertTrue(granted is AudioPermissionUiStatus.Granted)
        assertTrue(rationale is AudioPermissionUiStatus.RequiresRationale)
        assertEquals("Voice access required", (rationale as AudioPermissionUiStatus.RequiresRationale).explanation)
        assertTrue(permanentlyDenied is AudioPermissionUiStatus.PermanentlyDenied)
        assertTrue(notRequested is AudioPermissionUiStatus.NotRequested)
    }
}
