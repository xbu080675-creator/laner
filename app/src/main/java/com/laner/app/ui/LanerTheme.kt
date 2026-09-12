package com.laner.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LanerColors = darkColorScheme(
    background = Color(0xFF090B10),
    surface = Color(0xFF11151D),
    surfaceVariant = Color(0xFF171D27),
    primary = Color(0xFFB8F7FF),
    secondary = Color(0xFF8EA6C6),
    onBackground = Color(0xFFF4F7FB),
    onSurface = Color(0xFFF4F7FB),
    onSurfaceVariant = Color(0xFFAAB4C4),
    error = Color(0xFFFF6B78),
)

@Composable
fun LanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LanerColors,
        content = content,
    )
}
