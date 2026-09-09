package com.example.ui.theme

enum class ThemeMode(val id: String, val title: String, val subtitle: String) {
    SYSTEM(
        id = "system",
        title = "System Default",
        subtitle = "Matches your Android device system setting"
    ),
    DARK(
        id = "dark",
        title = "Dark (JARVIS HUD)",
        subtitle = "Deep space OLED black with electric cyan glow"
    ),
    LIGHT(
        id = "light",
        title = "Light (Clean Lab)",
        subtitle = "Crisp pearl white with high-contrast slate text"
    )
}
