package com.example.agent

object CommandNormalizer {

    fun normalize(input: String): String {
        var text = input.trim().lowercase()
        // Strip trailing punctuation
        text = text.replace(Regex("[.?!,;]+$"), "").trim()

        // Common Hinglish / Hindi wake words and conversational filler cleanup
        text = text.replace(Regex("^(hey jarvis|jarvis|bhai jarvis|suno jarvis)[,\\s]*"), "").trim()

        return text
    }

    /**
     * Translates or maps idiomatic Hindi/Hinglish commands to clear canonical intents
     * while preserving parameters.
     */
    fun standardizeHinglish(input: String): String {
        var text = normalize(input)

        // Flashlight
        if (text.matches(Regex(".*(flashlight|torch).*(jalao|chalu|on|jaga do).*")) ||
            text.matches(Regex(".*(torch|flashlight)\\s+(on|chalu).*"))) {
            return "turn on flashlight"
        }
        if (text.matches(Regex(".*(flashlight|torch).*(band|bujhao|off).*")) ||
            text.matches(Regex(".*(torch|flashlight)\\s+(off|band).*"))) {
            return "turn off flashlight"
        }

        // Battery
        if (text.contains("battery kitni hai") || text.contains("battery check") ||
            text.contains("charge kitna hai") || text.contains("battery percentage") ||
            text.contains("kitni charging hai") || text.contains("battery batao") ||
            text.contains("battery status") || text == "battery") {
            return "what is my battery"
        }

        // Instagram search: "Instagram par BMW search karo" or "Instagram kholo aur BMW search karo"
        val instaSearchMatch = Regex("instagram\\s+(?:par|pe|kholo\\s+aur)?\\s*(.+?)\\s+search\\s*karo", RegexOption.IGNORE_CASE).find(text)
        if (instaSearchMatch != null) {
            val q = instaSearchMatch.groupValues[1].trim()
            return "search instagram for $q"
        }

        // YouTube search: "YouTube par Android tutorial search karo"
        val ytSearchMatch = Regex("youtube\\s+(?:par|pe|kholo\\s+aur)?\\s*(.+?)\\s+search\\s*karo", RegexOption.IGNORE_CASE).find(text)
        if (ytSearchMatch != null) {
            val q = ytSearchMatch.groupValues[1].trim()
            return "search youtube for $q"
        }

        // Google / Web search: "Google par latest AI news search karo"
        val googleSearchMatch = Regex("(?:google|chrome|web)\\s+(?:par|pe)?\\s*(.+?)\\s+search\\s*karo", RegexOption.IGNORE_CASE).find(text)
        if (googleSearchMatch != null) {
            val q = googleSearchMatch.groupValues[1].trim()
            return "search google for $q"
        }

        // WhatsApp messages:
        // "WhatsApp par Rahul ko message karo: main 10 minute mein aa raha hoon"
        // "Rahul ko bolo main 10 minute late hoon"
        // "Rahul ko WhatsApp message bhejo"
        val waColonMatch = Regex("whatsapp\\s+(?:par|pe)?\\s*([a-zA-Z0-9]+)\\s+ko\\s+message\\s*karo\\s*[:\\-]?\\s*(.*)", RegexOption.IGNORE_CASE).find(text)
        if (waColonMatch != null) {
            val name = waColonMatch.groupValues[1].trim()
            val msg = waColonMatch.groupValues[2].trim()
            return if (msg.isNotBlank()) "whatsapp $name saying $msg" else "whatsapp $name"
        }

        val boloMatch = Regex("([a-zA-Z0-9]+)\\s+ko\\s+(?:whatsapp\\s+par\\s+)?bolo\\s+(ki\\s+)?(.*)", RegexOption.IGNORE_CASE).find(text)
        if (boloMatch != null) {
            val name = boloMatch.groupValues[1].trim()
            val msg = boloMatch.groupValues[3].trim()
            return "whatsapp $name saying $msg"
        }

        val waMsgMatch = Regex("([a-zA-Z0-9]+)\\s+ko\\s+whatsapp\\s+(?:message|pe\\s+message)?\\s*(bhejo|karo|send\\s*karo)?\\s*[:\\-]?\\s*(.*)", RegexOption.IGNORE_CASE).find(text)
        if (waMsgMatch != null) {
            val name = waMsgMatch.groupValues[1].trim()
            val msg = waMsgMatch.groupValues[3].trim()
            return if (msg.isNotBlank()) "whatsapp $name saying $msg" else "whatsapp $name"
        }

        // Time
        if (text.contains("time kya hua") || text.contains("kitne baje") ||
            text.contains("samay kya hai") || text.contains("kya time ho raha") ||
            text.contains("time batao") || text.contains("samay batao") || text == "time") {
            return "what time is it"
        }

        // Camera
        if (text.contains("camera kholo") || text.contains("photo khincho") ||
            text.contains("camera on karo") || text.contains("tasveer lo")) {
            return "open camera"
        }

        // Calling: "Rahul ko call karo" -> "call rahul"
        val callMatch = Regex("([a-zA-Z0-9]+)\\s+ko\\s+(call|phone|dial)\\s*(karo|lagao)?").find(text)
        if (callMatch != null) {
            val name = callMatch.groupValues[1]
            return "call $name"
        }

        // Timer: "10 minute ka timer laga do" / "5 minute timer set karo"
        val timerMatch = Regex("(\\d+)\\s*(minute|min|second|sec)?\\s*(?:ka\\s+)?timer\\s*(?:laga|set)", RegexOption.IGNORE_CASE).find(text)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1]
            val unit = if (timerMatch.groupValues[2].startsWith("sec")) "seconds" else "minutes"
            return "set a timer for $amount $unit"
        }

        // Alarms: "7 baje alarm laga do" / "7 baje ka alarm laga do"
        val alarmMatch = Regex("(\\d+)\\s*(baje|am|pm)?(?:\\s*ka)?\\s*alarm\\s*(laga|set)", RegexOption.IGNORE_CASE).find(text)
        if (alarmMatch != null) {
            val hour = alarmMatch.groupValues[1]
            val isPm = text.contains("shaam") || text.contains("raat") || text.contains("pm")
            val period = if (isPm) "PM" else "AM"
            return "set an alarm for $hour $period"
        }

        // Notifications: "Meri notifications dikhao", "notifications dikhao"
        if (text.contains("notification") && (text.contains("dikhao") || text.contains("padho") || text.contains("batao") || text.contains("show"))) {
            return "read my notifications"
        }

        // App opening: "WhatsApp kholo", "YouTube open karo", "Chrome chalu karo", "Instagram kholo"
        val appMatch = Regex("([a-zA-Z0-9]+)\\s+(kholo|open karo|chalu karo|start karo)").find(text)
        if (appMatch != null) {
            val app = appMatch.groupValues[1]
            return "open $app"
        }

        // Weather: "Delhi me mausam kaisa hai", "weather kaisa hai"
        val weatherMatch = Regex("([a-zA-Z]+)\\s+(me|mein|ka)?\\s*(mausam|weather)").find(text)
        if (weatherMatch != null) {
            val city = weatherMatch.groupValues[1]
            if (city != "aaj" && city != "abhi") {
                return "weather in $city"
            }
        }
        if (text.contains("mausam kaisa hai") || text.contains("barish hogi kya") || text.contains("weather kaisa hai")) {
            return "what is the weather"
        }

        // Settings: "wifi settings kholo" -> "open wifi settings"
        if (text.contains("wifi") && (text.contains("kholo") || text.contains("settings"))) {
            return "open wifi settings"
        }
        if (text.contains("bluetooth") && (text.contains("kholo") || text.contains("settings"))) {
            return "open bluetooth settings"
        }

        // Notifications
        if (text.contains("notifications padho") || text.contains("notification padho") ||
            text.contains("message dikhao") || text.contains("kya message aaya")) {
            return "read my notifications"
        }

        // Memory: "yaad rakho ki..."
        val rememberMatch = Regex("yaad\\s+rakho?\\s+(ki)?\\s*(.*)").find(text)
        if (rememberMatch != null) {
            val fact = rememberMatch.groupValues[2]
            return "remember $fact"
        }

        return text
    }
}
