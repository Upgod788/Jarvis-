package com.example.tools

import android.content.Context

interface Tool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>
    val requiredPermissions: List<String>
    val riskLevel: RiskLevel
    val requiresConfirmation: Boolean

    suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult
    suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}

data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean"
    val description: String,
    val required: Boolean = true
)
