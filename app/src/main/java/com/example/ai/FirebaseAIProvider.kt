package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
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
 * Intelligent AI Provider powered by the Gemini API via the Firebase AI SDK.
 *
 * Captures recognized speech text from [SpeechRecognizerManager] and leverages Gemini
 * to comprehend user intent, coordinate device actions through [ToolRegistry],
 * and generate natural, intelligent spoken responses.
 *
 * Features multi-tiered resilience:
 * 1. Primary: Firebase AI Logic SDK (Gemini 2.5 Flash)
 * 2. Secondary fallback: Direct Gemini REST API (Gemini 3.5 Flash)
 * 3. Offline fallback: Local Rule-Based Engine
 */
class FirebaseAIProvider(
    private val context: Context,
    private var customApiKey: String? = null,
    private val localFallback: LocalRuleAIProvider = LocalRuleAIProvider()
) : AIProvider {

    companion object {
        private const val TAG = "FirebaseAIProvider"
        private const val FIREBASE_MODEL_NAME = "gemini-2.5-flash"
        private const val REST_MODEL_NAME = "gemini-3.5-flash"
    }

    override val providerName: String = "Gemini AI (Firebase AI SDK)"

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedGenerativeModel: GenerativeModel? = null

    /**
     * Updates the custom user API key dynamically from Settings or UI.
     */
    fun updateApiKey(key: String?) {
        customApiKey = key?.trim()
        cachedGenerativeModel = null // Invalidate model so next call reconfigures with new credentials
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

    private fun ensureFirebaseInitialized() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val apiKey = getEffectiveApiKey()
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(context.packageName)
                    .setApiKey(apiKey.ifBlank { "AIzaSyDummyKeyForFirebaseAppInitialization" })
                    .setProjectId("aistudio-jarvis")
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Initialized default FirebaseApp with options.")
            } catch (e: Exception) {
                Log.w(TAG, "Custom FirebaseApp initialization notice: ${e.message}")
                try {
                    FirebaseApp.initializeApp(context)
                } catch (e2: Exception) {
                    Log.w(TAG, "Default FirebaseApp initialization notice: ${e2.message}")
                }
            }
        }
    }

    private fun getOrCreateGenerativeModel(tools: List<ToolInfo>): GenerativeModel {
        ensureFirebaseInitialized()

        val systemPrompt = buildSystemPrompt(tools)

        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = FIREBASE_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
            },
            systemInstruction = content { text(systemPrompt) }
        )
    }

    override suspend fun processCommand(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        // If no API key configured and placeholder present, use local rule engine
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "No active Gemini API key configured. Executing via local rule engine.")
            return@withContext localFallback.processCommand(request)
        }

        // Tier 1: Try Gemini via the Firebase AI SDK
        try {
            Log.d(TAG, "Processing recognized speech via Firebase AI SDK: \"${request.prompt}\"")
            val model = cachedGenerativeModel ?: getOrCreateGenerativeModel(request.tools).also {
                cachedGenerativeModel = it
            }

            val promptWithContext = buildString {
                if (!request.languageInstruction.isNullOrBlank()) {
                    append("LANGUAGE INSTRUCTION:\n")
                    append(request.languageInstruction)
                    append("\n\n")
                }
                if (request.conversationContext.isNotEmpty()) {
                    append("Recent context:\n")
                    append(request.conversationContext.takeLast(3).joinToString("\n"))
                    append("\n\n")
                }
                append("User request: ")
                append(request.prompt)
            }

            val response = model.generateContent(promptWithContext)
            val responseText = response.text ?: ""

            if (responseText.isNotBlank()) {
                val parsed = parseModelOutput(responseText, request)
                if (parsed != null) {
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI SDK attempt threw an exception (${e.localizedMessage}). Falling back to REST API.", e)
        }

        // Tier 2: Fallback to Direct Gemini REST API
        try {
            Log.d(TAG, "Attempting direct Gemini REST API fallback.")
            val restResponse = callGeminiRestApi(request, apiKey)
            if (restResponse != null) {
                return@withContext restResponse
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Gemini REST API failed: ${e.message}. Falling back to local offline engine.")
        }

        // Tier 3: Local Offline Rule Engine Fallback
        Log.i(TAG, "Falling back to local offline rule engine.")
        localFallback.processCommand(request)
    }

    private fun buildSystemPrompt(tools: List<ToolInfo>, languageInstruction: String? = null): String {
        val langBlock = if (!languageInstruction.isNullOrBlank()) {
            "\nCRITICAL RESPONSE LANGUAGE REQUIREMENT:\n$languageInstruction\nYou MUST formulate all spoken conversational replies, explanations, and 'response' text strictly following this language requirement.\n"
        } else ""

        return """
        You are JARVIS, a highly intelligent, polite personal Android assistant.
        You listen to recognized speech text from the user and generate helpful, accurate responses or execute device actions.
        $langBlock
        Rules:
        1. Understand natural speech commands in English, Hindi, and Hinglish.
        2. If the user's intent matches one of the registered tools below, invoke that tool via Format A or include intent keywords (e.g. [INTENT: TOGGLE_WIFI] or [INTENT: OPEN_APP app="..."]) in Format B.
        3. If the user asks a general knowledge, conversational, or reasoning question, answer it intelligently and concisely.
        4. Never make up device state or claim a physical device action succeeded before the tool executes.
        5. Respect privacy, permissions, and security.
        
        Registered Android Tools:
        ${tools.joinToString("\n") { "- ${it.name}: ${it.description} (parameters: ${it.parameters.joinToString()})" }}
        
        You MUST respond strictly in valid JSON format:
        Format A (when invoking an Android tool):
        {"tool": "ToolName", "params": {"paramName": "value"}, "thought": "Brief explanation for user"}
        
        Format B (conversational reply or question answer):
        {"response": "Your spoken conversational response here"}
        (Note: For system actions like toggling Wi-Fi or opening apps, you may also embed intent keywords like [INTENT: TOGGLE_WIFI] or [INTENT: OPEN_APP app="appName"] inside your response.)
        """.trimIndent()
    }

    private suspend fun callGeminiRestApi(request: AIRequest, apiKey: String): AIResponse? {
        val systemPrompt = buildSystemPrompt(request.tools, request.languageInstruction)

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
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$REST_MODEL_NAME:generateContent?key=$apiKey"

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = restClient.newCall(httpRequest).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful || responseBody.isBlank()) {
            return null
        }

        val responseJson = JSONObject(responseBody)
        val candidates = responseJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = parts?.optJSONObject(0)?.optString("text") ?: ""

        return parseModelOutput(text, request)
    }

    private suspend fun parseModelOutput(rawText: String, request: AIRequest): AIResponse? {
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

            // If raw text wasn't strict JSON, use it as a direct conversational response
            if (rawText.isNotBlank()) {
                AIResponse(textResponse = rawText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing model output: ${e.message}")
            if (rawText.isNotBlank() && !rawText.startsWith("{")) {
                AIResponse(textResponse = rawText)
            } else {
                null
            }
        }
    }
}
