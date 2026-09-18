package com.example.agent

import android.content.Context
import com.example.ai.AIProvider
import com.example.ai.AIRequest
import com.example.ai.ToolInfo
import com.example.emotion.EmotionManager
import com.example.history.ConversationRepository
import com.example.memory.MemoryManager
import com.example.memory.MemoryVoiceAction
import com.example.memory.QueryType
import com.example.memory.model.MemoryCandidate
import com.example.personality.PersonalityManager
import com.example.personality.ResponseStyleManager
import com.example.services.CommandHandlerService
import com.example.tools.Tool
import com.example.tools.ToolResult
import com.example.voice.VoiceSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JarvisAgent(
    private val context: Context,
    val toolRegistry: ToolRegistry,
    val confirmationManager: ConfirmationManager,
    var aiProvider: AIProvider,
    private val conversationRepository: ConversationRepository,
    var voiceSettingsManager: VoiceSettingsManager? = null,
    var memoryManager: MemoryManager? = null,
    var emotionManager: EmotionManager? = null,
    var personalityManager: PersonalityManager? = null,
    var responseStyleManager: ResponseStyleManager? = null
) {
    var onMemorySuggestion: ((MemoryCandidate) -> Unit)? = null

    suspend fun executeCommand(
        rawCommand: String,
        languageInstruction: String? = null,
        onStateChange: (AgentExecutionState) -> Unit,
        onFinished: (String, ToolResult?) -> Unit
    ) = withContext(Dispatchers.Default) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) {
            onFinished("I'm listening. Please give me a command.", null)
            return@withContext
        }

        onStateChange(AgentExecutionState.Thinking)

        // 1. Emotion detection
        emotionManager?.updateFromUserInput(trimmed)

        // 2. Direct Voice Memory actions (Remember, Forget, What do you know about me, Pause, Resume)
        val memoryAction = memoryManager?.checkVoiceAction(trimmed)
        if (memoryAction != null) {
            val responseText = handleMemoryVoiceAction(memoryAction)
            conversationRepository.saveConversation(
                command = trimmed,
                response = responseText,
                tool = "MemorySystem",
                result = responseText,
                success = true
            )
            onStateChange(AgentExecutionState.Speaking(responseText, "MemorySystem", ToolResult.ok(responseText)))
            onFinished(responseText, ToolResult.ok(responseText))
            return@withContext
        }

        // 3. Direct intent detection via CommandHandlerService
        val detected = CommandHandlerService.INSTANCE.parseIntent(trimmed, trimmed)
        if (detected != null) {
            val execResult = CommandHandlerService.INSTANCE.executeAction(context, detected, toolRegistry)
            val result = execResult.toolResult ?: if (execResult.success) ToolResult.ok(execResult.message) else ToolResult.error(execResult.message)
            conversationRepository.saveConversation(
                command = trimmed,
                response = execResult.message,
                tool = detected.actionType.name,
                result = result.message,
                success = execResult.success
            )
            onStateChange(AgentExecutionState.Speaking(execResult.message, detected.actionType.name, result))
            onFinished(execResult.message, result)
            return@withContext
        }

        // 4. Retrieve relevant memory context and personality instructions
        val memoryContext = memoryManager?.getRelevantContext(trimmed)
        val personalityInstruction = personalityManager?.getPersonalityInstruction()
        val emotionGuideline = emotionManager?.getCurrentPromptGuideline()

        // 5. Query AI Provider
        try {
            val toolInfos = toolRegistry.getAllTools().map {
                ToolInfo(
                    name = it.name,
                    description = it.description,
                    parameters = it.parameters.map { p -> p.name }
                )
            }
            val request = AIRequest(
                prompt = trimmed,
                tools = toolInfos,
                languageInstruction = languageInstruction,
                memoryContext = memoryContext,
                personalityInstruction = personalityInstruction,
                emotionalGuideline = emotionGuideline
            )
            val aiResponse = aiProvider.processCommand(request)

            // Check if tool was invoked
            val invocation = aiResponse.toolInvocation
            if (invocation != null) {
                val tool = toolRegistry.getTool(invocation.toolName)
                if (tool != null) {
                    runTool(tool, invocation.arguments, trimmed, onStateChange, onFinished)
                    checkMemorySuggestion(trimmed)
                    return@withContext
                }
            }

            // Also check if text contains an intent
            val detectedFromAi = CommandHandlerService.INSTANCE.parseIntent(aiResponse.textResponse, trimmed)
            if (detectedFromAi != null) {
                val execResult = CommandHandlerService.INSTANCE.executeAction(context, detectedFromAi, toolRegistry)
                val result = execResult.toolResult ?: if (execResult.success) ToolResult.ok(execResult.message) else ToolResult.error(execResult.message)
                conversationRepository.saveConversation(
                    command = trimmed,
                    response = execResult.message,
                    tool = detectedFromAi.actionType.name,
                    result = result.message,
                    success = execResult.success
                )
                onStateChange(AgentExecutionState.Speaking(execResult.message, detectedFromAi.actionType.name, result))
                onFinished(execResult.message, result)
                checkMemorySuggestion(trimmed)
                return@withContext
            }

            val cleanReply = CommandHandlerService.INSTANCE.cleanResponseText(aiResponse.textResponse)
            conversationRepository.saveConversation(
                command = trimmed,
                response = cleanReply,
                tool = null,
                result = null,
                success = true
            )
            onStateChange(AgentExecutionState.Speaking(cleanReply))
            onFinished(cleanReply, null)

            // 6. Check for implicit persistent facts to suggest to user
            checkMemorySuggestion(trimmed)
        } catch (e: Exception) {
            val errorMsg = "Ravan encountered an error: ${e.localizedMessage ?: "Unknown error"}"
            onStateChange(AgentExecutionState.Error(errorMsg))
            onFinished(errorMsg, ToolResult.error(errorMsg))
        }
    }

    private suspend fun handleMemoryVoiceAction(action: MemoryVoiceAction): String {
        val mm = memoryManager ?: return "Memory system is unavailable."
        return when (action) {
            is MemoryVoiceAction.StoreExplicit -> {
                mm.storeCandidate(action.candidate)
                "I have remembered that for you, sir."
            }
            is MemoryVoiceAction.Query -> {
                when (action.queryType) {
                    QueryType.NAME -> {
                        val nameMem = mm.repository.getMemoryByKey("user_name")
                        if (nameMem != null) {
                            "Your name is ${nameMem.effectiveText.replace(Regex("""(?i)^user's name is\s*"""), "")}, sir."
                        } else {
                            "I don't have your name recorded yet, sir. What would you like me to call you?"
                        }
                    }
                    QueryType.ALL_PERSONAL -> {
                        val list = mm.repository.getAllMemoriesOnce()
                        if (list.isEmpty()) {
                            "I don't have any memories saved about you yet, sir."
                        } else {
                            val items = list.take(5).joinToString(", ") { it.effectiveText }
                            "Here is what I remember about you, sir: $items."
                        }
                    }
                    QueryType.PREFERENCES -> {
                        val list = mm.repository.getAllMemoriesOnce().filter { it.category == "preferences" }
                        if (list.isEmpty()) {
                            "You haven't specified any custom preferences yet, sir."
                        } else {
                            val items = list.take(4).joinToString(", ") { it.effectiveText }
                            "Your stored preferences are: $items."
                        }
                    }
                    QueryType.ROUTINES -> {
                        val list = mm.repository.getAllMemoriesOnce().filter { it.category == "routines" }
                        if (list.isEmpty()) {
                            "I don't have any routines stored yet, sir."
                        } else {
                            val items = list.take(4).joinToString(", ") { it.effectiveText }
                            "Your stored routines are: $items."
                        }
                    }
                    else -> {
                        val memories = mm.retriever.getRelevantMemories(action.subject.ifBlank { "preferences" })
                        if (memories.isEmpty()) {
                            "I couldn't find anything in my memory regarding that, sir."
                        } else {
                            "I remember that: ${memories.first().effectiveText}."
                        }
                    }
                }
            }
            is MemoryVoiceAction.Forget -> {
                if (action.target != null) {
                    val deleted = mm.repository.deleteMemoryByKey(action.target)
                    if (deleted) "I have deleted the memory about ${action.target}, sir."
                    else "I couldn't find a matching memory to delete, sir."
                } else {
                    val all = mm.repository.getAllMemoriesOnce()
                    if (all.isNotEmpty()) {
                        mm.repository.deleteMemory(all.first())
                        "I have forgotten that for you, sir."
                    } else {
                        "There was nothing to forget in my memory, sir."
                    }
                }
            }
            is MemoryVoiceAction.Pause -> {
                mm.setMemoryPaused(true)
                "Memory recording has been paused. I won't save any new memories until you resume."
            }
            is MemoryVoiceAction.Resume -> {
                mm.setMemoryPaused(false)
                "Memory recording resumed. I'm ready to learn your preferences again, sir."
            }
            is MemoryVoiceAction.ClearAll -> {
                val count = mm.clearAll()
                "All Ravan long-term memories ($count items) have been cleared, sir."
            }
            is MemoryVoiceAction.ClearConversations -> {
                val count = mm.clearConversations()
                "Cleared $count conversation memories, sir."
            }
        }
    }

    private fun checkMemorySuggestion(trimmed: String) {
        val mm = memoryManager ?: return
        if (!mm.isMemoryActive()) return
        val suggestion = mm.detectImplicitSuggestion(trimmed)
        if (suggestion != null) {
            onMemorySuggestion?.invoke(suggestion)
        }
    }

    private suspend fun runTool(
        tool: Tool,
        params: Map<String, Any?>,
        rawCommand: String,
        onStateChange: (AgentExecutionState) -> Unit,
        onFinished: (String, ToolResult?) -> Unit
    ) {
        onStateChange(AgentExecutionState.Executing(tool.name))
        val result = try {
            tool.execute(context, params)
        } catch (e: Exception) {
            ToolResult.error("Failed to execute ${tool.name}: ${e.localizedMessage}")
        }

        val speechText = result.message
        conversationRepository.saveConversation(
            command = rawCommand,
            response = speechText,
            tool = tool.name,
            result = result.message,
            success = result.success
        )
        onStateChange(AgentExecutionState.Speaking(speechText, tool.name, result))
        onFinished(speechText, result)
    }
}
