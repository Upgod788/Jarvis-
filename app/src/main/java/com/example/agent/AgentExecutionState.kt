package com.example.agent

import com.example.tools.ToolResult

sealed interface AgentExecutionState {
    object Idle : AgentExecutionState
    object Listening : AgentExecutionState
    object Thinking : AgentExecutionState
    data class AwaitingConfirmation(val request: ConfirmationRequest) : AgentExecutionState
    data class Executing(val toolName: String) : AgentExecutionState
    data class Speaking(
        val response: String,
        val toolName: String? = null,
        val result: ToolResult? = null
    ) : AgentExecutionState
    data class Error(val message: String) : AgentExecutionState
}
