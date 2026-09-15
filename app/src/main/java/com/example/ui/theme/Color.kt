package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DefaultJarvisCyanPrimary = Color(0xFF00E5FF)
val DefaultJarvisCyanBright = Color(0xFF18FFFF)
val DefaultJarvisCyanDark = Color(0xFF0091EA)
val DefaultJarvisBlueAccent = Color(0xFF0099EA)

val JarvisSuccess = Color(0xFF00E676)
val JarvisWarning = Color(0xFFFFAB00)
val JarvisError = Color(0xFFFF5252)

val CyanAccent = Color(0xFF00E5FF)
val ArcBlue = Color(0xFF0099EA)
val TextSecondary = Color(0xFF8CA8BF)
val SurfaceNavy = Color(0xFF09101E)
val CardDark = Color(0xFF101E2D)
val BorderNavy = Color(0xFF1A3348)
val DeepNavy = Color(0xFF040811)
val HologramGreen = Color(0xFF00E676)
val WarningYellow = Color(0xFFFFAB00)
val CrimsonRed = Color(0xFFFF5252)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val DarkJarvisColors = JarvisColors(
    background = Color(0xFF040811),
    surface = Color(0xFF09101E),
    cardSurface = Color(0xFF101E2D),
    cardBorder = Color(0xFF1A3348),
    textPrimary = Color(0xFFEFF4F8),
    textSecondary = Color(0xFF8CA8BF),
    textMuted = Color(0xFF526D87),
    cyanPrimary = Color(0xFF00E5FF),
    cyanBright = Color(0xFF18FFFF),
    cyanDark = Color(0xFF0091EA),
    blueAccent = Color(0xFF0099EA),
    isDark = true
)

val LightJarvisColors = JarvisColors(
    background = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    cardSurface = Color(0xFFE8EEF5),
    cardBorder = Color(0xFFD0DDEB),
    textPrimary = Color(0xFF0D1B2A),
    textSecondary = Color(0xFF415A77),
    textMuted = Color(0xFF778DA9),
    cyanPrimary = Color(0xFF0091EA),
    cyanBright = Color(0xFF00B0FF),
    cyanDark = Color(0xFF01579B),
    blueAccent = Color(0xFF0288D1),
    isDark = false
)

val LocalJarvisColors = staticCompositionLocalOf { DarkJarvisColors }

val JarvisDarkBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.background

val JarvisDarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.surface

val JarvisCardSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.cardSurface

val JarvisCardBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.cardBorder

val JarvisTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.textPrimary

val JarvisTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.textSecondary

val JarvisTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.textMuted

val JarvisCyanPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.cyanPrimary

val JarvisCyanBright: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.cyanBright

val JarvisCyanDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.cyanDark

val JarvisBlueAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalJarvisColors.current.blueAccent
