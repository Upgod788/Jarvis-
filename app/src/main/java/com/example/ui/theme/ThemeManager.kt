package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val savedId = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.id) ?: ThemeMode.DARK.id
        return ThemeMode.values().find { it.id == savedId } ?: ThemeMode.DARK
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _themeMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "jarvis_theme_prefs"
        private const val KEY_THEME_MODE = "selected_theme_mode"
    }
}
