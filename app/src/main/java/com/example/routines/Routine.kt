package com.example.routines

data class RoutineAction(
    val id: String,
    val toolName: String,
    val actionName: String,
    val description: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val delayMs: Long = 300L
)

data class Routine(
    val id: String,
    val name: String,
    val triggerPhrase: String,
    val description: String,
    val isEnabled: Boolean = true,
    val actions: List<RoutineAction>
)
