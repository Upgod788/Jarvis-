package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenRouterAIProviderUnitTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAISettingsManagerDefaultsToGemini() {
        val settingsManager = AISettingsManager(context)
        val defaultSettings = settingsManager.settings.value

        // Default MUST be Gemini so user never has to repeatedly enter credentials
        assertEquals(AIProviderType.GEMINI, defaultSettings.providerType)
        assertEquals("", defaultSettings.geminiCustomApiKey)
        assertEquals("", defaultSettings.openRouterApiKey)
        assertEquals("google/gemini-2.5-flash", defaultSettings.openRouterModel)
    }

    @Test
    fun testAISettingsManagerSaveAndPersistOpenRouter() {
        val settingsManager = AISettingsManager(context)

        // Save OpenRouter settings
        settingsManager.saveProvider(AIProviderType.OPENROUTER)
        settingsManager.saveOpenRouterConfig(
            apiKey = "sk-or-v1-test-key-12345",
            model = "deepseek/deepseek-chat"
        )

        val updatedSettings = settingsManager.settings.value
        assertEquals(AIProviderType.OPENROUTER, updatedSettings.providerType)
        assertEquals("sk-or-v1-test-key-12345", updatedSettings.openRouterApiKey)
        assertEquals("deepseek/deepseek-chat", updatedSettings.openRouterModel)

        // Re-instantiating settings manager should load persisted values from SharedPreferences
        val newManagerInstance = AISettingsManager(context)
        val loadedSettings = newManagerInstance.settings.value
        assertEquals(AIProviderType.OPENROUTER, loadedSettings.providerType)
        assertEquals("sk-or-v1-test-key-12345", loadedSettings.openRouterApiKey)
        assertEquals("deepseek/deepseek-chat", loadedSettings.openRouterModel)
    }

    @Test
    fun testOpenRouterFallbackWhenKeyIsEmpty() = runTest {
        val fallbackMock = object : AIProvider {
            override val providerName: String = "MockFallback"
            override suspend fun processCommand(request: AIRequest): AIResponse {
                return AIResponse(textResponse = "Fallback response from Gemini")
            }
        }

        val openRouterProvider = OpenRouterAIProvider(
            apiKey = "",
            modelName = "google/gemini-2.5-flash",
            fallbackProvider = fallbackMock
        )

        val request = AIRequest(prompt = "Hello Jarvis", tools = emptyList())
        val response = openRouterProvider.processCommand(request)

        // When key is empty, should cleanly fall back to fallback mock without throwing
        assertEquals("Fallback response from Gemini", response.textResponse)
    }

    @Test
    fun testDynamicAIProviderDefaultsToGemini() {
        val dynamicProvider = DynamicAIProvider(context)
        assertEquals(AIProviderType.GEMINI, dynamicProvider.getActiveProviderType())
        assertTrue(dynamicProvider.providerName.contains("Gemini"))
    }
}
