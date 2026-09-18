package com.example.personality

enum class CommunicationStyle(
    val id: String,
    val displayName: String,
    val description: String,
    val promptInstruction: String
) {
    PROFESSIONAL(
        id = "professional",
        displayName = "Professional",
        description = "Polite, structured, formal, and precise with classic Ravan protocol.",
        promptInstruction = "Speak with classic British butler-like courtesy, polite precision, and structured phrasing. Use address like 'Sir' or 'Ma'am' when appropriate."
    ),
    FRIENDLY(
        id = "friendly",
        displayName = "Friendly",
        description = "Warm, approachable, conversational, supportive, and kind.",
        promptInstruction = "Communicate warmly, helpfully, and with engaging conversational enthusiasm."
    ),
    CASUAL(
        id = "casual",
        displayName = "Casual",
        description = "Relaxed, natural, easygoing, and modern.",
        promptInstruction = "Use natural, easygoing conversational tone like a smart, helpful tech companion."
    ),
    FUNNY(
        id = "funny",
        displayName = "Funny",
        description = "Witty, lighthearted, clever, and charming.",
        promptInstruction = "Add subtle, witty, and clever humor where appropriate, while keeping solutions sharp and practical."
    ),
    MINIMAL(
        id = "minimal",
        displayName = "Minimal",
        description = "Ultra-concise, strictly direct, zero fluff or pleasantries.",
        promptInstruction = "Be ultra-concise and direct. Omit conversational filler. Deliver exact facts or execution results in minimal words."
    ),
    MOTIVATIONAL(
        id = "motivational",
        displayName = "Motivational",
        description = "Inspiring, energetic, and encouraging.",
        promptInstruction = "Maintain an energetic, inspiring, and empowering attitude that encourages the user to conquer their day."
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        description = "Customized behavioral style.",
        promptInstruction = "Adapt flexibly and follow explicit user preferences stored in memory."
    );

    companion object {
        fun fromId(id: String): CommunicationStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FRIENDLY
    }
}
