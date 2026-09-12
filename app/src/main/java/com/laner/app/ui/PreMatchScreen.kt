package com.laner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.GlobalScheduleSnapshot
import com.laner.core.application.ScheduleLoadStatus
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface PreMatchLoadState {
    data object Loading : PreMatchLoadState
    data class Ready(val snapshot: GlobalScheduleSnapshot) : PreMatchLoadState
    data class Failed(val message: String) : PreMatchLoadState
}

@Composable
fun PreMatchScreen(
    scheduleService: GlobalScheduleService,
    modifier: Modifier = Modifier,
) {
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedCompetition by remember { mutableStateOf<CompetitionId?>(null) }
    val loadState by produceState<PreMatchLoadState>(
        initialValue = PreMatchLoadState.Loading,
        key1 = scheduleService,
        key2 = refreshNonce,
    ) {
        value = try {
            val now = System.currentTimeMillis()
            PreMatchLoadState.Ready(
                scheduleService.load(
                    SourceRequestContext(
                        nowEpochMillis = now,
                        correlationId = "pre-$now-$refreshNonce",
                    )
                )
            )
        } catch (error: Throwable) {
            PreMatchLoadState.Failed(
                error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
            )
        }
    }

    when (val state = loadState) {
        PreMatchLoadState.Loading -> PreMatchMessage(
            title = "正在同步全球赛事",
            body = "读取真实赛事目录与赛程，不使用 Mock 数据。",
            modifier = modifier,
        )
        is PreMatchLoadState.Failed -> PreMatchMessage(
            title = "赛前数据加载失败",
            body = state.message,
            modifier = modifier,
        )
        is PreMatchLoadState.Ready -> PreMatchContent(
            snapshot = state.snapshot,
            selectedCompetition = selectedCompetition,
            onCompetitionSelected = { selectedCompetition = it },
            onRefresh = { refreshNonce += 1 },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreMatchContent(
    snapshot: GlobalScheduleSnapshot,
    selectedCompetition: CompetitionId?,
    onCompetitionSelected: (CompetitionId?) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleMatches = remember(snapshot.matches, selectedCompetition) {
        snapshot.matches.filter { match ->
            selectedCompetition == null || match.competition.id == selectedCompetition
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScheduleStatusCard(snapshot = snapshot, onRefresh = onRefresh)
        }

        item {
            Column {
                Text(
                    text = "全球赛事目录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedCompetition == null,
                        onClick = { onCompetitionSelected(null) },
                        label = { Text("全部 ${snapshot.catalog.size}") },
                    )
                    snapshot.catalog.forEach { entry ->
                        FilterChip(
                            selected = selectedCompetition == entry.competition.id,
                            onClick = { onCompetitionSelected(entry.competition.id) },
                            label = {
                                Text(
                                    text = competitionLabel(entry.kind, entry.competition.name),
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        item {
            Column {
                Text(
                    text = if (selectedCompetition == null) "全球赛程" else "筛选赛程",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${visibleMatches.size} 场 · 按时间顺序 · 本地时区",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (visibleMatches.isEmpty()) {
            item {
                PreMatchMessage(
                    title = "暂无真实赛程",
                    body = snapshot.failures.firstOrNull()?.let { "${it.code} · ${it.message}" }
                        ?: "当前来源没有返回可展示比赛。",
                )
            }
        } else {
            items(
                items = visibleMatches,
                key = { it.matchId.value },
            ) { match ->
                ScheduleMatchCard(match)
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ScheduleStatusCard(
    snapshot: GlobalScheduleSnapshot,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = when (snapshot.status) {
                    ScheduleLoadStatus.READY -> "全球赛前源 · 正常"
                    ScheduleLoadStatus.DEGRADED -> "全球赛前源 · 降级"
                    ScheduleLoadStatus.UNAVAILABLE -> "全球赛前源 · 不可用"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when (snapshot.status) {
                    ScheduleLoadStatus.READY -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${snapshot.catalog.size} 个赛事 · ${snapshot.matches.size} 场比赛",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            snapshot.failures.firstOrNull()?.let { failure ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${failure.code} · ${failure.message}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "刷新",
                modifier = Modifier.padding(vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            // Keep interaction explicit and minimal: the whole status card remains informational.
            // Refresh is exposed by the caller through the compact action below.
            Surface(
                onClick = onRefresh,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "重新同步",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduleMatchCard(match: ScheduledSeries) {
    val blue = match.teams[0]
    val red = match.teams[1]
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (match.state == ScheduleState.EVENT_LIVE) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                text = "${match.competition.name} · ${match.blockName.ifBlank { "赛事" }}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${teamLabel(blue.team.code, blue.team.name)}  ${blue.gameWins}  :  ${red.gameWins}  ${teamLabel(red.team.code, red.team.name)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = buildString {
                    append(formatLocalTime(match.startTimeEpochMillis))
                    append(" · ")
                    append(match.bestOf?.let { "BO$it" } ?: "BO待确认")
                    append(" · ")
                    append(scheduleStateLabel(match.state))
                },
                fontSize = 11.sp,
                color = if (match.state == ScheduleState.EVENT_LIVE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = "来源 ${match.provenance.providerId}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun PreMatchMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun competitionLabel(kind: CompetitionKind, name: String): String = when (kind) {
    CompetitionKind.WORLD_CHAMPIONSHIP -> "世界赛 · $name"
    CompetitionKind.INTERNATIONAL -> "国际赛 · $name"
    CompetitionKind.REGIONAL -> name
    CompetitionKind.OTHER -> name
}

private fun scheduleStateLabel(state: ScheduleState): String = when (state) {
    ScheduleState.UPCOMING -> "未开始"
    ScheduleState.EVENT_LIVE -> "赛事已开始 · 不代表游戏开局"
    ScheduleState.COMPLETED -> "已结束"
    ScheduleState.UNKNOWN -> "状态待确认"
}

private fun teamLabel(code: String, name: String): String = code.ifBlank { name }

private fun formatLocalTime(epochMillis: Long): String =
    LOCAL_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val LOCAL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "MM-dd E HH:mm",
    Locale.SIMPLIFIED_CHINESE,
)
