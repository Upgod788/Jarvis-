package com.example.agent

import android.content.Context
import android.content.Intent
import com.example.services.JarvisAccessibilityService
import com.example.tools.ToolResult
import kotlinx.coroutines.delay

object UniversalAppAutomationEngine {
    enum class Operation {
        OPEN_APP,
        OPEN_SCREEN,
        CLICK,
        TYPE,
        SEARCH,
        SCROLL,
        BACK,
        PRESS,
        SELECT,
        READ_VISIBLE_TEXT,
        WAIT,
        VERIFY
    }

    data class AutomationStep(
        val operation: Operation,
        val target: String = "",
        val text: String = "",
        val delayMs: Long = 500L
    )

    fun openApp(context: Context, packageName: String): ToolResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.error("App $packageName not installed on device.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return ToolResult.ok("Opened app: $packageName")
    }

    fun clickElement(context: Context, targetText: String): ToolResult {
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Jarvis Accessibility Service is not active. Please enable it in Settings.")
        val success = service.clickNodeWithText(targetText)
        return if (success) {
            ToolResult.ok("Clicked '$targetText'.")
        } else {
            ToolResult.error("Could not find '$targetText' on current screen.")
        }
    }

    fun typeText(context: Context, text: String): ToolResult {
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Jarvis Accessibility Service is not active.")
        val success = service.typeTextIntoFocusedField(text)
        return if (success) {
            ToolResult.ok("Typed text: '$text'.")
        } else {
            ToolResult.error("Could not type text into input field.")
        }
    }

    fun scroll(context: Context, forward: Boolean = true): ToolResult {
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Jarvis Accessibility Service is not active.")
        val success = if (forward) service.scrollForward() else service.scrollBackward()
        return if (success) {
            ToolResult.ok("Scrolled screen.")
        } else {
            ToolResult.error("Could not scroll.")
        }
    }

    fun goBack(context: Context): ToolResult {
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Jarvis Accessibility Service is not active.")
        val success = service.performBackAction()
        return if (success) {
            ToolResult.ok("Went back.")
        } else {
            ToolResult.error("Failed to perform back gesture.")
        }
    }

    suspend fun executeStep(context: Context, step: AutomationStep): ToolResult {
        if (step.delayMs > 0) {
            delay(step.delayMs)
        }
        return when (step.operation) {
            Operation.OPEN_APP -> openApp(context, step.target)
            Operation.CLICK -> clickElement(context, step.target)
            Operation.TYPE -> typeText(context, step.text)
            Operation.SCROLL -> scroll(context, step.target != "backward")
            Operation.BACK -> goBack(context)
            Operation.WAIT -> {
                delay(step.delayMs)
                ToolResult.ok("Waited for ${step.delayMs}ms.")
            }
            else -> ToolResult.ok("Executed step: ${step.operation}")
        }
    }
}
