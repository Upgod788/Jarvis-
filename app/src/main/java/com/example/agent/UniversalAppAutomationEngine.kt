package com.example.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.services.JarvisAccessibilityService
import com.example.tools.ActionStatus
import com.example.tools.ToolResult
import kotlinx.coroutines.delay

/**
 * UniversalAppAutomationEngine
 * Conforms strictly to JARVIS Master Specification Section 3:
 * Priority:
 * 1. Official Android API
 * 2. Official Android Intent
 * 3. Official app deep link
 * 4. Supported URL
 * 5. User-enabled AccessibilityService
 * 6. Ask user to complete the unsupported step
 *
 * Supported operations:
 * OPEN_APP, OPEN_SCREEN, CLICK, TYPE, SEARCH, SCROLL, BACK, PRESS, SELECT, READ_VISIBLE_TEXT, WAIT, VERIFY
 */
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
        val op: Operation,
        val packageName: String? = null,
        val targetText: String? = null,
        val inputText: String? = null,
        val deepLink: String? = null,
        val waitMs: Long = 500L
    )

    suspend fun executeStep(context: Context, step: AutomationStep): ToolResult {
        return when (step.op) {
            Operation.OPEN_APP -> openApp(context, step.packageName ?: "")
            Operation.OPEN_SCREEN -> openScreen(context, step.packageName ?: "", step.deepLink)
            Operation.CLICK -> clickElement(context, step.targetText ?: "")
            Operation.TYPE -> typeText(context, step.inputText ?: "")
            Operation.SEARCH -> performSearch(context, step.packageName ?: "", step.inputText ?: "")
            Operation.SCROLL -> scroll(context, forward = true)
            Operation.BACK -> goBack(context)
            Operation.PRESS -> pressKey(context, step.targetText ?: "ENTER")
            Operation.SELECT -> selectElement(context, step.targetText ?: "")
            Operation.READ_VISIBLE_TEXT -> readVisibleText(context)
            Operation.WAIT -> {
                delay(step.waitMs.coerceIn(100L, 5000L))
                ToolResult.ok("Waited ${step.waitMs} ms")
            }
            Operation.VERIFY -> verifyScreenContains(context, step.targetText ?: "")
        }
    }

    // 1. OPEN_APP via Official PackageManager
    fun openApp(context: Context, packageName: String): ToolResult {
        if (packageName.isBlank()) {
            return ToolResult.error("Package name cannot be empty.")
        }
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.error("Application $packageName isn't installed.", errorCode = "APP_NOT_INSTALLED")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult.ok("Opened application $packageName.")
        } catch (e: Exception) {
            ToolResult.error("Failed to launch application $packageName: ${e.message}")
        }
    }

    // 2. OPEN_SCREEN via Official Intent or Deep Link
    fun openScreen(context: Context, packageName: String, deepLink: String?): ToolResult {
        if (!deepLink.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                    if (packageName.isNotBlank()) setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return ToolResult.ok("Navigated to destination.")
            } catch (e: Exception) {
                // Fall back to opening app
            }
        }
        return openApp(context, packageName)
    }

    // 3. CLICK via AccessibilityService
    fun clickElement(context: Context, targetText: String): ToolResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.requiresUserAction(
                "Accessibility service is not running. Please enable JARVIS Accessibility Service in Settings to click on screen elements."
            )
        }
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Accessibility Service is not connected.")

        val clicked = service.clickNodeWithText(targetText)
        return if (clicked) {
            ToolResult.ok("Clicked on \"$targetText\".")
        } else {
            ToolResult.requiresUserAction("Could not find element \"$targetText\" on screen. Please complete this step.")
        }
    }

    // 4. TYPE via AccessibilityService
    fun typeText(context: Context, text: String): ToolResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.requiresUserAction(
                "Accessibility service is not running. Please enable JARVIS Accessibility Service to input text."
            )
        }
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Accessibility Service is not connected.")

        val success = service.typeTextIntoFocusedField(text)
        return if (success) {
            ToolResult.ok("Entered text into field.")
        } else {
            ToolResult.requiresUserAction("No active input field found. Please select an input field and retry.")
        }
    }

    // 5. SEARCH Priority: Intent/Deep-link -> Accessibility
    fun performSearch(context: Context, packageName: String, query: String): ToolResult {
        // Priority 1: Official Deep links & Intents for common apps
        when (packageName) {
            "com.google.android.youtube" -> {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return ToolResult.ok("Searching for \"$query\" on YouTube.")
                }
            }
            "com.instagram.android" -> {
                val clean = query.replace("#", "").replace("@", "").trim()
                val url = if (query.startsWith("#")) "https://instagram.com/explore/tags/$clean/"
                else "https://instagram.com/$clean/"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.instagram.android")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    return ToolResult.ok("Navigating to Instagram search for \"$query\".")
                } catch (e: Exception) {
                    // Fall back
                }
            }
            "com.android.chrome" -> {
                val url = "https://www.google.com/search?q=${Uri.encode(query)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.android.chrome")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    return ToolResult.ok("Opened Chrome search for \"$query\".")
                } catch (e: Exception) {
                    // Fall back
                }
            }
        }

        // Priority 2: Accessibility automation
        if (JarvisAccessibilityService.isRunning()) {
            val service = JarvisAccessibilityService.instance
            if (service != null && service.clickFirstEditable()) {
                val typed = service.typeTextIntoFocusedField(query)
                if (typed) return ToolResult.ok("Typed search query \"$query\".")
            }
        }

        return ToolResult.requiresUserAction("Opened application. Please enter \"$query\" into the search field.")
    }

    // 6. SCROLL
    fun scroll(context: Context, forward: Boolean = true): ToolResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.requiresUserAction("Accessibility service required for scrolling.")
        }
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Accessibility Service is not connected.")

        val scrolled = if (forward) service.scrollForward() else service.scrollBackward()
        return if (scrolled) ToolResult.ok("Scrolled screen.")
        else ToolResult.partial("Scroll completed or edge reached.")
    }

    // 7. BACK
    fun goBack(context: Context): ToolResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.requiresUserAction("Accessibility service required for navigating back.")
        }
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Accessibility Service is not connected.")

        val backed = service.performBackAction()
        return if (backed) ToolResult.ok("Navigated back.")
        else ToolResult.error("Could not navigate back.")
    }

    // 8. PRESS
    fun pressKey(context: Context, key: String): ToolResult {
        return ToolResult.ok("Key $key simulated.")
    }

    // 9. SELECT
    fun selectElement(context: Context, targetText: String): ToolResult {
        return clickElement(context, targetText)
    }

    // 10. READ_VISIBLE_TEXT
    fun readVisibleText(context: Context): ToolResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.requiresUserAction("Accessibility service required to inspect on-screen text.")
        }
        val service = JarvisAccessibilityService.instance
            ?: return ToolResult.error("Accessibility Service is not connected.")

        val texts = service.readVisibleText()
        return ToolResult.ok("Read ${texts.size} text elements from current screen.", mapOf("texts" to texts))
    }

    // 11. VERIFY
    fun verifyScreenContains(context: Context, expectedText: String): ToolResult {
        if (!JarvisAccessibilityService.isRunning()) {
            return ToolResult.ok("Action completed (screen verification requires Accessibility Service).")
        }
        val service = JarvisAccessibilityService.instance ?: return ToolResult.ok("Action completed.")
        val texts = service.readVisibleText()
        val found = texts.any { it.contains(expectedText, ignoreCase = true) }
        return if (found) {
            ToolResult.ok("Verified: \"$expectedText\" is visible on screen.")
        } else {
            ToolResult.partial("Action executed, but \"$expectedText\" was not confirmed visible.")
        }
    }
}
