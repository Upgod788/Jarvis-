package com.example.update

import android.content.Intent
import java.io.File

sealed interface ValidationResult {
    object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

sealed interface InstallResult {
    object Success : InstallResult
    data class PermissionRequired(val settingsIntent: Intent) : InstallResult
    data class Error(val message: String) : InstallResult
}

sealed interface CheckResult {
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val isCritical: Boolean,
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : CheckResult

    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int
    ) : CheckResult

    data class Error(
        val reason: String,
        val isOffline: Boolean = false
    ) : CheckResult
}

sealed interface DownloadState {
    object Idle : DownloadState
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percent: Float,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : DownloadState
    data class Completed(
        val file: File,
        val sha256: String,
        val manifest: UpdateManifest
    ) : DownloadState
    data class Failed(
        val error: String,
        val canRetry: Boolean = true
    ) : DownloadState
    object Cancelled : DownloadState
}

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    data class Available(
        val manifest: UpdateManifest,
        val isCritical: Boolean
    ) : UpdateStatus
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val etaSeconds: Long
    ) : UpdateStatus
    data class ReadyToInstall(
        val apkFile: File,
        val manifest: UpdateManifest
    ) : UpdateStatus
    object Installing : UpdateStatus
    data class UpToDate(
        val versionName: String,
        val versionCode: Int
    ) : UpdateStatus
    data class Error(
        val message: String,
        val isOffline: Boolean = false
    ) : UpdateStatus
}
