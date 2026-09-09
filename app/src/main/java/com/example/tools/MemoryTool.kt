package com.example.tools

import android.content.Context
import com.example.memory.MemoryRepository
import kotlinx.coroutines.flow.first

class MemoryTool(private val memoryRepository: MemoryRepository) : Tool {
    override val name = "MemoryTool"
    override val description = "Stores, retrieves, or deletes personal preferences and facts in JARVIS local memory."
    override val parameters = listOf(
        ToolParameter(name = "action", type = "string", description = "'remember', 'recall', 'forget', or 'list'"),
        ToolParameter(name = "key", type = "string", description = "The subject or memory key (e.g. 'favorite color', 'language preference')", required = false),
        ToolParameter(name = "value", type = "string", description = "The value to remember", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase() ?: "recall"
        val key = params["key"]?.toString()?.trim() ?: ""
        val value = params["value"]?.toString()?.trim() ?: ""

        return when (action) {
            "remember", "save" -> {
                if (key.isBlank() || value.isBlank()) {
                    ToolResult.error("Both a memory topic and what to remember are needed.")
                } else {
                    memoryRepository.saveMemory(key = key, value = value)
                    ToolResult.ok("I will remember that your $key is $value.")
                }
            }
            "recall", "get" -> {
                if (key.isBlank() || key.contains("all", ignoreCase = true) || key.contains("everything", ignoreCase = true) || key.contains("about me", ignoreCase = true)) {
                    val all = memoryRepository.allMemories.first()
                    if (all.isEmpty()) {
                        ToolResult.ok("I do not have any saved memories yet.")
                    } else {
                        val list = all.take(10).joinToString("\n") { "• ${it.key}: ${it.value}" }
                        ToolResult.ok("Here is what I remember about you:\n$list")
                    }
                } else {
                    var memory = memoryRepository.getMemoryByKey(key)
                    if (memory == null) {
                        val all = memoryRepository.allMemories.first()
                        memory = all.firstOrNull {
                            it.key.contains(key, ignoreCase = true) ||
                            key.contains(it.key, ignoreCase = true) ||
                            it.value.contains(key, ignoreCase = true)
                        }
                    }
                    if (memory != null) {
                        ToolResult.ok("Your ${memory.key} is ${memory.value}.")
                    } else {
                        ToolResult.ok("I don't have anything remembered about \"$key\".")
                    }
                }
            }
            "forget", "delete" -> {
                if (key.isBlank()) {
                    ToolResult.error("Please specify which memory you'd like me to forget.")
                } else {
                    var deleted = memoryRepository.deleteMemoryByKey(key)
                    if (!deleted) {
                        val all = memoryRepository.allMemories.first()
                        val match = all.firstOrNull {
                            it.key.contains(key, ignoreCase = true) ||
                            key.contains(it.key, ignoreCase = true) ||
                            it.value.contains(key, ignoreCase = true) ||
                            key.contains(it.value, ignoreCase = true)
                        }
                        if (match != null) {
                            memoryRepository.deleteMemory(match)
                            deleted = true
                        }
                    }
                    if (deleted) {
                        ToolResult.ok("I have forgotten what was stored for \"$key\".")
                    } else {
                        ToolResult.ok("I couldn't find any memory stored for \"$key\".")
                    }
                }
            }
            "list" -> {
                val all = memoryRepository.allMemories.first()
                if (all.isEmpty()) {
                    ToolResult.ok("Your memory list is empty.")
                } else {
                    val summary = all.joinToString("\n") { "• ${it.key}: ${it.value}" }
                    ToolResult.ok("Stored memories:\n$summary")
                }
            }
            else -> ToolResult.error("Unknown memory action: $action.")
        }
    }
}
