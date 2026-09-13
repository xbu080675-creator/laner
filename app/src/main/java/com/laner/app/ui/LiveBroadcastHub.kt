package com.laner.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laner.app.stream.StreamPlatform
import com.laner.app.stream.StreamRegion
import com.laner.core.application.LiveMatchContextResult
import com.laner.core.application.LiveMatchContextService
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.MatchLifecycleState

private data class WatchStatus(
    val gameLive: Boolean = false,
    val eventActive: Boolean = false,
    val matchup: String = "",
)

@Composable
fun BroadcastHubLauncher(
    liveMatchContextService: LiveMatchContextService,
    onWatch: (StreamPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val watchStatus by produceState(
        initialValue = WatchStatus(),
        key1 = liveMatchContextService,
    ) {
        val now = System.currentTimeMillis()
        value = when (val result = liveMatchContextService.load(SourceRequestContext(now, "watch-hub-$now"))) {
            is LiveMatchContextResult.Ready -> {
                val lifecycle = result.liveState.state.lifecycle
                val gameLive = lifecycle == MatchLifecycleState.IN_GAME
                val eventActive = lifecycle in setOf(
                    MatchLifecycleState.EVENT_LIVE_PRE_GAME,
                    MatchLifecycleState.DRAFT,
                    MatchLifecycleState.LOADING,
                    MatchLifecycleState.IN_GAME,
                    MatchLifecycleState.POST_GAME,
                    MatchLifecycleState.BETWEEN_GAMES,
                )
                WatchStatus(
                    gameLive = gameLive,
                    eventActive = eventActive,
                    matchup = result.match.teams.take(2).joinToString(" VS ") {
                        it.team.code.ifBlank { it.team.name }
                    },
                )
            }
            else -> WatchStatus()
        }
    }

    val transition = rememberInfiniteTransition(label = "laner-watch-live")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760), repeatMode = RepeatMode.Reverse),
        label = "laner-watch-pulse",
    )
    val shape = CutCornerShape(topEnd = 12.dp, bottomStart = 8.dp)

    Column(
        modifier
            .clickable { open = true }
            .background(
                when {
                    watchStatus.gameLive -> RiftRed.copy(alpha = 0.12f)
                    watchStatus.eventActive -> RiftCyan.copy(alpha = 0.10f)
                    else -> RiftPanel
                },
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (watchStatus.gameLive) {
                Box(Modifier.size(8.dp).background(RiftRed.copy(alpha = pulse), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = RiftRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
                Text("观赛", color = RiftText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    if (watchStatus.eventActive) "ON AIR · 观赛" else "直播入口",
                    color = if (watchStatus.eventActive) RiftCyan else RiftText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (watchStatus.gameLive && watchStatus.matchup.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(watchStatus.matchup, color = RiftMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (open) {
        BroadcastSourceDialog(
            onClose = { open = false },
            onWatch = { platform ->
                open = false
                onWatch(platform)
            },
        )
    }
}

@Composable
private fun BroadcastSourceDialog(
    onClose: () -> Unit,
    onWatch: (StreamPlatform) -> Unit,
) {
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
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
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
                BroadcastRegionSection(
                    title = "中国大陆",
                    platforms = StreamPlatform.entries.filter { it.region == StreamRegion.MAINLAND },
                    onOpen = onWatch,
                )
                Spacer(Modifier.height(16.dp))
                BroadcastRegionSection(
                    title = "海外 / GLOBAL",
                    platforms = StreamPlatform.entries.filter { it.region == StreamRegion.GLOBAL },
                    onOpen = onWatch,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "直播入口只负责打开对应平台；RiftScreen 与赛事实时数据继续由 Laner 独立运行。平台入口不会成为赛事事实来源。",
                    color = RiftMuted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun BroadcastRegionSection(
    title: String,
    platforms: List<StreamPlatform>,
    onOpen: (StreamPlatform) -> Unit,
) {
    Text(title.uppercase(), color = RiftMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(7.dp))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        platforms.forEach { platform ->
            val shape = CutCornerShape(topEnd = 10.dp, bottomStart = 7.dp)
            Row(
                Modifier.fillMaxWidth()
                    .background(RiftPanel, shape)
                    .clickable { onOpen(platform) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(platform.displayName, color = RiftText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (platform.packages.isEmpty()) "网页入口" else "优先打开已安装 APP · 否则网页",
                        color = RiftMuted,
                        fontSize = 11.sp,
                    )
                }
                Text("OPEN", color = RiftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
