package com.example

import com.example.agent.CommandNormalizer
import com.example.ai.AIRequest
import com.example.ai.LocalRuleAIProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JarvisAgentUnitTest {

    private val localAi = LocalRuleAIProvider()

    @Test
    fun testCommandNormalizationHinglish() {
        val normalizedApp = CommandNormalizer.standardizeHinglish("WhatsApp kholo")
        assertEquals("open whatsapp", normalizedApp)

        val normalizedCall = CommandNormalizer.standardizeHinglish("Rahul ko call karo")
        assertEquals("call rahul", normalizedCall)

        val normalizedFlashlight = CommandNormalizer.standardizeHinglish("torch jalao")
        assertEquals("turn on flashlight", normalizedFlashlight)

        val normalizedBattery = CommandNormalizer.standardizeHinglish("battery kitni hai")
        assertEquals("what is my battery", normalizedBattery)
    }

    @Test
    fun testTwentyBenchmarkCommands() = runBlocking {
        // 1. "Jarvis, WhatsApp kholo."
        val r1 = localAi.processCommand(AIRequest("Jarvis, WhatsApp kholo.", emptyList()))
        assertNotNull(r1.toolInvocation)
        assertTrue(r1.toolInvocation?.toolName in listOf("WhatsAppTool", "OpenAppTool"))

        // 2. "WhatsApp par Rahul ko message karo: main 10 minute mein aa raha hoon."
        val r2 = localAi.processCommand(AIRequest("WhatsApp par Rahul ko message karo: main 10 minute mein aa raha hoon.", emptyList()))
        assertNotNull(r2.toolInvocation)
        assertEquals("WhatsAppTool", r2.toolInvocation?.toolName)
        assertEquals("message", r2.toolInvocation?.arguments?.get("action"))
        assertEquals("rahul", r2.toolInvocation?.arguments?.get("contactName"))

        // 3. "WhatsApp kholo, Rahul ki chat open karo"
        val r3 = localAi.processCommand(AIRequest("WhatsApp kholo Rahul ki chat open karo", emptyList()))
        assertNotNull(r3.toolInvocation)
        assertEquals("WhatsAppTool", r3.toolInvocation?.toolName)

        // 4. "Instagram kholo aur Priyanshu search karo."
        val r4 = localAi.processCommand(AIRequest("Instagram kholo aur Priyanshu search karo.", emptyList()))
        assertNotNull(r4.toolInvocation)
        assertEquals("InstagramTool", r4.toolInvocation?.toolName)
        assertEquals("search", r4.toolInvocation?.arguments?.get("action"))
        assertEquals("priyanshu", r4.toolInvocation?.arguments?.get("query"))

        // 5. "YouTube par lo-fi songs chalao."
        val r5 = localAi.processCommand(AIRequest("YouTube par lo-fi songs chalao.", emptyList()))
        assertNotNull(r5.toolInvocation)
        assertEquals("YouTubeTool", r5.toolInvocation?.toolName)
        assertTrue(r5.toolInvocation?.arguments?.get("query")?.toString()?.contains("lo-fi") == true)

        // 6. "Chrome kholo aur latest tech news search karo."
        val r6 = localAi.processCommand(AIRequest("Chrome kholo aur latest tech news search karo.", emptyList()))
        assertNotNull(r6.toolInvocation)
        assertEquals("BrowserTool", r6.toolInvocation?.toolName)
        assertEquals("search", r6.toolInvocation?.arguments?.get("action"))
        assertTrue(r6.toolInvocation?.arguments?.get("query")?.toString()?.contains("tech news") == true)

        // 7. "Flashlight on karo."
        val r7 = localAi.processCommand(AIRequest("Flashlight on karo.", emptyList()))
        assertNotNull(r7.toolInvocation)
        assertEquals("FlashlightTool", r7.toolInvocation?.toolName)
        assertEquals(true, r7.toolInvocation?.arguments?.get("enabled"))

        // 8. "Flashlight off karo."
        val r8 = localAi.processCommand(AIRequest("Flashlight off karo.", emptyList()))
        assertNotNull(r8.toolInvocation)
        assertEquals("FlashlightTool", r8.toolInvocation?.toolName)
        assertEquals(false, r8.toolInvocation?.arguments?.get("enabled"))

        // 9. "Battery kitni hai?"
        val r9 = localAi.processCommand(AIRequest("Battery kitni hai?", emptyList()))
        assertNotNull(r9.toolInvocation)
        assertEquals("BatteryTool", r9.toolInvocation?.toolName)

        // 10. "7 baje ka alarm laga do."
        val r10 = localAi.processCommand(AIRequest("7 baje ka alarm laga do.", emptyList()))
        assertNotNull(r10.toolInvocation)
        assertEquals("AlarmTool", r10.toolInvocation?.toolName)
        assertEquals(7, r10.toolInvocation?.arguments?.get("hour"))

        // 11. "10 minute ka timer start karo."
        val r11 = localAi.processCommand(AIRequest("10 minute ka timer start karo.", emptyList()))
        assertNotNull(r11.toolInvocation)
        assertEquals("TimerTool", r11.toolInvocation?.toolName)
        assertEquals(10, r11.toolInvocation?.arguments?.get("durationMinutes"))

        // 12. "Rahul ko phone lagao."
        val r12 = localAi.processCommand(AIRequest("Rahul ko phone lagao.", emptyList()))
        assertNotNull(r12.toolInvocation)
        assertEquals("CallContactTool", r12.toolInvocation?.toolName)
        assertEquals("rahul", r12.toolInvocation?.arguments?.get("contactName"))

        // 13. "Mummy ko SMS bhejo: main ghar aa raha hoon."
        val r13 = localAi.processCommand(AIRequest("Mummy ko SMS bhejo: main ghar aa raha hoon.", emptyList()))
        assertNotNull(r13.toolInvocation)
        assertEquals("SendSmsTool", r13.toolInvocation?.toolName)
        assertEquals("mummy", r13.toolInvocation?.arguments?.get("recipient"))

        // 14. "Meri notifications padho."
        val r14 = localAi.processCommand(AIRequest("Meri notifications padho.", emptyList()))
        assertNotNull(r14.toolInvocation)
        assertEquals("NotificationTool", r14.toolInvocation?.toolName)

        // 15. "WhatsApp ki recent notifications dikhao."
        val r15 = localAi.processCommand(AIRequest("WhatsApp ki recent notifications dikhao.", emptyList()))
        assertNotNull(r15.toolInvocation)
        assertEquals("NotificationTool", r15.toolInvocation?.toolName)
        assertEquals("whatsapp", r15.toolInvocation?.arguments?.get("appName"))

        // 16. "Wi-Fi on karo."
        val r16 = localAi.processCommand(AIRequest("Wi-Fi on karo.", emptyList()))
        assertNotNull(r16.toolInvocation)
        assertEquals("WifiTool", r16.toolInvocation?.toolName)
        assertEquals("on", r16.toolInvocation?.arguments?.get("action"))

        // 17. "Bluetooth off karo."
        val r17 = localAi.processCommand(AIRequest("Bluetooth off karo.", emptyList()))
        assertNotNull(r17.toolInvocation)
        assertEquals("BluetoothTool", r17.toolInvocation?.toolName)
        assertEquals("off", r17.toolInvocation?.arguments?.get("action"))

        // 18. "Remember that I prefer Hindi."
        val r18 = localAi.processCommand(AIRequest("Remember that I prefer Hindi.", emptyList()))
        assertNotNull(r18.toolInvocation)
        assertEquals("MemoryTool", r18.toolInvocation?.toolName)
        assertEquals("remember", r18.toolInvocation?.arguments?.get("action"))

        // 19. "What do you remember about me?"
        val r19 = localAi.processCommand(AIRequest("What do you remember about me?", emptyList()))
        assertNotNull(r19.toolInvocation)
        assertEquals("MemoryTool", r19.toolInvocation?.toolName)
        assertEquals("recall", r19.toolInvocation?.arguments?.get("action"))

        // 20. "Forget that I prefer Hindi."
        val r20 = localAi.processCommand(AIRequest("Forget that I prefer Hindi.", emptyList()))
        assertNotNull(r20.toolInvocation)
        assertEquals("MemoryTool", r20.toolInvocation?.toolName)
        assertEquals("forget", r20.toolInvocation?.arguments?.get("action"))
    }
}
