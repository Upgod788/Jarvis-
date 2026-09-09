package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI Provider for OpenRouter API (https://openrouter.ai).
 * Allows users to bring their own API key and choose any model supported by OpenRouter
 * (e.g., google/gemini-2.5-flash, deepseek/deepseek-chat, meta-llama/llama-3.3-70b-instruct,
 * anthropic/claude-3.5-sonnet, openai/gpt-4o-mini).
 */
class OpenRouterAIProvider(
    private var apiKey: String = "",
    private var modelName: String = "google/gemini-2.5-flash",
    private val fallbackProvider: AIProvider? = null
) : AIProvider {

    companion object {
        private const val TAG = "OpenRouterAIProvider"
        private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }

    override val providerName: String
        get() = "OpenRouter ($modelName)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun updateConfig(newApiKey: String?, newModel: String?) {
        if (newApiKey != null) {
            apiKey = newApiKey.trim()
        }
        if (!newModel.isNullOrBlank()) {
            modelName = newModel.trim()
        }
    }

    fun getModel(): String = modelName
    fun getApiKey(): String = apiKey

    /**
     * Tests the OpenRouter connection with the specified API key and model.
     * Returns Pair(isSuccess, message).
     */
    suspend fun testConnection(testKey: String, testModel: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmedKey = testKey.trim()
        val trimmedModel = testModel.trim().ifBlank { "google/gemini-2.5-flash" }

        if (trimmedKey.isBlank()) {
            return@withContext Pair(false, "API key cannot be empty.")
        }

        try {
            val payload = JSONObject().apply {
                put("model", trimmedModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Ping! Reply with: OK")
                    })
                })
                put("max_tokens", 10)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url(OPENROUTER_ENDPOINT)
                .addHeader("Authorization", "Bearer $trimmedKey")
                .addHeader("HTTP-Referer", "https://ai.studio/build")
                .addHeader("X-Title", "JARVIS Android Assistant")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Pair(true, "Connection successful! Model \"$trimmedModel\" is ready.")
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                Pair(false, "OpenRouter Error: $errorMsg")
            }
        } catch (e: Exception) {
            Pair(false, "Network error: ${e.localizedMessage ?: "Failed to connect"}")
        }
    }

    override suspend fun processCommand(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        val currentKey = apiKey.trim()
        if (currentKey.isBlank()) {
            Log.w(TAG, "OpenRouter API key is empty. Falling back to default provider.")
            return@withContext fallbackProvider?.processCommand(request)
                ?: AIResponse(textResponse = "OpenRouter API key is not configured. Please enter your key in Settings.")
        }

        val systemPrompt = buildSystemPrompt(request.tools)
        try {
            val messagesArray = JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // Context messages
                request.conversationContext.takeLast(3).forEach { ctx ->
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", ctx)
                    })
                }

                // Current user command
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", request.prompt)
                })
            }

            val payload = JSONObject().apply {
                put("model", modelName.ifBlank { "google/gemini-2.5-flash" })
                put("messages", messagesArray)
                put("temperature", 0.2)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val httpRequest = Request.Builder()
                .url(OPENROUTER_ENDPOINT)
                .addHeader("Authorization", "Bearer $currentKey")
                .addHeader("HTTP-Referer", "https://ai.studio/build")
                .addHeader("X-Title", "JARVIS Android Assistant")
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful || responseBody.isBlank()) {
                Log.w(TAG, "OpenRouter call failed: HTTP ${response.code}, body: $responseBody")
                return@withContext fallbackProvider?.processCommand(request)
                    ?: AIResponse(textResponse = "OpenRouter error (HTTP ${response.code}). Falling back to backup engine.")
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val content = message?.optString("content") ?: ""

            if (content.isNotBlank()) {
                parseOpenRouterOutput(content, request)
            } else {
                fallbackProvider?.processCommand(request)
                    ?: AIResponse(textResponse = "No response received from OpenRouter model.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter call threw exception: ${e.message}", e)
            fallbackProvider?.processCommand(request)
                ?: AIResponse(textResponse = "Failed to reach OpenRouter (${e.localizedMessage}). Using backup engine.")
        }
    }

    private fun buildSystemPrompt(tools: List<ToolInfo>): String {
        return """
        You are JARVIS, a personal Android assistant.
        You listen to recognized speech or text commands and choose the safest available tool or respond conversationally.
        
        Rules:
        1. Understand natural commands in English, Hindi, and Hinglish.
        2. If the user wants to perform an action matching a registered tool below, respond in Format A or embed intent tags (e.g. [INTENT: TOGGLE_WIFI] or [INTENT: OPEN_APP app="..."]) in Format B.
        3. For general questions or conversations, answer concisely and helpfully in Format B.
        4. Never pretend an action succeeded before the tool executes.
        
        Registered Android Tools:
        ${tools.joinToString("\n") { "- ${it.name}: ${it.description} (params: ${it.parameters.joinToString()})" }}
        
        You MUST respond strictly in valid JSON format:
        Format A (when invoking a tool):
        {"tool": "ToolName", "params": {"paramName": "value"}, "thought": "Brief explanation for user"}
        
        Format B (conversational reply):
        {"response": "Your spoken conversational response here"}
        (Note: For system actions like toggling Wi-Fi or opening apps, you may also embed intent keywords like [INTENT: TOGGLE_WIFI] or [INTENT: OPEN_APP app="appName"] inside your response.)
        """.trimIndent()
    }

    private fun parseOpenRouterOutput(rawText: String, request: AIRequest): AIResponse {
        return try {
            val cleanJson = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            if (cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
                val parsed = JSONObject(cleanJson)
                if (parsed.has("tool")) {
                    val toolName = parsed.getString("tool")
                    val paramsObj = parsed.optJSONObject("params")
                    val paramsMap = mutableMapOf<String, Any?>()
                    paramsObj?.keys()?.forEach { key ->
                        paramsMap[key] = paramsObj.get(key)
                    }
                    val thought = parsed.optString("thought", "Executing $toolName.")
                    return AIResponse(textResponse = thought, toolInvocation = ToolInvocation(toolName, paramsMap))
                } else if (parsed.has("response")) {
                    return AIResponse(textResponse = parsed.getString("response"))
                }
            }

            // If not strict JSON, return clean raw text as conversational response
            AIResponse(textResponse = rawText)
        } catch (e: Exception) {
            AIResponse(textResponse = rawText)
        }
    }
}
