package com.example.ai

import com.example.agent.CommandNormalizer

class LocalRuleAIProvider : AIProvider {
    override val providerName: String = "Local Engine (Offline/Rule-Based)"

    override suspend fun processCommand(request: AIRequest): AIResponse {
        val raw = request.prompt
        val normalized = CommandNormalizer.standardizeHinglish(raw)

        // 0. Identity & Greetings
        if (normalized.contains("who are you") || normalized.contains("what is your name") || normalized.contains("what are you")) {
            return AIResponse("I am JARVIS, your AI voice and system command assistant.")
        }
        if (normalized == "hello" || normalized == "hi" || normalized == "hey" || normalized.startsWith("hello ") || normalized.startsWith("hi ")) {
            return AIResponse("Hello! I am JARVIS, your AI assistant. How can I help you?")
        }

        // 1. Flashlight
        if (normalized.contains("turn on flashlight") || normalized == "flashlight on" || normalized == "torch on") {
            return AIResponse("Turning on flashlight.", ToolInvocation("FlashlightTool", mapOf("enabled" to true)))
        }
        if (normalized.contains("turn off flashlight") || normalized == "flashlight off" || normalized == "torch off") {
            return AIResponse("Turning off flashlight.", ToolInvocation("FlashlightTool", mapOf("enabled" to false)))
        }

        // 1.5 Wi-Fi Toggle & Controls
        val isWifi = normalized.contains("wifi") || normalized.contains("wi-fi")
        if (isWifi && (normalized.contains("on") || normalized.contains("off") || normalized.contains("toggle") ||
            normalized.contains("chalu") || normalized.contains("band") || normalized.contains("enable") || normalized.contains("disable"))) {
            val action = if (normalized.contains("off") || normalized.contains("band") || normalized.contains("disable")) "off"
            else if (normalized.contains("on") || normalized.contains("chalu") || normalized.contains("enable")) "on"
            else "toggle"
            return AIResponse("Toggling Wi-Fi [INTENT: TOGGLE_WIFI]", ToolInvocation("WifiTool", mapOf("action" to action)))
        }

        // 1.6 Bluetooth Toggle & Controls
        val isBt = normalized.contains("bluetooth")
        if (isBt && (normalized.contains("on") || normalized.contains("off") || normalized.contains("toggle") ||
            normalized.contains("chalu") || normalized.contains("band") || normalized.contains("enable") || normalized.contains("disable"))) {
            val action = if (normalized.contains("off") || normalized.contains("band") || normalized.contains("disable")) "off"
            else if (normalized.contains("on") || normalized.contains("chalu") || normalized.contains("enable")) "on"
            else "toggle"
            return AIResponse("Toggling Bluetooth [INTENT: TOGGLE_BLUETOOTH]", ToolInvocation("BluetoothTool", mapOf("action" to action)))
        }

        // 2. Battery
        if (normalized.contains("battery") || normalized.contains("charge")) {
            return AIResponse("Checking battery status.", ToolInvocation("BatteryTool", emptyMap()))
        }

        // 3. Time
        if (normalized.contains("what time") || normalized.contains("current time") || normalized == "time") {
            return AIResponse("Checking current time.", ToolInvocation("CurrentTimeTool", emptyMap()))
        }

        // 4. Device Info & Storage
        if (normalized.contains("storage") || normalized.contains("space")) {
            return AIResponse("Checking storage.", ToolInvocation("DeviceInfoTool", mapOf("queryType" to "storage")))
        }
        if (normalized.contains("what phone") || normalized.contains("device info") || normalized.contains("model")) {
            return AIResponse("Checking device details.", ToolInvocation("DeviceInfoTool", mapOf("queryType" to "model")))
        }
        if (normalized.contains("is bluetooth on") || normalized.contains("bluetooth status")) {
            return AIResponse("Checking Bluetooth.", ToolInvocation("DeviceInfoTool", mapOf("queryType" to "bluetooth")))
        }
        if (normalized.contains("is wi-fi") || normalized.contains("wifi status") || normalized.contains("is wifi")) {
            return AIResponse("Checking Wi-Fi.", ToolInvocation("DeviceInfoTool", mapOf("queryType" to "wifi")))
        }

        // 5. Camera & Photo
        if (normalized.contains("open camera") || normalized.contains("take a photo") || normalized.contains("take photo")) {
            return AIResponse("Opening camera.", ToolInvocation("CameraTool", mapOf("mode" to "photo")))
        }

        // 6. Settings navigation
        if (normalized.contains("wifi setting") || normalized.contains("wi-fi setting")) {
            return AIResponse("Opening Wi-Fi settings.", ToolInvocation("SettingsTool", mapOf("settingType" to "wifi")))
        }
        if (normalized.contains("bluetooth setting")) {
            return AIResponse("Opening Bluetooth settings.", ToolInvocation("SettingsTool", mapOf("settingType" to "bluetooth")))
        }
        if (normalized.contains("notification setting")) {
            return AIResponse("Opening notification settings.", ToolInvocation("SettingsTool", mapOf("settingType" to "notifications")))
        }
        if (normalized.contains("accessibility setting")) {
            return AIResponse("Opening accessibility settings.", ToolInvocation("SettingsTool", mapOf("settingType" to "accessibility")))
        }
        if (normalized.contains("app setting")) {
            return AIResponse("Opening app settings.", ToolInvocation("SettingsTool", mapOf("settingType" to "app")))
        }

        // 7. Alarms: e.g. "set an alarm for 7 AM", "set alarm for 6:30 pm"
        val alarmRegex = Regex("(?:set|create)\\s+(?:an\\s+)?alarm(?:\\s+for)?\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val alarmMatch = alarmRegex.find(normalized)
        if (alarmMatch != null) {
            val hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val minute = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = alarmMatch.groupValues[3].ifBlank { if (hour in 1..6) "pm" else "am" }
            return AIResponse("Setting alarm.", ToolInvocation("AlarmTool", mapOf(
                "hour" to hour,
                "minute" to minute,
                "amPm" to amPm.lowercase(),
                "label" to "JARVIS Alarm"
            )))
        }

        // 8. Timers: e.g. "set a timer for 10 minutes", "timer for 30 seconds"
        val timerRegex = Regex("(?:set|start)\\s+(?:a\\s+)?timer(?:\\s+for)?\\s+(\\d+)\\s*(minute|min|second|sec)", RegexOption.IGNORE_CASE)
        val timerMatch = timerRegex.find(normalized)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 5
            val unit = timerMatch.groupValues[2].lowercase()
            return if (unit.startsWith("sec")) {
                AIResponse("Setting timer.", ToolInvocation("TimerTool", mapOf("durationSeconds" to amount)))
            } else {
                AIResponse("Setting timer.", ToolInvocation("TimerTool", mapOf("durationMinutes" to amount)))
            }
        }

        // 9. Calls: "call rahul", "phone mom"
        val callRegex = Regex("^(?:call|dial|phone)\\s+([a-zA-Z0-9\\s]+)$", RegexOption.IGNORE_CASE)
        val callMatch = callRegex.find(normalized)
        if (callMatch != null) {
            val name = callMatch.groupValues[1].trim()
            return AIResponse("Initiating call to $name.", ToolInvocation("CallContactTool", mapOf("contactName" to name)))
        }

        // 10. WhatsApp Actions:
        // "whatsapp rahul saying main 10 minute late hoon"
        val waSayingRegex = Regex("^whatsapp\\s+([a-zA-Z0-9]+)\\s+saying\\s+(.*)", RegexOption.IGNORE_CASE)
        val waSayingMatch = waSayingRegex.find(normalized)
        if (waSayingMatch != null) {
            val recipient = waSayingMatch.groupValues[1].trim()
            val message = waSayingMatch.groupValues[2].trim()
            return AIResponse("Composing WhatsApp message to $recipient.", ToolInvocation("WhatsAppTool", mapOf(
                "action" to "message",
                "contactName" to recipient,
                "message" to message
            )))
        }

        // "whatsapp rahul"
        val waContactRegex = Regex("^whatsapp\\s+([a-zA-Z0-9]+)$", RegexOption.IGNORE_CASE)
        val waContactMatch = waContactRegex.find(normalized)
        if (waContactMatch != null && waContactMatch.groupValues[1].lowercase() !in listOf("kholo", "open", "app")) {
            val recipient = waContactMatch.groupValues[1].trim()
            return AIResponse("Opening WhatsApp chat with $recipient.", ToolInvocation("WhatsAppTool", mapOf(
                "action" to "chat",
                "contactName" to recipient
            )))
        }

        if (normalized == "open whatsapp" || normalized == "whatsapp kholo" || normalized == "whatsapp") {
            return AIResponse("Opening WhatsApp.", ToolInvocation("WhatsAppTool", mapOf("action" to "open")))
        }

        // 11. SMS / Messages: "send rahul a message saying i'll reach home at 8", "message rahul hello"
        val smsRegex1 = Regex("(?:send|message)\\s+([a-zA-Z0-9]+)\\s+(?:a\\s+message\\s+)?saying\\s+(.*)", RegexOption.IGNORE_CASE)
        val smsMatch1 = smsRegex1.find(normalized)
        if (smsMatch1 != null) {
            val recipient = smsMatch1.groupValues[1].trim()
            val message = smsMatch1.groupValues[2].trim()
            return AIResponse("Preparing message to $recipient.", ToolInvocation("SendSmsTool", mapOf(
                "recipient" to recipient,
                "message" to message
            )))
        }
        val smsRegex2 = Regex("(?:send\\s+sms\\s+to|text)\\s+([a-zA-Z0-9]+)\\s+(.*)", RegexOption.IGNORE_CASE)
        val smsMatch2 = smsRegex2.find(normalized)
        if (smsMatch2 != null) {
            val recipient = smsMatch2.groupValues[1].trim()
            val message = smsMatch2.groupValues[2].trim()
            return AIResponse("Preparing SMS to $recipient.", ToolInvocation("SendSmsTool", mapOf(
                "recipient" to recipient,
                "message" to message
            )))
        }

        // 12. Weather
        val weatherCityRegex = Regex("weather\\s+(?:in|for|at)\\s+([a-zA-Z\\s]+)", RegexOption.IGNORE_CASE)
        val weatherCityMatch = weatherCityRegex.find(normalized)
        if (weatherCityMatch != null) {
            val city = weatherCityMatch.groupValues[1].trim()
            return AIResponse("Checking weather in $city.", ToolInvocation("WeatherTool", mapOf("city" to city)))
        }
        if (normalized.contains("weather") || normalized.contains("rain today") || normalized.contains("temperature")) {
            return AIResponse("Checking local weather.", ToolInvocation("WeatherTool", emptyMap()))
        }

        // 13. Instagram & YouTube & Google Search
        val instaSearchRegex = Regex("(?:search\\s+instagram\\s+(?:for\\s+)?|instagram\\s+search\\s+)(.*)", RegexOption.IGNORE_CASE)
        val instaMatch = instaSearchRegex.find(normalized)
        if (instaMatch != null) {
            val query = instaMatch.groupValues[1].trim()
            return AIResponse("Searching Instagram for \"$query\".", ToolInvocation("InstagramTool", mapOf("action" to "search", "query" to query)))
        }
        if (normalized == "open instagram" || normalized == "instagram kholo" || normalized == "instagram") {
            return AIResponse("Opening Instagram.", ToolInvocation("InstagramTool", mapOf("action" to "open")))
        }

        val ytRegex = Regex("(?:search\\s+youtube\\s+(?:for\\s+)?|youtube\\s+search\\s+)(.*)", RegexOption.IGNORE_CASE)
        val ytMatch = ytRegex.find(normalized)
        if (ytMatch != null) {
            val query = ytMatch.groupValues[1].trim()
            return AIResponse("Searching YouTube for \"$query\".", ToolInvocation("YouTubeTool", mapOf("action" to "search", "query" to query)))
        }
        if (normalized == "open youtube" || normalized == "youtube kholo" || normalized == "youtube") {
            return AIResponse("Opening YouTube.", ToolInvocation("YouTubeTool", mapOf("action" to "open")))
        }

        val googleSearchRegex = Regex("(?:search\\s+google\\s+(?:for\\s+)?|search\\s+(?:the\\s+)?web\\s+(?:for\\s+)?|google\\s+)(.*)", RegexOption.IGNORE_CASE)
        val googleMatch = googleSearchRegex.find(normalized)
        if (googleMatch != null) {
            val query = googleMatch.groupValues[1].trim()
            return AIResponse("Searching Google for \"$query\".", ToolInvocation("BrowserTool", mapOf("action" to "search", "query" to query)))
        }
        if (normalized == "open chrome" || normalized == "chrome kholo" || normalized == "chrome") {
            return AIResponse("Opening Chrome.", ToolInvocation("BrowserTool", mapOf("action" to "open")))
        }

        // 14. Notifications
        if (normalized.contains("notification")) {
            val pkg = if (normalized.contains("whatsapp")) "whatsapp" else ""
            return AIResponse("Checking notifications.", ToolInvocation("NotificationTool", if (pkg.isNotBlank()) mapOf("appName" to pkg) else emptyMap()))
        }

        // 15. Memory:
        // "what do you remember about me?" / "what do you remember" / "recall all memories"
        if (normalized.contains("what do you remember") || normalized.contains("what you remember") ||
            normalized.contains("show memories") || normalized == "memories") {
            return AIResponse("Checking my memory.", ToolInvocation("MemoryTool", mapOf("action" to "recall", "key" to "")))
        }

        // "remember that I prefer Hindi" or "remember that my favorite color is blue"
        val rememberThatRegex = Regex("remember\\s+(?:that\\s+)?(.*)", RegexOption.IGNORE_CASE)
        val rememberThatMatch = rememberThatRegex.find(normalized)
        if (rememberThatMatch != null) {
            val content = rememberThatMatch.groupValues[1].trim()
            // Check if it's "my X is Y"
            val isMatch = Regex("(?:my\\s+)?([^=]+?)\\s+(?:is|are)\\s+(.*)", RegexOption.IGNORE_CASE).find(content)
            val (key, value) = if (isMatch != null) {
                Pair(isMatch.groupValues[1].trim(), isMatch.groupValues[2].trim())
            } else if (content.contains("prefer", ignoreCase = true)) {
                Pair("preference", content)
            } else {
                Pair("note", content)
            }
            return AIResponse("Saving memory.", ToolInvocation("MemoryTool", mapOf(
                "action" to "remember",
                "key" to key,
                "value" to value
            )))
        }

        // "forget that I prefer Hindi" / "forget my favorite color"
        val forgetRegex = Regex("forget\\s+(?:that\\s+)?(?:my\\s+)?(.*)", RegexOption.IGNORE_CASE)
        val forgetMatch = forgetRegex.find(normalized)
        if (forgetMatch != null) {
            val key = forgetMatch.groupValues[1].trim()
            return AIResponse("Forgetting \"$key\".", ToolInvocation("MemoryTool", mapOf(
                "action" to "forget",
                "key" to key
            )))
        }

        // "what is my favorite color"
        val recallRegex = Regex("(?:what\\s+is\\s+my|recall\\s+my|tell\\s+me\\s+my)\\s+(.*)", RegexOption.IGNORE_CASE)
        val recallMatch = recallRegex.find(normalized)
        if (recallMatch != null) {
            val key = recallMatch.groupValues[1].trim()
            return AIResponse("Checking memory for $key.", ToolInvocation("MemoryTool", mapOf(
                "action" to "recall",
                "key" to key
            )))
        }

        // Routines: "activate movie mode", "activate good night", "activate work focus"
        if (normalized.contains("activate movie mode") || normalized == "movie mode") {
            return AIResponse("Activating Movie Mode.", ToolInvocation("RoutineTool", mapOf("routineName" to "movie mode")))
        }
        if (normalized.contains("activate good night") || normalized == "good night") {
            return AIResponse("Activating Good Night routine.", ToolInvocation("RoutineTool", mapOf("routineName" to "good night")))
        }
        if (normalized.contains("activate work focus") || normalized == "work focus") {
            return AIResponse("Activating Work Focus routine.", ToolInvocation("RoutineTool", mapOf("routineName" to "work focus")))
        }

        // PC Control: "lock pc", "show pc battery", "open chrome on pc", "play music on pc"
        if (normalized.contains("lock pc") || normalized.contains("lock my pc")) {
            return AIResponse("Locking your PC.", ToolInvocation("PcControlTool", mapOf("action" to "lock")))
        }
        if (normalized.contains("show pc battery") || normalized.contains("pc battery")) {
            return AIResponse("Checking PC battery.", ToolInvocation("PcControlTool", mapOf("action" to "battery")))
        }
        if (normalized.contains("open chrome on pc")) {
            return AIResponse("Opening Chrome on your PC.", ToolInvocation("PcControlTool", mapOf("action" to "open_app", "appName" to "Chrome")))
        }
        if (normalized.contains("play music on pc")) {
            return AIResponse("Playing music on your PC.", ToolInvocation("PcControlTool", mapOf("action" to "play_music")))
        }

        // Smart Home Devices: "turn on bedroom light", "turn off tv", "set living room light to 30 percent", "show connected devices"
        if (normalized.contains("show connected devices") || normalized.contains("what devices")) {
            return AIResponse("Checking connected devices.", ToolInvocation("SmartDeviceTool", mapOf("action" to "query_devices")))
        }
        val turnDeviceMatch = Regex("turn\\s+(on|off)\\s+(.*)", RegexOption.IGNORE_CASE).find(normalized)
        if (turnDeviceMatch != null && !normalized.contains("flashlight")) {
            val st = turnDeviceMatch.groupValues[1]
            val dev = turnDeviceMatch.groupValues[2].trim()
            return AIResponse("Turning $st $dev.", ToolInvocation("SmartDeviceTool", mapOf("deviceName" to dev, "action" to "power", "state" to st)))
        }
        val setDimMatch = Regex("set\\s+(.*?)\\s+(?:light\\s+)?to\\s+(\\d+)\\s*(?:percent)?", RegexOption.IGNORE_CASE).find(normalized)
        if (setDimMatch != null && !normalized.contains("volume")) {
            val dev = setDimMatch.groupValues[1].trim() + " light"
            val pct = setDimMatch.groupValues[2]
            return AIResponse("Setting $dev brightness to $pct%.", ToolInvocation("SmartDeviceTool", mapOf("deviceName" to dev, "action" to "brightness", "state" to pct)))
        }

        // Media Controls: "play music", "pause music", "next track", "previous track", "set volume to 50 percent", "increase volume", "decrease volume"
        if (normalized == "play music") {
            return AIResponse("Resuming music.", ToolInvocation("MediaControlTool", mapOf("action" to "play")))
        }
        if (normalized == "pause music" || normalized == "pause") {
            return AIResponse("Pausing music.", ToolInvocation("MediaControlTool", mapOf("action" to "pause")))
        }
        if (normalized == "next track") {
            return AIResponse("Skipping to next track.", ToolInvocation("MediaControlTool", mapOf("action" to "next")))
        }
        if (normalized == "previous track") {
            return AIResponse("Playing previous track.", ToolInvocation("MediaControlTool", mapOf("action" to "previous")))
        }
        val volSetMatch = Regex("set\\s+volume\\s+to\\s+(\\d+)\\s*(?:percent)?", RegexOption.IGNORE_CASE).find(normalized)
        if (volSetMatch != null) {
            val vol = volSetMatch.groupValues[1]
            return AIResponse("Setting volume to $vol%.", ToolInvocation("MediaControlTool", mapOf("action" to "set_volume", "level" to vol)))
        }
        if (normalized == "increase volume") {
            return AIResponse("Increasing volume.", ToolInvocation("MediaControlTool", mapOf("action" to "volume_up")))
        }
        if (normalized == "decrease volume") {
            return AIResponse("Decreasing volume.", ToolInvocation("MediaControlTool", mapOf("action" to "volume_down")))
        }

        // Navigation: "navigate to nearest petrol pump", "navigate to nearest hospital", "navigate to home", "navigate to delhi"
        val navMatch = Regex("navigate\\s+to\\s+(.*)", RegexOption.IGNORE_CASE).find(normalized)
        if (navMatch != null) {
            val dest = navMatch.groupValues[1].trim()
            return AIResponse("Navigating to $dest.", ToolInvocation("NavigationTool", mapOf("destination" to dest)))
        }
        if (normalized == "what is my location") {
            return AIResponse("Checking your current location.", ToolInvocation("DeviceInfoTool", mapOf("queryType" to "location")))
        }

        // Files: "open downloads folder", "view pdf files"
        if (normalized.contains("open downloads folder")) {
            return AIResponse("Opening Downloads folder.", ToolInvocation("FileTool", mapOf("action" to "open_downloads")))
        }
        if (normalized.contains("view pdf files")) {
            return AIResponse("Opening documents.", ToolInvocation("FileTool", mapOf("action" to "view_documents")))
        }

        // Calendar: "view calendar agenda", "add meeting to calendar"
        if (normalized.contains("view calendar agenda")) {
            return AIResponse("Opening calendar agenda.", ToolInvocation("CalendarTool", mapOf("action" to "view_calendar")))
        }
        if (normalized.contains("add meeting to calendar")) {
            return AIResponse("Adding meeting to calendar.", ToolInvocation("CalendarTool", mapOf("action" to "add_event", "title" to "Meeting")))
        }

        // 15. Open App: "open youtube", "open whatsapp", "open chrome", "open settings"
        val openAppRegex = Regex("^open\\s+([a-zA-Z0-9\\s]+)$", RegexOption.IGNORE_CASE)
        val openMatch = openAppRegex.find(normalized)
        if (openMatch != null) {
            val app = openMatch.groupValues[1].trim()
            return AIResponse("Opening $app.", ToolInvocation("OpenAppTool", mapOf("appName" to app)))
        }

        // Default conversational response
        return AIResponse(
            textResponse = "I'm not sure how to help with \"$raw\". Try asking to open an app, check the battery, set an alarm, turn on the flashlight, or check the weather."
        )
    }
}
