package com.laner.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laner.core.application.WatchDestination
import com.laner.core.application.WatchPort
import com.laner.core.application.WatchRegion
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries

data class WatchHubPresentation(
    val gameLive: Boolean,
    val eventActive: Boolean,
    val matchup: String,
) {
    companion object {
        val Idle = WatchHubPresentation(gameLive = false, eventActive = false, matchup = "")
    }
}

/** Presentation-only mapping of canonical Application/domain state; it never invents match facts. */
internal object WatchHubPresentationMapper {
    fun from(match: ScheduledSeries?, lifecycle: MatchLifecycleState?): WatchHubPresentation {
        if (match == null) return WatchHubPresentation.Idle

        val gameLive = lifecycle == MatchLifecycleState.IN_GAME
        val eventActive = gameLive ||
            match.state == ScheduleState.EVENT_LIVE ||
            lifecycle in setOf(
                MatchLifecycleState.EVENT_LIVE_PRE_GAME,
                MatchLifecycleState.DRAFT,
                MatchLifecycleState.LOADING,
                MatchLifecycleState.POST_GAME,
                MatchLifecycleState.BETWEEN_GAMES,
            )
        val matchup = match.teams
            .take(2)
            .joinToString(" VS ") { scheduled ->
                scheduled.team.code.ifBlank { scheduled.team.name }
            }

        return WatchHubPresentation(
            gameLive = gameLive,
            eventActive = eventActive,
            matchup = matchup,
        )
    }
}

@Composable
internal fun WatchHubSurface(
    presentation: WatchHubPresentation,
    watchPort: WatchPort,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var launchMessage by remember { mutableStateOf<String?>(null) }

    WatchHubLauncher(
        presentation = presentation,
        onClick = { open = true },
        modifier = modifier,
    )

    if (open) {
        WatchHubDialog(
            watchPort = watchPort,
            launchMessage = launchMessage,
            onClose = { open = false },
            onLaunch = { destination ->
                val result = watchPort.launch(destination.id)
                launchMessage = result.message
                if (result.destinationId != null) open = false
            },
        )
    }
}

@Composable
private fun WatchHubLauncher(
    presentation: WatchHubPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "laner-live-badge")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "laner-live-pulse",
    )
    val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 8.dp)

    Column(
        modifier
            .clickable(onClick = onClick)
            .background(
                when {
                    presentation.gameLive -> RiftRed.copy(alpha = 0.12f)
                    presentation.eventActive -> RiftCyan.copy(alpha = 0.10f)
                    else -> RiftPanel
                },
                shape,
            )
            .border(1.dp, RiftLine, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (presentation.gameLive) {
                Box(
                    Modifier.size(8.dp).background(RiftRed.copy(alpha = pulse), CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = RiftRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
                Text("观赛", color = RiftText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    if (presentation.eventActive) "ON AIR · 观赛" else "直播入口",
                    color = if (presentation.eventActive) RiftCyan else RiftText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (presentation.gameLive && presentation.matchup.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(presentation.matchup, color = RiftMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WatchHubDialog(
    watchPort: WatchPort,
    launchMessage: String?,
    onClose: () -> Unit,
    onLaunch: (WatchDestination) -> Unit,
) {
    val destinations = remember(watchPort) { watchPort.destinations() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            color = RiftBg,
            contentColor = RiftText,
            shape = CutCornerShape(topEnd = 20.dp, bottomStart = 14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LIVE / 观赛入口", color = RiftText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("国内 + 海外官方/主流直播入口", color = RiftMuted, fontSize = 11.sp)
                    }
                    Text(
                        "关闭",
                        color = RiftCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                WatchRegionSection(
                    title = "中国大陆",
                    destinations = destinations.filter { it.region == WatchRegion.MAINLAND },
                    onLaunch = onLaunch,
                )

                Spacer(Modifier.height(16.dp))
                WatchRegionSection(
                    title = "海外 / GLOBAL",
                    destinations = destinations.filter { it.region == WatchRegion.GLOBAL },
                    onLaunch = onLaunch,
                )

                launchMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = RiftMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "直播入口只负责跳转到对应平台；RiftScreen 与赛事实时数据仍由 Laner 独立运行。海外入口包括 LoL Esports 官方站、YouTube、Twitch 与 X。",
                    color = RiftMuted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun WatchRegionSection(
    title: String,
    destinations: List<WatchDestination>,
    onLaunch: (WatchDestination) -> Unit,
) {
    Text(title, color = RiftMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        destinations.forEach { destination ->
            val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 8.dp)
            Row(
                Modifier.fillMaxWidth()
                    .background(RiftPanel, shape)
                    .border(1.dp, RiftLine, shape)
                    .clickable { onLaunch(destination) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(destination.displayName, color = RiftText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (destination.nativeAppSupported) "优先打开已安装 APP · 否则网页" else "网页入口",
                        color = RiftMuted,
                        fontSize = 11.sp,
                    )
                }
                Text("OPEN", color = RiftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
