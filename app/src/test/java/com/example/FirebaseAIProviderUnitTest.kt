package com.example

import com.example.ai.AIRequest
import com.example.ai.AIResponse
import com.example.ai.LocalRuleAIProvider
import com.example.ai.ToolInfo
import com.example.ai.ToolInvocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FirebaseAIProviderUnitTest {

    @Test
    fun testAIRequestAndResponseStructures() {
        val tools = listOf(
            ToolInfo(name = "FlashlightTool", description = "Controls flashlight", parameters = listOf("enabled (Boolean)"))
        )
        val request = AIRequest(
            prompt = "turn on flashlight",
            tools = tools,
            conversationContext = listOf("Previous turn")
        )

        assertEquals("turn on flashlight", request.prompt)
        assertEquals(1, request.tools.size)
        assertEquals(1, request.conversationContext.size)

        val responseWithTool = AIResponse(
            textResponse = "Turning on flashlight.",
            toolInvocation = ToolInvocation("FlashlightTool", mapOf("enabled" to true))
        )
        assertNotNull(responseWithTool.toolInvocation)
        assertEquals("FlashlightTool", responseWithTool.toolInvocation?.toolName)
        assertEquals(true, responseWithTool.toolInvocation?.arguments?.get("enabled"))

        val conversationalResponse = AIResponse(
            textResponse = "Good evening, Sir. How may I assist you today?"
        )
        assertNull(conversationalResponse.toolInvocation)
        assertTrue(conversationalResponse.textResponse.contains("Good evening"))
    }

    @Test
    fun testLocalFallbackExecution() = runTest {
        val provider = LocalRuleAIProvider()
        val request = AIRequest(
            prompt = "turn on flashlight",
            tools = emptyList()
        )
        val response = provider.processCommand(request)
        assertNotNull(response.toolInvocation)
        assertEquals("FlashlightTool", response.toolInvocation?.toolName)
    }

    @Test
    fun testLocalFallbackConversational() = runTest {
        val provider = LocalRuleAIProvider()
        val request = AIRequest(
            prompt = "who are you",
            tools = emptyList()
        )
        val response = provider.processCommand(request)
        assertTrue(response.textResponse.contains("JARVIS") || response.textResponse.contains("assistant"))
    }
}
