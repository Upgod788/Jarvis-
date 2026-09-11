package com.example.update

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UpdateSettings(
    val autoUpdateEnabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val autoDownload: Boolean = false,
    val updateOverMobileData: Boolean = false,
    val includeBetaUpdates: Boolean = false,
    val updateChannel: String = "stable",
    val lastUpdateCheckTime: Long = 0L,
    val lastNotifiedVersionCode: Int = 0,
    val dismissedVersionCode: Int = 0,
    val customServerUrl: String = "",
    val simulationMode: Boolean = false,
    val simulatedTargetVersion: String = "2.5.0",
    val simulatedIsMandatory: Boolean = false
)

class UpdatePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis_update_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UpdateSettings> = _settings.asStateFlow()

    private fun loadSettings(): UpdateSettings {
        return UpdateSettings(
            autoUpdateEnabled = prefs.getBoolean(KEY_AUTO_UPDATE, true),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
            autoDownload = prefs.getBoolean(KEY_AUTO_DOWNLOAD, false),
            updateOverMobileData = prefs.getBoolean(KEY_MOBILE_DATA, false),
            includeBetaUpdates = prefs.getBoolean(KEY_INCLUDE_BETA, false),
            updateChannel = prefs.getString(KEY_CHANNEL, "stable") ?: "stable",
            lastUpdateCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L),
            lastNotifiedVersionCode = prefs.getInt(KEY_LAST_NOTIFIED_VERSION, 0),
            dismissedVersionCode = prefs.getInt(KEY_DISMISSED_VERSION, 0),
            customServerUrl = prefs.getString(KEY_CUSTOM_SERVER_URL, "") ?: "",
            simulationMode = prefs.getBoolean(KEY_SIMULATION_MODE, false),
            simulatedTargetVersion = prefs.getString(KEY_SIM_VERSION, "2.5.0") ?: "2.5.0",
            simulatedIsMandatory = prefs.getBoolean(KEY_SIM_MANDATORY, false)
        )
    }

    private fun saveAndEmit(update: UpdateSettings.() -> UpdateSettings) {
        val newSettings = _settings.value.update()
        prefs.edit()
            .putBoolean(KEY_AUTO_UPDATE, newSettings.autoUpdateEnabled)
            .putBoolean(KEY_WIFI_ONLY, newSettings.wifiOnly)
            .putBoolean(KEY_AUTO_DOWNLOAD, newSettings.autoDownload)
            .putBoolean(KEY_MOBILE_DATA, newSettings.updateOverMobileData)
            .putBoolean(KEY_INCLUDE_BETA, newSettings.includeBetaUpdates)
            .putString(KEY_CHANNEL, newSettings.updateChannel)
            .putLong(KEY_LAST_CHECK_TIME, newSettings.lastUpdateCheckTime)
            .putInt(KEY_LAST_NOTIFIED_VERSION, newSettings.lastNotifiedVersionCode)
            .putInt(KEY_DISMISSED_VERSION, newSettings.dismissedVersionCode)
            .putString(KEY_CUSTOM_SERVER_URL, newSettings.customServerUrl)
            .putBoolean(KEY_SIMULATION_MODE, newSettings.simulationMode)
            .putString(KEY_SIM_VERSION, newSettings.simulatedTargetVersion)
            .putBoolean(KEY_SIM_MANDATORY, newSettings.simulatedIsMandatory)
            .apply()
        _settings.value = newSettings
    }

    fun setAutoUpdate(enabled: Boolean) = saveAndEmit { copy(autoUpdateEnabled = enabled) }
    fun setWifiOnly(enabled: Boolean) = saveAndEmit { copy(wifiOnly = enabled) }
    fun setAutoDownload(enabled: Boolean) = saveAndEmit { copy(autoDownload = enabled) }
    fun setUpdateOverMobileData(enabled: Boolean) = saveAndEmit { copy(updateOverMobileData = enabled) }
    fun setIncludeBetaUpdates(enabled: Boolean) = saveAndEmit {
        val newChannel = if (enabled && updateChannel == "stable") "beta" else if (!enabled && updateChannel == "beta") "stable" else updateChannel
        copy(includeBetaUpdates = enabled, updateChannel = newChannel)
    }
    fun setUpdateChannel(channel: String) = saveAndEmit {
        copy(updateChannel = channel, includeBetaUpdates = channel != "stable")
    }
    fun setLastCheckTime(time: Long) = saveAndEmit { copy(lastUpdateCheckTime = time) }
    fun setLastNotifiedVersion(versionCode: Int) = saveAndEmit { copy(lastNotifiedVersionCode = versionCode) }
    fun setDismissedVersion(versionCode: Int) = saveAndEmit { copy(dismissedVersionCode = versionCode) }
    fun setCustomServerUrl(url: String) = saveAndEmit { copy(customServerUrl = url) }
    fun setSimulationMode(enabled: Boolean) = saveAndEmit { copy(simulationMode = enabled) }
    fun setSimulatedTargetVersion(version: String) = saveAndEmit { copy(simulatedTargetVersion = version) }
    fun setSimulatedIsMandatory(mandatory: Boolean) = saveAndEmit { copy(simulatedIsMandatory = mandatory) }

    companion object {
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AUTO_DOWNLOAD = "auto_download"
        private const val KEY_MOBILE_DATA = "mobile_data"
        private const val KEY_INCLUDE_BETA = "include_beta"
        private const val KEY_CHANNEL = "update_channel"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_CUSTOM_SERVER_URL = "custom_server_url"
        private const val KEY_SIMULATION_MODE = "simulation_mode"
        private const val KEY_SIM_VERSION = "sim_version"
        private const val KEY_SIM_MANDATORY = "sim_mandatory"
    }
}
