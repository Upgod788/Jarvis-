package com.example.update

import org.json.JSONException
import org.json.JSONObject

data class FeatureFlags(
    val voiceAssistant: Boolean = true,
    val smartHome: Boolean = true,
    val instagramAutomation: Boolean = true,
    val pcControl: Boolean = true,
    val experimentalMode: Boolean = false
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("voiceAssistant", voiceAssistant)
        json.put("smartHome", smartHome)
        json.put("instagramAutomation", instagramAutomation)
        json.put("pcControl", pcControl)
        json.put("experimentalMode", experimentalMode)
        return json
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
