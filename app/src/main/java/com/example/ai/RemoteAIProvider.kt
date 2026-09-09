package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RemoteAIProvider(
    private var customApiKey: String? = null,
    private val localFallback: LocalRuleAIProvider = LocalRuleAIProvider()
) : AIProvider {

    override val providerName: String = "Gemini AI (Remote & Local Fallback)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun updateApiKey(key: String?) {
        customApiKey = key
    }

    private fun getEffectiveApiKey(): String {
        val custom = customApiKey?.trim()
        if (!custom.isNullOrBlank()) return custom
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }

    override suspend fun processCommand(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        // If no API key is provided or placeholder is still set, use local rule engine
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext localFallback.processCommand(request)
        }

        val systemPrompt = """
You are JARVIS, a personal Android assistant.
Your job is to understand the user's natural-language request and choose the safest available tool.
You must never claim an action succeeded unless the Android tool reports success.
You must never invent device information.
You must never bypass Android permissions or security.
You must ask for clarification when a command is ambiguous.
You must request confirmation for consequential actions according to the application's risk policy.
You may only use tools available in the ToolRegistry.
Never execute arbitrary code.
Never expose API keys, credentials, tokens, or private data.
When a tool fails, explain the failure honestly and suggest the next safe step.
Be concise, helpful, and natural.
Support English, Hindi, and Hinglish.

Registered tools:
${request.tools.joinToString("\n") { "- ${it.name}: ${it.description} (params: ${it.parameters.joinToString()})" }}

You MUST respond strictly with a valid JSON object in one of two formats:
Format 1 (when calling a tool):
{"tool": "ToolName", "params": {"paramName": "value"}, "thought": "Brief explanation"}

Format 2 (conversational response without tool):
{"response": "Your spoken conversational response here"}
(Note: For system actions like toggling Wi-Fi or opening apps, you may also embed intent keywords like [INTENT: TOGGLE_WIFI] or [INTENT: OPEN_APP app="appName"] inside your response.)
""".trimIndent()

        try {
            val jsonPayload = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", request.prompt))
                        })
                    })
                }
                put("contents", contents)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt))
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }

            val mediaType = "application/json".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful || responseBody.isBlank()) {
                // Fallback to local rule engine
                return@withContext localFallback.processCommand(request)
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            parseModelJsonOutput(text, request)
        } catch (e: Exception) {
            // Network failure or timeout: graceful fallback to local offline engine
            localFallback.processCommand(request)
        }
    }

    private suspend fun parseModelJsonOutput(text: String, request: AIRequest): AIResponse {
        return try {
            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsed = JSONObject(cleanJson)

            if (parsed.has("tool")) {
                val toolName = parsed.getString("tool")
                val paramsObj = parsed.optJSONObject("params")
                val paramsMap = mutableMapOf<String, Any?>()
                paramsObj?.keys()?.forEach { key ->
                    paramsMap[key] = paramsObj.get(key)
                }
                val thought = parsed.optString("thought", "Executing $toolName.")
                AIResponse(textResponse = thought, toolInvocation = ToolInvocation(toolName, paramsMap))
            } else if (parsed.has("response")) {
                AIResponse(textResponse = parsed.getString("response"))
            } else {
                localFallback.processCommand(request)
            }
        } catch (e: Exception) {
            localFallback.processCommand(request)
        }
    }
}
