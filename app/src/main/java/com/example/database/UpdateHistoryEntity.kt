package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "update_history")
data class UpdateHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val versionCode: Int,
    val versionName: String,
    val installedAt: Long = System.currentTimeMillis(),
    val status: String,
    val releaseNotes: String,
    val channel: String = "stable"
)
