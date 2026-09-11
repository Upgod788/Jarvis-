package com.example.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

class MediaControlTool : Tool {
    override val name = "MediaControlTool"
    override val description = "Controls phone media playback (play, pause, next, previous) and audio volume levels."
    override val parameters = listOf(
        ToolParameter("action", "string", "play, pause, toggle, next, previous, volume_up, volume_down, set_volume", required = true),
        ToolParameter("level", "number", "Volume percentage 0 to 100 for set_volume", required = false)
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString()?.lowercase() ?: "toggle"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.error("AudioManager unavailable.")

        return when (action) {
            "play", "resume" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
                ToolResult.ok("Media playback resumed.")
            }
            "pause", "stop" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
                ToolResult.ok("Media playback paused.")
            }
            "toggle" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ToolResult.ok("Toggled media playback.")
            }
            "next", "skip" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                ToolResult.ok("Skipped to next track.")
            }
            "previous", "prev" -> {
                sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                ToolResult.ok("Skipped to previous track.")
            }
            "volume_up", "louder" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                ToolResult.ok("Increased volume.")
            }
            "volume_down", "quieter" -> {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                ToolResult.ok("Decreased volume.")
            }
            "set_volume" -> {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val percent = when (val p = params["level"] ?: params["volume"]) {
                    is Number -> p.toInt().coerceIn(0, 100)
                    is String -> p.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    else -> 50
                }
                val targetVol = (percent * maxVol) / 100
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                ToolResult.ok("Volume set to $percent%.")
            }
            else -> ToolResult.error("Unknown media action: $action")
        }
    }

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        context.sendOrderedBroadcast(down, null)
        context.sendOrderedBroadcast(up, null)
    }

    override suspend fun verify(context: Context, params: Map<String, Any?>): Boolean = true
}
