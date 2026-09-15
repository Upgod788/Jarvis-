package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DefaultJarvisCyanPrimary,
    secondary = DefaultJarvisCyanBright,
    tertiary = DefaultJarvisBlueAccent,
    background = DeepNavy,
    surface = SurfaceNavy,
    onPrimary = DeepNavy,
    onSecondary = DeepNavy,
    onTertiary = DeepNavy,
    onBackground = Color(0xFFEFF4F8),
    onSurface = Color(0xFFEFF4F8)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0091EA),
    secondary = Color(0xFF00B0FF),
    tertiary = Color(0xFF0288D1),
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D1B2A),
    onSurface = Color(0xFF0D1B2A)
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM, null -> darkTheme
    }

    val jarvisColors = if (isDark) DarkJarvisColors else LightJarvisColors

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalJarvisColors provides jarvisColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
