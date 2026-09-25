package com.batteryfox.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FoxBackground = Color(0xFF0F0F12)
val FoxSurface = Color(0xFF1A1A22)
val FoxSurfaceVariant = Color(0xFF262633)
val FoxAccentOrange = Color(0xFFFF6D00)
val FoxElectricGreen = Color(0xFF00E676)
val FoxTextPrimary = Color(0xFFFFFFFF)
val FoxTextSecondary = Color(0xFF9E9EAE)

private val DarkColorScheme = darkColorScheme(
    primary = FoxAccentOrange,
    secondary = FoxElectricGreen,
    background = FoxBackground,
    surface = FoxSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = FoxTextPrimary,
    onSurface = FoxTextPrimary
)

@Composable
fun BatteryFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
