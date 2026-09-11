package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.database.JarvisDatabase
import com.example.database.UpdateHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface InstallResult {
    data object SuccessLaunched : InstallResult
    data class PermissionRequired(val settingsIntent: Intent) : InstallResult
    data class Error(val message: String) : InstallResult
}

class UpdateInstaller(
    private val context: Context
) {
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun getUnknownAppSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    suspend fun installApk(
        apkFile: File,
        manifest: UpdateManifest
    ): InstallResult = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            return@withContext InstallResult.Error("Update package not found on device storage.")
        }

        // Check if unknown sources install permission is granted on API 26+
        if (!canRequestPackageInstalls()) {
            return@withContext InstallResult.PermissionRequired(getUnknownAppSourcesIntent())
        }

        try {
            val authority = "${context.packageName}.updateprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Record into database UpdateHistory
            try {
                JarvisDatabase.getInstance(context).updateHistoryDao().insert(
                    UpdateHistoryEntity(
                        versionCode = manifest.latestVersionCode,
                        versionName = manifest.latestVersionName,
                        installedAt = System.currentTimeMillis(),
                        status = "INSTALLING",
                        releaseNotes = manifest.releaseNotes.joinToString(" • "),
                        channel = manifest.channel
                    )
                )
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                context.startActivity(installIntent)
            }

            InstallResult.SuccessLaunched
        } catch (e: Exception) {
            InstallResult.Error("Failed to launch Android Package Installer: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
