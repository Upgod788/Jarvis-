package com.example.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSummaryDao {
    @Query("SELECT * FROM conversation_summaries ORDER BY createdAt DESC")
    fun getAllSummaries(): Flow<List<ConversationSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ConversationSummaryEntity): Long

    @Query("DELETE FROM conversation_summaries")
    suspend fun clearAllSummaries(): Int
}
