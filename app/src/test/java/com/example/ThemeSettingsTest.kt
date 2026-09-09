package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.ThemeManager
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeSettingsTest {

    @Test
    fun testDefaultThemeIsDark() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themeManager = ThemeManager(context)
        assertEquals(ThemeMode.DARK, themeManager.themeMode.value)
    }

    @Test
    fun testSwitchToLightAndPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themeManager = ThemeManager(context)
        themeManager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, themeManager.themeMode.value)

        // Create a new instance with the same context to verify persistence
        val newManager = ThemeManager(context)
        assertEquals(ThemeMode.LIGHT, newManager.themeMode.value)
    }

    @Test
    fun testSwitchToSystemDefaultAndPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val themeManager = ThemeManager(context)
        themeManager.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, themeManager.themeMode.value)

        // Create a new instance to verify persistence
        val newManager = ThemeManager(context)
        assertEquals(ThemeMode.SYSTEM, newManager.themeMode.value)
    }
}
