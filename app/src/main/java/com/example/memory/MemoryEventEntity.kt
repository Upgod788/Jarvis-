package com.example.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_events")
data class MemoryEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val eventType: String, // CREATED, UPDATED, FORGOTTEN, PAUSED, RESUMED, CLEARED
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
