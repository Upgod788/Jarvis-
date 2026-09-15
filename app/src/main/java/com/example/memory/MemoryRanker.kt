package com.example.memory

import java.util.Locale
import kotlin.math.max

object MemoryRanker {

    private val STOP_WORDS = setOf(
        "the", "a", "an", "is", "in", "to", "at", "for", "of", "and", "or", "that", "this",
        "my", "i", "me", "you", "user", "your", "with", "as", "on", "can", "please", "jarvis",
        "what", "how", "why", "tell", "show", "give", "do", "does", "did", "are", "was", "were"
    )

    /**
     * Scores how relevant a memory is to the user's prompt (0.0 to 1.0).
     */
    fun scoreMemory(memory: MemoryEntity, query: String, now: Long = System.currentTimeMillis()): Double {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return 0.0

        val memoryTokens = tokenize("${memory.effectiveKey} ${memory.effectiveText} ${memory.category}")
        if (memoryTokens.isEmpty()) return 0.0

        // 1. Keyword Overlap (Relevance)
        val matches = queryTokens.count { it in memoryTokens }
        val relevance = matches.toDouble() / queryTokens.size.toDouble()

        // Exact key match bonus
        val keyMatchBonus = if (queryTokens.any { it.length > 2 && memory.effectiveKey.contains(it, ignoreCase = true) }) 0.25 else 0.0

        val effectiveRelevance = (relevance + keyMatchBonus).coerceAtMost(1.0)
        if (effectiveRelevance <= 0.05) return 0.0 // No meaningful overlap

        // 2. Recency Decay (half-life of 14 days)
        val ageDays = max(0L, now - memory.lastUsedAt).toDouble() / (24.0 * 60.0 * 60.0 * 1000.0)
        val recencyFactor = 1.0 / (1.0 + (ageDays / 14.0))

        // 3. Importance & Confidence
        val importanceFactor = memory.importance.toDouble().coerceIn(0.1, 1.0)
        val confidenceFactor = memory.confidence.toDouble().coerceIn(0.1, 1.0)

        // Weighted combination
        return (effectiveRelevance * 0.50) +
                (importanceFactor * 0.20) +
                (confidenceFactor * 0.15) +
                (recencyFactor * 0.15)
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() && it !in STOP_WORDS && it.length > 1 }
            .toSet()
    }
}
