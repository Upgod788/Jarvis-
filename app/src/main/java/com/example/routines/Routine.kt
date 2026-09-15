package com.example.routines

import android.content.Context
import android.content.SharedPreferences
import com.example.agent.ToolRegistry
import com.example.database.AutomationHistoryEntity
import com.example.database.JarvisDatabase
import com.example.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RoutineAction(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val actionName: String = "",
    val description: String = "",
    val parameters: Map<String, Any?> = emptyMap(),
    val delayMs: Long = 0L
)

data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggerPhrase: String,
    val description: String,
    val isEnabled: Boolean = true,
    val actions: List<RoutineAction> = emptyList()
)

class RoutineManager(
    private val context: Context,
    private val toolRegistryProvider: () -> ToolRegistry?
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_routines", Context.MODE_PRIVATE)

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    init {
        loadRoutines()
    }

    private fun loadRoutines() {
        val raw = prefs.getString("routines_json", null)
        if (raw.isNullOrBlank()) {
            val defaults = getDefaultRoutines()
            _routines.value = defaults
            saveRoutines(defaults)
        } else {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<Routine>()
                for (i in 0 until array.length()) {
                    list.add(parseRoutine(array.getJSONObject(i)))
                }
                _routines.value = list
            } catch (e: Exception) {
                _routines.value = getDefaultRoutines()
            }
        }
    }

    private fun saveRoutines(list: List<Routine>) {
        try {
            val array = JSONArray()
            for (r in list) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("triggerPhrase", r.triggerPhrase)
                    put("description", r.description)
                    put("isEnabled", r.isEnabled)
                    val actArray = JSONArray()
                    for (a in r.actions) {
                        val aObj = JSONObject().apply {
                            put("id", a.id)
                            put("toolName", a.toolName)
                            put("actionName", a.actionName)
                            put("description", a.description)
                            put("delayMs", a.delayMs)
                            val pObj = JSONObject()
                            for ((k, v) in a.parameters) {
                                pObj.put(k, v)
                            }
                            put("parameters", pObj)
                        }
                        actArray.put(aObj)
                    }
                    put("actions", actArray)
                }
                array.put(obj)
            }
            prefs.edit().putString("routines_json", array.toString()).apply()
        } catch (e: Exception) {
            // Log error
        }
    }

    private fun parseRoutine(obj: JSONObject): Routine {
        val id = obj.getString("id")
        val name = obj.getString("name")
        val trigger = obj.getString("triggerPhrase")
        val desc = obj.optString("description", "")
        val enabled = obj.optBoolean("isEnabled", true)
        val actions = mutableListOf<RoutineAction>()
        val actArray = obj.optJSONArray("actions")
        if (actArray != null) {
            for (i in 0 until actArray.length()) {
                val aObj = actArray.getJSONObject(i)
                val params = mutableMapOf<String, Any?>()
                val pObj = aObj.optJSONObject("parameters")
                if (pObj != null) {
                    val keys = pObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        params[k] = pObj.opt(k)
                    }
                }
                actions.add(
                    RoutineAction(
                        id = aObj.optString("id", UUID.randomUUID().toString()),
                        toolName = aObj.getString("toolName"),
                        actionName = aObj.optString("actionName", ""),
                        description = aObj.optString("description", ""),
                        parameters = params,
                        delayMs = aObj.optLong("delayMs", 0L)
                    )
                )
            }
        }
        return Routine(id, name, trigger, desc, enabled, actions)
    }

    private fun getDefaultRoutines(): List<Routine> {
        return listOf(
            Routine(
                id = "routine_good_morning",
                name = "Good Morning Routine",
                triggerPhrase = "good morning",
                description = "Greets you, checks time and battery, and gives weather overview.",
                isEnabled = true,
                actions = listOf(
                    RoutineAction(
                        toolName = "CurrentTimeTool",
                        description = "Report current time"
                    ),
                    RoutineAction(
                        toolName = "BatteryTool",
                        description = "Check battery status",
                        delayMs = 500
                    ),
                    RoutineAction(
                        toolName = "WeatherTool",
                        description = "Get weather update",
                        delayMs = 500
                    )
                )
            ),
            Routine(
                id = "routine_system_diagnostics",
                name = "System Check & Updates",
                triggerPhrase = "run system check",
                description = "Runs system diagnostics and checks for application updates.",
                isEnabled = true,
                actions = listOf(
                    RoutineAction(
                        toolName = "DeviceInfoTool",
                        description = "Query device status"
                    ),
                    RoutineAction(
                        toolName = "BatteryTool",
                        description = "Query battery health",
                        delayMs = 300
                    ),
                    RoutineAction(
                        toolName = "app_update",
                        actionName = "check",
                        parameters = mapOf("action" to "check"),
                        description = "Check for JARVIS updates",
                        delayMs = 500
                    )
                )
            ),
            Routine(
                id = "routine_bedtime",
                name = "Bedtime Routine",
                triggerPhrase = "good night",
                description = "Checks battery level and prepares system for night.",
                isEnabled = true,
                actions = listOf(
                    RoutineAction(
                        toolName = "BatteryTool",
                        description = "Check battery before sleep"
                    )
                )
            )
        )
    }

    fun saveRoutine(routine: Routine) {
        val current = _routines.value.toMutableList()
        val index = current.indexOfFirst { it.id == routine.id }
        if (index >= 0) {
            current[index] = routine
        } else {
            current.add(routine)
        }
        _routines.value = current
        saveRoutines(current)
    }

    fun toggleRoutine(id: String) {
        val current = _routines.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        _routines.value = current
        saveRoutines(current)
    }

    fun deleteRoutine(id: String) {
        val current = _routines.value.filter { it.id != id }
        _routines.value = current
        saveRoutines(current)
    }

    suspend fun executeRoutine(routineIdOrTrigger: String): ToolResult {
        val routine = _routines.value.firstOrNull {
            it.id.equals(routineIdOrTrigger, ignoreCase = true) ||
                    it.triggerPhrase.equals(routineIdOrTrigger, ignoreCase = true) ||
                    it.name.equals(routineIdOrTrigger, ignoreCase = true)
        } ?: return ToolResult.error("Routine not found for '$routineIdOrTrigger'")

        if (!routine.isEnabled) {
            return ToolResult.error("Routine '${routine.name}' is currently disabled.")
        }

        val registry = toolRegistryProvider()
            ?: return ToolResult.error("Tool registry not initialized")

        val results = mutableListOf<String>()
        var hasFailures = false

        for (action in routine.actions) {
            if (action.delayMs > 0) {
                delay(action.delayMs)
            }
            val tool = registry.getTool(action.toolName)
            if (tool != null) {
                val res = tool.execute(context, action.parameters)
                results.add("${tool.name}: ${res.message}")
                if (!res.success) hasFailures = true
            } else {
                results.add("Tool '${action.toolName}' not found")
                hasFailures = true
            }
        }

        // Record automation history
        try {
            JarvisDatabase.getInstance(context).automationHistoryDao().insert(
                AutomationHistoryEntity(
                    command = "Routine: ${routine.name}",
                    tool = "RoutineManager",
                    status = if (hasFailures) "Partial" else "Success",
                    result = results.joinToString("\n"),
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            // Ignore
        }

        val summary = "Executed routine '${routine.name}':\n" + results.joinToString("\n")
        return if (hasFailures) ToolResult.partial(summary) else ToolResult.ok(summary)
    }
}
