package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationHistoryDao {
    @Query("SELECT * FROM automation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<AutomationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AutomationHistoryEntity): Long

    @Query("DELETE FROM automation_history")
    suspend fun clearAll()
}
