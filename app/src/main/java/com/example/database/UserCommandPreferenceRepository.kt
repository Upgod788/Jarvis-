package com.example.database

import kotlinx.coroutines.flow.Flow

/**
 * Repository providing clean abstraction for user command preferences.
 */
class UserCommandPreferenceRepository(
    private val dao: UserCommandPreferenceDao
) {
    val allPreferences: Flow<List<UserCommandPreferenceEntity>> = dao.getAllPreferences()
    val enabledPreferences: Flow<List<UserCommandPreferenceEntity>> = dao.getEnabledPreferences()

    suspend fun getAllList(): List<UserCommandPreferenceEntity> = dao.getAllPreferencesList()

    suspend fun getById(id: Long): UserCommandPreferenceEntity? = dao.getPreferenceById(id)

    suspend fun getByCommand(commandKey: String): UserCommandPreferenceEntity? =
        dao.getPreferenceByCommand(commandKey)

    fun search(query: String): Flow<List<UserCommandPreferenceEntity>> =
        dao.searchPreferences(query)

    suspend fun savePreference(
        commandKey: String,
        preferredTool: String = "",
        requiresConfirmation: Boolean = false,
        autoExecute: Boolean = true,
        voiceFeedbackEnabled: Boolean = true,
        customFeedback: String? = null,
        parametersJson: String? = null,
        isEnabled: Boolean = true,
        priority: Int = 0
    ): Long {
        val now = System.currentTimeMillis()
        val existing = dao.getPreferenceByCommand(commandKey)
        val entity = if (existing != null) {
            existing.copy(
                preferredTool = preferredTool,
                requiresConfirmation = requiresConfirmation,
                autoExecute = autoExecute,
                voiceFeedbackEnabled = voiceFeedbackEnabled,
                customFeedback = customFeedback,
                parametersJson = parametersJson,
                isEnabled = isEnabled,
                priority = priority,
                updatedAt = now
            )
        } else {
            UserCommandPreferenceEntity(
                commandKey = commandKey,
                preferredTool = preferredTool,
                requiresConfirmation = requiresConfirmation,
                autoExecute = autoExecute,
                voiceFeedbackEnabled = voiceFeedbackEnabled,
                customFeedback = customFeedback,
                parametersJson = parametersJson,
                isEnabled = isEnabled,
                priority = priority,
                createdAt = now,
                updatedAt = now
            )
        }
        return dao.insertPreference(entity)
    }

    suspend fun insert(preference: UserCommandPreferenceEntity): Long =
        dao.insertPreference(preference)

    suspend fun update(preference: UserCommandPreferenceEntity) {
        dao.updatePreference(preference.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(preference: UserCommandPreferenceEntity) =
        dao.deletePreference(preference)

    suspend fun deleteById(id: Long): Int =
        dao.deletePreferenceById(id)

    suspend fun deleteByCommand(commandKey: String): Int =
        dao.deletePreferenceByCommand(commandKey)

    suspend fun clearAll(): Int =
        dao.clearAllPreferences()
}

typealias CommandPreferenceRepository = UserCommandPreferenceRepository
