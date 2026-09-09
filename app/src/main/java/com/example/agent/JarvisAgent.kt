package com.example.agent

import android.content.Context
import com.example.ai.AIProvider
import com.example.ai.AIRequest
import com.example.ai.ToolInfo
import com.example.database.AutomationHistoryEntity
import com.example.database.JarvisDatabase
import com.example.history.ConversationRepository
import com.example.permissions.PermissionManager
import com.example.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AgentExecutionState {
    data object Idle : AgentExecutionState
    data object Listening : AgentExecutionState
    data class Thinking(val command: String) : AgentExecutionState
    data class AwaitingConfirmation(val request: ConfirmationRequest) : AgentExecutionState
    data class MissingPermission(val permissions: List<String>, val command: String) : AgentExecutionState
    data class Executing(val toolName: String) : AgentExecutionState
    data class Speaking(val response: String, val toolName: String? = null, val result: ToolResult? = null) : AgentExecutionState
    data class Error(val message: String) : AgentExecutionState
}

class JarvisAgent(
    private val context: Context,
    val toolRegistry: ToolRegistry,
    val confirmationManager: ConfirmationManager,
    var aiProvider: AIProvider,
    private val conversationRepository: ConversationRepository
) {

    suspend fun executeCommand(
        rawCommand: String,
        onStateChange: (AgentExecutionState) -> Unit,
        onFinished: (response: String, toolResult: ToolResult?) -> Unit
    ) = withContext(Dispatchers.Main) {
        val normalized = CommandNormalizer.normalize(rawCommand)
        if (normalized.isBlank()) {
            onStateChange(AgentExecutionState.Idle)
            return@withContext
        }

        onStateChange(AgentExecutionState.Thinking(rawCommand))

        // Build list of tools for AI model
        val toolInfos = toolRegistry.getAllTools().map {
            ToolInfo(
                name = it.name,
                description = it.description,
                parameters = it.parameters.map { p -> "${p.name} (${p.type})" }
            )
        }

        // Call AI Provider (Remote Gemini or Local Rule Fallback)
        val aiResponse = withContext(Dispatchers.IO) {
            aiProvider.processCommand(AIRequest(prompt = rawCommand, tools = toolInfos))
        }

        val invocation = aiResponse.toolInvocation

        // If no tool selected, map intent-based keywords from Gemini's response to actual system actions
        if (invocation == null) {
            val detectedAction = com.example.services.CommandHandlerService.parseIntent(aiResponse.textResponse, rawCommand)
            if (detectedAction != null && detectedAction.actionType != com.example.services.IntentActionType.NONE) {
                onStateChange(AgentExecutionState.Executing(detectedAction.actionType.name))
                val result = withContext(Dispatchers.IO) {
                    com.example.services.CommandHandlerService.executeAction(context, detectedAction, toolRegistry)
                }
                val cleanReply = com.example.services.CommandHandlerService.cleanResponseText(aiResponse.textResponse)
                val speechOutput = if (cleanReply.isNotBlank()) cleanReply else result.message

                conversationRepository.saveConversation(
                    command = rawCommand,
                    response = speechOutput,
                    tool = detectedAction.actionType.name,
                    result = if (result.success) "SUCCESS" else "FAILED: ${result.message}",
                    success = result.success
                )
                try {
                    JarvisDatabase.getInstance(context).automationHistoryDao().insert(
                        AutomationHistoryEntity(
                            command = rawCommand,
                            tool = detectedAction.actionType.name,
                            status = if (result.success) "SUCCESS" else "FAILED",
                            result = result.message,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}
                onStateChange(AgentExecutionState.Speaking(speechOutput, detectedAction.actionType.name, result.toolResult))
                onFinished(speechOutput, result.toolResult)
                return@withContext
            }

            val reply = com.example.services.CommandHandlerService.cleanResponseText(aiResponse.textResponse)
            conversationRepository.saveConversation(
                command = rawCommand,
                response = reply,
                tool = null,
                result = null,
                success = true
            )
            onStateChange(AgentExecutionState.Speaking(reply))
            onFinished(reply, null)
            return@withContext
        }

        // Tool execution flow
        val tool = toolRegistry.getTool(invocation.toolName)
        if (tool == null) {
            // Check if invocation toolName or command maps to an intent action via CommandHandlerService
            val detected = com.example.services.CommandHandlerService.parseIntent(invocation.toolName, rawCommand)
            if (detected != null && detected.actionType != com.example.services.IntentActionType.NONE) {
                onStateChange(AgentExecutionState.Executing(detected.actionType.name))
                val result = withContext(Dispatchers.IO) {
                    com.example.services.CommandHandlerService.executeAction(context, detected, toolRegistry)
                }
                val speechOutput = result.message
                conversationRepository.saveConversation(
                    command = rawCommand,
                    response = speechOutput,
                    tool = detected.actionType.name,
                    result = if (result.success) "SUCCESS" else "FAILED: ${result.message}",
                    success = result.success
                )
                onStateChange(AgentExecutionState.Speaking(speechOutput, detected.actionType.name, result.toolResult))
                onFinished(speechOutput, result.toolResult)
                return@withContext
            }

            val errorMsg = "The requested tool \"${invocation.toolName}\" is not registered in the safety registry."
            conversationRepository.saveConversation(
                command = rawCommand,
                response = errorMsg,
                tool = invocation.toolName,
                result = "TOOL_NOT_REGISTERED",
                success = false
            )
            onStateChange(AgentExecutionState.Error(errorMsg))
            onFinished(errorMsg, null)
            return@withContext
        }

        // Step 1: Check Permissions
        val missingPermissions = PermissionManager.getMissingPermissions(context, tool.requiredPermissions)
        if (missingPermissions.isNotEmpty()) {
            val labels = missingPermissions.joinToString(", ") { PermissionManager.getPermissionLabel(it) }
            val permMsg = "I need permission to access $labels to complete this action."
            onStateChange(AgentExecutionState.MissingPermission(missingPermissions, rawCommand))
            onFinished(permMsg, ToolResult.error(permMsg, errorCode = "PERMISSION_REQUIRED"))
            return@withContext
        }

        // Step 2: Check Confirmation requirements
        if (confirmationManager.requiresConfirmation(tool, invocation.arguments)) {
            val (title, msg) = confirmationManager.buildConfirmationDetails(tool, invocation.arguments)
            val request = ConfirmationRequest(
                toolName = tool.name,
                riskLevel = tool.riskLevel,
                title = title,
                message = msg,
                parameters = invocation.arguments,
                onConfirm = {
                    runTool(tool, invocation.arguments, rawCommand, onStateChange, onFinished)
                },
                onCancel = {
                    val cancelMsg = "Action cancelled."
                    onStateChange(AgentExecutionState.Speaking(cancelMsg, tool.name))
                    onFinished(cancelMsg, ToolResult.error("Cancelled by user", errorCode = "USER_CANCELLED"))
                }
            )
            onStateChange(AgentExecutionState.AwaitingConfirmation(request))
            return@withContext
        }

        // Step 3: Run Tool directly
        runTool(tool, invocation.arguments, rawCommand, onStateChange, onFinished)
    }

    private suspend fun runTool(
        tool: com.example.tools.Tool,
        params: Map<String, Any?>,
        rawCommand: String,
        onStateChange: (AgentExecutionState) -> Unit,
        onFinished: (response: String, toolResult: ToolResult?) -> Unit
    ) {
        onStateChange(AgentExecutionState.Executing(tool.name))

        val result = withContext(Dispatchers.IO) {
            tool.execute(context, params)
        }

        val speechText = result.message

        conversationRepository.saveConversation(
            command = rawCommand,
            response = speechText,
            tool = tool.name,
            result = if (result.success) "SUCCESS" else "FAILED: ${result.errorCode ?: ""}",
            success = result.success
        )

        try {
            JarvisDatabase.getInstance(context).automationHistoryDao().insert(
                AutomationHistoryEntity(
                    command = rawCommand,
                    tool = tool.name,
                    status = result.status.name,
                    result = result.message,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        onStateChange(AgentExecutionState.Speaking(speechText, tool.name, result))
        onFinished(speechText, result)
    }
}
