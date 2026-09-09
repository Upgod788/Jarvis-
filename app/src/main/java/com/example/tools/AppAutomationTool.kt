package com.example.tools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.services.JarvisAccessibilityService

class AppAutomationTool : Tool {
    override val name = "AppAutomationTool"
    override val description = "Executes legitimate UI actions using user-enabled Accessibility Service (such as clicking buttons, typing text, scrolling, or navigating back)."
    override val parameters = listOf(
        ToolParameter(name = "packageName", type = "string", description = "Target app package name", required = false),
        ToolParameter(name = "action", type = "string", description = "OPEN_APP, OPEN_SCREEN, CLICK_TEXT, TYPE_TEXT, SCROLL, BACK, PRESS_ENTER, READ_VISIBLE_TEXT"),
        ToolParameter(name = "targetText", type = "string", description = "Target button or label text to click", required = false),
        ToolParameter(name = "inputText", type = "string", description = "Text to type into input field", required = false),
        ToolParameter(name = "searchQuery", type = "string", description = "Search query to enter", required = false),
        ToolParameter(name = "timeout", type = "number", description = "Timeout in milliseconds", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.uppercase() ?: "OPEN_APP"
        val packageName = params["packageName"]?.toString()?.trim() ?: ""
        val targetText = params["targetText"]?.toString()?.trim() ?: ""
        val inputText = params["inputText"]?.toString()?.trim() ?: params["searchQuery"]?.toString()?.trim() ?: ""

        // Check if user has enabled accessibility service
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.requiresUserAction(
                message = "Accessibility automation is not enabled. Please enable JARVIS Accessibility Service in Settings to allow automated screen interaction.",
                data = mapOf("serviceStatus" to "DISABLED", "action" to action)
            )
        }

        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Accessibility Service instance is not reachable.")

        return when (action) {
            "OPEN_APP" -> {
                if (packageName.isBlank()) {
                    ToolResult.error("Package name is required for OPEN_APP.")
                } else {
                    val pm = context.packageManager
                    val intent = pm.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ToolResult.ok("Opened application $packageName.")
                    } else {
                        ToolResult.error("Application $packageName is not installed.", errorCode = "APP_NOT_INSTALLED")
                    }
                }
            }

            "CLICK_TEXT" -> {
                if (targetText.isBlank()) {
                    ToolResult.error("targetText is required for CLICK_TEXT.")
                } else {
                    val clicked = service.clickNodeWithText(targetText)
                    if (clicked) {
                        ToolResult.ok("Clicked on \"$targetText\".")
                    } else {
                        ToolResult.partial("Could not find visible element with text \"$targetText\".")
                    }
                }
            }

            "TYPE_TEXT" -> {
                if (inputText.isBlank()) {
                    ToolResult.error("inputText is required for TYPE_TEXT.")
                } else {
                    val typed = service.typeTextIntoFocusedField(inputText)
                    if (typed) {
                        ToolResult.ok("Entered \"$inputText\".")
                    } else {
                        ToolResult.partial("No focused editable field found to enter text.")
                    }
                }
            }

            "BACK" -> {
                val back = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                if (back) ToolResult.ok("Navigated back.") else ToolResult.error("Failed to perform back action.")
            }

            "SCROLL" -> {
                val scrolled = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                ToolResult.ok("Performed screen action.")
            }

            "READ_VISIBLE_TEXT" -> {
                val texts = service.readVisibleText()
                ToolResult.ok(
                    message = "Read ${texts.size} visible text elements.",
                    data = mapOf("visibleTexts" to texts.take(15))
                )
            }

            else -> ToolResult.error("Unsupported automation action: $action")
        }
    }
}
