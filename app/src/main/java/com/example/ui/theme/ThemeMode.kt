package com.example.ui.theme

enum class ThemeMode(
    val id: String,
    val title: String,
    val subtitle: String
) {
    SYSTEM("system", "System Default", "Matches your Android device system setting"),
    DARK("dark", "Dark (Ravan HUD)", "Deep space OLED black with electric cyan glow"),
    LIGHT("light", "Light (Clean Lab)", "Crisp pearl white with high-contrast slate text")
}
