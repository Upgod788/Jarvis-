package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

class OpenAppTool : Tool {
    override val name = "OpenAppTool"
    override val description = "Opens an installed Android application (e.g. WhatsApp, YouTube, Chrome, Instagram, Camera, Spotify, Settings)."
    override val parameters = listOf(
        ToolParameter(name = "appName", type = "string", description = "Common name of the app to open (e.g. whatsapp, youtube, chrome, camera, settings)")
    )
    override val requiredPermissions = emptyList<String>()
    override val riskLevel = RiskLevel.LOW
    override val requiresConfirmation = false

    private val commonPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "spotify" to "com.spotify.music",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "play store" to "com.android.vending",
        "google play" to "com.android.vending",
        "calculator" to "com.google.android.calculator",
        "calc" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "calendar" to "com.google.android.calendar",
        "photos" to "com.google.android.apps.photos",
        "gallery" to "com.google.android.apps.photos",
        "messages" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "telegram" to "org.telegram.messenger",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "keep" to "com.google.android.keep",
        "notes" to "com.google.android.keep",
        "files" to "com.google.android.documentsui",
        "netflix" to "com.netflix.mediaclient"
    )

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val appName = params["appName"]?.toString()?.trim()?.lowercase()
            ?: return ToolResult.error("No application name provided.")

        val pm: PackageManager = context.packageManager

        // Special system apps
        if (appName.contains("setting") || appName == "settings") {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult.ok("Opened Settings.")
        }

        if (appName.contains("camera")) {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ToolResult.ok("Opened Camera.")
            } else {
                val launchIntent = pm.getLaunchIntentForPackage("com.android.camera")
                    ?: pm.getLaunchIntentForPackage("com.google.android.GoogleCamera")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ToolResult.ok("Opened Camera.")
                } else {
                    ToolResult.error("Camera app could not be opened on this device.", errorCode = "APP_NOT_FOUND")
                }
            }
        }

        // Map standard known app names
        val knownPkg = commonPackages.entries.firstOrNull { appName.contains(it.key) }?.value

        val intent = if (knownPkg != null) {
            pm.getLaunchIntentForPackage(knownPkg)
        } else {
            // Search all installed packages by label
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val matchedApp = installedApps.firstOrNull {
                val label = pm.getApplicationLabel(it).toString().lowercase()
                label.contains(appName) || appName.contains(label)
            }
            matchedApp?.let { pm.getLaunchIntentForPackage(it.packageName) }
        }

        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                val displayName = appName.replaceFirstChar { it.uppercase() }
                ToolResult.ok("Opened $displayName.")
            } catch (e: Exception) {
                ToolResult.error("Failed to launch $appName: ${e.localizedMessage}")
            }
        } else {
            val displayName = appName.replaceFirstChar { it.uppercase() }
            ToolResult.error(
                message = "$displayName is not installed on this device.",
                errorCode = "APP_NOT_INSTALLED",
                data = mapOf("appName" to appName)
            )
        }
    }
}
