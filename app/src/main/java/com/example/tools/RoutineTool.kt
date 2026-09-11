package com.example.tools

import android.content.Context
import com.example.routines.RoutineManager

class RoutineTool(private val routineManager: RoutineManager) : Tool {
    override val name = "RoutineTool"
    override val description = "Executes multi-device automated routines (such as Movie Mode, Good Night, or custom user routines)."
    override val parameters = listOf(
        ToolParameter("routineName", "string", "Name or trigger phrase of the routine (e.g. 'movie mode', 'good night')", required = true)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val routineName = params["routineName"]?.toString()?.trim() ?: "movie mode"
        return routineManager.executeRoutine(routineName)
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
