package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

class NavigationTool : Tool {
    override val name = "NavigationTool"
    override val description = "Opens navigation and search for routes, destinations, and nearby places (e.g. petrol pumps, hospitals) in Google Maps."
    override val parameters = listOf(
        ToolParameter("destination", "string", "Target destination, address, or nearby place query", required = true),
        ToolParameter("mode", "string", "navigation, search, or route", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val dest = params["destination"]?.toString()?.trim() ?: "nearest petrol pump"
        val mode = params["mode"]?.toString()?.lowercase() ?: "navigation"

        val uri = if (mode == "navigation") {
            Uri.parse("google.navigation:q=${Uri.encode(dest)}")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(dest)}")
        }

        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                ToolResult.ok("Opening navigation to $dest in Google Maps.")
            } else {
                // Fallback to generic map/browser view
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(dest)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                ToolResult.ok("Opened route to $dest.")
            }
        } catch (e: Exception) {
            ToolResult.error("Could not open navigation for $dest: ${e.message}")
        }
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
