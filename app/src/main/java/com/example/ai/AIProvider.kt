package com.example.ai

data class ToolInfo(
    val name: String,
    val description: String,
    val parameters: List<String>
)

data class ToolInvocation(
    val toolName: String,
    val arguments: Map<String, Any?>
)

data class AIRequest(
    val prompt: String,
    val tools: List<ToolInfo>,
    val conversationContext: List<String> = emptyList()
)

data class AIResponse(
    val textResponse: String,
    val toolInvocation: ToolInvocation? = null
)

interface AIProvider {
    val providerName: String
    suspend fun processCommand(request: AIRequest): AIResponse
}
