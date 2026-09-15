package com.example.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_summaries")
data class ConversationSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val summary: String,
    val timeRange: String = "",
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
