package com.example.update

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RemoteConfigManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_remote_config", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadInitialConfig())
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    private fun loadInitialConfig(): RemoteConfig {
        val cached = prefs.getString("cached_config_json", null)
        if (!cached.isNullOrBlank()) {
            try {
                return parseConfigJson(cached)
            } catch (e: Exception) {
                // fallback
            }
        }
        return RemoteConfig()
    }

    private fun parseConfigJson(jsonStr: String): RemoteConfig {
        val root = JSONObject(jsonStr)
        val flagsObj = root.optJSONObject("featureFlags")
        val maintObj = root.optJSONObject("maintenance")

        val flags = FeatureFlags.fromJson(flagsObj)
        val maintEnabled = maintObj?.optBoolean("enabled", false) ?: false
        val maintMsg = maintObj?.optString("message", "Local device control is available.") ?: "Local device control is available."

        return RemoteConfig(
            latestVersion = root.optString("latestVersion", "2.5.0"),
            featureFlags = flags,
            maintenance = MaintenanceStatus(maintEnabled, maintMsg),
            announcement = if (root.has("announcement") && !root.isNull("announcement")) root.getString("announcement") else null,
            supportedAndroidMinSdk = root.optInt("supportedAndroidMinSdk", 24),
            updateChannel = root.optString("updateChannel", "stable")
        )
    }

    suspend fun refreshRemoteConfig(endpointUrl: String = "https://updates.jarvis-ai.cloud/v1/config.json") = withContext(Dispatchers.IO) {
        if (!UpdateSecurityValidator.isHttpsUrl(endpointUrl)) return@withContext

        try {
            val request = Request.Builder().url(endpointUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val parsed = parseConfigJson(body)
                    prefs.edit().putString("cached_config_json", body).apply()
                    _config.value = parsed
                }
            }
        } catch (e: Exception) {
            // Keep existing cached or default config
        }
    }

    fun setSimulatedMaintenance(enabled: Boolean, customMessage: String = "") {
        val current = _config.value
        _config.value = current.copy(
            maintenance = MaintenanceStatus(
                isEnabled = enabled,
                message = if (customMessage.isNotBlank()) customMessage else current.maintenance.message
            )
        )
    }

    fun toggleFeatureFlag(featureName: String, enabled: Boolean) {
        val current = _config.value
        val flags = current.featureFlags
        val newFlags = when (featureName) {
            "voiceAssistant" -> flags.copy(voiceAssistant = enabled)
            "smartHome" -> flags.copy(smartHome = enabled)
            "instagramAutomation" -> flags.copy(instagramAutomation = enabled)
            "pcControl" -> flags.copy(pcControl = enabled)
            "experimentalMode" -> flags.copy(experimentalMode = enabled)
            else -> flags
        }
        _config.value = current.copy(featureFlags = newFlags)
    }
}
