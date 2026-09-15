package com.example.memory.model

enum class MemoryType(val displayName: String) {
    SHORT_TERM_MEMORY("Short-Term"),
    SESSION_MEMORY("Session"),
    LONG_TERM_MEMORY("Long-Term"),
    PREFERENCE_MEMORY("Preference"),
    TASK_MEMORY("Task"),
    DEVICE_MEMORY("Device"),
    CONVERSATION_SUMMARY_MEMORY("Summary");

    companion object {
        fun fromString(value: String): MemoryType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LONG_TERM_MEMORY
    }
}

enum class MemoryCategory(val id: String, val title: String) {
    ALL("all", "All"),
    PERSONAL("personal", "Personal"),
    PREFERENCES("preferences", "Preferences"),
    ROUTINES("routines", "Routines"),
    DEVICES("devices", "Devices"),
    PROJECTS("projects", "Projects"),
    CONVERSATIONS("conversations", "Conversations"),
    GENERAL("general", "General");

    companion object {
        fun fromId(id: String): MemoryCategory =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERAL
    }
}

enum class ExpirationPolicy(val policyName: String, val durationMillis: Long) {
    TEMPORARY("TEMPORARY", 24 * 60 * 60 * 1000L), // 1 day
    SHORT_TERM("SHORT_TERM", 7 * 24 * 60 * 60 * 1000L), // 7 days
    LONG_TERM("LONG_TERM", 90 * 24 * 60 * 60 * 1000L), // 90 days
    PERMANENT("PERMANENT", Long.MAX_VALUE);

    companion object {
        fun fromString(value: String): ExpirationPolicy =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PERMANENT
    }
}

data class MemoryCandidate(
    val key: String,
    val content: String,
    val category: String,
    val memoryType: String = MemoryType.LONG_TERM_MEMORY.name,
    val importance: Float = 0.6f,
    val confidence: Float = 0.9f,
    val source: String = "USER_EXPLICIT",
    val requiresConfirmation: Boolean = false
)
