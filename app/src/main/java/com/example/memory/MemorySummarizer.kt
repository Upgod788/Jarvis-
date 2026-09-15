package com.example.memory

import com.example.history.ConversationDao
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemorySummarizer(
    private val conversationDao: ConversationDao,
    private val conversationSummaryDao: ConversationSummaryDao,
    private val memoryDao: MemoryDao
) {
    suspend fun summarizeRecentConversations(limit: Int = 20): String? = withContext(Dispatchers.IO) {
        val history = conversationDao.getRecentConversationsList(limit)
        if (history.size < 4) return@withContext null

        val commands = history.map { it.userCommand }
        val recentPrompts = commands.take(6)

        // Extract high-level topic hints
        val summaryText = "User discussed ${history.size} queries recently including: ${recentPrompts.joinToString("; ") { it.take(40) }}."

        val summaryEntity = ConversationSummaryEntity(
            summary = summaryText,
            timeRange = "${history.last().timestamp} - ${history.first().timestamp}",
            messageCount = history.size
        )
        conversationSummaryDao.insertSummary(summaryEntity)

        // Also store as a conversation memory in memories table
        val mem = MemoryEntity(
            key = "summary_${System.currentTimeMillis() % 100000}",
            content = summaryText,
            value = summaryText,
            category = MemoryCategory.CONVERSATIONS.id,
            memoryType = MemoryType.CONVERSATION_SUMMARY_MEMORY.name,
            importance = 0.5f,
            confidence = 0.8f,
            source = "SYSTEM_SUMMARIZER"
        )
        memoryDao.insertMemory(mem)

        summaryText
    }
}
