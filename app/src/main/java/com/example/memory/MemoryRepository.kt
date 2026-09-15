package com.example.memory

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val memoryEventDao: MemoryEventDao? = null
) {
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    suspend fun getMemory(key: String): MemoryEntity? =
        memoryDao.getMemoryByKey(key)

    suspend fun getMemoryByKey(key: String): MemoryEntity? =
        memoryDao.getMemoryByKey(key)

    suspend fun getMemoryById(memoryId: String): MemoryEntity? =
        memoryDao.getMemoryByMemoryId(memoryId)

    suspend fun getAllMemoriesOnce(): List<MemoryEntity> =
        memoryDao.getAllMemoriesList()

    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> =
        if (category == "all") memoryDao.getAllMemories() else memoryDao.getMemoriesByCategory(category)

    fun searchMemories(query: String): Flow<List<MemoryEntity>> =
        memoryDao.searchMemories(query)

    suspend fun saveMemory(
        key: String,
        value: String,
        category: String = "general",
        memoryType: String = "LONG_TERM_MEMORY",
        importance: Float = 0.5f,
        confidence: Float = 1.0f,
        source: String = "USER_EXPLICIT"
    ): Long {
        val existing = memoryDao.getMemoryByKey(key)
        return if (existing != null) {
            val updated = existing.copy(
                content = value,
                value = value,
                category = category,
                memoryType = memoryType,
                importance = importance,
                confidence = confidence,
                updatedAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            logEvent("UPDATED", "Updated memory: '$key' -> '$value'")
            existing.id
        } else {
            val entity = MemoryEntity(
                memoryId = UUID.randomUUID().toString(),
                key = key,
                content = value,
                value = value,
                category = category,
                memoryType = memoryType,
                importance = importance,
                confidence = confidence,
                source = source,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis()
            )
            val id = memoryDao.insertMemory(entity)
            logEvent("CREATED", "Stored memory in $category: '$key' -> '$value'")
            id
        }
    }

    suspend fun saveMemoryEntity(entity: MemoryEntity): Long {
        val id = memoryDao.insertMemory(entity)
        logEvent("CREATED", "Stored memory: '${entity.effectiveKey}'")
        return id
    }

    suspend fun updateMemory(memory: MemoryEntity): Int {
        val result = memoryDao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
        logEvent("UPDATED", "Modified memory: '${memory.effectiveKey}'")
        return result
    }

    suspend fun touchMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory.copy(lastUsedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(memory: MemoryEntity) {
        memoryDao.deleteMemory(memory)
        logEvent("FORGOTTEN", "Forgotten memory: '${memory.effectiveKey}'")
    }

    suspend fun deleteMemoryByKey(key: String): Boolean {
        val count = memoryDao.deleteMemoryByKey(key)
        if (count > 0) {
            logEvent("FORGOTTEN", "Forgotten memory with key: '$key'")
        }
        return count > 0
    }

    suspend fun deleteMemoryById(memoryId: String): Boolean {
        val count = memoryDao.deleteMemoryById(memoryId)
        if (count > 0) {
            logEvent("FORGOTTEN", "Forgotten memory ID: '$memoryId'")
        }
        return count > 0
    }

    suspend fun clearConversationMemories(): Int {
        val count = memoryDao.clearConversationMemories()
        logEvent("CLEARED", "Cleared $count conversation summaries.")
        return count
    }

    suspend fun clearAll(): Int {
        val count = memoryDao.clearAllMemories()
        logEvent("CLEARED", "Cleared all JARVIS long-term memory ($count items).")
        return count
    }

    private suspend fun logEvent(type: String, description: String) {
        try {
            memoryEventDao?.insertEvent(MemoryEventEntity(eventType = type, description = description))
        } catch (_: Exception) {}
    }
}
