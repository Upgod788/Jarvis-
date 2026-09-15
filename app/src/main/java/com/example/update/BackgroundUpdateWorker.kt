package com.example.update

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
            if (settings.lastNotifiedVersionCode != manifest.latestVersionCode || result.isCritical) {
                val notifManager = UpdateNotificationManager(context)
                notifManager.showUpdateAvailableNotification(manifest, result.isCritical)
                prefs.setLastNotifiedVersion(manifest.latestVersionCode)
            }

            if (settings.autoDownload) {
                val downloader = UpdateDownloader(context, prefs)
                downloader.downloadUpdate(manifest, false)
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

                val updateWorkRequest = PeriodicWorkRequest.Builder(
                    BackgroundUpdateWorker::class.java,
                    24L,
                    TimeUnit.HOURS,
                    6L,
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.HOURS)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UpdateConfig.BACKGROUND_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    updateWorkRequest
                )
            } catch (e: Throwable) {
                Log.w("BackgroundUpdateWorker", "WorkManager scheduling skipped: ${e.message}")
            }
        }

        fun cancelPeriodicCheck(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(UpdateConfig.BACKGROUND_WORK_NAME)
            } catch (e: Throwable) {
                Log.w("BackgroundUpdateWorker", "WorkManager cancel skipped: ${e.message}")
            }
        }
    }
}
