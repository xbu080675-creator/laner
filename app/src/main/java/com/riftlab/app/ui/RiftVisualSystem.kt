package com.riftlab.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Laner console visual system.
 *
 * The UI behaves like a console shell rather than a mobile card stack: rectangular focus surfaces,
 * visible controller/keyboard focus, layered depth and restrained team-accent chrome.
 */
@Composable
internal fun RiftHudPanel(
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val engaged = accent || focused
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 1.012f else 1f,
        label = "laner-console-focus-scale"
    )

    val accentColor = RiftCyan
    val panel = RiftPanel
    val panelAlt = RiftPanelAlt
    val line = RiftLine
    val shape = RoundedCornerShape(3.dp)

    var base = modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
        }
        .clip(shape)
        .background(
            Brush.horizontalGradient(
                listOf(
                    if (engaged) panelAlt.copy(alpha = 0.98f) else panel.copy(alpha = 0.93f),
                    panel.copy(alpha = 0.86f),
                    panelAlt.copy(alpha = 0.72f)
                )
            )
        )
        .drawBehind {
            val leftRail = if (engaged) 4.dp.toPx() else 2.dp.toPx()
            drawRect(
                color = if (engaged) accentColor else line.copy(alpha = 0.52f),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(leftRail, size.height)
            )
            drawLine(
                color = if (engaged) accentColor.copy(alpha = 0.82f) else line.copy(alpha = 0.30f),
                start = Offset(leftRail, 0f),
                end = Offset(size.width * if (engaged) 0.54f else 0.24f, 0f),
                strokeWidth = if (engaged) 2.dp.toPx() else 1.dp.toPx()
            )
            drawLine(
                color = line.copy(alpha = if (focused) 0.72f else 0.24f),
                start = Offset(leftRail, size.height - 1.dp.toPx()),
                end = Offset(size.width, size.height - 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            if (focused) {
                drawLine(
                    color = accentColor.copy(alpha = 0.38f),
                    start = Offset(size.width - 1.dp.toPx(), 0f),
                    end = Offset(size.width - 1.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

    if (onClick != null) {
        base = base
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    }

    Column(
        base.padding(start = 18.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        content = content
    )
}

@Composable
internal fun RiftSectionLabel(value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "//",
            color = RiftCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = RiftText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(RiftLine.copy(alpha = 0.70f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
internal fun RiftStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color? = null
) {
    val resolved = tone ?: RiftCyan
    Text(
        text,
        color = resolved,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.9.sp,
        modifier = modifier
            .background(resolved.copy(alpha = 0.08f), RoundedCornerShape(2.dp))
            .drawBehind {
                drawRect(
                    color = resolved.copy(alpha = 0.88f),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)
                )
            }
            .padding(start = 9.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)
    )
}

/**
 * Non-interactive system chrome layered over the team backdrop. It adds subtle scan bands, edge
 * rails and directional glow without owning or inferring any business state.
 */
@Composable
internal fun RiftConsoleAmbientLayer(modifier: Modifier = Modifier) {
    val accent = RiftCyan
    val secondary = MaterialTheme.colorScheme.secondary
    val line = RiftLine

    Canvas(modifier.fillMaxSize()) {
        val band = 56.dp.toPx()
        var y = band
        while (y < size.height) {
            drawLine(
                color = line.copy(alpha = 0.055f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += band
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.08f),
                radius = size.minDimension * 0.70f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.055f), Color.Transparent),
                center = Offset(size.width * 0.10f, size.height * 0.92f),
                radius = size.minDimension * 0.62f
            )
        )

        drawLine(
            color = accent.copy(alpha = 0.20f),
            start = Offset(size.width * 0.72f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = line.copy(alpha = 0.22f),
            start = Offset(0f, size.height - 1.dp.toPx()),
            end = Offset(size.width * 0.34f, size.height - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}
