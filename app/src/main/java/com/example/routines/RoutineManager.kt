package com.example.routines

import android.content.Context
import android.content.SharedPreferences
import com.example.agent.ToolRegistry
import com.example.tools.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RoutineManager(
    private val context: Context,
    private val toolRegistryProvider: () -> ToolRegistry?
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_routines_prefs", Context.MODE_PRIVATE)

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    init {
        loadRoutines()
    }

    private fun loadRoutines() {
        val jsonString = prefs.getString("saved_routines", null)
        if (jsonString != null) {
            try {
                val list = mutableListOf<Routine>()
                val arr = JSONArray(jsonString)
                for (i in 0 until arr.length()) {
                    list.add(parseRoutine(arr.getJSONObject(i)))
                }
                _routines.value = list
                return
            } catch (e: Exception) {
                // fallback
            }
        }

        // Initialize default master routines
        val defaults = listOf(
            Routine(
                id = "routine_movie_mode",
                name = "Movie Mode",
                triggerPhrase = "movie mode",
                description = "Dims living room lights to 20%, turns on Smart TV, and opens YouTube.",
                isEnabled = true,
                actions = listOf(
                    RoutineAction(
                        id = "a1",
                        toolName = "SmartDeviceTool",
                        actionName = "brightness",
                        description = "Set Living Room Light to 20%",
                        parameters = mapOf("deviceName" to "Living Room Light", "action" to "brightness", "state" to "20")
                    ),
                    RoutineAction(
                        id = "a2",
                        toolName = "SmartDeviceTool",
                        actionName = "power",
                        description = "Turn on Living Room TV",
                        parameters = mapOf("deviceName" to "Living Room TV", "action" to "power", "state" to "on")
                    ),
                    RoutineAction(
                        id = "a3",
                        toolName = "OpenAppTool",
                        actionName = "launch",
                        description = "Launch YouTube",
                        parameters = mapOf("appName" to "YouTube")
                    )
                )
            ),
            Routine(
                id = "routine_good_night",
                name = "Good Night",
                triggerPhrase = "good night",
                description = "Turns off all lights, locks paired PC, and sets a wake-up alarm.",
                isEnabled = true,
                actions = listOf(
                    RoutineAction(
                        id = "b1",
                        toolName = "SmartDeviceTool",
                        actionName = "power",
                        description = "Turn off Bedroom Light",
                        parameters = mapOf("deviceName" to "Bedroom Light", "action" to "power", "state" to "off")
                    ),
                    RoutineAction(
                        id = "b2",
                        toolName = "SmartDeviceTool",
                        actionName = "power",
                        description = "Turn off Living Room Light",
                        parameters = mapOf("deviceName" to "Living Room Light", "action" to "power", "state" to "off")
                    ),
                    RoutineAction(
                        id = "b3",
                        toolName = "PcControlTool",
                        actionName = "lock",
                        description = "Lock Work PC",
                        parameters = mapOf("action" to "lock")
                    ),
                    RoutineAction(
                        id = "b4",
                        toolName = "AlarmTool",
                        actionName = "set_alarm",
                        description = "Set morning alarm for 7:00 AM",
                        parameters = mapOf("hour" to 7, "minute" to 0, "message" to "Good morning")
                    )
                )
            ),
            Routine(
                id = "routine_work_focus",
                name = "Work Focus",
                triggerPhrase = "work focus",
                description = "Turns on office lights and launches Chrome on paired PC.",
                isEnabled = true,
                actions = listOf(
                    RoutineAction(
                        id = "c1",
                        toolName = "SmartDeviceTool",
                        actionName = "power",
                        description = "Turn on Bedroom Light to 100%",
                        parameters = mapOf("deviceName" to "Bedroom Light", "action" to "brightness", "state" to "100")
                    ),
                    RoutineAction(
                        id = "c2",
                        toolName = "PcControlTool",
                        actionName = "open_app",
                        description = "Open Chrome on PC",
                        parameters = mapOf("action" to "open_app", "appName" to "Chrome")
                    )
                )
            )
        )
        _routines.value = defaults
        saveRoutines(defaults)
    }

    suspend fun executeRoutine(routineIdOrTrigger: String): ToolResult {
        val routine = _routines.value.firstOrNull {
            it.id == routineIdOrTrigger ||
            it.name.equals(routineIdOrTrigger, ignoreCase = true) ||
            it.triggerPhrase.equals(routineIdOrTrigger, ignoreCase = true) ||
            routineIdOrTrigger.contains(it.triggerPhrase, ignoreCase = true)
        } ?: return ToolResult.error("Routine \"$routineIdOrTrigger\" not found.")

        if (!routine.isEnabled) {
            return ToolResult.error("Routine \"${routine.name}\" is currently disabled.")
        }

        val registry = toolRegistryProvider()
            ?: return ToolResult.error("Tool registry is unavailable.")

        val results = mutableListOf<String>()
        for (action in routine.actions) {
            val tool = registry.getTool(action.toolName)
            if (tool != null) {
                try {
                    val res = tool.execute(context, action.parameters)
                    results.add("${action.description}: ${res.message}")
                } catch (e: Exception) {
                    results.add("${action.description}: Failed (${e.message})")
                }
            } else {
                results.add("${action.description}: Tool ${action.toolName} not registered")
            }
            if (action.delayMs > 0) delay(action.delayMs)
        }

        return ToolResult.ok(
            message = "Routine \"${routine.name}\" completed.",
            data = mapOf("summary" to results.joinToString("\n"))
        )
    }

    fun toggleRoutine(id: String) {
        val current = _routines.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        _routines.value = current
        saveRoutines(current)
    }

    fun saveRoutine(routine: Routine) {
        val current = _routines.value.toMutableList()
        current.removeAll { it.id == routine.id }
        current.add(routine)
        _routines.value = current
        saveRoutines(current)
    }

    fun deleteRoutine(id: String) {
        val current = _routines.value.filter { it.id != id }
        _routines.value = current
        saveRoutines(current)
    }

    private fun saveRoutines(list: List<Routine>) {
        try {
            val arr = JSONArray()
            for (r in list) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("triggerPhrase", r.triggerPhrase)
                    put("description", r.description)
                    put("isEnabled", r.isEnabled)
                    val actionsArr = JSONArray()
                    for (a in r.actions) {
                        val aObj = JSONObject().apply {
                            put("id", a.id)
                            put("toolName", a.toolName)
                            put("actionName", a.actionName)
                            put("description", a.description)
                            put("delayMs", a.delayMs)
                            val pObj = JSONObject()
                            a.parameters.forEach { (k, v) -> pObj.put(k, v) }
                            put("parameters", pObj)
                        }
                        actionsArr.put(aObj)
                    }
                    put("actions", actionsArr)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_routines", arr.toString()).apply()
        } catch (e: Exception) {}
    }

    private fun parseRoutine(obj: JSONObject): Routine {
        val id = obj.getString("id")
        val name = obj.getString("name")
        val triggerPhrase = obj.getString("triggerPhrase")
        val description = obj.optString("description", "")
        val isEnabled = obj.optBoolean("isEnabled", true)

        val actionsList = mutableListOf<RoutineAction>()
        val actionsArr = obj.optJSONArray("actions")
        if (actionsArr != null) {
            for (i in 0 until actionsArr.length()) {
                val aObj = actionsArr.getJSONObject(i)
                val paramsMap = mutableMapOf<String, Any?>()
                val pObj = aObj.optJSONObject("parameters")
                if (pObj != null) {
                    val keys = pObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        paramsMap[k] = pObj.get(k)
                    }
                }
                actionsList.add(
                    RoutineAction(
                        id = aObj.optString("id", UUID.randomUUID().toString()),
                        toolName = aObj.getString("toolName"),
                        actionName = aObj.optString("actionName", ""),
                        description = aObj.optString("description", ""),
                        parameters = paramsMap,
                        delayMs = aObj.optLong("delayMs", 300L)
                    )
                )
            }
        }

        return Routine(
            id = id,
            name = name,
            triggerPhrase = triggerPhrase,
            description = description,
            isEnabled = isEnabled,
            actions = actionsList
        )
    }
}
