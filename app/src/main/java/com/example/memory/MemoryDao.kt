package com.example.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category ORDER BY updatedAt DESC")
    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE `memoryId` = :memoryId LIMIT 1")
    suspend fun getMemoryByMemoryId(memoryId: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%' OR `content` LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity): Int

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity): Int

    @Query("DELETE FROM memories WHERE `key` = :key")
    suspend fun deleteMemoryByKey(key: String): Int

    @Query("DELETE FROM memories WHERE `memoryId` = :memoryId")
    suspend fun deleteMemoryById(memoryId: String): Int

    @Query("DELETE FROM memories WHERE category = 'conversations' OR memoryType = 'CONVERSATION_SUMMARY_MEMORY'")
    suspend fun clearConversationMemories(): Int

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories(): Int
}
