package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateHistoryDao {
    @Query("SELECT * FROM update_history ORDER BY installedAt DESC")
    fun getAll(): Flow<List<UpdateHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UpdateHistoryEntity): Long

    @Query("DELETE FROM update_history")
    suspend fun clearAll(): Int
}
