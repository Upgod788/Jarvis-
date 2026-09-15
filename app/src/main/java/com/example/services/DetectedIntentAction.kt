package com.example.services

import com.example.tools.ToolResult

data class DetectedIntentAction(
    val actionType: IntentActionType,
    val parameters: Map<String, Any?> = emptyMap(),
    val rawKeyword: String,
    val spokenFeedback: String,
    val confidence: Float = 1.0f
)

data class CommandExecutionResult(
    val success: Boolean,
    val action: DetectedIntentAction,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val toolResult: ToolResult? = null
)
