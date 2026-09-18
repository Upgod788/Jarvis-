package com.example.agent

import android.content.Context
import android.content.SharedPreferences
import com.example.tools.RiskLevel
import com.example.tools.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConfirmationRequest(
    val tool: Tool,
    val actionName: String,
    val riskLevel: RiskLevel,
    val title: String,
    val message: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val onConfirm: suspend () -> Unit,
    val onCancel: () -> Unit
)

data class ConfirmationSettings(
    val requireCallConfirmation: Boolean = true,
    val requireSmsConfirmation: Boolean = true,
    val confirmWhatsAppMessages: Boolean = true,
    val confirmDestructiveActions: Boolean = true,
    val requireMediumRiskConfirmation: Boolean = true
)

class ConfirmationManager(context: Context? = null) {
    companion object {
        private const val PREFS_NAME = "jarvis_confirmation_prefs"
        private const val KEY_CONFIRM_CALLS = "confirm_calls"
        private const val KEY_CONFIRM_SMS = "confirm_sms"
        private const val KEY_CONFIRM_WHATSAPP = "confirm_whatsapp"
        private const val KEY_CONFIRM_DESTRUCTIVE = "confirm_destructive"
        private const val KEY_CONFIRM_MEDIUM_RISK = "confirm_medium_risk"
    }

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ConfirmationSettings> = _settings.asStateFlow()

    private fun loadSettings(): ConfirmationSettings {
        if (prefs == null) return ConfirmationSettings()
        return ConfirmationSettings(
            requireCallConfirmation = prefs.getBoolean(KEY_CONFIRM_CALLS, true),
            requireSmsConfirmation = prefs.getBoolean(KEY_CONFIRM_SMS, true),
            confirmWhatsAppMessages = prefs.getBoolean(KEY_CONFIRM_WHATSAPP, true),
            confirmDestructiveActions = prefs.getBoolean(KEY_CONFIRM_DESTRUCTIVE, true),
            requireMediumRiskConfirmation = prefs.getBoolean(KEY_CONFIRM_MEDIUM_RISK, true)
        )
    }

    var requireCallConfirmation: Boolean
        get() = _settings.value.requireCallConfirmation
        set(value) = setCallConfirmation(value)

    var requireSmsConfirmation: Boolean
        get() = _settings.value.requireSmsConfirmation
        set(value) = setSmsConfirmation(value)

    var confirmMessagesBeforeSending: Boolean
        get() = _settings.value.confirmWhatsAppMessages
        set(value) = setWhatsAppConfirmation(value)

    var confirmDestructiveActions: Boolean
        get() = _settings.value.confirmDestructiveActions
        set(value) {
            prefs?.edit()?.putBoolean(KEY_CONFIRM_DESTRUCTIVE, value)?.apply()
            _settings.value = _settings.value.copy(confirmDestructiveActions = value)
        }

    var requireMediumRiskConfirmation: Boolean
        get() = _settings.value.requireMediumRiskConfirmation
        set(value) {
            prefs?.edit()?.putBoolean(KEY_CONFIRM_MEDIUM_RISK, value)?.apply()
            _settings.value = _settings.value.copy(requireMediumRiskConfirmation = value)
        }

    fun setCallConfirmation(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CONFIRM_CALLS, enabled)?.apply()
        _settings.value = _settings.value.copy(requireCallConfirmation = enabled)
    }

    fun setSmsConfirmation(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CONFIRM_SMS, enabled)?.apply()
        _settings.value = _settings.value.copy(requireSmsConfirmation = enabled)
    }

    fun setWhatsAppConfirmation(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CONFIRM_WHATSAPP, enabled)?.apply()
        _settings.value = _settings.value.copy(confirmWhatsAppMessages = enabled)
    }

    fun requiresConfirmation(tool: Tool, params: Map<String, Any?>): Boolean {
        if (tool.riskLevel == RiskLevel.HIGH) return true
        if (tool.name == "CallContactTool" && requireCallConfirmation) return true
        if (tool.name == "SendSmsTool" && (requireSmsConfirmation || confirmMessagesBeforeSending)) return true
        if (tool.name == "WhatsAppTool") {
            val msg = (params["message"] as? String)?.trim().orEmpty()
            if (msg.isNotBlank() && confirmMessagesBeforeSending) return true
        }
        if (tool.riskLevel == RiskLevel.MEDIUM && requireMediumRiskConfirmation) return true
        return tool.requiresConfirmation
    }

    fun buildConfirmationDetails(tool: Tool, params: Map<String, Any?>): Pair<String, String> {
        return when (tool.name) {
            "WhatsAppTool" -> {
                val recipient = params["contactName"] ?: params["phoneNumber"] ?: "Contact"
                val message = params["message"] ?: ""
                Pair("WhatsApp Message", "To: $recipient\n\nMessage:\n\"$message\"")
            }
            "SendSmsTool" -> {
                val recipient = params["recipient"] ?: "recipient"
                val message = params["message"] ?: ""
                Pair("Confirm SMS", "Send message to \"$recipient\":\n\n\"$message\"?")
            }
            "CallContactTool" -> {
                val target = params["contactName"] ?: params["phoneNumber"] ?: "contact"
                Pair("Confirm Phone Call", "Do you want Ravan to place a call to \"$target\"?")
            }
            else -> {
                Pair("Confirm Action", "Do you want Ravan to execute ${tool.name}?")
            }
        }
    }
}
