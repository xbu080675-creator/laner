package com.laner.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laner.app.BuildConfig
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveMatchContextService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostTimelineService
import com.laner.core.application.PreMatchContextService
import com.laner.core.domain.MatchPhase

/**
 * User-facing root keeps the established RiftLab product shell while consuming the rebuilt Laner
 * Application services. Provider credentials and other implementation details must not live here.
 */
@Composable
fun LanerRoot(
    scheduleService: GlobalScheduleService,
    preMatchContextService: PreMatchContextService,
    liveMatchContextService: LiveMatchContextService,
    postMatchService: PostMatchService,
    postTimelineService: PostTimelineService,
    overlayPermissionGranted: Boolean,
    riftScreenRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartRiftScreen: () -> Unit,
    onStopRiftScreen: () -> Unit,
) {
    var selectedPhase by remember { mutableStateOf(MatchPhase.LIVE_MATCH) }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            RiftHeader()
            RiftPhaseTabs(selected = selectedPhase, onSelect = { selectedPhase = it })
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = selectedPhase,
                modifier = Modifier.weight(1f).padding(horizontal = 18.dp),
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "laner-rift-phase",
            ) { phase ->
                when (phase) {
                    MatchPhase.PRE_MATCH -> PreMatchScreen(
                        scheduleService = scheduleService,
                        preMatchContextService = preMatchContextService,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MatchPhase.LIVE_MATCH -> LiveMatchScreen(
                        liveMatchContextService = liveMatchContextService,
                        overlayPermissionGranted = overlayPermissionGranted,
                        riftScreenRunning = riftScreenRunning,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onStartRiftScreen = onStartRiftScreen,
                        onStopRiftScreen = onStopRiftScreen,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MatchPhase.POST_MATCH -> PostMatchScreen(
                        scheduleService = scheduleService,
                        postMatchService = postMatchService,
                        postTimelineService = postTimelineService,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RiftHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "L",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "LANER",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "LEAGUE ESPORTS COMPANION",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                letterSpacing = 1.1.sp,
            )
        }
        Text(
            text = BuildConfig.VERSION_NAME.uppercase(),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RiftPhaseTabs(
    selected: MatchPhase,
    onSelect: (MatchPhase) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                CutCornerShape(topEnd = 16.dp, bottomStart = 10.dp),
            )
            .padding(4.dp),
    ) {
        MatchPhase.entries.forEach { phase ->
            val active = phase == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(phase) }
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                        CutCornerShape(topEnd = 11.dp, bottomStart = 7.dp),
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = phase.label,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(if (active) 30.dp else 12.dp)
                        .height(if (active) 3.dp else 1.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        )
                )
            }
        }
    }
}

private val MatchPhase.label: String
    get() = when (this) {
        MatchPhase.PRE_MATCH -> "赛前"
        MatchPhase.LIVE_MATCH -> "赛中"
        MatchPhase.POST_MATCH -> "赛后"
    }
