package com.example.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userCommand: String,
    val assistantResponse: String,
    val toolUsed: String? = null,
    val executionResult: String? = null,
    val isSuccess: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

typealias ConversationHistoryEntity = ConversationEntity
