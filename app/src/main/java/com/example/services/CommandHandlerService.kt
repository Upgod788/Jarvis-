package com.example.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import com.example.agent.ToolRegistry
import com.example.tools.AlarmTool
import com.example.tools.BatteryTool
import com.example.tools.BluetoothTool
import com.example.tools.BrowserTool
import com.example.tools.CallContactTool
import com.example.tools.CameraTool
import com.example.tools.CurrentTimeTool
import com.example.tools.FlashlightTool
import com.example.tools.InstagramTool
import com.example.tools.NotificationTool
import com.example.tools.OpenAppTool
import com.example.tools.SendSmsTool
import com.example.tools.SettingsTool
import com.example.tools.TimerTool
import com.example.tools.WebSearchTool
import com.example.tools.WhatsAppTool
import com.example.tools.WifiTool
import com.example.tools.YouTubeTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class CommandHandlerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val geminiResponse = intent?.getStringExtra(EXTRA_GEMINI_RESPONSE) ?: ""
        val userPrompt = intent?.getStringExtra(EXTRA_USER_PROMPT) ?: ""

        if (geminiResponse.isNotBlank()) {
            serviceScope.launch {
                val detected = parseIntent(geminiResponse, userPrompt)
                if (detected != null) {
                    executeAction(applicationContext, detected, null)
                }
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val TAG = "CommandHandlerService"
        const val EXTRA_GEMINI_RESPONSE = "extra_gemini_response"
        const val EXTRA_USER_PROMPT = "extra_user_prompt"
        const val ACTION_EXECUTE_INTENT = "com.example.ACTION_EXECUTE_INTENT"

        @JvmField
        val INSTANCE = this

        private val EXPLICIT_INTENT_REGEX =
            Regex("""\[(?:INTENT|ACTION):\s*([A-Z0-9_]+)(?:\s*\((.*?)\))?\]""", RegexOption.IGNORE_CASE)

        fun cleanResponseText(rawText: String): String {
            var cleaned = rawText.replace(EXPLICIT_INTENT_REGEX, "")
            cleaned = cleaned.replace(Regex("""\{[\s\S]*?"(?:intent|action)"[\s\S]*?\}"""), "")
            cleaned = cleaned.replace(Regex("""\[ACTION:[\s\S]*?\]""", RegexOption.IGNORE_CASE), "")
            return cleaned.trim()
        }

        fun parseIntent(geminiResponse: String, userPrompt: String? = null): DetectedIntentAction? {
            val trimmedResponse = geminiResponse.trim()
            val combinedText =
                if (userPrompt.isNullOrBlank()) trimmedResponse else "$trimmedResponse\n${userPrompt.trim()}"
            val lowerResponse = trimmedResponse.lowercase(Locale.ROOT).replace("wi-fi", "wifi")
            val lowerCombined = combinedText.lowercase(Locale.ROOT).replace("wi-fi", "wifi")

            // 1. Check explicit regex [ACTION: TAG(params)]
            val explicitMatch = EXPLICIT_INTENT_REGEX.find(trimmedResponse)
            if (explicitMatch != null) {
                val intentName = explicitMatch.groupValues[1].uppercase(Locale.ROOT)
                val rawParams = explicitMatch.groupValues.getOrNull(2)?.trim() ?: ""
                val action = mapTagToAction(intentName, rawParams, trimmedResponse)
                if (action != null) return action
            }

            // 2. Check JSON intent
            if (trimmedResponse.contains("{") && trimmedResponse.contains("}")) {
                val jsonStart = trimmedResponse.indexOf("{")
                val jsonEnd = trimmedResponse.lastIndexOf("}")
                if (jsonEnd > jsonStart) {
                    val jsonStr = trimmedResponse.substring(jsonStart, jsonEnd + 1)
                    try {
                        val json = JSONObject(jsonStr)
                        val intentVal = json.optString("intent").ifBlank { json.optString("action") }
                        if (intentVal.isNotBlank()) {
                            val action = mapTagToAction(intentVal.uppercase(Locale.ROOT), jsonStr, trimmedResponse)
                            if (action != null) return action
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            // 3. Update intents
            if (lowerCombined.contains("install update") || lowerCombined.contains("update yourself") || lowerCombined.contains("update install karo")) {
                return DetectedIntentAction(
                    IntentActionType.INSTALL_UPDATE,
                    mapOf("action" to "install"),
                    "update",
                    "Opening Android Package Installer to approve update."
                )
            }
            if (lowerCombined.contains("download update") || lowerCombined.contains("download the latest update") || lowerCombined.contains("download new version") || lowerCombined.contains("update download karo")) {
                return DetectedIntentAction(
                    IntentActionType.DOWNLOAD_UPDATE,
                    mapOf("action" to "download"),
                    "update",
                    "Starting download of JARVIS update."
                )
            }
            if (lowerCombined.contains("what's new") || lowerCombined.contains("whats new") || lowerCombined.contains("changelog") || lowerCombined.contains("release notes")) {
                return DetectedIntentAction(
                    IntentActionType.SHOW_WHATS_NEW,
                    mapOf("action" to "whats_new"),
                    "update",
                    "Checking release notes."
                )
            }
            if (lowerCombined.contains("check for update") || lowerCombined.contains("check for updates") || lowerCombined.contains("check update") || lowerCombined.contains("check updates") || lowerCombined.contains("is there a new version") || lowerCombined.contains("any update")) {
                return DetectedIntentAction(
                    IntentActionType.CHECK_UPDATE,
                    mapOf("action" to "check"),
                    "update",
                    "Checking for JARVIS updates."
                )
            }

            // 4. Wi-Fi intents
            if (matchesWifiIntent(lowerResponse, lowerCombined)) {
                val isTurnOff = listOf(
                    "turn off wifi", "turning off wifi", "disable wifi", "switch off wifi", "wifi off", "wifi band"
                ).any { lowerCombined.contains(it) }
                val isTurnOn = listOf(
                    "turn on wifi", "turning on wifi", "enable wifi", "switch on wifi", "wifi on", "wifi chalu"
                ).any { lowerCombined.contains(it) }
                val isSettings = lowerCombined.contains("wifi setting") || lowerCombined.contains("wifi settings")

                val targetAction = when {
                    isSettings && !isTurnOff && !isTurnOn -> IntentActionType.OPEN_WIFI_SETTINGS
                    isTurnOff -> IntentActionType.DISABLE_WIFI
                    isTurnOn -> IntentActionType.ENABLE_WIFI
                    else -> IntentActionType.TOGGLE_WIFI
                }
                val actionParam = when (targetAction) {
                    IntentActionType.OPEN_WIFI_SETTINGS -> "settings"
                    IntentActionType.ENABLE_WIFI -> "on"
                    IntentActionType.DISABLE_WIFI -> "off"
                    else -> "toggle"
                }
                val spoken = when (targetAction) {
                    IntentActionType.OPEN_WIFI_SETTINGS -> "Opening Wi-Fi settings."
                    IntentActionType.ENABLE_WIFI -> "Turning on Wi-Fi."
                    IntentActionType.DISABLE_WIFI -> "Turning off Wi-Fi."
                    else -> "Toggling Wi-Fi."
                }
                return DetectedIntentAction(targetAction, mapOf("action" to actionParam), "wifi", spoken)
            }

            // 5. Flashlight intents
            if (matchesFlashlightIntent(lowerResponse, lowerCombined)) {
                val isTurnOff = listOf(
                    "turn off torch", "turn off flashlight", "disable flashlight", "torch off", "flashlight off", "torch band"
                ).any { lowerCombined.contains(it) }
                val isTurnOn = listOf(
                    "turn on torch", "turn on flashlight", "enable flashlight", "torch on", "flashlight on", "torch chalu"
                ).any { lowerCombined.contains(it) }
                val targetAction = when {
                    isTurnOff -> IntentActionType.DISABLE_FLASHLIGHT
                    isTurnOn -> IntentActionType.ENABLE_FLASHLIGHT
                    else -> IntentActionType.TOGGLE_FLASHLIGHT
                }
                val enabled = when (targetAction) {
                    IntentActionType.ENABLE_FLASHLIGHT -> true
                    IntentActionType.DISABLE_FLASHLIGHT -> false
                    else -> null
                }
                val params = if (enabled != null) mapOf("enabled" to enabled) else emptyMap<String, Any?>()
                val spoken = when (targetAction) {
                    IntentActionType.ENABLE_FLASHLIGHT -> "Turning on flashlight."
                    IntentActionType.DISABLE_FLASHLIGHT -> "Turning off flashlight."
                    else -> "Toggling flashlight."
                }
                return DetectedIntentAction(targetAction, params, "flashlight", spoken)
            }

            // 6. Bluetooth intents
            if (matchesBluetoothIntent(lowerResponse, lowerCombined)) {
                val isTurnOff = listOf(
                    "turn off bluetooth", "disable bluetooth", "bluetooth off", "bluetooth band"
                ).any { lowerCombined.contains(it) }
                val isTurnOn = listOf(
                    "turn on bluetooth", "enable bluetooth", "bluetooth on", "bluetooth chalu"
                ).any { lowerCombined.contains(it) }
                val isSettings = lowerCombined.contains("bluetooth setting") || lowerCombined.contains("bluetooth settings")

                val targetAction = when {
                    isSettings && !isTurnOff && !isTurnOn -> IntentActionType.OPEN_BLUETOOTH_SETTINGS
                    isTurnOff -> IntentActionType.DISABLE_BLUETOOTH
                    isTurnOn -> IntentActionType.ENABLE_BLUETOOTH
                    else -> IntentActionType.TOGGLE_BLUETOOTH
                }
                val actionParam = when (targetAction) {
                    IntentActionType.OPEN_BLUETOOTH_SETTINGS -> "settings"
                    IntentActionType.ENABLE_BLUETOOTH -> "on"
                    IntentActionType.DISABLE_BLUETOOTH -> "off"
                    else -> "toggle"
                }
                val spoken = when (targetAction) {
                    IntentActionType.OPEN_BLUETOOTH_SETTINGS -> "Opening Bluetooth settings."
                    IntentActionType.ENABLE_BLUETOOTH -> "Turning on Bluetooth."
                    IntentActionType.DISABLE_BLUETOOTH -> "Turning off Bluetooth."
                    else -> "Toggling Bluetooth."
                }
                return DetectedIntentAction(targetAction, mapOf("action" to actionParam), "bluetooth", spoken)
            }

            // 7. Camera intents
            if (matchesCameraIntent(lowerResponse, lowerCombined)) {
                val isFront = lowerCombined.contains("front camera") || lowerCombined.contains("selfie")
                val isVideo = lowerCombined.contains("record video") || lowerCombined.contains("record a video")
                val mode = if (isVideo) "video" else if (isFront) "front" else "photo"
                return DetectedIntentAction(IntentActionType.OPEN_CAMERA, mapOf("mode" to mode), "camera", "Opening camera.")
            }

            // 8. Volume intents
            if (lowerCombined.contains("volume up") || lowerCombined.contains("increase volume") || lowerCombined.contains("raise volume")) {
                return DetectedIntentAction(IntentActionType.ADJUST_VOLUME, mapOf("direction" to "up"), "volume", "Increasing volume.")
            }
            if (lowerCombined.contains("volume down") || lowerCombined.contains("decrease volume") || lowerCombined.contains("lower volume")) {
                return DetectedIntentAction(IntentActionType.ADJUST_VOLUME, mapOf("direction" to "down"), "volume", "Decreasing volume.")
            }
            if (lowerCombined.contains("mute audio") || lowerCombined.contains("mute phone") || lowerCombined.contains("mute volume") || lowerCombined.equals("mute", ignoreCase = true)) {
                return DetectedIntentAction(IntentActionType.ADJUST_VOLUME, mapOf("direction" to "mute"), "volume", "Muting audio.")
            }

            // 9. Battery & Time intents
            if (matchesBatteryIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(IntentActionType.CHECK_BATTERY, emptyMap(), "battery", "Checking battery status.")
            }
            if (matchesTimeIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(IntentActionType.CHECK_TIME, emptyMap(), "time", "Checking current time.")
            }

            // 10. Settings intent
            if (matchesSettingsIntent(lowerResponse, lowerCombined)) {
                return DetectedIntentAction(IntentActionType.OPEN_SETTINGS, mapOf("settingType" to "all"), "settings", "Opening Settings.")
            }

            // 11. Open App / specific apps
            val appAction = detectOpenAppIntent(lowerResponse, lowerCombined)
            if (appAction != null) return appAction

            // 12. Call contact intent
            if (lowerCombined.startsWith("call ") || lowerCombined.contains("call to ") || lowerCombined.contains("make a phone call")) {
                val name = lowerCombined.replace(Regex("""^(?:please\s+)?(?:call|phone|make a call to)\s+"""), "").trim()
                if (name.isNotBlank()) {
                    return DetectedIntentAction(IntentActionType.CALL_CONTACT, mapOf("contactName" to name), "call", "Calling $name.")
                }
            }

            // 13. Send SMS intent
            if (lowerCombined.startsWith("send sms") || lowerCombined.startsWith("text ") || lowerCombined.contains("send a message")) {
                return DetectedIntentAction(IntentActionType.SEND_SMS, mapOf("message" to trimmedResponse), "sms", "Preparing to send text message.")
            }

            // 14. Web search intent
            if (lowerCombined.startsWith("search for ") || lowerCombined.startsWith("google ") || lowerCombined.startsWith("search web for ")) {
                val query = lowerCombined.replace(Regex("""^(?:search for|google|search web for)\s+"""), "").trim()
                return DetectedIntentAction(IntentActionType.WEB_SEARCH, mapOf("query" to query), "search", "Searching for $query.")
            }

            return null
        }

        private fun mapTagToAction(tag: String, paramsStr: String, fullText: String): DetectedIntentAction? {
            val upperTag = tag.uppercase(Locale.ROOT)
            return when {
                upperTag.contains("WIFI") -> {
                    val isOff = upperTag.contains("OFF") || upperTag.contains("DISABLE") || paramsStr.contains("off")
                    val isSettings = upperTag.contains("SETTING") || paramsStr.contains("setting")
                    val type = if (isSettings) IntentActionType.OPEN_WIFI_SETTINGS else if (isOff) IntentActionType.DISABLE_WIFI else IntentActionType.ENABLE_WIFI
                    val act = if (isSettings) "settings" else if (isOff) "off" else "on"
                    DetectedIntentAction(type, mapOf("action" to act), "wifi", "Managing Wi-Fi.")
                }
                upperTag.contains("BLUETOOTH") -> {
                    val isOff = upperTag.contains("OFF") || upperTag.contains("DISABLE") || paramsStr.contains("off")
                    val isSettings = upperTag.contains("SETTING") || paramsStr.contains("setting")
                    val type = if (isSettings) IntentActionType.OPEN_BLUETOOTH_SETTINGS else if (isOff) IntentActionType.DISABLE_BLUETOOTH else IntentActionType.ENABLE_BLUETOOTH
                    val act = if (isSettings) "settings" else if (isOff) "off" else "on"
                    DetectedIntentAction(type, mapOf("action" to act), "bluetooth", "Managing Bluetooth.")
                }
                upperTag.contains("FLASHLIGHT") || upperTag.contains("TORCH") -> {
                    val isOff = upperTag.contains("OFF") || upperTag.contains("DISABLE") || paramsStr.contains("off") || paramsStr.contains("false")
                    val type = if (isOff) IntentActionType.DISABLE_FLASHLIGHT else IntentActionType.ENABLE_FLASHLIGHT
                    DetectedIntentAction(type, mapOf("enabled" to !isOff), "flashlight", if (isOff) "Turning off flashlight." else "Turning on flashlight.")
                }
                upperTag.contains("CAMERA") -> {
                    DetectedIntentAction(IntentActionType.OPEN_CAMERA, mapOf("mode" to "photo"), "camera", "Opening camera.")
                }
                upperTag.contains("BATTERY") -> {
                    DetectedIntentAction(IntentActionType.CHECK_BATTERY, emptyMap(), "battery", "Checking battery status.")
                }
                upperTag.contains("TIME") -> {
                    DetectedIntentAction(IntentActionType.CHECK_TIME, emptyMap(), "time", "Checking current time.")
                }
                upperTag.contains("ALARM") -> {
                    DetectedIntentAction(IntentActionType.SET_ALARM, emptyMap(), "alarm", "Setting alarm.")
                }
                upperTag.contains("TIMER") -> {
                    DetectedIntentAction(IntentActionType.SET_TIMER, emptyMap(), "timer", "Setting timer.")
                }
                upperTag.contains("VOLUME") -> {
                    val dir = if (upperTag.contains("DOWN") || paramsStr.contains("down")) "down" else if (upperTag.contains("MUTE") || paramsStr.contains("mute")) "mute" else "up"
                    DetectedIntentAction(IntentActionType.ADJUST_VOLUME, mapOf("direction" to dir), "volume", "Adjusting volume.")
                }
                upperTag.contains("UPDATE") -> {
                    val act = if (upperTag.contains("INSTALL") || paramsStr.contains("install")) IntentActionType.INSTALL_UPDATE
                    else if (upperTag.contains("DOWNLOAD") || paramsStr.contains("download")) IntentActionType.DOWNLOAD_UPDATE
                    else if (upperTag.contains("WHATS_NEW") || paramsStr.contains("whats_new")) IntentActionType.SHOW_WHATS_NEW
                    else IntentActionType.CHECK_UPDATE
                    DetectedIntentAction(act, mapOf("action" to "check"), "update", "Checking updates.")
                }
                upperTag.contains("OPEN") || upperTag.contains("APP") -> {
                    val appName = extractParamValue(paramsStr, "appName").ifBlank { extractParamValue(paramsStr, "app") }.ifBlank { "app" }
                    DetectedIntentAction(IntentActionType.OPEN_APP, mapOf("appName" to appName), "open_app", "Opening $appName.")
                }
                else -> null
            }
        }

        private fun extractParamValue(paramsStr: String, key: String): String {
            try {
                if (paramsStr.startsWith("{")) {
                    val json = JSONObject(paramsStr)
                    return json.optString(key, "")
                }
            } catch (_: Exception) {
            }
            val regex = Regex("""$key\s*[:=]\s*["']?([^"',\)]+)["']?""", RegexOption.IGNORE_CASE)
            return regex.find(paramsStr)?.groupValues?.getOrNull(1)?.trim() ?: ""
        }

        private fun matchesWifiIntent(response: String, combined: String): Boolean =
            listOf("wifi", "wi-fi", "internet connection", "wireless network").any { combined.contains(it) }

        private fun matchesFlashlightIntent(response: String, combined: String): Boolean =
            listOf("flashlight", "torch", "flash light").any { combined.contains(it) }

        private fun matchesCameraIntent(response: String, combined: String): Boolean =
            listOf("open camera", "take photo", "take a picture", "take selfie", "open the camera", "record video").any { combined.contains(it) }

        private fun matchesBluetoothIntent(response: String, combined: String): Boolean =
            listOf("bluetooth", "bt connect", "bt device").any { combined.contains(it) }

        private fun matchesSettingsIntent(response: String, combined: String): Boolean =
            listOf("open settings", "system settings", "device settings", "phone settings").any { combined.contains(it) }

        private fun matchesBatteryIntent(response: String, combined: String): Boolean =
            listOf("battery percentage", "battery level", "battery status", "check battery", "how much battery").any { combined.contains(it) }

        private fun matchesTimeIntent(response: String, combined: String): Boolean =
            listOf("what time is it", "current time", "what's the time", "tell me the time", "what day is it", "today's date").any { combined.contains(it) }

        private fun detectOpenAppIntent(response: String, combined: String): DetectedIntentAction? {
            if (combined.contains("whatsapp")) {
                return DetectedIntentAction(IntentActionType.WHATSAPP, mapOf("appName" to "WhatsApp"), "whatsapp", "Opening WhatsApp.")
            }
            if (combined.contains("youtube")) {
                return DetectedIntentAction(IntentActionType.YOUTUBE, mapOf("appName" to "YouTube"), "youtube", "Opening YouTube.")
            }
            if (combined.contains("instagram")) {
                return DetectedIntentAction(IntentActionType.INSTAGRAM, mapOf("appName" to "Instagram"), "instagram", "Opening Instagram.")
            }
            if (combined.contains("open chrome") || combined.contains("open browser")) {
                return DetectedIntentAction(IntentActionType.BROWSER, mapOf("appName" to "Browser"), "browser", "Opening browser.")
            }
            val openRegex = Regex("""(?:open|launch|start)\s+(?:the\s+)?([a-zA-Z0-9\s]+?)(?:\s+app)?$""", RegexOption.IGNORE_CASE)
            val match = openRegex.find(combined.trim())
            if (match != null) {
                val appName = match.groupValues[1].trim()
                if (appName.isNotBlank() && !listOf("settings", "camera", "wifi", "bluetooth", "torch", "flashlight").contains(appName.lowercase(Locale.ROOT))) {
                    return DetectedIntentAction(IntentActionType.OPEN_APP, mapOf("appName" to appName), "open_app", "Opening $appName.")
                }
            }
            return null
        }

        suspend fun executeAction(
            context: Context,
            detectedAction: DetectedIntentAction,
            toolRegistry: ToolRegistry? = null
        ): CommandExecutionResult {
            Log.d(TAG, "Executing system action: ${detectedAction.actionType} with params: ${detectedAction.parameters}")
            val appContext = context.applicationContext

            if (detectedAction.actionType == IntentActionType.ADJUST_VOLUME) {
                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    ?: return CommandExecutionResult(false, detectedAction, "Audio service unavailable.")
                val dir = detectedAction.parameters["direction"]?.toString() ?: "up"
                when (dir) {
                    "up" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "down" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "mute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    "unmute" -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                }
                return CommandExecutionResult(true, detectedAction, "Volume adjusted.", mapOf("direction" to dir))
            }

            val toolName = when (detectedAction.actionType) {
                IntentActionType.TOGGLE_WIFI, IntentActionType.ENABLE_WIFI, IntentActionType.DISABLE_WIFI, IntentActionType.OPEN_WIFI_SETTINGS -> "WifiTool"
                IntentActionType.TOGGLE_FLASHLIGHT, IntentActionType.ENABLE_FLASHLIGHT, IntentActionType.DISABLE_FLASHLIGHT -> "FlashlightTool"
                IntentActionType.TOGGLE_BLUETOOTH, IntentActionType.ENABLE_BLUETOOTH, IntentActionType.DISABLE_BLUETOOTH, IntentActionType.OPEN_BLUETOOTH_SETTINGS -> "BluetoothTool"
                IntentActionType.OPEN_CAMERA -> "CameraTool"
                IntentActionType.OPEN_APP -> "OpenAppTool"
                IntentActionType.OPEN_SETTINGS -> "SettingsTool"
                IntentActionType.SET_ALARM -> "AlarmTool"
                IntentActionType.SET_TIMER -> "TimerTool"
                IntentActionType.CHECK_BATTERY -> "BatteryTool"
                IntentActionType.CHECK_TIME -> "CurrentTimeTool"
                IntentActionType.WEB_SEARCH -> "WebSearchTool"
                IntentActionType.CALL_CONTACT -> "CallContactTool"
                IntentActionType.SEND_SMS -> "SendSmsTool"
                IntentActionType.WHATSAPP -> "WhatsAppTool"
                IntentActionType.INSTAGRAM -> "InstagramTool"
                IntentActionType.YOUTUBE -> "YouTubeTool"
                IntentActionType.BROWSER -> "BrowserTool"
                IntentActionType.NOTIFICATIONS -> "NotificationTool"
                IntentActionType.CHECK_UPDATE, IntentActionType.DOWNLOAD_UPDATE, IntentActionType.INSTALL_UPDATE, IntentActionType.SHOW_WHATS_NEW -> "app_update"
                else -> null
            }

            val tool = toolRegistry?.getTool(toolName ?: "") ?: when (toolName) {
                "WifiTool" -> WifiTool()
                "FlashlightTool" -> FlashlightTool()
                "BluetoothTool" -> BluetoothTool()
                "OpenAppTool" -> OpenAppTool()
                "CameraTool" -> CameraTool()
                "SettingsTool" -> SettingsTool()
                "AlarmTool" -> AlarmTool()
                "TimerTool" -> TimerTool()
                "BatteryTool" -> BatteryTool()
                "CurrentTimeTool" -> CurrentTimeTool()
                "WebSearchTool" -> WebSearchTool()
                "CallContactTool" -> CallContactTool()
                "SendSmsTool" -> SendSmsTool()
                "WhatsAppTool" -> WhatsAppTool()
                "InstagramTool" -> InstagramTool()
                "YouTubeTool" -> YouTubeTool()
                "BrowserTool" -> BrowserTool()
                "NotificationTool" -> NotificationTool()
                else -> null
            }

            if (tool != null) {
                val result = tool.execute(appContext, detectedAction.parameters)
                return CommandExecutionResult(result.success, detectedAction, result.message, result.data ?: emptyMap(), result)
            }

            return CommandExecutionResult(true, detectedAction, detectedAction.spokenFeedback)
        }

        fun startServiceForResponse(context: Context, geminiResponse: String, userPrompt: String? = null) {
            val intent = Intent(context, CommandHandlerService::class.java).apply {
                putExtra(EXTRA_GEMINI_RESPONSE, geminiResponse)
                putExtra(EXTRA_USER_PROMPT, userPrompt)
            }
            context.startService(intent)
        }
    }
}
