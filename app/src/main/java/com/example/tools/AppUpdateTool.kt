package com.example.tools

import android.content.Context
import com.example.update.CheckResult
import com.example.update.InstallResult
import com.example.update.UpdateManager
import com.example.update.UpdateStatus

class AppUpdateTool(
    private val updateManagerProvider: () -> UpdateManager
) : Tool {
    override val name: String = "app_update"
    override val description: String =
        "Check for Ravan application updates, download the latest version, view release notes, or initiate official Android package installation."

    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "The update action to perform: 'check', 'download', 'install', 'status', 'version', or 'whats_new'",
            required = true
        )
    )

    override val requiredPermissions: List<String> = emptyList()
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(context: Context, params: Map<String, Any?>): ToolResult {
        val action = (params["action"] as? String)?.trim()?.lowercase() ?: "check"
        val updateManager = updateManagerProvider()

        return when (action) {
            "check", "check_updates" -> {
                val checkResult = updateManager.checker.checkForUpdates(forceRemote = true)
                when (checkResult) {
                    is CheckResult.UpdateAvailable -> {
                        val m = checkResult.manifest
                        ToolResult.ok(
                            "A new update for Ravan is available: v${m.latestVersionName} (Build ${m.latestVersionCode}). Release notes: ${m.releaseNotes}. Say 'Update Ravan' or 'Download update' to proceed.",
                            mapOf(
                                "versionName" to m.latestVersionName,
                                "versionCode" to m.latestVersionCode,
                                "releaseNotes" to m.releaseNotes,
                                "apkUrl" to m.downloadUrl,
                                "isCritical" to checkResult.isCritical
                            )
                        )
                    }
                    is CheckResult.UpToDate -> {
                        ToolResult.ok(
                            "Ravan is currently up to date on v${checkResult.currentVersionName} (Build ${checkResult.currentVersionCode}). No new updates found.",
                            mapOf(
                                "versionName" to checkResult.currentVersionName,
                                "versionCode" to checkResult.currentVersionCode
                            )
                        )
                    }
                    is CheckResult.Error -> {
                        ToolResult.error(
                            "Failed to check for updates: ${checkResult.reason}",
                            data = mapOf("reason" to checkResult.reason, "isOffline" to checkResult.isOffline)
                        )
                    }
                }
            }
            "download" -> {
                val currentStatus = updateManager.status.value
                val manifest = when (currentStatus) {
                    is UpdateStatus.Available -> currentStatus.manifest
                    is UpdateStatus.ReadyToInstall -> {
                        return ToolResult.ok("Update is already downloaded and ready to install. Say 'Install update' to proceed.")
                    }
                    is UpdateStatus.Downloading -> {
                        return ToolResult.ok("Update download is already in progress: ${currentStatus.progress}% complete.")
                    }
                    else -> {
                        // Check first
                        val res = updateManager.checker.checkForUpdates(forceRemote = false)
                        if (res is CheckResult.UpdateAvailable) res.manifest else null
                    }
                }

                if (manifest != null) {
                    updateManager.startDownload(manifest)
                    ToolResult.ok("Started downloading update v${manifest.latestVersionName}. I will notify you when it's ready to install.")
                } else {
                    ToolResult.error("No update is currently pending download. Check for updates first.")
                }
            }
            "install" -> {
                val installResult = updateManager.installDownloadedApk()
                when (installResult) {
                    is InstallResult.Success -> {
                        ToolResult.ok("Launching Android system package installer. Please confirm installation on your screen.")
                    }
                    is InstallResult.PermissionRequired -> {
                        ToolResult.requiresUserAction(
                            "Permission needed: Please enable 'Install unknown apps' for Ravan in system settings to complete the update.",
                            mapOf("action" to "grant_install_unknown_apps")
                        )
                    }
                    is InstallResult.Error -> {
                        ToolResult.error("Could not launch package installer: ${installResult.message}")
                    }
                }
            }
            "status" -> {
                when (val st = updateManager.status.value) {
                    is UpdateStatus.Idle -> ToolResult.ok("Ravan update status is idle. Current version is v${updateManager.currentVersionName}.")
                    is UpdateStatus.Checking -> ToolResult.ok("Checking for new updates...")
                    is UpdateStatus.Available -> ToolResult.ok("Update v${st.manifest.latestVersionName} is available.")
                    is UpdateStatus.Downloading -> ToolResult.ok("Downloading update: ${st.progress}% completed.")
                    is UpdateStatus.ReadyToInstall -> ToolResult.ok("Update v${st.manifest.latestVersionName} is downloaded and ready to install.")
                    is UpdateStatus.Installing -> ToolResult.ok("Installation in progress...")
                    is UpdateStatus.UpToDate -> ToolResult.ok("Ravan is up to date (v${st.versionName}).")
                    is UpdateStatus.Error -> ToolResult.error("Update system encountered an error: ${st.message}")
                }
            }
            "version" -> {
                ToolResult.ok(
                    "Current Ravan Version: v${updateManager.currentVersionName} (Build ${updateManager.currentVersionCode})",
                    mapOf(
                        "versionName" to updateManager.currentVersionName,
                        "versionCode" to updateManager.currentVersionCode
                    )
                )
            }
            "whats_new", "changelog", "release_notes" -> {
                val res = updateManager.checker.checkForUpdates(forceRemote = false)
                if (res is CheckResult.UpdateAvailable) {
                    ToolResult.ok("What's new in v${res.manifest.latestVersionName}:\n${res.manifest.releaseNotes}")
                } else {
                    ToolResult.ok("You are running Ravan v${updateManager.currentVersionName}. Systems operating normally.")
                }
            }
            else -> {
                ToolResult.error("Unknown update action '$action'. Available actions: check, download, install, status, version, whats_new.")
            }
        }
    }
}
