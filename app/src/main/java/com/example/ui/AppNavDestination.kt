package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppNavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.SmartToy),
    HISTORY("History", Icons.Default.History),
    MEMORY("Memory", Icons.Default.Psychology),
    AUTOMATION("Automation", Icons.Default.AutoMode),
    SETTINGS("Settings", Icons.Default.Settings)
}

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    ERROR
}
