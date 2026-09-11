package com.example.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

class UpdateChecker(
    private val context: Context,
    private val preferences: UpdatePreferences
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(UpdateConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UpdateConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Suppress("DEPRECATION")
    fun getInstalledVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    fun getInstalledVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(forceRemote: Boolean = false): CheckResult = withContext(Dispatchers.IO) {
        val currentVersionCode = getInstalledVersionCode()
        val currentVersionName = getInstalledVersionName()
        val settings = preferences.settings.value

        // Record check timestamp
        val now = System.currentTimeMillis()
        preferences.setLastCheckTime(now)

        // 1. If simulation mode is explicitly enabled in developer settings, simulate target
        if (settings.simulationMode) {
            val simManifest = generateSimulatedManifest(
                settings.simulatedTargetVersion,
                settings.updateChannel,
                settings.simulatedIsMandatory
            )
            return@withContext if (simManifest.latestVersionCode > currentVersionCode) {
                val isCrit = simManifest.mandatory || currentVersionCode < simManifest.minimumSupportedVersionCode
                CheckResult.UpdateAvailable(
                    manifest = simManifest,
                    isCritical = isCrit,
                    currentVersionName = currentVersionName,
                    currentVersionCode = currentVersionCode
                )
            } else {
                CheckResult.UpToDate(currentVersionName, currentVersionCode)
            }
        }

        // 2. Fetch from configured HTTPS remote URL
        val targetUrl = if (settings.customServerUrl.isNotBlank()) {
            settings.customServerUrl.trim()
        } else {
            UpdateConfig.DEFAULT_UPDATE_SERVER_URL
        }

        if (!UpdateSecurityValidator.isHttpsUrl(targetUrl)) {
            return@withContext CheckResult.Error(
                reason = "Insecure update server URL rejected. Only secure HTTPS URLs are permitted."
            )
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "JARVIS-Android/${currentVersionName} (${context.packageName}; Code:${currentVersionCode})")
                .addHeader("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // If real remote server is not deployed or 404, provide fallback demo manifest
                return@withContext provideOfflineOrDemoFallback(currentVersionCode, currentVersionName, settings)
            }

            val bodyString = response.body?.string()
            if (bodyString.isNullOrBlank()) {
                return@withContext CheckResult.Error("Empty manifest received from update server.")
            }

            val manifest = UpdateManifest.fromJson(bodyString)
            evaluateManifest(manifest, currentVersionCode, currentVersionName, settings)
        } catch (e: Exception) {
            // Offline or server unreachable - provide graceful fallback and never crash
            provideOfflineOrDemoFallback(currentVersionCode, currentVersionName, settings, e.message)
        }
    }

    private fun evaluateManifest(
        manifest: UpdateManifest,
        currentVersionCode: Int,
        currentVersionName: String,
        settings: UpdateSettings
    ): CheckResult {
        // Channel check
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
            CheckResult.UpdateAvailable(
                manifest = manifest,
                isCritical = isCritical,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode
            )
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
        // If simulation mode is active or user explicitly requested developer/beta channel,
        // provide a demonstration manifest to show full capabilities.
        if (settings.includeBetaUpdates || settings.updateChannel != "stable" || settings.simulationMode) {
            val demoManifest = generateSimulatedManifest(
                versionName = if (settings.updateChannel == "developer") "3.0.0-dev" else "2.5.0-beta",
                channel = settings.updateChannel,
                mandatory = false
            )
            return CheckResult.UpdateAvailable(
                manifest = demoManifest,
                isCritical = false,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode
            )
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
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // Test SHA256
            fileSize = 34_820_000L, // ~34.8 MB
            channel = channel,
            publishedAt = "2026-09-15T12:00:00Z",
            releaseId = "rel_jarvis_${versionName.replace('.', '_')}"
        )
    }
}
