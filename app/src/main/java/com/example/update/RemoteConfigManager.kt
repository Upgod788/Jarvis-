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
import java.util.concurrent.TimeUnit

data class FeatureFlags(
    val voiceAssistant: Boolean = true,
    val smartHome: Boolean = true,
    val instagramAutomation: Boolean = true,
    val pcControl: Boolean = true,
    val experimentalMode: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("voiceAssistant", voiceAssistant)
            put("smartHome", smartHome)
            put("instagramAutomation", instagramAutomation)
            put("pcControl", pcControl)
            put("experimentalMode", experimentalMode)
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): FeatureFlags {
            if (json == null) return FeatureFlags()
            return FeatureFlags(
                voiceAssistant = json.optBoolean("voiceAssistant", true),
                smartHome = json.optBoolean("smartHome", true),
                instagramAutomation = json.optBoolean("instagramAutomation", true),
                pcControl = json.optBoolean("pcControl", true),
                experimentalMode = json.optBoolean("experimentalMode", false)
            )
        }
    }
}

data class MaintenanceStatus(
    val isEnabled: Boolean = false,
    val message: String = "JARVIS Cloud Services are undergoing scheduled maintenance. Local phone control and automation continue to operate offline."
)

data class RemoteConfig(
    val latestVersion: String = "2.5.0",
    val featureFlags: FeatureFlags = FeatureFlags(),
    val maintenance: MaintenanceStatus = MaintenanceStatus(),
    val announcement: String? = null,
    val supportedAndroidMinSdk: Int = 24,
    val updateChannel: String = "stable"
)

class RemoteConfigManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_remote_config", Context.MODE_PRIVATE)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _config = MutableStateFlow(loadLocalConfig())
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    private fun loadLocalConfig(): RemoteConfig {
        val jsonStr = prefs.getString("cached_config_json", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                return parseConfigJson(jsonStr)
            } catch (_: Exception) {}
        }
        return RemoteConfig()
    }

    private fun parseConfigJson(jsonStr: String): RemoteConfig {
        val root = JSONObject(jsonStr)
        val flagsObj = root.optJSONObject("featureFlags")
        val maintObj = root.optJSONObject("maintenance")

        val flags = FeatureFlags.fromJson(flagsObj)
        val maintenance = MaintenanceStatus(
            isEnabled = maintObj?.optBoolean("enabled", false) ?: false,
            message = maintObj?.optString("message", "Maintenance in progress")
                ?: "Local device control is available."
        )

        return RemoteConfig(
            latestVersion = root.optString("latestVersion", "2.5.0"),
            featureFlags = flags,
            maintenance = maintenance,
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
        } catch (_: Exception) {
            // Unreachable remote config server is fine - offline-first fallback
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
