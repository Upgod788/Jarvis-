package com.example.agent

import com.example.memory.MemoryRepository
import com.example.tools.*

class ToolRegistry(memoryRepository: MemoryRepository) {

    private val tools = mutableMapOf<String, Tool>()

    init {
        register(OpenAppTool())
        register(OpenUrlTool())
        register(WebSearchTool())
        register(CallContactTool())
        register(SendSmsTool())
        register(AlarmTool())
        register(TimerTool())
        register(CameraTool())
        register(FlashlightTool())
        register(BatteryTool())
        register(DeviceInfoTool())
        register(CurrentTimeTool())
        register(WeatherTool())
        register(SettingsTool())
        register(WifiTool())
        register(BluetoothTool())
        register(NotificationTool())
        register(MemoryTool(memoryRepository))
        register(WhatsAppTool())
        register(InstagramTool())
        register(YouTubeTool())
        register(BrowserTool())
        register(AppAutomationTool())
    }

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): Tool? {
        val direct = tools[name]
        if (direct != null) return direct

        // Flexible name lookup for snake_case or lowercase or alias mappings
        val clean = name.replace("_", "").lowercase()
        return tools.entries.firstOrNull {
            it.key.replace("_", "").lowercase() == clean ||
            it.key.lowercase().contains(clean) ||
            clean.contains(it.key.lowercase())
        }?.value
    }

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getRegisteredToolNames(): Set<String> = tools.keys
}
