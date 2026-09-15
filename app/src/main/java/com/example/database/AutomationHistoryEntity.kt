package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_history")
data class AutomationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val command: String,
    val tool: String,
    val status: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)
