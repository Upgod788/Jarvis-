package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing user-configured preferences for commands and actions.
 * Stores user preferences such as preferred tool handler, confirmation requirements,
 * auto-execution, spoken feedback settings, and custom response phrasing.
 */
@Entity(tableName = "user_command_preferences")
data class UserCommandPreferenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val commandKey: String,
    val preferredTool: String = "",
    val requiresConfirmation: Boolean = false,
    val autoExecute: Boolean = true,
    val voiceFeedbackEnabled: Boolean = true,
    val customFeedback: String? = null,
    val parametersJson: String? = null,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

typealias CommandPreferenceEntity = UserCommandPreferenceEntity
