package com.example.update

import android.content.Context
import com.example.database.JarvisDatabase
import com.example.database.UpdateHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(
        val manifest: UpdateManifest,
        val isCritical: Boolean
    ) : UpdateStatus
    data class Downloading(
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class Downloaded(
        val file: File,
        val manifest: UpdateManifest,
        val sha256: String
    ) : UpdateStatus
    data object Installing : UpdateStatus
    data class UpToDate(
        val versionName: String,
        val versionCode: Int
    ) : UpdateStatus
    data class Error(
        val message: String,
        val isOffline: Boolean = false
    ) : UpdateStatus
}

class UpdateManager(
    val context: Context,
    val preferences: UpdatePreferences = UpdatePreferences(context),
    val remoteConfigManager: RemoteConfigManager = RemoteConfigManager(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val checker = UpdateChecker(context, preferences)
    val downloader = UpdateDownloader(context, preferences)
    val installer = UpdateInstaller(context)
    val notificationManager = UpdateNotificationManager(context)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var activeManifest: UpdateManifest? = null
    private var downloadedApkFile: File? = null

    val currentVersionName: String
        get() = checker.getInstalledVersionName()

    val currentVersionCode: Int
        get() = checker.getInstalledVersionCode()

    init {
        // Collect downloader state
        scope.launch {
            downloader.downloadState.collect { dlState ->
                when (dlState) {
                    is DownloadState.Progress -> {
                        _status.value = UpdateStatus.Downloading(
                            progressPercent = dlState.percentage,
                            bytesDownloaded = dlState.bytesDownloaded,
                            totalBytes = dlState.totalBytes
                        )
                    }
                    is DownloadState.Completed -> {
                        downloadedApkFile = dlState.file
                        activeManifest = dlState.manifest
                        _status.value = UpdateStatus.Downloaded(
                            file = dlState.file,
                            manifest = dlState.manifest,
                            sha256 = dlState.sha256
                        )
                    }
                    is DownloadState.Failed -> {
                        _status.value = UpdateStatus.Error(dlState.error)
                    }
                    is DownloadState.Cancelled -> {
                        val m = activeManifest
                        if (m != null) {
                            val isCrit = m.mandatory || currentVersionCode < m.minimumSupportedVersionCode
                            _status.value = UpdateStatus.Available(m, isCrit)
                        } else {
                            _status.value = UpdateStatus.Idle
                        }
                    }
                    is DownloadState.Idle -> {}
                }
            }
        }

        // Schedule background worker if enabled
        if (preferences.settings.value.autoUpdateEnabled) {
            BackgroundUpdateWorker.schedulePeriodicCheck(
                context,
                wifiOnly = preferences.settings.value.wifiOnly
            )
        }
    }

    fun checkForUpdates(isUserInitiated: Boolean = false) {
        _status.value = UpdateStatus.Checking
        scope.launch {
            val result = checker.checkForUpdates()
            when (result) {
                is CheckResult.UpdateAvailable -> {
                    activeManifest = result.manifest
                    _status.value = UpdateStatus.Available(
                        manifest = result.manifest,
                        isCritical = result.isCritical
                    )

                    // Auto download if enabled
                    if (preferences.settings.value.autoDownload && !result.isCritical) {
                        startDownload(result.manifest)
                    }

                    // Notification for background checks or when user is away
                    if (!isUserInitiated) {
                        notificationManager.showUpdateAvailableNotification(
                            manifest = result.manifest,
                            isCritical = result.isCritical
                        )
                    }
                }
                is CheckResult.UpToDate -> {
                    activeManifest = null
                    _status.value = UpdateStatus.UpToDate(
                        versionName = result.currentVersionName,
                        versionCode = result.currentVersionCode
                    )
                }
                is CheckResult.Error -> {
                    _status.value = UpdateStatus.Error(
                        message = result.reason,
                        isOffline = result.isOffline
                    )
                }
            }
        }
    }

    fun startDownload(manifest: UpdateManifest? = activeManifest) {
        val target = manifest ?: activeManifest ?: return
        activeManifest = target
        scope.launch {
            downloader.downloadUpdate(target)
        }
    }

    fun cancelDownload() {
        downloader.cancelDownload()
    }

    suspend fun installDownloadedApk(): InstallResult {
        val file = downloadedApkFile
        val manifest = activeManifest
        if (file == null || manifest == null) {
            return InstallResult.Error("No downloaded update ready to install.")
        }

        _status.value = UpdateStatus.Installing
        val result = installer.installApk(file, manifest)
        if (result is InstallResult.Error) {
            _status.value = UpdateStatus.Error(result.message)
        }
        return result
    }

    fun dismissUpdate(versionCode: Int) {
        preferences.setDismissedVersion(versionCode)
        notificationManager.dismissNotification()
        _status.value = UpdateStatus.Idle
    }

    fun clearCache() {
        downloader.cleanOldUpdates()
        downloadedApkFile = null
        activeManifest = null
        _status.value = UpdateStatus.Idle
    }

    suspend fun recordHistoryEntry(versionName: String, versionCode: Int, status: String, notes: String, channel: String) {
        try {
            JarvisDatabase.getInstance(context).updateHistoryDao().insert(
                UpdateHistoryEntity(
                    versionCode = versionCode,
                    versionName = versionName,
                    installedAt = System.currentTimeMillis(),
                    status = status,
                    releaseNotes = notes,
                    channel = channel
                )
            )
        } catch (_: Exception) {}
    }
}
