package com.laner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.LiveMatchStateService
import com.laner.core.application.LiveStateLoadStatus
import com.laner.core.application.LiveStateResolution
import com.laner.core.application.LiveTimelineService
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DraftChangedEvent
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.GoldLeadChangedEvent
import com.laner.core.domain.KillEvent
import com.laner.core.domain.MatchEvent
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.MatchStateChanged
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import kotlin.math.abs

private sealed interface LiveScreenState {
    data object Loading : LiveScreenState
    data class NoTarget(val reason: String) : LiveScreenState
    data class Ready(
        val match: ScheduledSeries,
        val resolution: LiveStateResolution,
        val timeline: GameTimeline?,
    ) : LiveScreenState
    data class Failed(val message: String) : LiveScreenState
}

@Composable
fun LiveMatchScreen(
    scheduleService: GlobalScheduleService,
    liveMatchStateService: LiveMatchStateService,
    liveTimelineService: LiveTimelineService,
    modifier: Modifier = Modifier,
) {
    var refreshNonce by remember { mutableIntStateOf(0) }

    val state by produceState<LiveScreenState>(
        initialValue = LiveScreenState.Loading,
        key1 = scheduleService,
        key2 = liveMatchStateService,
        key3 = refreshNonce,
    ) {
        value = try {
            val now = System.currentTimeMillis()
            val context = SourceRequestContext(
                nowEpochMillis = now,
                correlationId = "live-$now-$refreshNonce",
            )
            val schedule = scheduleService.load(context)
            val target = selectLiveTarget(schedule.matches, now)
            if (target == null) {
                LiveScreenState.NoTarget(
                    reason = if (schedule.matches.isEmpty()) {
                        "当前没有可用赛事目录，LIVE 不会自行猜测比赛目标。"
                    } else {
                        "当前没有可识别的赛事目标。"
                    }
                )
            } else {
                val resolution = liveMatchStateService.refresh(
                    query = LiveMatchSourceQuery.from(target),
                    context = context,
                )
                LiveScreenState.Ready(
                    match = target,
                    resolution = resolution,
                    timeline = resolution.state.currentGameId?.let { liveTimelineService.load(it) },
                )
            }
        } catch (error: Throwable) {
            LiveScreenState.Failed(
                error.message?.take(180)?.takeIf { it.isNotBlank() }
                    ?: error::class.java.simpleName
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LiveHeaderCard(onRefresh = { refreshNonce += 1 })
        }
        when (val current = state) {
            LiveScreenState.Loading -> item {
                LiveMessageCard(
                    title = "正在建立赛中上下文",
                    body = "先从标准赛程确定比赛目标，再读取 Application LIVE truth。",
                )
            }
            is LiveScreenState.NoTarget -> item {
                LiveMessageCard(
                    title = "暂无赛中目标",
                    body = current.reason,
                )
            }
            is LiveScreenState.Failed -> item {
                LiveMessageCard(
                    title = "赛中状态读取失败",
                    body = current.message,
                )
            }
            is LiveScreenState.Ready -> {
                item { LiveTargetCard(current.match) }
                item { LiveAuthorityCard(current.resolution) }
                item { LiveTimelineCard(current.resolution, current.timeline) }
            }
        }
    }
}

@Composable
private fun LiveHeaderCard(onRefresh: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE / 赛中",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "比赛发生什么，为什么",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "页面只消费 Application truth；没有可验证 LIVE Source 时明确降级，不生成假数据。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRefresh) { Text("刷新") }
        }
    }
}

@Composable
private fun LiveTargetCard(match: ScheduledSeries) {
    val left = match.teams.getOrNull(0)?.team?.code.orEmpty()
    val right = match.teams.getOrNull(1)?.team?.code.orEmpty()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "MATCH TARGET / 当前目标",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$left  vs  $right",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = match.competition.name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveAuthorityCard(resolution: LiveStateResolution) {
    val state = resolution.state
    val statusText = when (resolution.status) {
        LiveStateLoadStatus.READY -> "READY"
        LiveStateLoadStatus.DEGRADED -> "DEGRADED"
        LiveStateLoadStatus.CONFLICT -> "CONFLICT"
        LiveStateLoadStatus.UNAVAILABLE -> "UNAVAILABLE"
    }
    val body = when (resolution.status) {
        LiveStateLoadStatus.UNAVAILABLE ->
            "当前没有已验证的实时 Provider。Cito 在线验收暂缓；若本地存在 last-known state 会保留，否则保持未知。"
        LiveStateLoadStatus.CONFLICT ->
            "不同事实发生冲突，Application 已阻止静默覆盖。"
        LiveStateLoadStatus.DEGRADED ->
            "部分来源不可用或证据不足，当前状态按降级语义展示。"
        LiveStateLoadStatus.READY ->
            "当前状态已通过 LIVE Source Arbitration。"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "AUTHORITATIVE STATE / 权威状态",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = lifecycleLabel(state.lifecycle),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Source $statusText · ${resolution.selectedProviderId ?: "NO VERIFIED SOURCE"}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.currentGameNumber?.let { gameNumber ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "当前 G$gameNumber",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveTimelineCard(
    resolution: LiveStateResolution,
    timeline: GameTimeline?,
) {
    val gameId = resolution.state.currentGameId
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "TIMELINE / 本地事件链",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            when {
                gameId == null -> Text(
                    text = "尚无已验证 Game identity。赛事开始本身不会创建小局 Timeline。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                timeline == null -> Text(
                    text = "G${resolution.state.currentGameNumber ?: "?"} 已有 identity，但本地尚无标准 Snapshot/Event。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    Text(
                        text = "G${timeline.gameNumber} · ${timeline.snapshots.size} snapshots · ${timeline.events.size} events${if (timeline.completed) " · COMPLETE" else ""}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (timeline.events.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "本地 Timeline 暂无事件。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        timeline.events.takeLast(8).forEachIndexed { index, event ->
                            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "${eventTime(event)}  ${eventLabel(event)}",
                                fontSize = 13.sp,
                            )
                            Text(
                                text = "${event.evidence.name} · ${event.provenance.providerId}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMessageCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun selectLiveTarget(
    matches: List<ScheduledSeries>,
    nowEpochMillis: Long,
): ScheduledSeries? {
    val live = matches
        .filter { it.state == ScheduleState.EVENT_LIVE }
        .minByOrNull { abs(nowEpochMillis - it.startTimeEpochMillis) }
    if (live != null) return live

    return matches
        .filter { it.state != ScheduleState.COMPLETED }
        .minByOrNull { abs(nowEpochMillis - it.startTimeEpochMillis) }
}

private fun lifecycleLabel(state: MatchLifecycleState): String = when (state) {
    MatchLifecycleState.UNKNOWN -> "状态未知"
    MatchLifecycleState.PRE_EVENT -> "赛事未开始"
    MatchLifecycleState.EVENT_LIVE_PRE_GAME -> "赛事已开始 · 游戏未开始"
    MatchLifecycleState.DRAFT -> "BP / Draft"
    MatchLifecycleState.LOADING -> "载入游戏"
    MatchLifecycleState.IN_GAME -> "游戏进行中"
    MatchLifecycleState.POST_GAME -> "本局已结束"
    MatchLifecycleState.BETWEEN_GAMES -> "场间"
    MatchLifecycleState.SERIES_COMPLETE -> "系列赛结束"
}

private fun eventTime(event: MatchEvent): String {
    val seconds = event.gameTimeSeconds ?: return "--:--"
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun eventLabel(event: MatchEvent): String = when (event) {
    is MatchStateChanged -> "${lifecycleLabel(event.previous)} → ${lifecycleLabel(event.current)}"
    is KillEvent -> "击杀事件 · ${event.teamId?.value ?: "未知队伍"}"
    is ObjectiveTakenEvent -> "${event.objective.name} · ${event.teamId.value}${event.detail?.let { " · $it" } ?: ""}"
    is GoldLeadChangedEvent -> "经济差 ${event.goldDifference} · ${event.leadingTeamId?.value ?: "持平/未知"}"
    is DraftChangedEvent -> "${event.action.name} · ${event.championId ?: "unknown champion"}"
}
