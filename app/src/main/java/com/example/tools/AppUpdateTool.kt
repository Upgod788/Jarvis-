package com.example.tools

import android.content.Context
import com.example.update.CheckResult
import com.example.update.InstallResult
import com.example.update.UpdateManager

class AppUpdateTool(
    private val updateManagerProvider: () -> UpdateManager
) : Tool {
    override val name: String = "app_update"
    override val description: String =
        "Check for JARVIS application updates, download the latest version, view release notes, or initiate official Android package installation."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "The update action to perform: 'check', 'download', 'install', or 'whats_new'",
            required = true
        )
    )

    override val requiredPermissions: List<String> = emptyList()

    override val riskLevel: RiskLevel = RiskLevel.MEDIUM

    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = (params["action"] as? String)?.lowercase()?.trim() ?: "check"
        val updateManager = updateManagerProvider()

        return when (action) {
            "check", "status", "version" -> {
                val result = updateManager.checker.checkForUpdates(forceRemote = true)
                when (result) {
                    is CheckResult.UpdateAvailable -> {
                        val m = result.manifest
                        val notes = m.releaseNotes.take(2).joinToString("; ")
                        ToolResult.ok(
                            message = "A new version of JARVIS is available: version ${m.latestVersionName}. " +
                                    (if (notes.isNotBlank()) "Highlights: $notes. " else "") +
                                    "Would you like me to download and install it?",
                            data = mapOf(
                                "versionName" to m.latestVersionName,
                                "versionCode" to m.latestVersionCode,
                                "mandatory" to m.mandatory,
                                "releaseNotes" to m.releaseNotes
                            )
                        )
                    }
                    is CheckResult.UpToDate -> {
                        ToolResult.ok(
                            message = "You are already running the latest version of JARVIS (version ${result.currentVersionName}). Everything is up to date.",
                            data = mapOf("version" to result.currentVersionName)
                        )
                    }
                    is CheckResult.Error -> {
                        ToolResult.ok(
                            message = "I couldn't reach the update server right now: ${result.reason}. Local device operations are functioning normally.",
                            data = mapOf("offline" to result.isOffline)
                        )
                    }
                }
            }

            "download" -> {
                val currentStatus = updateManager.status.value
                val checkResult = updateManager.checker.checkForUpdates()
                if (checkResult is CheckResult.UpdateAvailable) {
                    updateManager.startDownload(checkResult.manifest)
                    ToolResult.ok(
                        message = "Starting secure download for JARVIS version ${checkResult.manifest.latestVersionName}. You can monitor progress in Settings > App Updates.",
                        data = mapOf("version" to checkResult.manifest.latestVersionName)
                    )
                } else {
                    ToolResult.ok("JARVIS is already on the latest version. No update download is needed.")
                }
            }

            "install" -> {
                val installResult = updateManager.installDownloadedApk()
                when (installResult) {
                    is InstallResult.SuccessLaunched -> {
                        ToolResult.ok("Opening the Android Package Installer. Please approve the prompt to complete the update.")
                    }
                    is InstallResult.PermissionRequired -> {
                        context.startActivity(installResult.settingsIntent)
                        ToolResult.ok("Android requires permission to install updates from this app. Please enable 'Allow from this source' on the screen that just opened.")
                    }
                    is InstallResult.Error -> {
                        ToolResult.error("Unable to start installation: ${installResult.message}")
                    }
                }
            }

            "whats_new", "changelog", "release_notes" -> {
                val checkResult = updateManager.checker.checkForUpdates()
                if (checkResult is CheckResult.UpdateAvailable) {
                    val notes = checkResult.manifest.releaseNotes.joinToString(". ")
                    ToolResult.ok("What's new in JARVIS version ${checkResult.manifest.latestVersionName}: $notes")
                } else {
                    ToolResult.ok(
                        "You are on JARVIS version ${updateManager.currentVersionName}. " +
                        "Current features include full voice device automation, smart routines, dynamic AI engine selection, and secure auto-updates."
                    )
                }
            }

            else -> {
                ToolResult.error("Unknown update command action: $action. Supported actions are: check, download, install, whats_new.")
            }
        }
    }
}
