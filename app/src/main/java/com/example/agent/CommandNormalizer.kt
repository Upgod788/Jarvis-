package com.example.agent

object CommandNormalizer {

    fun normalize(input: String): String {
        var text = input.trim().lowercase()
        // Strip trailing punctuation
        text = text.replace(Regex("[.?!,;]+$"), "").trim()

        // Common Hinglish / Hindi wake words and conversational filler cleanup
        text = text.replace(Regex("^(hey jarvis|jarvis|bhai jarvis|suno jarvis)[,\\s]*"), "").trim()

        // Normalize wi-fi
        text = text.replace("wi-fi", "wifi")

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

        // YouTube search / play: "YouTube par Android tutorial search karo" or "YouTube par lo-fi songs chalao"
        val ytSearchMatch = Regex("youtube\\s+(?:par|pe|kholo\\s+aur)?\\s*(.+?)\\s+(?:search\\s*karo|chalao|play\\s*karo|play|baja\\s*do|start\\s*karo)", RegexOption.IGNORE_CASE).find(text)
        if (ytSearchMatch != null) {
            val q = ytSearchMatch.groupValues[1].trim()
            return "search youtube for $q"
        }

        // Google / Web / Chrome search: "Chrome kholo aur latest tech news search karo"
        val googleSearchMatch = Regex("(?:google|chrome|web)\\s+(?:par|pe|kholo\\s+aur)?\\s*(.+?)\\s+search\\s*karo", RegexOption.IGNORE_CASE).find(text)
        if (googleSearchMatch != null) {
            val q = googleSearchMatch.groupValues[1].trim()
            return "search google for $q"
        }

        // SMS / Text messages: "Mummy ko SMS bhejo: main ghar aa raha hoon"
        val smsMatch = Regex("([a-zA-Z0-9]+)\\s+ko\\s+sms\\s*(?:bhejo|karo|send\\s*karo)?\\s*[:\\-]?\\s*(.*)", RegexOption.IGNORE_CASE).find(text)
        if (smsMatch != null) {
            val name = smsMatch.groupValues[1].trim()
            val msg = smsMatch.groupValues[2].trim()
            return if (msg.isNotBlank()) "send sms to $name $msg" else "send sms to $name"
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

        // Routines: "movie mode chalao", "good night jarvis"
        if (text.contains("movie mode")) {
            return "activate movie mode"
        }
        if (text.contains("good night") || text.contains("shubh ratri")) {
            return "activate good night"
        }
        if (text.contains("work focus")) {
            return "activate work focus"
        }

        // PC Control: "pc lock karo", "open chrome on my pc", "pc battery"
        if (text.contains("pc lock") || text.contains("laptop lock") || text.contains("lock my pc")) {
            return "lock pc"
        }
        if (text.contains("pc battery") || text.contains("laptop battery")) {
            return "show pc battery"
        }
        if (text.contains("on my pc") || text.contains("pc par") || text.contains("pc pe")) {
            if (text.contains("chrome")) return "open chrome on pc"
            if (text.contains("music") || text.contains("gaana")) return "play music on pc"
        }

        // Smart Home Devices: "bedroom light on karo", "tv on karo", "show my devices"
        if (text.contains("what devices") || text.contains("show my devices") || text.contains("devices dikhao") || text.contains("connected devices")) {
            return "show connected devices"
        }
        val lightMatch = Regex("(bedroom|living room|hall)?\\s*(light|bulb|lamp)\\s*(on|off|chalu|band|jalao|bujhao)", RegexOption.IGNORE_CASE).find(text)
        if (lightMatch != null) {
            val room = if (lightMatch.groupValues[1].isBlank()) "bedroom" else lightMatch.groupValues[1].trim()
            val state = if (lightMatch.groupValues[3] in listOf("off", "band", "bujhao")) "off" else "on"
            return "turn $state $room light"
        }
        val lightDimMatch = Regex("(bedroom|living room)?\\s*(light)\\s*(\\d+)\\s*(?:percent|%)?", RegexOption.IGNORE_CASE).find(text)
        if (lightDimMatch != null) {
            val room = if (lightDimMatch.groupValues[1].isBlank()) "living room" else lightDimMatch.groupValues[1].trim()
            val percent = lightDimMatch.groupValues[3]
            return "set $room light to $percent percent"
        }
        if (text.contains("tv") && (text.contains("on") || text.contains("off") || text.contains("chalu") || text.contains("band"))) {
            val state = if (text.contains("off") || text.contains("band")) "off" else "on"
            return "turn $state tv"
        }

        // Media Controls: "music play karo", "pause", "next song", "volume badhao"
        if (text.contains("music play") || text.contains("gaana chalao") || text.contains("gaana bajao") || text == "play music") {
            return "play music"
        }
        if (text == "pause" || text.contains("music pause") || text.contains("music roko") || text.contains("gaana roko")) {
            return "pause music"
        }
        if (text.contains("next song") || text.contains("agla gaana") || text.contains("next track")) {
            return "next track"
        }
        if (text.contains("previous song") || text.contains("pichhla gaana") || text.contains("prev track")) {
            return "previous track"
        }
        val volMatch = Regex("volume\\s*(\\d+)\\s*(?:percent|%)?", RegexOption.IGNORE_CASE).find(text)
        if (volMatch != null) {
            val lvl = volMatch.groupValues[1]
            return "set volume to $lvl percent"
        }
        if (text.contains("volume badhao") || text.contains("awaaz badhao") || text.contains("volume up")) {
            return "increase volume"
        }
        if (text.contains("volume kam karo") || text.contains("awaaz kam karo") || text.contains("volume down")) {
            return "decrease volume"
        }

        // Navigation: "Delhi jaana hai", "nearest petrol pump", "home ka route dikhao"
        if (text.contains("nearest petrol pump") || text.contains("petrol pump dikhao")) {
            return "navigate to nearest petrol pump"
        }
        if (text.contains("nearest hospital") || text.contains("hospital dikhao")) {
            return "navigate to nearest hospital"
        }
        if (text.contains("home ka route") || text.contains("ghar ka rasta")) {
            return "navigate to home"
        }
        val navMatch = Regex("([a-zA-Z]+)\\s*(?:jaana hai|ka rasta|ka route)", RegexOption.IGNORE_CASE).find(text)
        if (navMatch != null && navMatch.groupValues[1] != "ghar") {
            val dest = navMatch.groupValues[1].trim()
            return "navigate to $dest"
        }

        // Location: "Where am I?", "mera location"
        if (text.contains("where am i") || text.contains("meri location") || text.contains("kahan hoon")) {
            return "what is my location"
        }

        // Files: "Downloads folder kholo", "pdf files dikhao"
        if (text.contains("download") && (text.contains("folder") || text.contains("kholo"))) {
            return "open downloads folder"
        }
        if (text.contains("pdf") && (text.contains("file") || text.contains("dikhao"))) {
            return "view pdf files"
        }

        // Calendar: "Tomorrow 5 PM meeting add karo", "what's on my calendar"
        if (text.contains("calendar") && (text.contains("dikhao") || text.contains("what's on") || text.contains("agenda"))) {
            return "view calendar agenda"
        }
        if (text.contains("meeting") && (text.contains("add") || text.contains("laga") || text.contains("karo"))) {
            return "add meeting to calendar"
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

        // Timer: "10 minute ka timer laga do" / "5 minute timer set karo" / "10 minute ka timer start karo"
        val timerMatch = Regex("(\\d+)\\s*(minute|min|second|sec)?\\s*(?:ka\\s+)?timer\\s*(?:laga|set|start|chalu)", RegexOption.IGNORE_CASE).find(text)
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

        // Notifications: "Meri notifications dikhao", "notifications dikhao", "WhatsApp ki recent notifications dikhao"
        if (text.contains("notification") && (text.contains("dikhao") || text.contains("padho") || text.contains("batao") || text.contains("show"))) {
            return if (text.contains("whatsapp")) "read my whatsapp notifications" else "read my notifications"
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
