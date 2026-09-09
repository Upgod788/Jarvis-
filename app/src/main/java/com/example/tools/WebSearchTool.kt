package com.example.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri

class WebSearchTool : Tool {
    override val name = "WebSearchTool"
    override val description = "Searches the web or YouTube using default browser or search intents."
    override val parameters = listOf(
        ToolParameter(name = "query", type = "string", description = "The search query text"),
        ToolParameter(name = "target", type = "string", description = "Optional target: 'web' or 'youtube'", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val query = params["query"]?.toString()?.trim()
            ?: return ToolResult.error("Search query is missing.")
        val target = params["target"]?.toString()?.lowercase() ?: "web"

        return try {
            if (target.contains("youtube")) {
                val ytUri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
                val intent = Intent(Intent.ACTION_VIEW, ytUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.ok("Searching YouTube for \"$query\".")
            } else {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                }
                ToolResult.ok("Searching the web for \"$query\".")
            }
        } catch (e: Exception) {
            ToolResult.error("Unable to perform web search: ${e.localizedMessage}")
        }
    }
}

class OpenUrlTool : Tool {
    override val name = "OpenUrlTool"
    override val description = "Opens a website URL in the device web browser."
    override val parameters = listOf(
        ToolParameter(name = "url", type = "string", description = "The full or partial URL to open")
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        var url = params["url"]?.toString()?.trim()
            ?: return ToolResult.error("URL is required.")

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.ok("Opening $url.")
        } catch (e: Exception) {
            ToolResult.error("Failed to open URL $url: ${e.localizedMessage}")
        }
    }
}
