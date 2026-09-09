package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Default Fallback Color Constants
val DefaultJarvisCyanPrimary = Color(0xFF00E5FF)
val DefaultJarvisCyanBright = Color(0xFF18FFFF)
val DefaultJarvisCyanDark = Color(0xFF00838F)
val DefaultJarvisBlueAccent = Color(0xFF0091EA)

val JarvisSuccess = Color(0xFF00E676)
val JarvisWarning = Color(0xFFFFAB00)
val JarvisError = Color(0xFFFF5252)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Data class representing the full palette of JARVIS UI colors for either Light or Dark modes.
 */
data class JarvisColors(
    val background: Color,
    val surface: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val cyanPrimary: Color,
    val cyanBright: Color,
    val cyanDark: Color,
    val blueAccent: Color,
    val isDark: Boolean
)

// Dark Theme (Classic JARVIS Cyberpunk HUD)
val DarkJarvisColors = JarvisColors(
    background = Color(0xFF050811),
    surface = Color(0xFF0A101E),
    cardSurface = Color(0xFF111A2D),
    cardBorder = Color(0xFF1B2B48),
    textPrimary = Color(0xFFF0F4F8),
    textSecondary = Color(0xFF8FA3BF),
    textMuted = Color(0xFF556987),
    cyanPrimary = Color(0xFF00E5FF),
    cyanBright = Color(0xFF18FFFF),
    cyanDark = Color(0xFF00838F),
    blueAccent = Color(0xFF0091EA),
    isDark = true
)

// Light Theme (Clean Scientific Laboratory Interface)
val LightJarvisColors = JarvisColors(
    background = Color(0xFFF1F5F9), // Ice Pearl Slate-100
    surface = Color(0xFFFFFFFF),    // Pure Crisp White
    cardSurface = Color(0xFFF8FAFC), // Soft Slate-50 Card
    cardBorder = Color(0xFFCBD5E1),  // Slate-300 Border
    textPrimary = Color(0xFF0F172A), // Slate-900 High-Contrast Text
    textSecondary = Color(0xFF334155), // Slate-700 Secondary
    textMuted = Color(0xFF64748B),     // Slate-500 Muted
    cyanPrimary = Color(0xFF00838F), // Deep Cyan/Teal (passes 4.5:1 WCAG against white)
    cyanBright = Color(0xFF0097A7),
    cyanDark = Color(0xFF006064),
    blueAccent = Color(0xFF0284C7),  // Sky 600
    isDark = false
)

val LocalJarvisColors = staticCompositionLocalOf { DarkJarvisColors }

// Dynamic contextual color getters that adapt automatically based on the active theme
val JarvisDarkBackground: Color
    @Composable
    get() = LocalJarvisColors.current.background

val JarvisDarkSurface: Color
    @Composable
    get() = LocalJarvisColors.current.surface

val JarvisCardSurface: Color
    @Composable
    get() = LocalJarvisColors.current.cardSurface

val JarvisCardBorder: Color
    @Composable
    get() = LocalJarvisColors.current.cardBorder

val JarvisTextPrimary: Color
    @Composable
    get() = LocalJarvisColors.current.textPrimary

val JarvisTextSecondary: Color
    @Composable
    get() = LocalJarvisColors.current.textSecondary

val JarvisTextMuted: Color
    @Composable
    get() = LocalJarvisColors.current.textMuted

val JarvisCyanPrimary: Color
    @Composable
    get() = LocalJarvisColors.current.cyanPrimary

val JarvisCyanBright: Color
    @Composable
    get() = LocalJarvisColors.current.cyanBright

val JarvisCyanDark: Color
    @Composable
    get() = LocalJarvisColors.current.cyanDark

val JarvisBlueAccent: Color
    @Composable
    get() = LocalJarvisColors.current.blueAccent


