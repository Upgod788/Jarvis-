package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tools.AppUpdateTool
import com.example.tools.ToolResult
import com.example.update.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateSystemUnitTest {

    private lateinit var context: Context
    private lateinit var preferences: UpdatePreferences
    private lateinit var remoteConfigManager: RemoteConfigManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = UpdatePreferences(context)
        remoteConfigManager = RemoteConfigManager(context)
    }

    @Test
    fun testUpdateSecurityValidator_rejectsHttpUrl() {
        assertFalse(UpdateSecurityValidator.isHttpsUrl("http://insecure-server.com/update.apk"))
        assertFalse(UpdateSecurityValidator.isHttpsUrl("ftp://insecure-server.com/update.apk"))
        assertTrue(UpdateSecurityValidator.isHttpsUrl("https://updates.jarvis-ai.cloud/releases/update.apk"))
        assertTrue(UpdateSecurityValidator.isHttpsUrl("HTTPS://GITHUB.COM/RELEASES/UPDATE.APK"))
    }

    @Test
    fun testUpdateSecurityValidator_validatesCorrectSha256() {
        val tempFile = File(context.cacheDir, "test_sha.apk")
        tempFile.writeText("JARVIS_SECURE_PAYLOAD_DATA")

        val computedSha = UpdateSecurityValidator.calculateSha256(tempFile)
        assertNotNull(computedSha)
        assertEquals(64, computedSha.length)

        assertTrue(
            "Checksum should match computed hash",
            UpdateSecurityValidator.verifyChecksum(tempFile, computedSha)
        )

        assertFalse(
            "Mismatched hash should fail verification",
            UpdateSecurityValidator.verifyChecksum(tempFile, "0000000000000000000000000000000000000000000000000000000000000000")
        )

        tempFile.delete()
    }

    @Test
    fun testUpdatePreferences_persistsSettings() {
        preferences.setAutoUpdate(false)
        assertFalse(preferences.settings.value.autoUpdateEnabled)

        preferences.setAutoUpdate(true)
        assertTrue(preferences.settings.value.autoUpdateEnabled)

        preferences.setWifiOnly(false)
        assertFalse(preferences.settings.value.wifiOnly)

        preferences.setUpdateChannel("beta")
        assertEquals("beta", preferences.settings.value.updateChannel)

        preferences.setAutoDownload(true)
        assertTrue(preferences.settings.value.autoDownload)

        preferences.setIncludeBetaUpdates(true)
        assertTrue(preferences.settings.value.includeBetaUpdates)
    }

    @Test
    fun testRemoteConfigManager_featuresAndMaintenance() {
        val config = remoteConfigManager.config.value
        assertNotNull(config)
        assertFalse(config.maintenance.isEnabled)

        // Verify feature flags
        assertTrue(config.featureFlags.voiceAssistant)
        assertTrue(config.featureFlags.smartHome)
        assertTrue(config.featureFlags.instagramAutomation)
        assertTrue(config.featureFlags.pcControl)
    }

    @Test
    fun testAppUpdateTool_checkAndStatus() = runBlocking {
        val updateManager = UpdateManager(context, preferences, remoteConfigManager)
        val tool = AppUpdateTool(updateManagerProvider = { updateManager })

        // 1. Status action
        val statusResult = tool.execute(context, mapOf("action" to "status"))
        assertTrue("Status command should execute successfully", statusResult.success)

        // 2. Check action
        val checkResult = tool.execute(context, mapOf("action" to "check"))
        assertTrue("Check command should execute successfully", checkResult.success)

        // 3. Whats_new action
        val whatsNewResult = tool.execute(context, mapOf("action" to "whats_new"))
        assertTrue("What's new command should execute successfully", whatsNewResult.success)
    }
}
