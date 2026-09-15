package com.example.emotion

import java.util.Locale

class EmotionDetector {

    fun detectEmotion(userMessage: String): EmotionalState {
        val lower = userMessage.lowercase(Locale.ROOT)

        // 1. User frustration or mistake correction
        if (lower.contains("you are wrong") || lower.contains("incorrect") ||
            lower.contains("that's not what i asked") || lower.contains("mistake") ||
            lower.contains("you failed") || lower.contains("stop doing that")
        ) {
            return EmotionalState.APOLOGETIC
        }

        // 2. High excitement or celebration
        if (lower.contains("awesome") || lower.contains("fantastic") ||
            lower.contains("celebrat") || lower.contains("let's go") ||
            lower.contains("super excited") || lower.contains("hurray") ||
            lower.contains("woohoo") || lower.contains("got selected") ||
            lower.contains("i won")
        ) {
            return EmotionalState.EXCITED
        }

        // 3. User distress, sadness, or fatigue
        if (lower.contains("feeling sad") || lower.contains("feeling down") ||
            lower.contains("i'm depressed") || lower.contains("bad day") ||
            lower.contains("terrible day") || lower.contains("heartbroken") ||
            lower.contains("exhausted") || lower.contains("failed my") ||
            lower.contains("feeling sick")
        ) {
            return EmotionalState.EMPATHETIC
        }

        // 4. User anxiety, nervousness, or need for encouragement
        if (lower.contains("nervous") || lower.contains("wish me luck") ||
            lower.contains("scared about") || lower.contains("can i do this") ||
            lower.contains("give me confidence") || lower.contains("motivate me")
        ) {
            return EmotionalState.ENCOURAGING
        }

        // 5. Gratitude and happiness
        if (lower.contains("thank you") || lower.contains("thanks a lot") ||
            lower.contains("love this") || lower.contains("you're great") ||
            lower.contains("good morning") || lower.contains("happy")
        ) {
            return EmotionalState.HAPPY
        }

        // 6. Deep curiosity / intellectual exploration
        if (lower.startsWith("why is") || lower.startsWith("how does") ||
            lower.contains("what is the reason") || lower.contains("theory behind") ||
            lower.contains("explain why")
        ) {
            return EmotionalState.CURIOUS
        }

        // 7. Urgent or crisp command
        if (lower.contains("urgent") || lower.contains("immediately") ||
            lower.contains("quickly") || lower.contains("fast") ||
            lower.startsWith("open ") || lower.startsWith("call ") ||
            lower.startsWith("turn on ") || lower.startsWith("turn off ")
        ) {
            return EmotionalState.FOCUSED
        }

        // Default: Poised, calm, and helpful
        return EmotionalState.CALM
    }
}
