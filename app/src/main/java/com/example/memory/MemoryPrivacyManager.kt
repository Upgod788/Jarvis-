package com.example.memory

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

class MemoryPrivacyManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_memory_privacy", Context.MODE_PRIVATE)

    private val _isMemoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEMORY_ENABLED, true))
    val isMemoryEnabled: StateFlow<Boolean> = _isMemoryEnabled.asStateFlow()

    private val _isMemoryPaused = MutableStateFlow(prefs.getBoolean(KEY_MEMORY_PAUSED, false))
    val isMemoryPaused: StateFlow<Boolean> = _isMemoryPaused.asStateFlow()

    fun isMemoryActive(): Boolean = _isMemoryEnabled.value && !_isMemoryPaused.value

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply()
        _isMemoryEnabled.value = enabled
    }

    fun setMemoryPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_PAUSED, paused).apply()
        _isMemoryPaused.value = paused
    }

    /**
     * Checks if the given text contains sensitive information like passwords, OTPs,
     * credit cards, authentication tokens, or private secrets.
     */
    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()

        // 1. Passwords / Passcodes / Pins / Secrets
        if (PASSWORD_PATTERN.matcher(text).find()) return true

        // 2. OTPs / Verification codes
        if (OTP_PATTERN.matcher(text).find()) return true

        // 3. Credit / Debit card patterns (13 to 19 digits)
        if (CARD_PATTERN.matcher(text).find()) return true

        // 4. API keys / Auth tokens / Bearer tokens / Private keys
        if (TOKEN_PATTERN.matcher(text).find()) return true

        // 5. Keyword checks for credentials
        val sensitiveKeywords = listOf(
            "my password is", "my password:", "password is",
            "my pin is", "my pin:", "pin is",
            "my otp is", "my otp:", "otp is",
            "credit card", "cvv", "private key",
            "access token", "auth token", "secret key"
        )
        if (sensitiveKeywords.any { lower.contains(it) }) return true

        return false
    }

    companion object {
        private const val KEY_MEMORY_ENABLED = "key_memory_enabled"
        private const val KEY_MEMORY_PAUSED = "key_memory_paused"

        private val PASSWORD_PATTERN = Pattern.compile(
            """(?i)\b(password|passcode|secret\s*key|pin\s*code|credentials?)\s*[:=]?\s*\S+""",
            Pattern.CASE_INSENSITIVE
        )

        private val OTP_PATTERN = Pattern.compile(
            """(?i)\b(otp|one[\s-]?time[\s-]?password|verification[\s-]?code)\s*[:=]?\s*\d{4,8}\b""",
            Pattern.CASE_INSENSITIVE
        )

        private val CARD_PATTERN = Pattern.compile(
            """\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12})\b"""
        )

        private val TOKEN_PATTERN = Pattern.compile(
            """(?i)\b(bearer\s+[a-zA-Z0-9_\-\.]{20,}|api[_-]?key\s*[:=]\s*[a-zA-Z0-9_\-]{16,}|ghp_[a-zA-Z0-9]{20,}|sk-[a-zA-Z0-9]{20,})\b""",
            Pattern.CASE_INSENSITIVE
        )
    }
}
