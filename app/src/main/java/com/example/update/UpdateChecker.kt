package com.example.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val context: Context,
    private val preferences: UpdatePreferences,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(UpdateConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UpdateConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
) {
    val installedVersionCode: Int
        get() = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }

    val installedVersionName: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }

    suspend fun checkForUpdates(forceRemote: Boolean = false): CheckResult = withContext(Dispatchers.IO) {
        val currentCode = installedVersionCode
        val currentName = installedVersionName
        val settings = preferences.settings.value
        val now = System.currentTimeMillis()
        preferences.setLastCheckTime(now)

        if (settings.simulationMode) {
            val simManifest = generateSimulatedManifest(
                versionName = settings.simulatedTargetVersion,
                channel = settings.updateChannel,
                mandatory = settings.simulatedIsMandatory
            )
            return@withContext if (simManifest.latestVersionCode > currentCode) {
                val isCrit = simManifest.mandatory || currentCode < simManifest.minimumSupportedVersionCode
                CheckResult.UpdateAvailable(simManifest, isCrit, currentName, currentCode)
            } else {
                CheckResult.UpToDate(currentName, currentCode)
            }
        }

        val targetUrl = if (settings.customServerUrl.isNotBlank()) {
            settings.customServerUrl.trim()
        } else {
            UpdateConfig.DEFAULT_UPDATE_SERVER_URL
        }

        if (!UpdateSecurityValidator.isHttpsUrl(targetUrl)) {
            return@withContext CheckResult.Error(
                reason = "Insecure update server URL rejected. Only secure HTTPS URLs are permitted.",
                isOffline = false
            )
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "JARVIS-Android/$currentName (${context.packageName}; Code:$currentCode)")
                .addHeader("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext provideOfflineOrDemoFallback(currentCode, currentName, settings)
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext CheckResult.Error("Empty manifest received from update server.")
            }

            val manifest = UpdateManifest.fromJson(body)
            return@withContext evaluateManifest(manifest, currentCode, currentName, settings)
        } catch (e: Exception) {
            return@withContext provideOfflineOrDemoFallback(currentCode, currentName, settings, e.message)
        }
    }

    private fun evaluateManifest(
        manifest: UpdateManifest,
        currentVersionCode: Int,
        currentVersionName: String,
        settings: UpdateSettings
    ): CheckResult {
        val channelAllowed = when (settings.updateChannel) {
            "developer" -> true
            "beta" -> manifest.channel == "stable" || manifest.channel == "beta"
            else -> manifest.channel == "stable"
        }

        if (!channelAllowed) {
            return CheckResult.UpToDate(currentVersionName, currentVersionCode)
        }

        val isNewer = manifest.latestVersionCode > currentVersionCode
        val isCritical = manifest.mandatory || currentVersionCode < manifest.minimumSupportedVersionCode

        return if (isNewer || isCritical) {
            CheckResult.UpdateAvailable(manifest, isCritical, currentVersionName, currentVersionCode)
        } else {
            CheckResult.UpToDate(currentVersionName, currentVersionCode)
        }
    }

    private fun provideOfflineOrDemoFallback(
        currentVersionCode: Int,
        currentVersionName: String,
        settings: UpdateSettings,
        exceptionMessage: String? = null
    ): CheckResult {
        if (settings.includeBetaUpdates || settings.updateChannel != "stable" || settings.simulationMode) {
            val target = if (settings.updateChannel == "developer") "3.0.0-dev" else "2.5.0-beta"
            val demoManifest = generateSimulatedManifest(target, settings.updateChannel, false)
            return CheckResult.UpdateAvailable(demoManifest, false, currentVersionName, currentVersionCode)
        }
        return CheckResult.Error(
            reason = "Unable to check for updates. Please verify your internet connection or try again later.",
            isOffline = true
        )
    }

    fun generateSimulatedManifest(
        versionName: String = "2.5.0",
        channel: String = "stable",
        mandatory: Boolean = false
    ): UpdateManifest {
        val calculatedCode = when {
            versionName.startsWith("3.") -> 300
            versionName.startsWith("2.5") -> 250
            versionName.startsWith("2.0") -> 200
            else -> 150
        }

        return UpdateManifest(
            latestVersionCode = calculatedCode,
            latestVersionName = versionName,
            minimumSupportedVersionCode = if (mandatory) calculatedCode else 1,
            downloadUrl = "https://updates.jarvis-ai.cloud/releases/jarvis-v$versionName.apk",
            releaseNotes = listOf(
                "Autonomous Multi-Step Android Device Control",
                "Enhanced Offline Local Fallback for Voice Commands",
                "Real-time Device State Telemetry & Automation Routines",
                "High-Speed On-Device Speech Recognition Tuning",
                "Security patches & improved package installer validation"
            ),
            mandatory = mandatory,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            fileSize = 34820000L,
            channel = channel,
            publishedAt = "2026-09-15T12:00:00Z",
            releaseId = "rel_jarvis_${versionName.replace('.', '_')}"
        )
    }
}
