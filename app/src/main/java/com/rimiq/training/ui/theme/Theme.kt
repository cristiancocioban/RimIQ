package com.rimiq.training.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF7A00),
    onPrimary = Color.White,
    secondary = Color(0xFF5B6B8A),
    background = Color(0xFF0B1020),
    surface = Color(0xFF151E33),
    onSurface = Color.White
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFFF7A00),
    onPrimary = Color.White,
    background = Color(0xFFF2F3F8),
    onBackground = Color(0xFF101522)
)

@Composable
fun RimIQTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
