package com.example.update

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class BackgroundUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = UpdatePreferences(context)
        val settings = prefs.settings.value

        if (!settings.autoUpdateEnabled) {
            return Result.success()
        }

        val checker = UpdateChecker(context, prefs)
        val result = checker.checkForUpdates()

        if (result is CheckResult.UpdateAvailable) {
            val manifest = result.manifest
            // Avoid duplicate notification for same version if already notified recently
            if (settings.lastNotifiedVersionCode != manifest.latestVersionCode || result.isCritical) {
                val notifManager = UpdateNotificationManager(context)
                notifManager.showUpdateAvailableNotification(manifest, result.isCritical)
                prefs.setLastNotifiedVersion(manifest.latestVersionCode)
            }

            // Auto-download if enabled and conditions met
            if (settings.autoDownload) {
                val downloader = UpdateDownloader(context, prefs)
                downloader.downloadUpdate(manifest, allowSimulationFallback = false)
            }
        }

        return Result.success()
    }

    companion object {
        fun schedulePeriodicCheck(context: Context, wifiOnly: Boolean = true) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val updateWorkRequest = PeriodicWorkRequestBuilder<BackgroundUpdateWorker>(
                    24, TimeUnit.HOURS,
                    6, TimeUnit.HOURS // Flex interval
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UpdateConfig.BACKGROUND_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    updateWorkRequest
                )
            } catch (e: Throwable) {
                android.util.Log.w("BackgroundUpdateWorker", "WorkManager scheduling skipped: ${e.message}")
            }
        }

        fun cancelPeriodicCheck(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(UpdateConfig.BACKGROUND_WORK_NAME)
            } catch (e: Throwable) {
                android.util.Log.w("BackgroundUpdateWorker", "WorkManager cancel skipped: ${e.message}")
            }
        }
    }
}
