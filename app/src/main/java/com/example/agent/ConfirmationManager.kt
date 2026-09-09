package com.example.agent

import com.example.tools.RiskLevel
import com.example.tools.Tool

data class ConfirmationRequest(
    val toolName: String,
    val riskLevel: RiskLevel,
    val title: String,
    val message: String,
    val parameters: Map<String, Any?>,
    val onConfirm: suspend () -> Unit,
    val onCancel: () -> Unit
)

class ConfirmationManager {

    var requireCallConfirmation: Boolean = true
    var requireSmsConfirmation: Boolean = true
    var confirmMessagesBeforeSending: Boolean = true
    var confirmDestructiveActions: Boolean = true
    var requireMediumRiskConfirmation: Boolean = true

    fun requiresConfirmation(tool: Tool, params: Map<String, Any?>): Boolean {
        if (tool.riskLevel == RiskLevel.HIGH) return true
        if (tool.name == "CallContactTool" && requireCallConfirmation) return true
        if (tool.name == "SendSmsTool" && (requireSmsConfirmation || confirmMessagesBeforeSending)) return true
        if (tool.name == "WhatsAppTool") {
            val message = params["message"]?.toString()?.trim() ?: ""
            if (message.isNotBlank() && confirmMessagesBeforeSending) return true
        }
        if (tool.riskLevel == RiskLevel.MEDIUM && requireMediumRiskConfirmation) return true
        return tool.requiresConfirmation
    }

    fun buildConfirmationDetails(tool: Tool, params: Map<String, Any?>): Pair<String, String> {
        return when (tool.name) {
            "WhatsAppTool" -> {
                val recipient = params["contactName"] ?: params["phoneNumber"] ?: "Contact"
                val message = params["message"] ?: ""
                Pair(
                    "WhatsApp Message",
                    "To: $recipient\n\nMessage:\n\"$message\""
                )
            }
            "CallContactTool" -> {
                val target = params["contactName"] ?: params["phoneNumber"] ?: "contact"
                Pair("Confirm Phone Call", "Do you want JARVIS to place a call to \"$target\"?")
            }
            "SendSmsTool" -> {
                val recipient = params["recipient"] ?: "recipient"
                val message = params["message"] ?: ""
                Pair("Confirm SMS", "Send message to \"$recipient\":\n\n\"$message\"?")
            }
            else -> {
                Pair("Confirm Action", "Do you want JARVIS to execute ${tool.name}?")
            }
        }
    }
}
