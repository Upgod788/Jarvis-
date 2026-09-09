package com.example.memory

import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    fun searchMemories(query: String): Flow<List<MemoryEntity>> = memoryDao.searchMemories(query)

    suspend fun getMemoryByKey(key: String): MemoryEntity? = memoryDao.getMemoryByKey(key)

    suspend fun saveMemory(key: String, value: String, category: String = "general"): Long {
        val existing = memoryDao.getMemoryByKey(key)
        return if (existing != null) {
            val updated = existing.copy(value = value, category = category, updatedAt = System.currentTimeMillis())
            memoryDao.updateMemory(updated)
            existing.id
        } else {
            val entity = MemoryEntity(key = key, value = value, category = category)
            memoryDao.insertMemory(entity)
        }
    }

    suspend fun deleteMemory(memory: MemoryEntity) = memoryDao.deleteMemory(memory)

    suspend fun deleteMemoryByKey(key: String): Boolean = memoryDao.deleteMemoryByKey(key) > 0

    suspend fun clearAll() = memoryDao.clearAllMemories()
}
