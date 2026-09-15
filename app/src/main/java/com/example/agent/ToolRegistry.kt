package com.example.agent

import com.example.devices.DeviceManager
import com.example.memory.MemoryRepository
import com.example.routines.RoutineManager
import com.example.tools.AlarmTool
import com.example.tools.AppAutomationTool
import com.example.tools.AppUpdateTool
import com.example.tools.BatteryTool
import com.example.tools.BluetoothTool
import com.example.tools.BrowserTool
import com.example.tools.CalendarTool
import com.example.tools.CallContactTool
import com.example.tools.CameraTool
import com.example.tools.CurrentTimeTool
import com.example.tools.DeviceInfoTool
import com.example.tools.FileTool
import com.example.tools.FlashlightTool
import com.example.tools.InstagramTool
import com.example.tools.MediaControlTool
import com.example.tools.MemoryTool
import com.example.tools.NavigationTool
import com.example.tools.NotificationTool
import com.example.tools.OpenAppTool
import com.example.tools.OpenUrlTool
import com.example.tools.PcControlTool
import com.example.tools.RoutineTool
import com.example.tools.SendSmsTool
import com.example.tools.SettingsTool
import com.example.tools.SmartDeviceTool
import com.example.tools.TimerTool
import com.example.tools.Tool
import com.example.tools.WeatherTool
import com.example.tools.WebSearchTool
import com.example.tools.WhatsAppTool
import com.example.tools.WifiTool
import com.example.tools.YouTubeTool
import com.example.update.UpdateManager
import java.util.Locale

class ToolRegistry(
    memoryRepository: MemoryRepository,
    deviceManager: DeviceManager? = null,
    routineManager: RoutineManager? = null,
    updateManagerProvider: (() -> UpdateManager)? = null
) {
    private val tools = LinkedHashMap<String, Tool>()

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
        register(WifiTool())
        register(BluetoothTool())
        register(BatteryTool())
        register(DeviceInfoTool())
        register(CurrentTimeTool())
        register(WeatherTool())
        register(NavigationTool())
        register(YouTubeTool())
        register(InstagramTool())
        register(WhatsAppTool())
        register(BrowserTool())
        register(MediaControlTool())
        register(SettingsTool())
        register(NotificationTool())
        register(CalendarTool())
        register(FileTool())
        register(MemoryTool(memoryRepository))

        if (deviceManager != null) {
            register(SmartDeviceTool(deviceManager))
            register(PcControlTool(deviceManager))
        }

        if (routineManager != null) {
            register(RoutineTool(routineManager))
        }

        register(AppAutomationTool())

        if (updateManagerProvider != null) {
            register(AppUpdateTool(updateManagerProvider))
        }
    }

    fun register(tool: Tool) {
        tools[tool.name.lowercase(Locale.ROOT)] = tool
    }

    fun getTool(name: String): Tool? {
        return tools[name.lowercase(Locale.ROOT)]
    }

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getRegisteredToolNames(): Set<String> = tools.keys.toSet()
}
