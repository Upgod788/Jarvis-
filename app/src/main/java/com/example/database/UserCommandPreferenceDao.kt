package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for accessing and modifying user command preferences.
 */
@Dao
interface UserCommandPreferenceDao {

    @Query("SELECT * FROM user_command_preferences ORDER BY priority DESC, updatedAt DESC")
    fun getAllPreferences(): Flow<List<UserCommandPreferenceEntity>>

    @Query("SELECT * FROM user_command_preferences ORDER BY priority DESC, updatedAt DESC")
    suspend fun getAllPreferencesList(): List<UserCommandPreferenceEntity>

    @Query("SELECT * FROM user_command_preferences WHERE id = :id LIMIT 1")
    suspend fun getPreferenceById(id: Long): UserCommandPreferenceEntity?

    @Query("SELECT * FROM user_command_preferences WHERE commandKey = :commandKey LIMIT 1")
    suspend fun getPreferenceByCommand(commandKey: String): UserCommandPreferenceEntity?

    @Query("SELECT * FROM user_command_preferences WHERE isEnabled = 1 ORDER BY priority DESC, updatedAt DESC")
    fun getEnabledPreferences(): Flow<List<UserCommandPreferenceEntity>>

    @Query("SELECT * FROM user_command_preferences WHERE commandKey LIKE '%' || :query || '%' OR preferredTool LIKE '%' || :query || '%' ORDER BY priority DESC")
    fun searchPreferences(query: String): Flow<List<UserCommandPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: UserCommandPreferenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(preferences: List<UserCommandPreferenceEntity>): List<Long>

    @Update
    suspend fun updatePreference(preference: UserCommandPreferenceEntity): Int

    @Delete
    suspend fun deletePreference(preference: UserCommandPreferenceEntity): Int

    @Query("DELETE FROM user_command_preferences WHERE id = :id")
    suspend fun deletePreferenceById(id: Long): Int

    @Query("DELETE FROM user_command_preferences WHERE commandKey = :commandKey")
    suspend fun deletePreferenceByCommand(commandKey: String): Int

    @Query("DELETE FROM user_command_preferences")
    suspend fun clearAllPreferences(): Int
}

typealias CommandPreferenceDao = UserCommandPreferenceDao
