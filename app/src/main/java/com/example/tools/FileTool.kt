package com.example.tools

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri

class FileTool : Tool {
    override val name = "FileTool"
    override val description = "Performs safe file operations such as opening the Downloads folder or documents viewer using standard Android intents."
    override val parameters = listOf(
        ToolParameter("action", "string", "open_downloads, view_documents, share_file", required = true),
        ToolParameter("fileName", "string", "Optional target file name or mime type", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase() ?: "open_downloads"

        return when (action) {
            "open_downloads", "downloads" -> {
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ToolResult.ok("Opened Downloads folder.")
                } catch (e: Exception) {
                    val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                    ToolResult.ok("Opened File Manager.")
                }
            }

            "view_documents", "pdf" -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/pdf"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ToolResult.ok("Opened documents viewer.")
                } catch (e: Exception) {
                    ToolResult.error("Could not open documents viewer: ${e.message}")
                }
            }

            else -> {
                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ToolResult.ok("Opened Downloads.")
                } catch (e: Exception) {
                    ToolResult.error("Failed to open files: ${e.message}")
                }
            }
        }
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
