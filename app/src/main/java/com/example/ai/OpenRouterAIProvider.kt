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

class OpenRouterAIProvider(
    private var apiKey: String = "",
    private var modelName: String = AISettings.DEFAULT_OPENROUTER_MODEL,
    private val fallbackProvider: AIProvider? = null
) : AIProvider {
    companion object {
        private const val TAG = "OpenRouterAIProvider"
        private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }

    override val providerName: String
        get() = "OpenRouter ($modelName)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .build()

    fun updateConfig(newApiKey: String?, newModel: String?) {
        if (newApiKey != null) apiKey = newApiKey.trim()
        if (!newModel.isNullOrBlank()) modelName = newModel.trim()
    }

    suspend fun testConnection(testKey: String, testModel: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("model", testModel.trim().ifBlank { AISettings.DEFAULT_OPENROUTER_MODEL })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                })
            }
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(OPENROUTER_ENDPOINT)
                .addHeader("Authorization", "Bearer ${testKey.trim()}")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Pair(true, "Connected successfully.")
            } else {
                Pair(false, "Failed: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.message}")
        }
    }

    override suspend fun processCommand(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext fallbackProvider?.processCommand(request)
                ?: AIResponse("OpenRouter API key is missing.")
        }
        try {
            val systemPrompt = buildSystemPrompt(request)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", request.prompt)
                })
            }
            val json = JSONObject().apply {
                put("model", modelName)
                put("messages", messages)
                put("temperature", 0.2)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val httpRequest = Request.Builder()
                .url(OPENROUTER_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = client.newCall(httpRequest).execute()
            val resBody = response.body?.string().orEmpty()
            if (response.isSuccessful && resBody.isNotBlank()) {
                val root = JSONObject(resBody)
                val text = root.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content").orEmpty()

                return@withContext parseOpenRouterOutput(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenRouter call failed: ${e.message}")
        }

        fallbackProvider?.processCommand(request) ?: AIResponse("Unable to complete request via OpenRouter.")
    }

    private fun buildSystemPrompt(request: AIRequest): String {
        val langBlock = if (!request.languageInstruction.isNullOrBlank()) {
            "\nLanguage Requirement:\n${request.languageInstruction}\n"
        } else ""

        val personalityBlock = if (!request.personalityInstruction.isNullOrBlank()) {
            "\nPersonality Style:\n${request.personalityInstruction}\n"
        } else ""

        val memoryBlock = if (!request.memoryContext.isNullOrBlank()) {
            "\n${request.memoryContext}\n"
        } else ""

        val toolListStr = request.tools.joinToString("\n") {
            "- ${it.name}: ${it.description}"
        }
        return """
        You are Ravan personal Android assistant.
        $langBlock$personalityBlock$memoryBlock
        Respond strictly in JSON format:
        {"tool": "ToolName", "params": {}} OR {"response": "Spoken text"}
        Tools:
        $toolListStr
        """.trimIndent()
    }

    private fun parseOpenRouterOutput(rawText: String): AIResponse {
        val clean = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (clean.startsWith("{") && clean.endsWith("}")) {
            try {
                val obj = JSONObject(clean)
                if (obj.has("tool")) {
                    val tName = obj.getString("tool")
                    val paramsObj = obj.optJSONObject("params")
                    val map = mutableMapOf<String, Any?>()
                    if (paramsObj != null) {
                        for (k in paramsObj.keys()) {
                            map[k] = paramsObj.get(k)
                        }
                    }
                    val thought = obj.optString("thought", "Executing $tName.")
                    return AIResponse(thought, ToolInvocation(tName, map))
                }
                if (obj.has("response")) {
                    return AIResponse(obj.getString("response"))
                }
            } catch (_: Exception) {}
        }
        return AIResponse(rawText)
    }
}
