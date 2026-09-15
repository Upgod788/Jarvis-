package com.example.tools

import android.content.Context

enum class ActionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    REQUIRES_USER_ACTION,
    PERMISSION_REQUIRED,
    DEVICE_OFFLINE
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val defaultValue: Any? = null
)

data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val errorCode: String? = null,
    val status: ActionStatus = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED
) {
    companion object {
        fun ok(message: String, data: Map<String, Any?> = emptyMap()): ToolResult =
            ToolResult(true, message, data, null, ActionStatus.SUCCESS)

        fun partial(message: String, data: Map<String, Any?> = emptyMap()): ToolResult =
            ToolResult(true, message, data, null, ActionStatus.PARTIAL_SUCCESS)

        fun requiresUserAction(message: String, data: Map<String, Any?> = emptyMap()): ToolResult =
            ToolResult(false, message, data, null, ActionStatus.REQUIRES_USER_ACTION)

        fun permissionRequired(message: String, data: Map<String, Any?> = emptyMap()): ToolResult =
            ToolResult(false, message, data, null, ActionStatus.PERMISSION_REQUIRED)

        fun deviceOffline(message: String, data: Map<String, Any?> = emptyMap()): ToolResult =
            ToolResult(false, message, data, null, ActionStatus.DEVICE_OFFLINE)

        fun error(message: String, errorCode: String? = null, data: Map<String, Any?> = emptyMap()): ToolResult =
            ToolResult(false, message, data, errorCode, ActionStatus.FAILED)
    }
}

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
