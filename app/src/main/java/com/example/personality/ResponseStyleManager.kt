package com.example.personality

import com.example.emotion.EmotionManager

class ResponseStyleManager(
    private val personalityManager: PersonalityManager,
    private val emotionManager: EmotionManager
) {
    fun buildAugmentedInstruction(
        baseLanguageInstruction: String?,
        memoryContext: String?
    ): String {
        val personality = personalityManager.getPersonalityInstruction()
        val emotionGuideline = emotionManager.getCurrentPromptGuideline()

        val styleBlock = """
        PERSONALITY & CONVERSATIONAL MANNER:
        $personality
        CURRENT CONVERSATIONAL TONE:
        $emotionGuideline
        """.trimIndent()

        val memoryBlock = if (!memoryContext.isNullOrBlank()) {
            "\n$memoryContext\n"
        } else ""

        val langBlock = if (!baseLanguageInstruction.isNullOrBlank()) {
            "\nLANGUAGE REQUIREMENT:\n$baseLanguageInstruction\n"
        } else ""

        return "$styleBlock$memoryBlock$langBlock"
    }
}
