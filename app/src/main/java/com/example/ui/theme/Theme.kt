package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkJarvisColors.cyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = DarkJarvisColors.cyanDark,
    onPrimaryContainer = DarkJarvisColors.cyanBright,
    secondary = DarkJarvisColors.blueAccent,
    onSecondary = Color.White,
    background = DarkJarvisColors.background,
    onBackground = DarkJarvisColors.textPrimary,
    surface = DarkJarvisColors.surface,
    onSurface = DarkJarvisColors.textPrimary,
    surfaceVariant = DarkJarvisColors.cardSurface,
    onSurfaceVariant = DarkJarvisColors.textSecondary,
    outline = DarkJarvisColors.cardBorder,
    error = JarvisError
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightJarvisColors.cyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF004D40),
    secondary = LightJarvisColors.blueAccent,
    onSecondary = Color.White,
    background = LightJarvisColors.background,
    onBackground = LightJarvisColors.textPrimary,
    surface = LightJarvisColors.surface,
    onSurface = LightJarvisColors.textPrimary,
    surfaceVariant = LightJarvisColors.cardSurface,
    onSurfaceVariant = LightJarvisColors.textSecondary,
    outline = LightJarvisColors.cardBorder,
    error = Color(0xFFD32F2F)
  )

@Composable
fun MyApplicationTheme(
  themeMode: ThemeMode? = null,
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    null -> darkTheme
  }

  val jarvisColors = if (isDark) DarkJarvisColors else LightJarvisColors

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      isDark -> DarkColorScheme
      else -> LightColorScheme
    }

  androidx.compose.runtime.CompositionLocalProvider(LocalJarvisColors provides jarvisColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
