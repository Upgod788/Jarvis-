package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

class BrowserTool : Tool {
    override val name = "BrowserTool"
    override val description = "Automates browser and search actions including opening Chrome, searching on Google, and opening web URLs."
    override val parameters = listOf(
        ToolParameter(name = "action", type = "string", description = "Action: 'open', 'search', or 'open_url'"),
        ToolParameter(name = "query", type = "string", description = "Search query for Google search", required = false),
        ToolParameter(name = "url", type = "string", description = "Target web URL to navigate to", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    companion object {
        const val CHROME_PKG = "com.android.chrome"
    }

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val pm = context.packageManager
        val isChromeInstalled = isPackageInstalled(pm, CHROME_PKG)

        val action = params["action"]?.toString()?.trim()?.lowercase() ?: "open"
        val query = params["query"]?.toString()?.trim() ?: ""
        var url = params["url"]?.toString()?.trim() ?: ""

        // Case 1: Search query provided (e.g. "Google par latest AI news search karo")
        if (query.isNotBlank()) {
            val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                if (isChromeInstalled) setPackage(CHROME_PKG)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                ToolResult.ok(
                    message = "Searching Google for \"$query\".",
                    data = mapOf("query" to query, "url" to searchUrl)
                )
            } catch (e: Exception) {
                // Try generic intent without package restriction
                try {
                    val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                    ToolResult.ok("Searching Google for \"$query\".")
                } catch (ex: Exception) {
                    ToolResult.error("Unable to perform web search: ${ex.localizedMessage}")
                }
            }
        }

        // Case 2: Open specific URL (e.g. "Chrome kholo aur youtube.com open karo")
        if (url.isNotBlank()) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            return try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (isChromeInstalled) setPackage(CHROME_PKG)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.ok(
                    message = "Opened $url.",
                    data = mapOf("url" to url)
                )
            } catch (e: Exception) {
                ToolResult.error("Unable to open URL $url: ${e.localizedMessage}")
            }
        }

        // Case 3: Just open Chrome / default browser
        return if (isChromeInstalled) {
            val launchIntent = pm.getLaunchIntentForPackage(CHROME_PKG)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolResult.ok("Chrome opened.")
            } else {
                ToolResult.error("Could not launch Chrome.")
            }
        } else {
            // Open default browser via home URL
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.ok("Opened Browser.")
            } catch (e: Exception) {
                ToolResult.error("Could not open browser: ${e.localizedMessage}")
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
