package com.example

import com.example.services.CommandHandlerService
import com.example.services.IntentActionType
import org.junit.Assert.*
import org.junit.Test

class CommandHandlerServiceUnitTest {

    @Test
    fun testExplicitTagParsing() {
        // Wi-Fi Intent Tags
        val wifiIntent = CommandHandlerService.parseIntent("Sure, I can help! [INTENT: TOGGLE_WIFI]")
        assertNotNull(wifiIntent)
        assertEquals(IntentActionType.TOGGLE_WIFI, wifiIntent?.actionType)

        val enableWifiIntent = CommandHandlerService.parseIntent("[INTENT: ENABLE_WIFI]")
        assertNotNull(enableWifiIntent)
        assertEquals(IntentActionType.ENABLE_WIFI, enableWifiIntent?.actionType)

        val disableWifiIntent = CommandHandlerService.parseIntent("[INTENT: DISABLE_WIFI]")
        assertNotNull(disableWifiIntent)
        assertEquals(IntentActionType.DISABLE_WIFI, disableWifiIntent?.actionType)

        // Bluetooth Intent Tags
        val bluetoothIntent = CommandHandlerService.parseIntent("Sure, I can help! [INTENT: TOGGLE_BLUETOOTH]")
        assertNotNull(bluetoothIntent)
        assertEquals(IntentActionType.TOGGLE_BLUETOOTH, bluetoothIntent?.actionType)

        val enableBtIntent = CommandHandlerService.parseIntent("[INTENT: ENABLE_BLUETOOTH]")
        assertNotNull(enableBtIntent)
        assertEquals(IntentActionType.ENABLE_BLUETOOTH, enableBtIntent?.actionType)

        val disableBtIntent = CommandHandlerService.parseIntent("[INTENT: DISABLE_BLUETOOTH]")
        assertNotNull(disableBtIntent)
        assertEquals(IntentActionType.DISABLE_BLUETOOTH, disableBtIntent?.actionType)

        // Open App Intent Tags
        val openYoutubeIntent = CommandHandlerService.parseIntent("Launching app. [INTENT: OPEN_APP app=\"youtube\"]")
        assertNotNull(openYoutubeIntent)
        assertEquals(IntentActionType.OPEN_APP, openYoutubeIntent?.actionType)
        assertEquals("youtube", openYoutubeIntent?.parameters?.get("appName"))

        val openWhatsappIntent = CommandHandlerService.parseIntent("[ACTION: OPEN_APP(whatsapp)]")
        assertNotNull(openWhatsappIntent)
        assertEquals(IntentActionType.OPEN_APP, openWhatsappIntent?.actionType)
        assertEquals("whatsapp", openWhatsappIntent?.parameters?.get("appName"))

        // Flashlight Intent Tags
        val flashlightIntent = CommandHandlerService.parseIntent("[INTENT: TOGGLE_FLASHLIGHT]")
        assertNotNull(flashlightIntent)
        assertEquals(IntentActionType.TOGGLE_FLASHLIGHT, flashlightIntent?.actionType)

        // Camera Intent Tags
        val cameraIntent = CommandHandlerService.parseIntent("[ACTION: OPEN_CAMERA]")
        assertNotNull(cameraIntent)
        assertEquals(IntentActionType.OPEN_CAMERA, cameraIntent?.actionType)

        // Settings Intent Tags
        val settingsIntent = CommandHandlerService.parseIntent("[INTENT: OPEN_SETTINGS]")
        assertNotNull(settingsIntent)
        assertEquals(IntentActionType.OPEN_SETTINGS, settingsIntent?.actionType)
    }

    @Test
    fun testNaturalLanguageKeywordParsing() {
        // Wi-Fi Natural keywords
        val naturalWifi = CommandHandlerService.parseIntent("I'll toggle Wi-Fi for you right away.")
        assertNotNull(naturalWifi)
        assertEquals(IntentActionType.TOGGLE_WIFI, naturalWifi?.actionType)

        val turnOffWifi = CommandHandlerService.parseIntent("Turning off Wi-Fi now.")
        assertNotNull(turnOffWifi)
        assertEquals(IntentActionType.DISABLE_WIFI, turnOffWifi?.actionType)

        // Open App Natural keywords
        val naturalYoutube = CommandHandlerService.parseIntent("Opening YouTube for you now.")
        assertNotNull(naturalYoutube)
        assertEquals(IntentActionType.OPEN_APP, naturalYoutube?.actionType)
        assertEquals("youtube", naturalYoutube?.parameters?.get("appName"))

        val naturalChrome = CommandHandlerService.parseIntent("Launching Chrome app.")
        assertNotNull(naturalChrome)
        assertEquals(IntentActionType.OPEN_APP, naturalChrome?.actionType)
        assertEquals("chrome", naturalChrome?.parameters?.get("appName"))

        // Flashlight Natural keywords
        val naturalFlashlight = CommandHandlerService.parseIntent("Turning on the flashlight.")
        assertNotNull(naturalFlashlight)
        assertEquals(IntentActionType.ENABLE_FLASHLIGHT, naturalFlashlight?.actionType)

        // Camera Natural keywords
        val naturalCamera = CommandHandlerService.parseIntent("Opening camera.")
        assertNotNull(naturalCamera)
        assertEquals(IntentActionType.OPEN_CAMERA, naturalCamera?.actionType)

        // Bluetooth Natural keywords
        val naturalBt = CommandHandlerService.parseIntent("Toggling Bluetooth for you.")
        assertNotNull(naturalBt)
        assertEquals(IntentActionType.TOGGLE_BLUETOOTH, naturalBt?.actionType)

        val turnOnBt = CommandHandlerService.parseIntent("Turning on Bluetooth now.")
        assertNotNull(turnOnBt)
        assertEquals(IntentActionType.ENABLE_BLUETOOTH, turnOnBt?.actionType)

        val turnOffBt = CommandHandlerService.parseIntent("Turning off Bluetooth now.")
        assertNotNull(turnOffBt)
        assertEquals(IntentActionType.DISABLE_BLUETOOTH, turnOffBt?.actionType)

        // Battery Natural keywords
        val naturalBattery = CommandHandlerService.parseIntent("Checking battery status for you.")
        assertNotNull(naturalBattery)
        assertEquals(IntentActionType.CHECK_BATTERY, naturalBattery?.actionType)
    }

    @Test
    fun testCleanResponseText() {
        val raw = "Certainly! [INTENT: TOGGLE_WIFI] Toggling Wi-Fi on your device."
        val cleaned = CommandHandlerService.cleanResponseText(raw)
        assertEquals("Certainly! Toggling Wi-Fi on your device.", cleaned)
        assertFalse(cleaned.contains("[INTENT:"))
    }

    @Test
    fun testJsonPayloadParsing() {
        val jsonResponse = """{"intent": "TOGGLE_WIFI", "response": "Switching your Wi-Fi now."}"""
        val action = CommandHandlerService.parseIntent(jsonResponse)
        assertNotNull(action)
        assertEquals(IntentActionType.TOGGLE_WIFI, action?.actionType)

        val jsonBtResponse = """{"intent": "TOGGLE_BLUETOOTH", "response": "Toggling Bluetooth now."}"""
        val btAction = CommandHandlerService.parseIntent(jsonBtResponse)
        assertNotNull(btAction)
        assertEquals(IntentActionType.TOGGLE_BLUETOOTH, btAction?.actionType)

        val jsonAppResponse = """{"action": "open_app", "app": "youtube"}"""
        val appAction = CommandHandlerService.parseIntent(jsonAppResponse)
        assertNotNull(appAction)
        assertEquals(IntentActionType.OPEN_APP, appAction?.actionType)
        assertEquals("youtube", appAction?.parameters?.get("appName"))
    }

    @Test
    fun testContainsIntentKeyword() {
        assertTrue(CommandHandlerService.containsIntentKeyword("[INTENT: TOGGLE_WIFI]"))
        assertTrue(CommandHandlerService.containsIntentKeyword("I am opening YouTube for you."))
        assertTrue(CommandHandlerService.containsIntentKeyword("Turning off flashlight."))
        assertFalse(CommandHandlerService.containsIntentKeyword("The sky is blue today."))
    }
}
