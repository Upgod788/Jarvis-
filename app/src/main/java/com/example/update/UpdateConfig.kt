package com.example.update

object UpdateConfig {
    const val DEFAULT_UPDATE_SERVER_URL = "https://updates.jarvis-ai.cloud/v1/manifest.json"
    const val UPDATE_CACHE_DIR = "update_cache"
    const val NOTIFICATION_CHANNEL_ID = "jarvis_updates"
    const val NOTIFICATION_CHANNEL_NAME = "JARVIS App Updates"
    const val NOTIFICATION_ID = 2001

    // Background sync work name
    const val BACKGROUND_WORK_NAME = "jarvis_periodic_update_check"

    // Safe maximum download size (150 MB)
    const val MAX_APK_SIZE_BYTES = 150L * 1024L * 1024L

    // HTTP Timeouts
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 30L

    enum class Channel(val id: String, val displayName: String) {
        STABLE("stable", "Stable"),
        BETA("beta", "Beta (Preview)"),
        DEVELOPER("developer", "Developer (Bleeding Edge)")
    }
}
