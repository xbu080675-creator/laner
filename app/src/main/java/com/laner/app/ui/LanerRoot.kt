package com.laner.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveMatchContextService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostTimelineService
import com.laner.core.application.PreMatchContextService
import com.laner.core.application.WatchPort
import com.laner.core.domain.MatchPhase

/**
 * Product shell intentionally mirrors the established RiftLab interaction language.
 * Architecture and data ownership are new; product behavior is not redesigned during migration.
 */
@Composable
fun LanerRoot(
    scheduleService: GlobalScheduleService,
    preMatchContextService: PreMatchContextService,
    competitionStructureService: CompetitionStructureService,
    liveMatchContextService: LiveMatchContextService,
    postMatchService: PostMatchService,
    postTimelineService: PostTimelineService,
    watchPort: WatchPort,
    overlayPermissionGranted: Boolean,
    riftScreenRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartRiftScreen: () -> Unit,
    onStopRiftScreen: () -> Unit,
) {
    var selectedPhase by remember { mutableStateOf(MatchPhase.LIVE_MATCH) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LanerHeader()
            PhaseSwitcher(selected = selectedPhase, onSelect = { selectedPhase = it })
            AnimatedContent(
                targetState = selectedPhase,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "phase",
            ) { phase ->
                when (phase) {
                    MatchPhase.PRE_MATCH -> Column(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    ) {
                        CompetitionStructurePanel(
                            service = competitionStructureService,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        PreMatchScreen(
                            scheduleService = scheduleService,
                            preMatchContextService = preMatchContextService,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MatchPhase.LIVE_MATCH -> LiveMatchScreen(
                        liveMatchContextService = liveMatchContextService,
                        watchPort = watchPort,
                        overlayPermissionGranted = overlayPermissionGranted,
                        riftScreenRunning = riftScreenRunning,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onStartRiftScreen = onStartRiftScreen,
                        onStopRiftScreen = onStopRiftScreen,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    )
                    MatchPhase.POST_MATCH -> PostMatchScreen(
                        scheduleService = scheduleService,
                        postMatchService = postMatchService,
                        postTimelineService = postTimelineService,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanerHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp)
                .background(MaterialTheme.colorScheme.primary, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "L",
                color = MaterialTheme.colorScheme.background,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "LANER",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "GLOBAL ESPORTS COMPANION",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

@Composable
private fun PhaseSwitcher(selected: MatchPhase, onSelect: (MatchPhase) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 2.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                CutCornerShape(topEnd = 16.dp, bottomStart = 10.dp),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        MatchPhase.entries.forEach { phase ->
            val active = phase == selected
            Column(
                Modifier.weight(1f)
                    .clickable { onSelect(phase) }
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                        CutCornerShape(topEnd = 11.dp, bottomStart = 7.dp),
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    phase.label,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.width(if (active) 30.dp else 12.dp)
                        .height(if (active) 3.dp else 1.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        ),
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
