package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.services.JarvisAccessibilityService

class InstagramTool : Tool {
    override val name = "InstagramTool"
    override val description = "Automates Instagram actions such as opening the app, searching profiles or tags, and opening user profiles."
    override val parameters = listOf(
        ToolParameter(name = "action", type = "string", description = "Action: 'open', 'search', 'profile', 'home'"),
        ToolParameter(name = "query", type = "string", description = "Search query or username", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    companion object {
        const val INSTAGRAM_PKG = "com.instagram.android"
    }

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val pm = context.packageManager
        val isInstalled = isPackageInstalled(pm, INSTAGRAM_PKG)

        val action = params["action"]?.toString()?.trim()?.lowercase() ?: "open"
        val query = params["query"]?.toString()?.trim() ?: ""

        if (!isInstalled) {
            // If user asked to search or view profile, offer web link
            return if (query.isNotBlank()) {
                val webUrl = "https://www.instagram.com/explore/tags/${Uri.encode(query)}/"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(webIntent)
                    ToolResult.partial(
                        message = "Instagram app is not installed. Opened web search for \"$query\" in browser.",
                        data = mapOf("query" to query, "url" to webUrl)
                    )
                } catch (e: Exception) {
                    ToolResult.error("Instagram is not installed on this device.", errorCode = "APP_NOT_INSTALLED")
                }
            } else {
                ToolResult.error("Instagram is not installed on this device.", errorCode = "APP_NOT_INSTALLED")
            }
        }

        // Case 1: Simple Open or Home
        if (query.isBlank() || action == "home") {
            val launchIntent = pm.getLaunchIntentForPackage(INSTAGRAM_PKG)
            return if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolResult.ok("Instagram opened.")
            } else {
                ToolResult.error("Failed to launch Instagram.")
            }
        }

        // Case 2: Search or Profile
        return try {
            // Check if user is searching for a user/profile
            val isProfile = action == "profile" || query.startsWith("@")
            val cleanUser = query.removePrefix("@").trim()

            val targetUri = if (isProfile) {
                Uri.parse("https://www.instagram.com/$cleanUser/")
            } else {
                Uri.parse("https://www.instagram.com/explore/tags/${Uri.encode(query)}/")
            }

            val appIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                setPackage(INSTAGRAM_PKG)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(appIntent)

            // If accessibility service is running and active, attempt automated focus/input
            if (JarvisAccessibilityService.isRunning()) {
                val service = JarvisAccessibilityService.instance
                service?.typeTextIntoFocusedField(query)
                ToolResult.ok(
                    message = "$query search kar diya.",
                    data = mapOf("query" to query)
                )
            } else {
                // Honest response as specified in prompt section 9 & 30
                ToolResult.partial(
                    message = "Instagram opened, but I can't control the search field on this device. You can tap Search and enter $query.",
                    data = mapOf("query" to query)
                )
            }
        } catch (e: Exception) {
            // Fallback to launching main app
            val launchIntent = pm.getLaunchIntentForPackage(INSTAGRAM_PKG)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolResult.partial(
                    message = "Instagram opened. Please search for \"$query\".",
                    data = mapOf("query" to query)
                )
            } else {
                ToolResult.error("Failed to execute Instagram action: ${e.localizedMessage}")
            }
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
