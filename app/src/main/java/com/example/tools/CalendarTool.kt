package com.example.tools

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.Calendar

class CalendarTool : Tool {
    override val name = "CalendarTool"
    override val description = "Adds events to calendar or displays upcoming calendar agenda using official Android CalendarContract."
    override val parameters = listOf(
        ToolParameter("title", "string", "Title or summary of the event", required = true),
        ToolParameter("action", "string", "add_event, view_calendar", required = false),
        ToolParameter("hour", "number", "Hour of the event (0-23)", required = false),
        ToolParameter("minute", "number", "Minute of the event (0-59)", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase() ?: "add_event"
        val title = params["title"]?.toString()?.trim() ?: "Meeting"

        if (action == "view_calendar" || title.contains("what's on my calendar", ignoreCase = true) || title.contains("agenda", ignoreCase = true)) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                ToolResult.ok("Opened Calendar.")
            } catch (e: Exception) {
                ToolResult.error("Failed to open calendar: ${e.message}")
            }
        }

        // Add event
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1) // default tomorrow
            val h = params["hour"]?.toString()?.toIntOrNull() ?: 17 // default 5 PM
            val m = params["minute"]?.toString()?.toIntOrNull() ?: 0
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 60 * 60 * 1000)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            ToolResult.ok("Opened Calendar to add \"$title\".")
        } catch (e: Exception) {
            ToolResult.error("Could not add event to calendar: ${e.message}")
        }
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
