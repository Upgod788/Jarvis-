package com.example.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryRetriever(
    private val memoryRepository: MemoryRepository,
    private val privacyManager: MemoryPrivacyManager
) {

    /**
     * Retrieves the most relevant memories for the prompt.
     * Returns empty list if memory is disabled or no memories meet relevance threshold.
     */
    suspend fun getRelevantMemories(
        prompt: String,
        limit: Int = 4,
        threshold: Double = 0.22
    ): List<MemoryEntity> = withContext(Dispatchers.IO) {
        if (!privacyManager.isMemoryActive()) return@withContext emptyList()

        val all = memoryRepository.getAllMemoriesOnce()
        if (all.isEmpty()) return@withContext emptyList()

        val scored = all.map { memory ->
            val score = MemoryRanker.scoreMemory(memory, prompt)
            Pair(memory, score)
        }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        // Touch lastUsedAt asynchronously for retrieved items
        scored.forEach { mem ->
            try {
                memoryRepository.touchMemory(mem)
            } catch (_: Exception) {}
        }

        scored
    }

    /**
     * Formats retrieved memories into a concise prompt context.
     */
    fun formatMemoryContext(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) return ""
        val lines = memories.joinToString("\n") { mem ->
            "- [${mem.category.uppercase()}]: ${mem.effectiveText}"
        }
        return """
        RELEVANT USER MEMORY CONTEXT:
        $lines
        """.trimIndent()
    }
}
