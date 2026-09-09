package com.example.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

class AlarmTool : Tool {
    override val name = "AlarmTool"
    override val description = "Sets an alarm for a specific hour and minute using official Android Clock APIs."
    override val parameters = listOf(
        ToolParameter(name = "hour", type = "number", description = "Hour in 24-hour format (0-23) or 12-hour with am/pm specified"),
        ToolParameter(name = "minute", type = "number", description = "Minute (0-59)", required = false),
        ToolParameter(name = "amPm", type = "string", description = "'am' or 'pm' if 12-hour format used", required = false),
        ToolParameter(name = "label", type = "string", description = "Label or note for the alarm", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val rawHour = params["hour"]?.toString()?.toDoubleOrNull()?.toInt()
            ?: return ToolResult.error("Please specify a valid hour for the alarm.")
        val minute = params["minute"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
        val amPm = params["amPm"]?.toString()?.trim()?.lowercase()
        val label = params["label"]?.toString()?.trim() ?: "JARVIS Alarm"

        var hour = rawHour
        if (amPm == "pm" && hour in 1..11) {
            hour += 12
        } else if (amPm == "am" && hour == 12) {
            hour = 0
        }

        if (hour !in 0..23 || minute !in 0..59) {
            return ToolResult.error("Invalid time: $hour:${String.format("%02d", minute)}.")
        }

        val displayTime = formatTime(hour, minute)

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.ok("Alarm set for $displayTime.")
            } else {
                ToolResult.error("No alarm clock application found on this device.")
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to set alarm: ${e.localizedMessage}")
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val period = if (hour >= 12) "PM" else "AM"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$h12:${String.format("%02d", minute)} $period"
    }
}

class TimerTool : Tool {
    override val name = "TimerTool"
    override val description = "Sets a countdown timer for a specified duration in minutes or seconds."
    override val parameters = listOf(
        ToolParameter(name = "durationMinutes", type = "number", description = "Duration in minutes", required = false),
        ToolParameter(name = "durationSeconds", type = "number", description = "Duration in seconds", required = false),
        ToolParameter(name = "label", type = "string", description = "Label for the timer", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val minutes = params["durationMinutes"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
        val seconds = params["durationSeconds"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
        val totalSeconds = (minutes * 60) + seconds
        val label = params["label"]?.toString()?.trim() ?: "JARVIS Timer"

        if (totalSeconds <= 0) {
            return ToolResult.error("Timer duration must be greater than 0.")
        }

        val display = when {
            totalSeconds >= 60 && totalSeconds % 60 == 0 -> "${totalSeconds / 60} minute(s)"
            totalSeconds >= 60 -> "${totalSeconds / 60} min ${totalSeconds % 60} sec"
            else -> "$totalSeconds seconds"
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.ok("Timer started for $display.")
            } else {
                ToolResult.error("No timer application found on this device.")
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to start timer: ${e.localizedMessage}")
        }
    }
}
