package com.example.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val memoryId: String = UUID.randomUUID().toString(),
    val key: String = "",
    val content: String = "",
    val value: String = "",
    val category: String = "general",
    val memoryType: String = "LONG_TERM_MEMORY",
    val importance: Float = 0.5f,
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val source: String = "USER_EXPLICIT",
    val expirationPolicy: String = "PERMANENT",
    val userApproved: Boolean = true
) {
    val effectiveText: String
        get() = content.ifBlank { value }

    val effectiveKey: String
        get() = key.ifBlank { effectiveText.take(30) }
}
