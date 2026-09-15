package com.example.update

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UpdateSettings(
    val autoUpdateEnabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val autoDownload: Boolean = false,
    val updateOverMobileData: Boolean = true,
    val includeBetaUpdates: Boolean = false,
    val updateChannel: String = "stable",
    val lastUpdateCheckTime: Long = 0L,
    val lastNotifiedVersionCode: Int = 0,
    val dismissedVersionCode: Int = 0,
    val customServerUrl: String = "",
    val simulationMode: Boolean = false,
    val simulatedTargetVersion: String = "1.1.0",
    val simulatedIsMandatory: Boolean = false
)

class UpdatePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UpdateSettings> = _settings.asStateFlow()

    private fun loadSettings(): UpdateSettings {
        return UpdateSettings(
            autoUpdateEnabled = prefs.getBoolean(KEY_AUTO_UPDATE, true),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            autoDownload = prefs.getBoolean(KEY_AUTO_DOWNLOAD, false),
            updateOverMobileData = prefs.getBoolean(KEY_UPDATE_OVER_MOBILE, true),
            includeBetaUpdates = prefs.getBoolean(KEY_INCLUDE_BETA, false),
            updateChannel = prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable",
            lastUpdateCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L),
            lastNotifiedVersionCode = prefs.getInt(KEY_LAST_NOTIFIED_VERSION, 0),
            dismissedVersionCode = prefs.getInt(KEY_DISMISSED_VERSION, 0),
            customServerUrl = prefs.getString(KEY_CUSTOM_SERVER_URL, "") ?: "",
            simulationMode = prefs.getBoolean(KEY_SIMULATION_MODE, false),
            simulatedTargetVersion = prefs.getString(KEY_SIMULATED_VERSION, "1.1.0") ?: "1.1.0",
            simulatedIsMandatory = prefs.getBoolean(KEY_SIMULATED_MANDATORY, false)
        )
    }

    private fun saveAndEmit(updater: (UpdateSettings) -> UpdateSettings) {
        val updated = updater(_settings.value)
        prefs.edit()
            .putBoolean(KEY_AUTO_UPDATE, updated.autoUpdateEnabled)
            .putBoolean(KEY_WIFI_ONLY, updated.wifiOnly)
            .putBoolean(KEY_AUTO_DOWNLOAD, updated.autoDownload)
            .putBoolean(KEY_UPDATE_OVER_MOBILE, updated.updateOverMobileData)
            .putBoolean(KEY_INCLUDE_BETA, updated.includeBetaUpdates)
            .putString(KEY_UPDATE_CHANNEL, updated.updateChannel)
            .putLong(KEY_LAST_CHECK_TIME, updated.lastUpdateCheckTime)
            .putInt(KEY_LAST_NOTIFIED_VERSION, updated.lastNotifiedVersionCode)
            .putInt(KEY_DISMISSED_VERSION, updated.dismissedVersionCode)
            .putString(KEY_CUSTOM_SERVER_URL, updated.customServerUrl)
            .putBoolean(KEY_SIMULATION_MODE, updated.simulationMode)
            .putString(KEY_SIMULATED_VERSION, updated.simulatedTargetVersion)
            .putBoolean(KEY_SIMULATED_MANDATORY, updated.simulatedIsMandatory)
            .apply()
        _settings.value = updated
    }

    fun setAutoUpdateEnabled(enabled: Boolean) = saveAndEmit { it.copy(autoUpdateEnabled = enabled) }
    fun setWifiOnly(wifiOnly: Boolean) = saveAndEmit { it.copy(wifiOnly = wifiOnly) }
    fun setAutoDownload(autoDownload: Boolean) = saveAndEmit { it.copy(autoDownload = autoDownload) }
    fun setUpdateOverMobileData(enabled: Boolean) = saveAndEmit { it.copy(updateOverMobileData = enabled) }
    fun setIncludeBetaUpdates(enabled: Boolean) = saveAndEmit { it.copy(includeBetaUpdates = enabled) }
    fun setUpdateChannel(channel: String) = saveAndEmit { it.copy(updateChannel = channel) }
    fun setLastUpdateCheckTime(time: Long) = saveAndEmit { it.copy(lastUpdateCheckTime = time) }
    fun setLastCheckTime(time: Long) = setLastUpdateCheckTime(time)
    fun setLastNotifiedVersion(versionCode: Int) = saveAndEmit { it.copy(lastNotifiedVersionCode = versionCode) }
    fun setDismissedVersion(versionCode: Int) = saveAndEmit { it.copy(dismissedVersionCode = versionCode) }
    fun setCustomServerUrl(url: String) = saveAndEmit { it.copy(customServerUrl = url) }
    fun setSimulationMode(enabled: Boolean) = saveAndEmit { it.copy(simulationMode = enabled) }
    fun setSimulatedTargetVersion(version: String) = saveAndEmit { it.copy(simulatedTargetVersion = version) }
    fun setSimulatedIsMandatory(mandatory: Boolean) = saveAndEmit { it.copy(simulatedIsMandatory = mandatory) }

    companion object {
        private const val PREFS_NAME = "jarvis_update_preferences"
        private const val KEY_AUTO_UPDATE = "pref_auto_update"
        private const val KEY_WIFI_ONLY = "pref_wifi_only"
        private const val KEY_AUTO_DOWNLOAD = "pref_auto_download"
        private const val KEY_UPDATE_OVER_MOBILE = "pref_update_mobile"
        private const val KEY_INCLUDE_BETA = "pref_include_beta"
        private const val KEY_UPDATE_CHANNEL = "pref_channel"
        private const val KEY_LAST_CHECK_TIME = "pref_last_check_time"
        private const val KEY_LAST_NOTIFIED_VERSION = "pref_last_notified_ver"
        private const val KEY_DISMISSED_VERSION = "pref_dismissed_ver"
        private const val KEY_CUSTOM_SERVER_URL = "pref_custom_url"
        private const val KEY_SIMULATION_MODE = "pref_sim_mode"
        private const val KEY_SIMULATED_VERSION = "pref_sim_version"
        private const val KEY_SIMULATED_MANDATORY = "pref_sim_mandatory"
    }
}
