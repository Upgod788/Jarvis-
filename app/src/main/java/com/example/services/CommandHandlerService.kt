package com.example.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.agent.ToolRegistry
import com.example.tools.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

/**
 * Enumeration of system action types that can be mapped from Gemini's responses.
 */
enum class IntentActionType {
    TOGGLE_WIFI,
    ENABLE_WIFI,
    DISABLE_WIFI,
    OPEN_WIFI_SETTINGS,
    OPEN_APP,
    TOGGLE_BLUETOOTH,
    ENABLE_BLUETOOTH,
    DISABLE_BLUETOOTH,
    OPEN_BLUETOOTH_SETTINGS,
    TOGGLE_FLASHLIGHT,
    ENABLE_FLASHLIGHT,
    DISABLE_FLASHLIGHT,
    OPEN_CAMERA,
    OPEN_SETTINGS,
    SET_ALARM,
    SET_TIMER,
    CHECK_BATTERY,
    CHECK_TIME,
    ADJUST_VOLUME,
    WEB_SEARCH,
    CALL_CONTACT,
    SEND_SMS,
    WHATSAPP,
    INSTAGRAM,
    YOUTUBE,
    BROWSER,
    NOTIFICATIONS,
    CHECK_UPDATE,
    DOWNLOAD_UPDATE,
    INSTALL_UPDATE,
    SHOW_WHATS_NEW,
    NONE
}

/**
 * Encapsulates an intent action detected from Gemini's response.
 */
data class DetectedIntentAction(
    val actionType: IntentActionType,
    val parameters: Map<String, Any?> = emptyMap(),
    val rawKeyword: String,
    val spokenFeedback: String,
    val confidence: Float = 1.0f
)

/**
 * Execution result of a dispatched system action.
 */
data class CommandExecutionResult(
    val success: Boolean,
    val action: DetectedIntentAction,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val toolResult: ToolResult? = null
)

/**
 * System Service and Dispatcher that maps intent-based keywords from Gemini's response
 * to actual physical Android system actions (like toggling Wi-Fi, launching apps, etc.).
 *
 * Can be executed synchronously via [executeFromResponse] or asynchronously as a
 * background Android [Service] via startService(Intent).
 */
class CommandHandlerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val geminiResponse = intent?.getStringExtra(EXTRA_GEMINI_RESPONSE) ?: ""
        val userPrompt = intent?.getStringExtra(EXTRA_USER_PROMPT)

        if (geminiResponse.isNotBlank()) {
            serviceScope.launch {
                val detected = parseIntent(geminiResponse, userPrompt)
                if (detected != null) {
                    executeAction(applicationContext, detected)
                }
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "CommandHandlerService"

        const val EXTRA_GEMINI_RESPONSE = "extra_gemini_response"
        const val EXTRA_USER_PROMPT = "extra_user_prompt"
        const val ACTION_EXECUTE_INTENT = "com.example.action.EXECUTE_INTENT"

        // Regular expression to identify explicit intent or action tags like:
        // [INTENT: TOGGLE_WIFI], [ACTION: OPEN_APP(youtube)], [INTENT: OPEN_APP app="whatsapp"]
        private val EXPLICIT_INTENT_REGEX = Regex(
            """\[?(?:INTENT|ACTION):\s*([A-Za-z0-9_]+)(?:(?:\s+|\s*:\s*|\s*=\s*|\()([^\n\])]*))?\]?""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Cleans explicit intent markers (e.g. [INTENT: TOGGLE_WIFI]) from Gemini's response
         * so the text is pristine for speech synthesis and chat UI.
         */
        fun cleanResponseText(rawText: String): String {
            return rawText
                .replace(Regex("""\[(?:INTENT|ACTION):[^\]]+\]""", RegexOption.IGNORE_CASE), "")
                .trim()
                .replace(Regex("""\s{2,}"""), " ")
        }

        /**
         * Checks whether a text contains any intent-based keywords or markers.
         */
        fun containsIntentKeyword(text: String): Boolean {
            return parseIntent(text) != null
        }

        /**
         * Parses intent-based keywords from Gemini's response (or conversational context)
         * into a structured [DetectedIntentAction].
         */
        fun parseIntent(geminiResponse: String, userPrompt: String? = null): DetectedIntentAction? {
            val trimmedResponse = geminiResponse.trim()
            val combinedText = if (userPrompt.isNullOrBlank()) {
                trimmedResponse
            } else {
                "$trimmedResponse\n${userPrompt.trim()}"
            }
            val lowerResponse = trimmedResponse.lowercase(Locale.ROOT).replace("wi-fi", "wifi")
            val lowerCombined = combinedText.lowercase(Locale.ROOT).replace("wi-fi", "wifi")

            // Tier 1: Check for explicit intent tags (e.g. [INTENT: TOGGLE_WIFI], [ACTION: OPEN_APP(youtube)])
            val explicitMatch = EXPLICIT_INTENT_REGEX.find(trimmedResponse)
            if (explicitMatch != null) {
                val intentName = explicitMatch.groupValues[1].uppercase(Locale.ROOT)
                val rawParams = explicitMatch.groupValues.getOrNull(2)?.trim() ?: ""

                val action = mapTagToAction(intentName, rawParams, trimmedResponse)
                if (action != null) return action
            }

            // Tier 2: Check for JSON payload format (e.g. {"intent": "TOGGLE_WIFI", ...} or {"action": "open_app", "app": "..."})
            if (trimmedResponse.contains("{") && trimmedResponse.contains("}")) {
                val jsonStart = trimmedResponse.indexOf("{")
                val jsonEnd = trimmedResponse.lastIndexOf("}")
                val jsonStr = trimmedResponse.substring(jsonStart, jsonEnd + 1)

                val intentMatch = Regex("""["'](?:intent|action)["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(jsonStr)
                val intentVal = intentMatch?.groupValues?.getOrNull(1) ?: try {
                    val json = JSONObject(jsonStr)
                    json.optString("intent").ifBlank { json.optString("action") }
                } catch (_: Throwable) { "" }

                if (!intentVal.isNullOrBlank()) {
                    val parsed = mapTagToAction(intentVal.uppercase(Locale.ROOT), jsonStr, trimmedResponse)
                    if (parsed != null) return parsed
                }
            }

            // Tier 3: Natural language intent-based keywords in Gemini's response

            // 0. Update Assistant Keywords
            val isUpdateCheck = lowerCombined.contains("check for update") ||
                    lowerCombined.contains("check for updates") ||
                    lowerCombined.contains("check update") ||
                    lowerCombined.contains("check updates") ||
                    lowerCombined.contains("is there a new version") ||
                    lowerCombined.contains("any update") ||
                    lowerCombined.contains("update status") ||
                    lowerCombined.contains("koi update") ||
                    lowerCombined.contains("update check karo")
            val isDownloadUpdate = lowerCombined.contains("download the latest update") ||
                    lowerCombined.contains("download update") ||
                    lowerCombined.contains("download new version") ||
                    lowerCombined.contains("update download karo")
            val isInstallUpdate = lowerCombined.contains("update yourself") ||
                    lowerCombined.contains("install update") ||
                    lowerCombined.contains("install the update") ||
                    lowerCombined.contains("update install karo")
            val isWhatsNew = lowerCombined.contains("what's new") ||
                    lowerCombined.contains("whats new") ||
                    lowerCombined.contains("show me what's new") ||
                    lowerCombined.contains("changelog") ||
                    lowerCombined.contains("release notes")

            if (isInstallUpdate) {
                return DetectedIntentAction(
                    actionType = IntentActionType.INSTALL_UPDATE,
                    parameters = mapOf("action" to "install"),
                    rawKeyword = "update",
                    spokenFeedback = "Opening Android Package Installer to approve update."
                )
            } else if (isDownloadUpdate) {
                return DetectedIntentAction(
                    actionType = IntentActionType.DOWNLOAD_UPDATE,
                    parameters = mapOf("action" to "download"),
                    rawKeyword = "update",
                    spokenFeedback = "Starting download of JARVIS update."
                )
            } else if (isWhatsNew) {
                return DetectedIntentAction(
                    actionType = IntentActionType.SHOW_WHATS_NEW,
                    parameters = mapOf("action" to "whats_new"),
                    rawKeyword = "update",
                    spokenFeedback = "Checking release notes."
                )
            } else if (isUpdateCheck) {
                return DetectedIntentAction(
                    actionType = IntentActionType.CHECK_UPDATE,
                    parameters = mapOf("action" to "check"),
                    rawKeyword = "update",
                    spokenFeedback = "Checking for JARVIS updates."
                )
            }

            // 1. Wi-Fi Keywords
            if (matchesWifiIntent(lowerResponse, lowerCombined)) {
                val isTurnOff = lowerCombined.contains("turn off wifi") ||
                        lowerCombined.contains("turning off wifi") ||
                        lowerCombined.contains("turn off the wifi") ||
                        lowerCombined.contains("turning off the wifi") ||
                        lowerCombined.contains("disable wifi") ||
                        lowerCombined.contains("disabling wifi") ||
                        lowerCombined.contains("switch off wifi") ||
                        lowerCombined.contains("switching off wifi") ||
                        lowerCombined.contains("wifi off") ||
                        lowerCombined.contains("wifi band")
                val isTurnOn = lowerCombined.contains("turn on wifi") ||
                        lowerCombined.contains("turning on wifi") ||
                        lowerCombined.contains("turn on the wifi") ||
                        lowerCombined.contains("turning on the wifi") ||
                        lowerCombined.contains("enable wifi") ||
                        lowerCombined.contains("enabling wifi") ||
                        lowerCombined.contains("switch on wifi") ||
                        lowerCombined.contains("switching on wifi") ||
                        lowerCombined.contains("wifi on") ||
                        lowerCombined.contains("wifi chalu")
                val isSettingsOnly = (lowerCombined.contains("wifi setting") || lowerCombined.contains("wifi settings")) && !isTurnOff && !isTurnOn

                val targetAction = when {
                    isSettingsOnly -> IntentActionType.OPEN_WIFI_SETTINGS
                    isTurnOff -> IntentActionType.DISABLE_WIFI
                    isTurnOn -> IntentActionType.ENABLE_WIFI
                    else -> IntentActionType.TOGGLE_WIFI
                }

                return DetectedIntentAction(
                    actionType = targetAction,
                    parameters = mapOf("action" to when (targetAction) {
                        IntentActionType.OPEN_WIFI_SETTINGS -> "settings"
                        IntentActionType.ENABLE_WIFI -> "on"
                        IntentActionType.DISABLE_WIFI -> "off"
                        else -> "toggle"
                    }),
                    rawKeyword = "wifi",
                    spokenFeedback = when (targetAction) {
                        IntentActionType.OPEN_WIFI_SETTINGS -> "Opening Wi-Fi settings."
                        IntentActionType.ENABLE_WIFI -> "Enabling Wi-Fi."
                        IntentActionType.DISABLE_WIFI -> "Disabling Wi-Fi."
                        else -> "Toggling Wi-Fi."
                    }
                )
            }

            // 2. Open App Keywords
            val appAction = detectOpenAppIntent(lowerResponse, lowerCombined)
            if (appAction != null) return appAction

            // 3. Flashlight / Torch Keywords
            if (matchesFlashlightIntent(lowerResponse, lowerCombined)) {
                val isTurnOff = lowerCombined.contains("turn off flashlight") ||
                        lowerCombined.contains("turning off flashlight") ||
                        lowerCombined.contains("turn off the flashlight") ||
                        lowerCombined.contains("turning off the flashlight") ||
                        lowerCombined.contains("disable flashlight") ||
                        lowerCombined.contains("disabling flashlight") ||
                        lowerCombined.contains("torch off") ||
                        lowerCombined.contains("turn off torch") ||
                        lowerCombined.contains("turning off torch") ||
                        lowerCombined.contains("turn off the torch") ||
                        lowerCombined.contains("turning off the torch") ||
                        lowerCombined.contains("torch band")
                val isTurnOn = lowerCombined.contains("turn on flashlight") ||
                        lowerCombined.contains("turning on flashlight") ||
                        lowerCombined.contains("turn on the flashlight") ||
                        lowerCombined.contains("turning on the flashlight") ||
                        lowerCombined.contains("enable flashlight") ||
                        lowerCombined.contains("enabling flashlight") ||
                        lowerCombined.contains("torch on") ||
                        lowerCombined.contains("turn on torch") ||
                        lowerCombined.contains("turning on torch") ||
                        lowerCombined.contains("turn on the torch") ||
                        lowerCombined.contains("turning on the torch") ||
                        lowerCombined.contains("torch chalu")

                val type = when {
                    isTurnOff -> IntentActionType.DISABLE_FLASHLIGHT
                    isTurnOn -> IntentActionType.ENABLE_FLASHLIGHT
                    else -> IntentActionType.TOGGLE_FLASHLIGHT
                }

                return DetectedIntentAction(
                    actionType = type,
                    parameters = mapOf("enabled" to if (isTurnOff) false else if (isTurnOn) true else null),
                    rawKeyword = "flashlight",
                    spokenFeedback = when (type) {
                        IntentActionType.DISABLE_FLASHLIGHT -> "Turning off flashlight."
                        IntentActionType.ENABLE_FLASHLIGHT -> "Turning on flashlight."
                        else -> "Toggling flashlight."
                    }
                )
            }

            // 4. Camera Keywords
            if (matchesCameraIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(
                    actionType = IntentActionType.OPEN_CAMERA,
                    parameters = mapOf("mode" to "photo"),
                    rawKeyword = "camera",
                    spokenFeedback = "Opening camera."
                )
            }

            // 5. Bluetooth Keywords
            if (matchesBluetoothIntent(lowerResponse, lowerCombined)) {
                val isTurnOff = lowerCombined.contains("turn off bluetooth") ||
                        lowerCombined.contains("turning off bluetooth") ||
                        lowerCombined.contains("turn off the bluetooth") ||
                        lowerCombined.contains("turning off the bluetooth") ||
                        lowerCombined.contains("disable bluetooth") ||
                        lowerCombined.contains("disabling bluetooth") ||
                        lowerCombined.contains("switch off bluetooth") ||
                        lowerCombined.contains("switching off bluetooth") ||
                        lowerCombined.contains("bluetooth off") ||
                        lowerCombined.contains("bluetooth band")
                val isTurnOn = lowerCombined.contains("turn on bluetooth") ||
                        lowerCombined.contains("turning on bluetooth") ||
                        lowerCombined.contains("turn on the bluetooth") ||
                        lowerCombined.contains("turning on the bluetooth") ||
                        lowerCombined.contains("enable bluetooth") ||
                        lowerCombined.contains("enabling bluetooth") ||
                        lowerCombined.contains("switch on bluetooth") ||
                        lowerCombined.contains("switching on bluetooth") ||
                        lowerCombined.contains("bluetooth on") ||
                        lowerCombined.contains("bluetooth chalu")
                val isSettingsOnly = (lowerCombined.contains("bluetooth setting") || lowerCombined.contains("bluetooth settings")) && !isTurnOff && !isTurnOn

                val targetAction = when {
                    isSettingsOnly -> IntentActionType.OPEN_BLUETOOTH_SETTINGS
                    isTurnOff -> IntentActionType.DISABLE_BLUETOOTH
                    isTurnOn -> IntentActionType.ENABLE_BLUETOOTH
                    else -> IntentActionType.TOGGLE_BLUETOOTH
                }

                return DetectedIntentAction(
                    actionType = targetAction,
                    parameters = mapOf("action" to when (targetAction) {
                        IntentActionType.OPEN_BLUETOOTH_SETTINGS -> "settings"
                        IntentActionType.ENABLE_BLUETOOTH -> "on"
                        IntentActionType.DISABLE_BLUETOOTH -> "off"
                        else -> "toggle"
                    }),
                    rawKeyword = "bluetooth",
                    spokenFeedback = when (targetAction) {
                        IntentActionType.OPEN_BLUETOOTH_SETTINGS -> "Opening Bluetooth settings."
                        IntentActionType.ENABLE_BLUETOOTH -> "Enabling Bluetooth."
                        IntentActionType.DISABLE_BLUETOOTH -> "Disabling Bluetooth."
                        else -> "Toggling Bluetooth."
                    }
                )
            }

            // 6. Settings Keywords
            if (matchesSettingsIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(
                    actionType = IntentActionType.OPEN_SETTINGS,
                    parameters = mapOf("settingType" to "all"),
                    rawKeyword = "settings",
                    spokenFeedback = "Opening Settings."
                )
            }

            // 7. Battery Check
            if (matchesBatteryIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(
                    actionType = IntentActionType.CHECK_BATTERY,
                    rawKeyword = "battery",
                    spokenFeedback = "Checking battery status."
                )
            }

            // 8. Time Check
            if (matchesTimeIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(
                    actionType = IntentActionType.CHECK_TIME,
                    rawKeyword = "time",
                    spokenFeedback = "Checking current time."
                )
            }

            return null
        }

        private fun mapTagToAction(tag: String, params: String, fullText: String): DetectedIntentAction? {
            val cleanParams = params.removePrefix("(").removeSuffix(")").trim()

            return when (tag) {
                "TOGGLE_WIFI" -> DetectedIntentAction(
                    actionType = IntentActionType.TOGGLE_WIFI,
                    parameters = mapOf("action" to "toggle"),
                    rawKeyword = tag,
                    spokenFeedback = "Toggling Wi-Fi."
                )
                "ENABLE_WIFI", "WIFI_ON" -> DetectedIntentAction(
                    actionType = IntentActionType.ENABLE_WIFI,
                    parameters = mapOf("action" to "on"),
                    rawKeyword = tag,
                    spokenFeedback = "Turning on Wi-Fi."
                )
                "DISABLE_WIFI", "WIFI_OFF" -> DetectedIntentAction(
                    actionType = IntentActionType.DISABLE_WIFI,
                    parameters = mapOf("action" to "off"),
                    rawKeyword = tag,
                    spokenFeedback = "Turning off Wi-Fi."
                )
                "OPEN_WIFI_SETTINGS" -> DetectedIntentAction(
                    actionType = IntentActionType.OPEN_WIFI_SETTINGS,
                    parameters = mapOf("settingType" to "wifi"),
                    rawKeyword = tag,
                    spokenFeedback = "Opening Wi-Fi settings."
                )
                "OPEN_APP", "LAUNCH_APP" -> {
                    // Extract app name from params (e.g. app="youtube" or youtube)
                    val appName = extractParamValue(cleanParams, "app")
                        .ifBlank { extractParamValue(cleanParams, "name") }
                        .ifBlank { cleanParams.replace("\"", "").replace("'", "").trim() }
                    DetectedIntentAction(
                        actionType = IntentActionType.OPEN_APP,
                        parameters = mapOf("appName" to appName),
                        rawKeyword = tag,
                        spokenFeedback = "Opening ${appName.replaceFirstChar { it.uppercase() }}."
                    )
                }
                "TOGGLE_FLASHLIGHT" -> DetectedIntentAction(
                    actionType = IntentActionType.TOGGLE_FLASHLIGHT,
                    rawKeyword = tag,
                    spokenFeedback = "Toggling flashlight."
                )
                "ENABLE_FLASHLIGHT", "FLASHLIGHT_ON", "TORCH_ON" -> DetectedIntentAction(
                    actionType = IntentActionType.ENABLE_FLASHLIGHT,
                    parameters = mapOf("enabled" to true),
                    rawKeyword = tag,
                    spokenFeedback = "Turning on flashlight."
                )
                "DISABLE_FLASHLIGHT", "FLASHLIGHT_OFF", "TORCH_OFF" -> DetectedIntentAction(
                    actionType = IntentActionType.DISABLE_FLASHLIGHT,
                    parameters = mapOf("enabled" to false),
                    rawKeyword = tag,
                    spokenFeedback = "Turning off flashlight."
                )
                "OPEN_CAMERA" -> DetectedIntentAction(
                    actionType = IntentActionType.OPEN_CAMERA,
                    parameters = mapOf("mode" to "photo"),
                    rawKeyword = tag,
                    spokenFeedback = "Opening camera."
                )
                "OPEN_SETTINGS" -> {
                    val settingType = extractParamValue(cleanParams, "type").ifBlank { "all" }
                    DetectedIntentAction(
                        actionType = IntentActionType.OPEN_SETTINGS,
                        parameters = mapOf("settingType" to settingType),
                        rawKeyword = tag,
                        spokenFeedback = "Opening Settings."
                    )
                }
                "TOGGLE_BLUETOOTH" -> DetectedIntentAction(
                    actionType = IntentActionType.TOGGLE_BLUETOOTH,
                    parameters = mapOf("action" to "toggle"),
                    rawKeyword = tag,
                    spokenFeedback = "Toggling Bluetooth."
                )
                "ENABLE_BLUETOOTH", "BLUETOOTH_ON" -> DetectedIntentAction(
                    actionType = IntentActionType.ENABLE_BLUETOOTH,
                    parameters = mapOf("action" to "on"),
                    rawKeyword = tag,
                    spokenFeedback = "Turning on Bluetooth."
                )
                "DISABLE_BLUETOOTH", "BLUETOOTH_OFF" -> DetectedIntentAction(
                    actionType = IntentActionType.DISABLE_BLUETOOTH,
                    parameters = mapOf("action" to "off"),
                    rawKeyword = tag,
                    spokenFeedback = "Turning off Bluetooth."
                )
                "OPEN_BLUETOOTH_SETTINGS" -> DetectedIntentAction(
                    actionType = IntentActionType.OPEN_BLUETOOTH_SETTINGS,
                    parameters = mapOf("action" to "settings"),
                    rawKeyword = tag,
                    spokenFeedback = "Opening Bluetooth settings."
                )
                "CHECK_BATTERY" -> DetectedIntentAction(
                    actionType = IntentActionType.CHECK_BATTERY,
                    rawKeyword = tag,
                    spokenFeedback = "Checking battery."
                )
                "CHECK_TIME" -> DetectedIntentAction(
                    actionType = IntentActionType.CHECK_TIME,
                    rawKeyword = tag,
                    spokenFeedback = "Checking time."
                )
                else -> null
            }
        }

        private fun extractParamValue(params: String, key: String): String {
            val regex = Regex("""["']?$key["']?\s*[:=]\s*["']?([^"',\s)}]+)["']?""", RegexOption.IGNORE_CASE)
            val match = regex.find(params)
            return match?.groupValues?.getOrNull(1)?.trim() ?: ""
        }

        private fun matchesWifiIntent(response: String, combined: String): Boolean {
            return response.contains("wifi") || combined.contains("wifi")
        }

        private fun matchesFlashlightIntent(response: String, combined: String): Boolean {
            return response.contains("flashlight") || response.contains("torch") ||
                    combined.contains("flashlight") || combined.contains("torch")
        }

        private fun matchesCameraIntent(response: String, combined: String): Boolean {
            return response.contains("opening camera") || response.contains("launching camera") ||
                    combined.contains("open camera") || combined.contains("take a photo") ||
                    combined.contains("take photo") || combined.contains("camera kholo")
        }

        private fun matchesBluetoothIntent(response: String, combined: String): Boolean {
            return response.contains("bluetooth") || combined.contains("bluetooth")
        }

        private fun matchesSettingsIntent(response: String, combined: String): Boolean {
            return response.contains("opening settings") || response.contains("opened settings") ||
                    combined.contains("open settings") || combined.contains("settings kholo")
        }

        private fun matchesBatteryIntent(response: String, combined: String): Boolean {
            return response.contains("battery is at") || response.contains("checking battery") ||
                    combined.contains("battery kitni") || combined.contains("battery check") ||
                    combined.contains("battery status") || combined.contains("battery batao")
        }

        private fun matchesTimeIntent(response: String, combined: String): Boolean {
            return response.contains("current time is") || response.contains("checking time") ||
                    combined.contains("time kya hua") || combined.contains("kitne baje") ||
                    combined.contains("time batao") || combined.contains("what time is it")
        }

        private fun detectOpenAppIntent(response: String, combined: String): DetectedIntentAction? {
            // Check known app keywords
            val knownApps = listOf(
                "youtube", "whatsapp", "chrome", "browser", "instagram", "spotify",
                "maps", "google maps", "gmail", "calculator", "calc", "clock",
                "calendar", "photos", "gallery", "messages", "sms", "phone",
                "dialer", "telegram", "twitter", "x", "keep", "notes", "settings",
                "files", "netflix"
            )

            // 1. "Opening YouTube", "Launching WhatsApp", etc.
            val openingRegex = Regex("""(?:opening|launching|starting|opened)\s+([a-zA-Z0-9\s]+?)(?:\s+app|\s+for\s+you|\.|$|\!)""", RegexOption.IGNORE_CASE)
            val match = openingRegex.find(response)
            if (match != null) {
                val candidate = match.groupValues[1].trim().lowercase(Locale.ROOT)
                val matchedApp = knownApps.firstOrNull { candidate.contains(it) || it.contains(candidate) }
                if (matchedApp != null) {
                    return DetectedIntentAction(
                        actionType = IntentActionType.OPEN_APP,
                        parameters = mapOf("appName" to matchedApp),
                        rawKeyword = matchedApp,
                        spokenFeedback = "Opening ${matchedApp.replaceFirstChar { it.uppercase() }}."
                    )
                }
            }

            // 2. User prompt commands like "open youtube", "launch chrome", "whatsapp kholo"
            val promptRegex = Regex("""(?:open|launch|kholo)\s+([a-zA-Z0-9\s]+)$""", RegexOption.IGNORE_CASE)
            val promptMatch = promptRegex.find(combined)
            if (promptMatch != null) {
                val candidate = promptMatch.groupValues[1].trim().lowercase(Locale.ROOT)
                val matchedApp = knownApps.firstOrNull { candidate.contains(it) || it.contains(candidate) }
                if (matchedApp != null) {
                    return DetectedIntentAction(
                        actionType = IntentActionType.OPEN_APP,
                        parameters = mapOf("appName" to matchedApp),
                        rawKeyword = matchedApp,
                        spokenFeedback = "Opening ${matchedApp.replaceFirstChar { it.uppercase() }}."
                    )
                }
            }

            return null
        }

        /**
         * Executes the mapped system action on Android using the appropriate system APIs or tools.
         */
        suspend fun executeAction(
            context: Context,
            detectedAction: DetectedIntentAction,
            toolRegistry: ToolRegistry? = null
        ): CommandExecutionResult {
            Log.d(TAG, "Executing system action: ${detectedAction.actionType} with params: ${detectedAction.parameters}")
            val appContext = context.applicationContext

            return when (detectedAction.actionType) {
                IntentActionType.TOGGLE_WIFI,
                IntentActionType.ENABLE_WIFI,
                IntentActionType.DISABLE_WIFI,
                IntentActionType.OPEN_WIFI_SETTINGS -> {
                    val wifiTool = toolRegistry?.getTool("WifiTool") ?: WifiTool()
                    val result = wifiTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.OPEN_APP -> {
                    val openAppTool = toolRegistry?.getTool("OpenAppTool") ?: OpenAppTool()
                    val result = openAppTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.TOGGLE_FLASHLIGHT,
                IntentActionType.ENABLE_FLASHLIGHT,
                IntentActionType.DISABLE_FLASHLIGHT -> {
                    val flashlightTool = toolRegistry?.getTool("FlashlightTool") ?: FlashlightTool()
                    val result = flashlightTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.OPEN_CAMERA -> {
                    val cameraTool = toolRegistry?.getTool("CameraTool") ?: CameraTool()
                    val result = cameraTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.TOGGLE_BLUETOOTH,
                IntentActionType.ENABLE_BLUETOOTH,
                IntentActionType.DISABLE_BLUETOOTH,
                IntentActionType.OPEN_BLUETOOTH_SETTINGS -> {
                    val bluetoothTool = toolRegistry?.getTool("BluetoothTool") ?: BluetoothTool()
                    val result = bluetoothTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.OPEN_SETTINGS -> {
                    val settingsTool = toolRegistry?.getTool("SettingsTool") ?: SettingsTool()
                    val result = settingsTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.CHECK_BATTERY -> {
                    val batteryTool = toolRegistry?.getTool("BatteryTool") ?: BatteryTool()
                    val result = batteryTool.execute(appContext, emptyMap())
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.CHECK_TIME -> {
                    val timeTool = toolRegistry?.getTool("CurrentTimeTool") ?: CurrentTimeTool()
                    val result = timeTool.execute(appContext, emptyMap())
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.ADJUST_VOLUME -> {
                    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        val dir = detectedAction.parameters["direction"]?.toString() ?: "up"
                        val adjustDirection = if (dir == "down") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustDirection, AudioManager.FLAG_SHOW_UI)
                        CommandExecutionResult(
                            success = true,
                            action = detectedAction,
                            message = "Volume adjusted.",
                            data = mapOf("direction" to dir)
                        )
                    } else {
                        CommandExecutionResult(
                            success = false,
                            action = detectedAction,
                            message = "Audio service unavailable."
                        )
                    }
                }

                IntentActionType.SET_ALARM -> {
                    val alarmTool = toolRegistry?.getTool("AlarmTool") ?: AlarmTool()
                    val result = alarmTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.SET_TIMER -> {
                    val timerTool = toolRegistry?.getTool("TimerTool") ?: TimerTool()
                    val result = timerTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.WEB_SEARCH -> {
                    val searchTool = toolRegistry?.getTool("WebSearchTool") ?: WebSearchTool()
                    val result = searchTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.CALL_CONTACT -> {
                    val callTool = toolRegistry?.getTool("CallContactTool") ?: CallContactTool()
                    val result = callTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data ?: emptyMap(),
                        toolResult = result
                    )
                }

                IntentActionType.SEND_SMS -> {
                    val smsTool = toolRegistry?.getTool("SendSmsTool") ?: SendSmsTool()
                    val result = smsTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data,
                        toolResult = result
                    )
                }

                IntentActionType.WHATSAPP -> {
                    val waTool = toolRegistry?.getTool("WhatsAppTool") ?: WhatsAppTool()
                    val result = waTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data,
                        toolResult = result
                    )
                }

                IntentActionType.INSTAGRAM -> {
                    val instaTool = toolRegistry?.getTool("InstagramTool") ?: InstagramTool()
                    val result = instaTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data,
                        toolResult = result
                    )
                }

                IntentActionType.YOUTUBE -> {
                    val ytTool = toolRegistry?.getTool("YouTubeTool") ?: YouTubeTool()
                    val result = ytTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data,
                        toolResult = result
                    )
                }

                IntentActionType.BROWSER -> {
                    val browserTool = toolRegistry?.getTool("BrowserTool") ?: BrowserTool()
                    val result = browserTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data,
                        toolResult = result
                    )
                }

                IntentActionType.NOTIFICATIONS -> {
                    val notifTool = toolRegistry?.getTool("NotificationTool") ?: NotificationTool()
                    val result = notifTool.execute(appContext, detectedAction.parameters)
                    CommandExecutionResult(
                        success = result.success,
                        action = detectedAction,
                        message = result.message,
                        data = result.data,
                        toolResult = result
                    )
                }

                IntentActionType.CHECK_UPDATE,
                IntentActionType.DOWNLOAD_UPDATE,
                IntentActionType.INSTALL_UPDATE,
                IntentActionType.SHOW_WHATS_NEW -> {
                    val updateTool = toolRegistry?.getTool("app_update")
                    if (updateTool != null) {
                        val result = updateTool.execute(appContext, detectedAction.parameters)
                        CommandExecutionResult(
                            success = result.success,
                            action = detectedAction,
                            message = result.message,
                            data = result.data,
                            toolResult = result
                        )
                    } else {
                        CommandExecutionResult(
                            success = false,
                            action = detectedAction,
                            message = "Update system is initializing. Please try again shortly."
                        )
                    }
                }

                IntentActionType.NONE -> {
                    CommandExecutionResult(
                        success = true,
                        action = detectedAction,
                        message = "No system action required."
                    )
                }
            }
        }

        /**
         * Dispatches a command asynchronously through the Android CommandHandlerService.
         */
        fun startServiceForResponse(context: Context, geminiResponse: String, userPrompt: String? = null) {
            val intent = Intent(context, CommandHandlerService::class.java).apply {
                putExtra(EXTRA_GEMINI_RESPONSE, geminiResponse)
                putExtra(EXTRA_USER_PROMPT, userPrompt)
            }
            context.startService(intent)
        }
    }
}
