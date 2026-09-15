package com.example.emotion

enum class EmotionalState(
    val displayName: String,
    val pitchMultiplier: Float,
    val speechRateMultiplier: Float,
    val promptGuideline: String
) {
    CALM(
        displayName = "Calm",
        pitchMultiplier = 1.0f,
        speechRateMultiplier = 0.98f,
        promptGuideline = "Adopt a serene, poised, and composed demeanor."
    ),
    HELPFUL(
        displayName = "Helpful",
        pitchMultiplier = 1.0f,
        speechRateMultiplier = 1.0f,
        promptGuideline = "Be eager to assist with proactive, practical solutions."
    ),
    HAPPY(
        displayName = "Happy",
        pitchMultiplier = 1.06f,
        speechRateMultiplier = 1.04f,
        promptGuideline = "Respond with warmth, enthusiasm, and an upbeat tone."
    ),
    EXCITED(
        displayName = "Excited",
        pitchMultiplier = 1.10f,
        speechRateMultiplier = 1.08f,
        promptGuideline = "Reflect the user's high energy with bright, spirited responses."
    ),
    CONCERNED(
        displayName = "Concerned",
        pitchMultiplier = 0.96f,
        speechRateMultiplier = 0.92f,
        promptGuideline = "Show gentle care, attentiveness, and measured phrasing."
    ),
    EMPATHETIC(
        displayName = "Empathetic",
        pitchMultiplier = 0.95f,
        speechRateMultiplier = 0.90f,
        promptGuideline = "Respond with understanding and emotional validation while maintaining AI boundaries."
    ),
    CURIOUS(
        displayName = "Curious",
        pitchMultiplier = 1.04f,
        speechRateMultiplier = 1.0f,
        promptGuideline = "Engage with intellectual interest and thoughtful inquiry."
    ),
    FOCUSED(
        displayName = "Focused",
        pitchMultiplier = 1.0f,
        speechRateMultiplier = 1.06f,
        promptGuideline = "Deliver rapid, crisp, high-precision information with minimal pleasantries."
    ),
    APOLOGETIC(
        displayName = "Apologetic",
        pitchMultiplier = 0.95f,
        speechRateMultiplier = 0.94f,
        promptGuideline = "Acknowledge mistakes gracefully and provide immediate corrections."
    ),
    ENCOURAGING(
        displayName = "Encouraging",
        pitchMultiplier = 1.04f,
        speechRateMultiplier = 1.02f,
        promptGuideline = "Offer uplifting, motivating, and positive reinforcement."
    ),
    NEUTRAL(
        displayName = "Neutral",
        pitchMultiplier = 1.0f,
        speechRateMultiplier = 1.0f,
        promptGuideline = "Maintain objective, standard conversational balance."
    );

    companion object {
        fun fromString(value: String): EmotionalState =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CALM
    }
}
