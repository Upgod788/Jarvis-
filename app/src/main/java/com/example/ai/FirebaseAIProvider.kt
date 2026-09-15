package com.example.ai

import android.content.Context
import android.util.Log
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

class FirebaseAIProvider(
    private val context: Context,
    private var customApiKey: String? = null,
    private val localFallback: LocalRuleAIProvider = LocalRuleAIProvider()
) : AIProvider {
    companion object {
        private const val TAG = "FirebaseAIProvider"
        private const val REST_MODEL_NAME = "gemini-2.5-flash"
    }

    override val providerName: String = "Gemini AI (Firebase AI / REST)"

    private val restClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .build()

    fun updateApiKey(key: String?) {
        this.customApiKey = key?.trim()
    }

    private fun getEffectiveApiKey(): String {
        val custom = customApiKey?.trim()
        if (!custom.isNullOrBlank()) return custom
        return try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String)?.trim().orEmpty()
        } catch (e: Throwable) {
            ""
        }
    }

    override suspend fun processCommand(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val res = callGeminiRestApi(request, apiKey)
                if (res != null) return@withContext res
            } catch (e: Exception) {
                Log.w(TAG, "Gemini REST call failed: ${e.message}. Using local rule engine.")
            }
        }
        localFallback.processCommand(request)
    }

    private suspend fun callGeminiRestApi(request: AIRequest, apiKey: String): AIResponse? {
        val systemPrompt = buildSystemPrompt(request)

        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", request.prompt))
                    })
                })
            })
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

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$REST_MODEL_NAME:generateContent?key=$apiKey"
        val httpRequest = Request.Builder().url(url).post(requestBody).build()

        val response = restClient.newCall(httpRequest).execute()
        val responseBody = response.body?.string().orEmpty()

        if (response.isSuccessful && responseBody.isNotBlank()) {
            val root = JSONObject(responseBody)
            val text = root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text").orEmpty()

            return parseModelOutput(text, request)
        }
        return null
    }

    private fun buildSystemPrompt(request: AIRequest): String {
        val langBlock = if (!request.languageInstruction.isNullOrBlank()) {
            "\nCRITICAL RESPONSE LANGUAGE REQUIREMENT:\n${request.languageInstruction}\nYou MUST formulate all spoken conversational replies, explanations, and 'response' text strictly following this language requirement.\n"
        } else ""

        val personalityBlock = if (!request.personalityInstruction.isNullOrBlank()) {
            "\nCOMMUNICATION STYLE & PERSONALITY:\n${request.personalityInstruction}\n"
        } else ""

        val emotionBlock = if (!request.emotionalGuideline.isNullOrBlank()) {
            "\nCONVERSATIONAL TONE:\n${request.emotionalGuideline}\n"
        } else ""

        val memoryBlock = if (!request.memoryContext.isNullOrBlank()) {
            "\n${request.memoryContext}\n"
        } else ""

        val toolListStr = request.tools.joinToString("\n") {
            "- ${it.name}: ${it.description} (parameters: ${it.parameters.joinToString()})"
        }

        return """
        You are JARVIS, a highly intelligent, polite personal Android assistant.
        You listen to recognized speech text from the user and generate helpful, accurate responses or execute device actions.
        $langBlock$personalityBlock$emotionBlock$memoryBlock
        Rules:
        1. Understand natural speech commands in English, Hindi, and Hinglish.
        2. If the user's intent matches one of the registered tools below, invoke that tool via Format A or include intent keywords in Format B.
        3. If the user asks a personal question (e.g. name, preferences, favorite things) and memory context contains it, answer with that remembered fact.
        4. If the user asks a general knowledge, conversational, or reasoning question, answer it intelligently and concisely according to your personality style.
        5. Never make up device state or claim a physical device action succeeded before the tool executes.
        6. Respect privacy, permissions, and security.

        Registered Android Tools:
        $toolListStr

        You MUST respond strictly in valid JSON format:
        Format A (when invoking an Android tool):
        {"tool": "ToolName", "params": {"paramName": "value"}, "thought": "Brief explanation for user"}

        Format B (conversational reply or question answer):
        {"response": "Your spoken conversational response here"}
        """.trimIndent()
    }

    private fun parseModelOutput(rawText: String, request: AIRequest): AIResponse? {
        try {
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
                    if (paramsObj != null) {
                        for (k in paramsObj.keys()) {
                            paramsMap[k] = paramsObj.get(k)
                        }
                    }
                    val thought = parsed.optString("thought", "Executing $toolName.")
                    return AIResponse(thought, ToolInvocation(toolName, paramsMap))
                }
                if (parsed.has("response")) {
                    return AIResponse(parsed.getString("response"))
                }
            }
            if (rawText.isNotBlank()) {
                return AIResponse(rawText)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing model output: ${e.message}")
        }
        return null
    }
}
