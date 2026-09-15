package com.example.memory

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MemoryExportManager(private val privacyManager: MemoryPrivacyManager) {

    fun exportToJson(memories: List<MemoryEntity>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()

        memories.forEach { mem ->
            val obj = JSONObject()
            obj.put("memoryId", mem.memoryId)
            obj.put("key", mem.key)
            obj.put("content", mem.effectiveText)
            obj.put("category", mem.category)
            obj.put("memoryType", mem.memoryType)
            obj.put("importance", mem.importance.toDouble())
            obj.put("confidence", mem.confidence.toDouble())
            obj.put("source", mem.source)
            obj.put("expirationPolicy", mem.expirationPolicy)
            obj.put("createdAt", mem.createdAt)
            arr.put(obj)
        }

        root.put("memories", arr)
        return root.toString(2)
    }

    fun importFromJson(jsonString: String): List<MemoryEntity> {
        val imported = mutableListOf<MemoryEntity>()
        try {
            val root = JSONObject(jsonString)
            val arr = root.optJSONArray("memories") ?: return emptyList()

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val content = item.optString("content").ifBlank { item.optString("value") }
                val key = item.optString("key").ifBlank { content.take(30) }

                // Sensitivity check
                if (content.isBlank() || privacyManager.isSensitive(content) || privacyManager.isSensitive(key)) {
                    continue
                }

                val memory = MemoryEntity(
                    memoryId = item.optString("memoryId").ifBlank { UUID.randomUUID().toString() },
                    key = key,
                    content = content,
                    value = content,
                    category = item.optString("category", "general"),
                    memoryType = item.optString("memoryType", "LONG_TERM_MEMORY"),
                    importance = item.optDouble("importance", 0.5).toFloat(),
                    confidence = item.optDouble("confidence", 0.9).toFloat(),
                    source = "IMPORT",
                    expirationPolicy = item.optString("expirationPolicy", "PERMANENT"),
                    userApproved = true
                )
                imported.add(memory)
            }
        } catch (_: Exception) {
            // Invalid JSON
        }
        return imported
    }
}
