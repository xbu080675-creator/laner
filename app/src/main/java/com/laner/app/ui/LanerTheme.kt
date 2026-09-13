package com.laner.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Product-surface compatibility theme.
 *
 * Laner keeps the rebuilt Core/Application stack, but the user-facing visual language follows the
 * existing RiftLab baseline instead of introducing a second unrelated Material shell.
 */
private val RiftDarkColors = darkColorScheme(
    primary = Color(0xFFFF365D),
    secondary = Color(0xFF43D6F1),
    background = Color(0xFF090B10),
    surface = Color(0xEF10141C),
    surfaceVariant = Color(0xE8161C26),
    outline = Color(0xFF2A3340),
    error = Color(0xFFFF667A),
    onPrimary = Color(0xFF090B10),
    onSecondary = Color(0xFF090B10),
    onBackground = Color(0xFFF3F5F8),
    onSurface = Color(0xFFF3F5F8),
    onSurfaceVariant = Color(0xFFA0A8B5),
)

private val RiftLightColors = lightColorScheme(
    primary = Color(0xFFD92345),
    secondary = Color(0xFF238FB9),
    background = Color(0xFFF6F4F6),
    surface = Color(0xEEFFFFFF),
    surfaceVariant = Color(0xEAF0EEF2),
    outline = Color(0xFFC9CBD3),
    error = Color(0xFFC83D50),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF15181D),
    onSurface = Color(0xFF15181D),
    onSurfaceVariant = Color(0xFF626B78),
)

private val RiftTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

@Composable
fun LanerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) RiftDarkColors else RiftLightColors

    MaterialTheme(
        colorScheme = scheme,
        typography = RiftTypography,
    ) {
        RiftBackdrop(dark = dark, content = content)
    }
}

@Composable
private fun RiftBackdrop(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val base = if (dark) {
        listOf(scheme.background, scheme.surfaceVariant, scheme.background)
    } else {
        listOf(scheme.background, Color.White, scheme.surfaceVariant)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = base,
                    start = Offset.Zero,
                    end = Offset(1200f, 2100f),
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glowAlpha = if (dark) 0.16f else 0.09f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = glowAlpha), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.12f),
                    radius = size.minDimension * 0.88f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.secondary.copy(alpha = glowAlpha * 0.75f), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.78f),
                    radius = size.minDimension * 0.78f,
                )
            )

            val gridColor = scheme.outline.copy(alpha = if (dark) 0.16f else 0.12f)
            val step = 72f
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
        content()
    }
}
