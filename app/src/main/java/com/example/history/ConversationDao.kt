package com.example.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConversations(limit: Int): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversationsList(limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE userCommand LIKE '%' || :query || '%' OR assistantResponse LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    suspend fun getAllConversationsList(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>): List<Long>

    @Update
    suspend fun updateConversation(conversation: ConversationEntity): Int

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity): Int

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long): Int

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations(): Int
}

typealias ConversationHistoryDao = ConversationDao
