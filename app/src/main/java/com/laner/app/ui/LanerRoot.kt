package com.laner.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveMatchStateService
import com.laner.core.application.LiveTimelineService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostTimelineService
import com.laner.core.application.PreMatchContextService
import com.laner.core.application.StartingRosterAssistService
import com.laner.core.domain.MatchPhase

@Composable
fun LanerRoot(
    scheduleService: GlobalScheduleService,
    preMatchContextService: PreMatchContextService,
    startingRosterAssistService: StartingRosterAssistService,
    competitionStructureService: CompetitionStructureService,
    liveMatchStateService: LiveMatchStateService,
    liveTimelineService: LiveTimelineService,
    postMatchService: PostMatchService,
    postTimelineService: PostTimelineService,
    riotCredentialConfigured: Boolean,
    runtimeCredentialActive: Boolean,
    onSaveRuntimeCredential: (String) -> Unit,
    onClearRuntimeCredential: () -> Unit,
) {
    var selectedPhase by remember { mutableStateOf(MatchPhase.PRE_MATCH) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            LanerHeader()
            Spacer(Modifier.height(10.dp))
            RiotCredentialPanel(
                configured = riotCredentialConfigured,
                runtimeOverrideActive = runtimeCredentialActive,
                onSave = onSaveRuntimeCredential,
                onClearRuntimeOverride = onClearRuntimeCredential,
            )
            Spacer(Modifier.height(12.dp))
            PhaseSwitcher(selected = selectedPhase, onSelect = { selectedPhase = it })
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = selectedPhase,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "laner-phase",
            ) { phase ->
                when (phase) {
                    MatchPhase.PRE_MATCH -> Column(Modifier.fillMaxSize()) {
                        StartingRosterAssistPanel(
                            scheduleService = scheduleService,
                            assistService = startingRosterAssistService,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        CompetitionStructurePanel(service = competitionStructureService, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        PreMatchScreen(
                            scheduleService = scheduleService,
                            preMatchContextService = preMatchContextService,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    MatchPhase.LIVE_MATCH -> LiveMatchScreen(
                        scheduleService = scheduleService,
                        liveMatchStateService = liveMatchStateService,
                        liveTimelineService = liveTimelineService,
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
private fun LanerHeader() {
    Column {
        Text("LANER", fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("GLOBAL ESPORTS COMPANION", fontSize = 11.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhaseSwitcher(selected: MatchPhase, onSelect: (MatchPhase) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MatchPhase.entries.forEach { phase ->
            val active = phase == selected
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(phase) }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = phase.label,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
