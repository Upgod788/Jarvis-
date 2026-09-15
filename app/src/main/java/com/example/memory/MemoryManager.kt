package com.example.memory

import android.content.Context
import com.example.database.JarvisDatabase
import com.example.memory.model.MemoryCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class MemoryManager(
    context: Context,
    val database: JarvisDatabase = JarvisDatabase.getInstance(context)
) {
    val privacyManager = MemoryPrivacyManager(context)
    val memoryDao = database.memoryDao()
    val memoryEventDao = database.memoryEventDao()
    val conversationSummaryDao = database.conversationSummaryDao()
    val conversationDao = database.conversationDao()

    val repository = MemoryRepository(memoryDao, memoryEventDao)
    val extractor = MemoryExtractor(privacyManager)
    val retriever = MemoryRetriever(repository, privacyManager)
    val summarizer = MemorySummarizer(conversationDao, conversationSummaryDao, memoryDao)
    val exportManager = MemoryExportManager(privacyManager)

    val isMemoryEnabled: StateFlow<Boolean> = privacyManager.isMemoryEnabled
    val isMemoryPaused: StateFlow<Boolean> = privacyManager.isMemoryPaused

    val allMemories: Flow<List<MemoryEntity>> = repository.allMemories
    val recentEvents: Flow<List<MemoryEventEntity>> = memoryEventDao.getRecentEvents(50)
    val conversationSummaries: Flow<List<ConversationSummaryEntity>> = conversationSummaryDao.getAllSummaries()

    fun isMemoryActive(): Boolean = privacyManager.isMemoryActive()

    fun setMemoryEnabled(enabled: Boolean) {
        privacyManager.setMemoryEnabled(enabled)
    }

    fun setMemoryPaused(paused: Boolean) {
        privacyManager.setMemoryPaused(paused)
    }

    fun checkVoiceAction(input: String): MemoryVoiceAction? =
        extractor.checkMemoryAction(input)

    fun detectImplicitSuggestion(input: String): MemoryCandidate? =
        extractor.detectImplicitSuggestion(input)

    suspend fun getRelevantContext(prompt: String): String {
        val memories = retriever.getRelevantMemories(prompt)
        return retriever.formatMemoryContext(memories)
    }

    suspend fun storeCandidate(candidate: MemoryCandidate): Long {
        if (!isMemoryActive()) return -1L
        if (privacyManager.isSensitive(candidate.content) || privacyManager.isSensitive(candidate.key)) return -1L

        return repository.saveMemory(
            key = candidate.key,
            value = candidate.content,
            category = candidate.category,
            memoryType = candidate.memoryType,
            importance = candidate.importance,
            confidence = candidate.confidence,
            source = candidate.source
        )
    }

    suspend fun exportJson(): String {
        val list = repository.getAllMemoriesOnce()
        return exportManager.exportToJson(list)
    }

    suspend fun importJson(json: String): Int {
        val list = exportManager.importFromJson(json)
        var count = 0
        list.forEach { mem ->
            repository.saveMemoryEntity(mem)
            count++
        }
        return count
    }

    suspend fun clearAll(): Int = repository.clearAll()

    suspend fun clearConversations(): Int = repository.clearConversationMemories()
}
