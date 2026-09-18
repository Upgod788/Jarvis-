package com.example.ai

class LocalRuleAIProvider : AIProvider {
    override val providerName: String = "Local Engine (Offline/Rule-Based)"

    override suspend fun processCommand(request: AIRequest): AIResponse {
        val p = request.prompt.trim().lowercase()

        // Update commands
        if (p.contains("check update") || p.contains("check for update") || p.contains("update app") ||
            p.contains("update ravan") || p.contains("update jarvis") || p.contains("system update") || p.contains("check for updates")) {
            return AIResponse("Checking for Ravan updates.", ToolInvocation("AppUpdateTool", mapOf("action" to "check")))
        }
        if (p.contains("download update") || p.contains("download the update") || p.contains("install update")) {
            return AIResponse("Processing update.", ToolInvocation("AppUpdateTool", mapOf("action" to "download")))
        }
        if (p.contains("what's new") || p.contains("whats new") || p.contains("changelog") || p.contains("update history")) {
            return AIResponse("Here are the latest update details.", ToolInvocation("AppUpdateTool", mapOf("action" to "changelog")))
        }

        // Flashlight
        if (p.contains("flashlight on") || p.contains("turn on flashlight") || p.contains("torch on")) {
            return AIResponse("Turning on flashlight.", ToolInvocation("FlashlightTool", mapOf("enabled" to true)))
        }
        if (p.contains("flashlight off") || p.contains("turn off flashlight") || p.contains("torch off")) {
            return AIResponse("Turning off flashlight.", ToolInvocation("FlashlightTool", mapOf("enabled" to false)))
        }

        // Wi-Fi
        if (p.contains("turn on wifi") || p.contains("enable wifi") || p.contains("wifi on")) {
            return AIResponse("Enabling Wi-Fi.", ToolInvocation("WifiTool", mapOf("action" to "enable")))
        }
        if (p.contains("turn off wifi") || p.contains("disable wifi") || p.contains("wifi off")) {
            return AIResponse("Disabling Wi-Fi.", ToolInvocation("WifiTool", mapOf("action" to "disable")))
        }
        if (p.contains("wifi")) {
            return AIResponse("Opening Wi-Fi settings.", ToolInvocation("WifiTool", mapOf("action" to "settings")))
        }

        // Bluetooth
        if (p.contains("bluetooth")) {
            return AIResponse("Opening Bluetooth settings.", ToolInvocation("BluetoothTool", mapOf("action" to "settings")))
        }

        // Camera
        if (p.contains("camera") || p.contains("take photo") || p.contains("take picture")) {
            return AIResponse("Opening Camera.", ToolInvocation("CameraTool", mapOf("mode" to "photo")))
        }

        // Time / Battery / Device
        if (p.contains("time") || p.contains("what time")) {
            return AIResponse("Checking current time.", ToolInvocation("CurrentTimeTool", emptyMap()))
        }
        if (p.contains("battery")) {
            return AIResponse("Checking battery status.", ToolInvocation("BatteryTool", emptyMap()))
        }
        if (p.contains("device") || p.contains("specs") || p.contains("model")) {
            return AIResponse("Checking device details.", ToolInvocation("DeviceInfoTool", emptyMap()))
        }

        // Common Apps
        if (p.contains("whatsapp")) {
            return AIResponse("Opening WhatsApp.", ToolInvocation("WhatsAppTool", mapOf("action" to "open")))
        }
        if (p.contains("instagram")) {
            return AIResponse("Opening Instagram.", ToolInvocation("InstagramTool", mapOf("action" to "open")))
        }
        if (p.contains("youtube")) {
            return AIResponse("Opening YouTube.", ToolInvocation("YouTubeTool", mapOf("action" to "open")))
        }
        if (p.contains("chrome") || p.contains("browser")) {
            return AIResponse("Opening Browser.", ToolInvocation("BrowserTool", mapOf("action" to "open")))
        }

        // Weather
        if (p.contains("weather")) {
            return AIResponse("Checking weather forecast.", ToolInvocation("WeatherTool", mapOf("location" to "current")))
        }

        // Generic / Fallback response
        return AIResponse("I heard: \"${request.prompt}\". How can I assist you further, sir?")
    }
}
