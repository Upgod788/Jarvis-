package com.example.memory

import com.example.memory.model.MemoryCandidate
import com.example.memory.model.MemoryCategory
import com.example.memory.model.MemoryType
import java.util.Locale

sealed class MemoryVoiceAction {
    data class StoreExplicit(val candidate: MemoryCandidate) : MemoryVoiceAction()
    data class Query(val queryType: QueryType, val subject: String = "") : MemoryVoiceAction()
    data class Forget(val target: String? = null) : MemoryVoiceAction()
    object Pause : MemoryVoiceAction()
    object Resume : MemoryVoiceAction()
    object ClearAll : MemoryVoiceAction()
    object ClearConversations : MemoryVoiceAction()
}

enum class QueryType {
    NAME,
    ALL_PERSONAL,
    PREFERENCES,
    ROUTINES,
    DEVICES,
    PROJECTS,
    GENERAL
}

class MemoryExtractor(private val privacyManager: MemoryPrivacyManager) {

    fun checkMemoryAction(input: String): MemoryVoiceAction? {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Control commands
        if (lower.contains("stop remembering") || lower.contains("pause memory") || lower.contains("don't remember things anymore")) {
            return MemoryVoiceAction.Pause
        }
        if (lower.contains("start remembering") || lower.contains("resume memory") || lower.contains("start remembering again")) {
            return MemoryVoiceAction.Resume
        }
        if (lower.contains("clear my memory") || lower.contains("clear all memory") || lower.contains("clear all jarvis memory")) {
            return MemoryVoiceAction.ClearAll
        }
        if (lower.contains("clear conversation memory") || lower.contains("clear conversation summaries")) {
            return MemoryVoiceAction.ClearConversations
        }
        if (lower.contains("forget that") || lower.contains("forget what i just told you") || lower.contains("forget this") || lower == "forget it") {
            return MemoryVoiceAction.Forget(null)
        }
        val forgetRegex = Regex("""(?:forget|delete memory of|remove memory of)\s+(?:about\s+)?(.+)""", RegexOption.IGNORE_CASE)
        val forgetMatch = forgetRegex.find(lower)
        if (forgetMatch != null) {
            val target = forgetMatch.groupValues[1].trim()
            if (target.isNotBlank()) {
                return MemoryVoiceAction.Forget(target)
            }
        }

        // 2. Query commands
        if (lower.contains("what is my name") || lower.contains("what's my name") || lower.contains("mera naam kya hai") || lower.contains("do you know my name")) {
            return MemoryVoiceAction.Query(QueryType.NAME)
        }
        if (lower.contains("what do you remember about me") || lower.contains("what do you know about me") || lower.contains("tell me what you remember") || lower.contains("what is in your memory")) {
            return MemoryVoiceAction.Query(QueryType.ALL_PERSONAL)
        }
        if (lower.contains("what are my preferences") || lower.contains("what do i like") || lower.contains("my preferences")) {
            return MemoryVoiceAction.Query(QueryType.PREFERENCES)
        }
        if (lower.contains("what are my routines") || lower.contains("what is my routine")) {
            return MemoryVoiceAction.Query(QueryType.ROUTINES)
        }

        // 3. Explicit storage commands
        val explicit = extractExplicitMemory(trimmed, lower)
        if (explicit != null) {
            return MemoryVoiceAction.StoreExplicit(explicit)
        }

        return null
    }

    private fun extractExplicitMemory(trimmed: String, lower: String): MemoryCandidate? {
        // Name pattern
        val nameRegex = Regex("""(?:my name is|i am|call me|mera naam)\s+([a-zA-Z]+)""", RegexOption.IGNORE_CASE)
        val nameMatch = nameRegex.find(trimmed)
        if (nameMatch != null && !lower.contains("what") && !lower.contains("if")) {
            val name = nameMatch.groupValues[1].trim()
            if (name.length in 2..30) {
                return MemoryCandidate(
                    key = "user_name",
                    content = "User's name is $name",
                    category = MemoryCategory.PERSONAL.id,
                    importance = 0.95f,
                    confidence = 1.0f,
                    source = "USER_EXPLICIT"
                )
            }
        }

        // "Remember that [fact]" or "Remember [fact]"
        val rememberRegex = Regex("""^(?:jarvis[,:\s]*)?(?:please\s+)?(?:remember\s+that|remember\s+this[:\s]*|remember)\s+(.+)""", RegexOption.IGNORE_CASE)
        val remMatch = rememberRegex.find(trimmed)
        if (remMatch != null) {
            val fact = remMatch.groupValues[1].trim()
            if (fact.isNotBlank() && !privacyManager.isSensitive(fact)) {
                val cat = categorizeContent(fact)
                val key = deriveKey(fact)
                return MemoryCandidate(
                    key = key,
                    content = fact,
                    category = cat,
                    importance = 0.85f,
                    confidence = 1.0f,
                    source = "USER_EXPLICIT"
                )
            }
        }

        // "I prefer [X]" or "My preferred language is [X]"
        if (lower.startsWith("i prefer ") || lower.contains("my preferred ")) {
            val pref = trimmed.replace(Regex("""^(?:i\s+prefer\s+|my\s+preferred\s+)""", RegexOption.IGNORE_CASE), "").trim()
            if (pref.isNotBlank() && !privacyManager.isSensitive(pref)) {
                val isLanguage = pref.contains("english", ignoreCase = true) || pref.contains("hindi", ignoreCase = true) || pref.contains("hinglish", ignoreCase = true)
                return MemoryCandidate(
                    key = if (isLanguage) "preferred_language" else deriveKey(pref),
                    content = "User prefers $pref",
                    category = MemoryCategory.PREFERENCES.id,
                    importance = 0.85f,
                    confidence = 1.0f,
                    source = "USER_EXPLICIT"
                )
            }
        }

        // "My favorite [app/music/food/etc] is [X]"
        val favRegex = Regex("""my\s+favorite\s+([a-zA-Z]+)\s+is\s+(.+)""", RegexOption.IGNORE_CASE)
        val favMatch = favRegex.find(trimmed)
        if (favMatch != null) {
            val item = favMatch.groupValues[1].trim().lowercase(Locale.ROOT)
            val value = favMatch.groupValues[2].trim()
            if (value.isNotBlank() && !privacyManager.isSensitive(value)) {
                return MemoryCandidate(
                    key = "favorite_$item",
                    content = "Favorite $item is $value",
                    category = MemoryCategory.PREFERENCES.id,
                    importance = 0.75f,
                    confidence = 0.95f,
                    source = "USER_EXPLICIT"
                )
            }
        }

        return null
    }

    /**
     * Inspects conversational statements to see if there's a valuable persistent fact
     * that can be suggested to the user with a confirmation prompt.
     */
    fun detectImplicitSuggestion(input: String): MemoryCandidate? {
        if (!privacyManager.isMemoryActive()) return null
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // Ignore commands or questions
        if (lower.startsWith("what") || lower.startsWith("how") || lower.startsWith("why") ||
            lower.startsWith("when") || lower.startsWith("where") || lower.startsWith("open") ||
            lower.startsWith("turn on") || lower.startsWith("turn off") || lower.endsWith("?")) {
            return null
        }

        if (privacyManager.isSensitive(trimmed)) return null

        // Implicit routines e.g. "I wake up at 7 AM" or "I usually sleep at 11 PM"
        val routineRegex = Regex("""(?:i\s+usually|i\s+always|i)\s+(wake\s+up|sleep|go\s+to\s+bed|exercise|workout|leave\s+for\s+work)\s+at\s+([0-9:apm\s]+)""", RegexOption.IGNORE_CASE)
        val rMatch = routineRegex.find(trimmed)
        if (rMatch != null) {
            val action = rMatch.groupValues[1]
            val time = rMatch.groupValues[2]
            return MemoryCandidate(
                key = "routine_${action.replace(" ", "_")}",
                content = "User usually ${action.lowercase()} at ${time.trim()}",
                category = MemoryCategory.ROUTINES.id,
                importance = 0.7f,
                confidence = 0.85f,
                source = "CONVERSATION_INFERRED",
                requiresConfirmation = true
            )
        }

        // Implicit projects e.g. "I'm working on Project Orion" or "Currently building an Android app"
        val projectRegex = Regex("""(?:i am|i'm|currently)\s+(?:working\s+on|building|developing)\s+(.+)""", RegexOption.IGNORE_CASE)
        val pMatch = projectRegex.find(trimmed)
        if (pMatch != null) {
            val proj = pMatch.groupValues[1].trim()
            if (proj.length in 3..60) {
                return MemoryCandidate(
                    key = "current_project",
                    content = "User is working on: $proj",
                    category = MemoryCategory.PROJECTS.id,
                    importance = 0.75f,
                    confidence = 0.85f,
                    source = "CONVERSATION_INFERRED",
                    requiresConfirmation = true
                )
            }
        }

        return null
    }

    private fun categorizeContent(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("name") || lower.contains("live in") || lower.contains("born") || lower.contains("contact") || lower.contains("wife") || lower.contains("husband") -> MemoryCategory.PERSONAL.id
            lower.contains("prefer") || lower.contains("like") || lower.contains("love") || lower.contains("favorite") || lower.contains("dislike") || lower.contains("style") -> MemoryCategory.PREFERENCES.id
            lower.contains("routine") || lower.contains("daily") || lower.contains("morning") || lower.contains("night") || lower.contains("every day") -> MemoryCategory.ROUTINES.id
            lower.contains("device") || lower.contains("tv") || lower.contains("light") || lower.contains("speaker") || lower.contains("ac") -> MemoryCategory.DEVICES.id
            lower.contains("project") || lower.contains("work") || lower.contains("task") || lower.contains("code") || lower.contains("build") -> MemoryCategory.PROJECTS.id
            else -> MemoryCategory.GENERAL.id
        }
    }

    private fun deriveKey(text: String): String {
        val clean = text.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it !in STOP_WORDS }
            .take(4)
            .joinToString("_")
        return clean.ifBlank { "memory_${System.currentTimeMillis() % 10000}" }
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "is", "in", "to", "at", "for", "of", "and", "or", "that", "this",
            "my", "i", "me", "you", "user", "your", "with", "as", "on"
        )
    }
}
