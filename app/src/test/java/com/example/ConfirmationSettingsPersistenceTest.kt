package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.agent.ConfirmationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfirmationSettingsPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences before test
        context.getSharedPreferences("jarvis_confirmation_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun testDefaultConfirmationSettingsAreTrue() {
        val manager = ConfirmationManager(context)
        assertTrue(manager.requireCallConfirmation)
        assertTrue(manager.requireSmsConfirmation)
        assertTrue(manager.confirmMessagesBeforeSending)
        assertTrue(manager.settings.value.requireCallConfirmation)
        assertTrue(manager.settings.value.requireSmsConfirmation)
        assertTrue(manager.settings.value.confirmWhatsAppMessages)
    }

    @Test
    fun testConfirmationSettingsPersistWhenToggledOff() {
        val manager1 = ConfirmationManager(context)

        // Turn all 3 off
        manager1.setCallConfirmation(false)
        manager1.setSmsConfirmation(false)
        manager1.setWhatsAppConfirmation(false)

        assertFalse(manager1.requireCallConfirmation)
        assertFalse(manager1.requireSmsConfirmation)
        assertFalse(manager1.confirmMessagesBeforeSending)
        assertFalse(manager1.settings.value.requireCallConfirmation)
        assertFalse(manager1.settings.value.requireSmsConfirmation)
        assertFalse(manager1.settings.value.confirmWhatsAppMessages)

        // Simulate reopening Settings screen or restarting the application
        val manager2 = ConfirmationManager(context)

        // Values MUST stay false, NOT revert to true
        assertFalse(manager2.requireCallConfirmation)
        assertFalse(manager2.requireSmsConfirmation)
        assertFalse(manager2.confirmMessagesBeforeSending)
        assertFalse(manager2.settings.value.requireCallConfirmation)
        assertFalse(manager2.settings.value.requireSmsConfirmation)
        assertFalse(manager2.settings.value.confirmWhatsAppMessages)
    }
}
