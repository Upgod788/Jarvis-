package com.example.ui

import com.example.agent.ConfirmationRequest
import com.example.history.ConversationEntity
import com.example.tools.ToolResult

data class JarvisUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val currentCommand: String = "",
    val assistantResponse: String = "Hello, I'm JARVIS.\nTap the microphone or say \"Hey JARVIS\".",
    val activeToolName: String? = null,
    val activeToolResult: ToolResult? = null,
    val audioRmsLevel: Float = 0.0f,
    val pendingConfirmation: ConfirmationRequest? = null,
    val missingPermissions: List<String> = emptyList(),
    val isTtsSpeaking: Boolean = false,
    val isVoiceMuted: Boolean = false,
    val recentConversations: List<ConversationEntity> = emptyList()
)
