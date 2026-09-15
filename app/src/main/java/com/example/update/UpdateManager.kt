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
        get() = checker.installedVersionName

    val currentVersionCode: Int
        get() = checker.installedVersionCode

    init {
        // Collect downloader state
        scope.launch {
            downloader.downloadState.collect { dState ->
                when (dState) {
                    is DownloadState.Idle -> {
                        // Keep current status if idle
                    }
                    is DownloadState.Progress -> {
                        _status.value = UpdateStatus.Downloading(
                            progress = dState.percent,
                            bytesDownloaded = dState.bytesDownloaded,
                            totalBytes = dState.totalBytes,
                            speedBytesPerSec = dState.speedBytesPerSec,
                            etaSeconds = dState.etaSeconds
                        )
                    }
                    is DownloadState.Completed -> {
                        downloadedApkFile = dState.file
                        activeManifest = dState.manifest
                        _status.value = UpdateStatus.ReadyToInstall(dState.file, dState.manifest)
                    }
                    is DownloadState.Failed -> {
                        _status.value = UpdateStatus.Error(dState.error)
                    }
                    is DownloadState.Cancelled -> {
                        _status.value = activeManifest?.let {
                            UpdateStatus.Available(it, isCritical = false)
                        } ?: UpdateStatus.Idle
                    }
                }
            }
        }

        if (preferences.settings.value.autoUpdateEnabled) {
            BackgroundUpdateWorker.schedulePeriodicCheck(context, preferences.settings.value.wifiOnly)
        }
    }

    fun checkForUpdates(isUserInitiated: Boolean = true) {
        scope.launch {
            _status.value = UpdateStatus.Checking
            val result = checker.checkForUpdates(forceRemote = isUserInitiated)
            when (result) {
                is CheckResult.UpdateAvailable -> {
                    activeManifest = result.manifest
                    _status.value = UpdateStatus.Available(result.manifest, result.isCritical)
                    if (!isUserInitiated) {
                        notificationManager.showUpdateAvailableNotification(result.manifest, result.isCritical)
                        preferences.setLastNotifiedVersion(result.manifest.latestVersionCode)
                    }
                }
                is CheckResult.UpToDate -> {
                    _status.value = UpdateStatus.UpToDate(result.currentVersionName, result.currentVersionCode)
                }
                is CheckResult.Error -> {
                    _status.value = UpdateStatus.Error(result.reason, result.isOffline)
                }
            }
        }
    }

    fun startDownload(manifest: UpdateManifest? = null) {
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
        downloader.cleanCache()
        downloadedApkFile = null
        activeManifest = null
        _status.value = UpdateStatus.Idle
    }

    suspend fun recordHistoryEntry(
        versionName: String,
        versionCode: Int,
        status: String,
        notes: String,
        channel: String
    ) {
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
        } catch (e: Exception) {
            // Log or ignore
        }
    }
}
