package com.example.tools

/**
 * Standardized status for all tool executions conforming to JARVIS specifications:
 * SUCCESS, PARTIAL_SUCCESS, FAILED, REQUIRES_USER_ACTION, PERMISSION_REQUIRED, DEVICE_OFFLINE.
 */
enum class ActionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    REQUIRES_USER_ACTION,
    PERMISSION_REQUIRED,
    DEVICE_OFFLINE
}

data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val errorCode: String? = null,
    val status: ActionStatus = if (success) ActionStatus.SUCCESS else ActionStatus.FAILED
) {
    companion object {
        fun ok(message: String, data: Map<String, Any?> = emptyMap()) =
            ToolResult(success = true, message = message, data = data, status = ActionStatus.SUCCESS)

        fun partial(message: String, data: Map<String, Any?> = emptyMap()) =
            ToolResult(success = true, message = message, data = data, status = ActionStatus.PARTIAL_SUCCESS)

        fun requiresUserAction(message: String, data: Map<String, Any?> = emptyMap()) =
            ToolResult(success = false, message = message, data = data, status = ActionStatus.REQUIRES_USER_ACTION)

        fun permissionRequired(message: String, data: Map<String, Any?> = emptyMap()) =
            ToolResult(success = false, message = message, data = data, status = ActionStatus.PERMISSION_REQUIRED)

        fun deviceOffline(message: String, data: Map<String, Any?> = emptyMap()) =
            ToolResult(success = false, message = message, data = data, status = ActionStatus.DEVICE_OFFLINE)

        fun error(message: String, errorCode: String? = null, data: Map<String, Any?> = emptyMap()) =
            ToolResult(success = false, message = message, errorCode = errorCode, data = data, status = ActionStatus.FAILED)
    }
}

