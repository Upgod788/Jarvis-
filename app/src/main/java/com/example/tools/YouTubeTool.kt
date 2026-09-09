package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

class YouTubeTool : Tool {
    override val name = "YouTubeTool"
    override val description = "Automates YouTube actions including opening YouTube and searching for videos, channels, or tutorials."
    override val parameters = listOf(
        ToolParameter(name = "action", type = "string", description = "Action: 'open' or 'search'"),
        ToolParameter(name = "query", type = "string", description = "Search query for YouTube videos", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    companion object {
        const val YOUTUBE_PKG = "com.google.android.youtube"
    }

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val pm = context.packageManager
        val isInstalled = isPackageInstalled(pm, YOUTUBE_PKG)
        val query = params["query"]?.toString()?.trim() ?: ""

        // Case 1: Just opening YouTube
        if (query.isBlank()) {
            if (isInstalled) {
                val launchIntent = pm.getLaunchIntentForPackage(YOUTUBE_PKG)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ToolResult.ok("YouTube opened.")
                }
            }
            // Fallback to browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(webIntent)
                ToolResult.ok("Opened YouTube in browser.")
            } catch (e: Exception) {
                ToolResult.error("Unable to open YouTube: ${e.localizedMessage}")
            }
        }

        // Case 2: Searching YouTube
        val encodedQuery = Uri.encode(query)
        val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")

        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(YOUTUBE_PKG)
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (isInstalled && intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ToolResult.ok(
                    message = "Searching YouTube for $query.",
                    data = mapOf("query" to query)
                )
            } else {
                // Use VIEW Intent with youtube deep link / web fallback
                val viewIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                    if (isInstalled) setPackage(YOUTUBE_PKG)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(viewIntent)
                ToolResult.ok(
                    message = "Searching YouTube for $query.",
                    data = mapOf("query" to query)
                )
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to search YouTube: ${e.localizedMessage}")
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
