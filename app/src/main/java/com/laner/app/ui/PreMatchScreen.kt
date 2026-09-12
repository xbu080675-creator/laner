package com.laner.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
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
import com.laner.core.application.MatchPreContextSnapshot
import com.laner.core.application.PreMatchContextService
import com.laner.core.application.PreMatchContextStatus
import com.laner.core.application.ScheduleLoadStatus
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.RecentSeries
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.SeriesOutcome
import com.laner.core.domain.StaffMember
import com.laner.core.domain.StaffRole
import com.laner.core.domain.StartingRosterResolution
import com.laner.core.domain.TeamPreMatchContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface PreMatchLoadState {
    data object Loading : PreMatchLoadState
    data class Ready(val snapshot: GlobalScheduleSnapshot) : PreMatchLoadState
    data class Failed(val message: String) : PreMatchLoadState
}

private sealed interface ContextLoadState {
    data object Idle : ContextLoadState
    data object Loading : ContextLoadState
    data class Ready(val snapshot: MatchPreContextSnapshot) : ContextLoadState
    data class Failed(val message: String) : ContextLoadState
}

@Composable
fun PreMatchScreen(
    scheduleService: GlobalScheduleService,
    preMatchContextService: PreMatchContextService,
    modifier: Modifier = Modifier,
) {
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedCompetition by remember { mutableStateOf<CompetitionId?>(null) }
    var selectedMatchId by remember { mutableStateOf<String?>(null) }

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

    val scheduleSnapshot = (loadState as? PreMatchLoadState.Ready)?.snapshot

    LaunchedEffect(scheduleSnapshot, selectedCompetition) {
        val snapshot = scheduleSnapshot ?: return@LaunchedEffect
        val eligible = snapshot.matches.filter { match ->
            selectedCompetition == null || match.competition.id == selectedCompetition
        }
        if (eligible.none { it.matchId.value == selectedMatchId }) {
            selectedMatchId = focusMatch(eligible, System.currentTimeMillis())?.matchId?.value
        }
    }

    val contextState by produceState<ContextLoadState>(
        initialValue = ContextLoadState.Idle,
        key1 = scheduleSnapshot,
        key2 = selectedMatchId,
        key3 = refreshNonce,
    ) {
        val snapshot = scheduleSnapshot
        val match = snapshot?.matches?.firstOrNull { it.matchId.value == selectedMatchId }
        if (snapshot == null || match == null) {
            value = ContextLoadState.Idle
            return@produceState
        }
        value = ContextLoadState.Loading
        value = try {
            val now = System.currentTimeMillis()
            ContextLoadState.Ready(
                preMatchContextService.load(
                    match = match,
                    scheduleHistory = snapshot.matches,
                    context = SourceRequestContext(
                        nowEpochMillis = now,
                        correlationId = "pre-context-${match.matchId.value}-$now-$refreshNonce",
                    ),
                )
            )
        } catch (error: Throwable) {
            ContextLoadState.Failed(
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
            contextState = contextState,
            selectedCompetition = selectedCompetition,
            selectedMatchId = selectedMatchId,
            onCompetitionSelected = { selectedCompetition = it },
            onMatchSelected = { selectedMatchId = it.matchId.value },
            onRefresh = { refreshNonce += 1 },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreMatchContent(
    snapshot: GlobalScheduleSnapshot,
    contextState: ContextLoadState,
    selectedCompetition: CompetitionId?,
    selectedMatchId: String?,
    onCompetitionSelected: (CompetitionId?) -> Unit,
    onMatchSelected: (ScheduledSeries) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleMatches = remember(snapshot.matches, selectedCompetition) {
        snapshot.matches.filter { match ->
            selectedCompetition == null || match.competition.id == selectedCompetition
        }
    }
    val focusedMatch = snapshot.matches.firstOrNull { it.matchId.value == selectedMatchId }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScheduleStatusCard(snapshot = snapshot, onRefresh = onRefresh) }

        item {
            Column {
                SectionLabel("GLOBAL COMPETITIONS / 全球赛事")
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

        focusedMatch?.let { match ->
            item { SectionLabel("MATCH FOCUS / 本场赛前") }
            item { FocusMatchCard(match) }
            when (contextState) {
                ContextLoadState.Idle -> Unit
                ContextLoadState.Loading -> item {
                    PreMatchMessage(
                        title = "正在建立本场上下文",
                        body = "同步名单池、官方首发证据、Staff 与历史 Series。",
                    )
                }
                is ContextLoadState.Failed -> item {
                    PreMatchMessage(
                        title = "本场上下文加载失败",
                        body = contextState.message,
                    )
                }
                is ContextLoadState.Ready -> {
                    item { ContextHealthCard(contextState.snapshot) }
                    item {
                        TeamContextCard(
                            context = contextState.snapshot.left,
                            recentSeries = contextState.snapshot.leftRecentSeries,
                        )
                    }
                    item {
                        TeamContextCard(
                            context = contextState.snapshot.right,
                            recentSeries = contextState.snapshot.rightRecentSeries,
                        )
                    }
                    item { HeadToHeadCard(contextState.snapshot) }
                }
            }
        }

        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }

        item {
            Column {
                SectionLabel(if (selectedCompetition == null) "GLOBAL SCHEDULE / 全球赛程" else "FILTERED SCHEDULE / 筛选赛程")
                Text(
                    text = "${visibleMatches.size} 场 · 本地时区 · 点选比赛查看赛前上下文",
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
                ScheduleMatchCard(
                    match = match,
                    selected = match.matchId.value == selectedMatchId,
                    onClick = { onMatchSelected(match) },
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ScheduleStatusCard(snapshot: GlobalScheduleSnapshot, onRefresh: () -> Unit) {
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
                color = if (snapshot.status == ScheduleLoadStatus.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
            Surface(onClick = onRefresh, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
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
private fun FocusMatchCard(match: ScheduledSeries) {
    val left = match.teams[0]
    val right = match.teams[1]
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("${match.competition.name} · ${match.blockName.ifBlank { "赛事" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(
                "${teamLabel(left.team.code, left.team.name)}  VS  ${teamLabel(right.team.code, right.team.name)}",
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatLocalTime(match.startTimeEpochMillis)} · ${match.bestOf?.let { "BO$it" } ?: "BO待确认"} · ${scheduleStateLabel(match.state)}",
                fontSize = 11.sp,
                color = if (match.state == ScheduleState.EVENT_LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextHealthCard(snapshot: MatchPreContextSnapshot) {
    if (snapshot.status == PreMatchContextStatus.READY && snapshot.failures.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("赛前上下文 · 部分降级", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            snapshot.failures.take(3).forEach { failure ->
                Text("${failure.code} · ${failure.message}", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun TeamContextCard(context: TeamPreMatchContext, recentSeries: List<RecentSeries>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(teamLabel(context.team.code, context.team.name), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))

            Text("STARTING ROSTER / 首发", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            StartingRosterBlock(context.startingRoster)

            Spacer(Modifier.height(12.dp))
            Text("ROSTER POOL / 名单池（不等于首发）", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val pool = context.rosterPool
            if (pool == null || pool.members.isEmpty()) {
                MutedLine("当前没有可核实名单池。")
            } else {
                pool.members.sortedBy { roleOrder(it.role) }.forEach { player -> MutedLine("${playerRoleLabel(player.role)} · ${player.handle}") }
                MutedLine("SOURCE · ${pool.provenance.providerId}")
            }

            Spacer(Modifier.height(12.dp))
            Text("TEAM STAFF / 教练组与管理人员", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val staff = context.staff
            if (staff == null || (staff.management.isEmpty() && staff.coachingStaff.isEmpty())) {
                MutedLine("当前没有可核实 Staff 数据。")
            } else {
                staff.coachingStaff.take(6).forEach { StaffLine(it) }
                staff.management.take(6).forEach { StaffLine(it) }
                MutedLine("SOURCE · ${staff.provenance.providerId}")
            }

            Spacer(Modifier.height(12.dp))
            Text("RECENT FORM / 近期正式 Series", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recentSeries.isEmpty()) MutedLine("当前历史窗口没有可核实的已结束 Series。")
            else recentSeries.forEach { series -> MutedLine(recentSeriesLine(series)) }
        }
    }
}

@Composable
private fun StartingRosterBlock(resolution: StartingRosterResolution) {
    when (resolution) {
        StartingRosterResolution.Unknown -> {
            Text("首发未确认", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            MutedLine("名单池即使恰好五人，也不会被 Laner 自动当作官方首发。")
        }
        is StartingRosterResolution.Confirmed -> {
            Text(
                if (resolution.crossConfirmed) "首发已交叉确认" else "首发已确认",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            resolution.roster.starters.sortedBy { roleOrder(it.role) }.forEach { player ->
                Text("${playerRoleLabel(player.role)} · ${player.handle}", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            MutedLine("${resolution.roster.account} · ${resolution.roster.platform} · ${resolution.roster.evidenceSource.name} · evidence ${resolution.evidenceCount}")
        }
        is StartingRosterResolution.Conflict -> {
            Text("官方首发证据冲突", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            MutedLine("检测到 ${resolution.candidates.size} 套同级官方阵容；不静默覆盖，等待新证据确认。")
            resolution.candidates.take(2).forEachIndexed { index, roster ->
                MutedLine("方案 ${index + 1} · ${roster.starters.sortedBy { roleOrder(it.role) }.joinToString(" / ") { it.handle }} · ${roster.account}")
            }
        }
    }
}

@Composable
private fun StaffLine(staff: StaffMember) {
    MutedLine(buildString {
        append(staffRoleLabel(staff.role))
        append(" · ")
        append(staff.displayName)
        staff.realName?.let { append(" · "); append(it) }
    })
}

@Composable
private fun HeadToHeadCard(snapshot: MatchPreContextSnapshot) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Text("RECENT H2H / 近期交手", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("${teamLabel(snapshot.left.team.code, snapshot.left.team.name)} 视角", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (snapshot.recentHeadToHeadFromLeftPerspective.isEmpty()) MutedLine("当前历史窗口没有可核实的近期直接交手。")
            else snapshot.recentHeadToHeadFromLeftPerspective.forEach { series -> MutedLine(recentSeriesLine(series)) }
            MutedLine("只使用已验证结束的 Series；W/L 以上方队伍为视角。")
        }
    }
}

@Composable
private fun ScheduleMatchCard(match: ScheduledSeries, selected: Boolean, onClick: () -> Unit) {
    val left = match.teams[0]
    val right = match.teams[1]
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (match.state == ScheduleState.EVENT_LIVE || selected) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${match.competition.name} · ${match.blockName.ifBlank { "赛事" }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (selected) Text("已选", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "${teamLabel(left.team.code, left.team.name)}  ${left.gameWins}  :  ${right.gameWins}  ${teamLabel(right.team.code, right.team.name)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "${formatLocalTime(match.startTimeEpochMillis)} · ${match.bestOf?.let { "BO$it" } ?: "BO待确认"} · ${scheduleStateLabel(match.state)}",
                fontSize = 11.sp,
                color = if (match.state == ScheduleState.EVENT_LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("来源 ${match.provenance.providerId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun MutedLine(text: String) {
    Text(text, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PreMatchMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun focusMatch(matches: List<ScheduledSeries>, nowEpochMillis: Long): ScheduledSeries? {
    if (matches.isEmpty()) return null
    return matches.filter { it.state == ScheduleState.EVENT_LIVE }.minByOrNull { kotlin.math.abs(it.startTimeEpochMillis - nowEpochMillis) }
        ?: matches.filter { it.state == ScheduleState.UPCOMING && it.startTimeEpochMillis >= nowEpochMillis }.minByOrNull { it.startTimeEpochMillis }
        ?: matches.filter { it.state == ScheduleState.COMPLETED }.maxByOrNull { it.startTimeEpochMillis }
        ?: matches.minByOrNull { kotlin.math.abs(it.startTimeEpochMillis - nowEpochMillis) }
}

private fun recentSeriesLine(series: RecentSeries): String = buildString {
    append(seriesOutcomeLabel(series.outcome)); append(" · "); append(series.scoreFor); append(':'); append(series.scoreAgainst)
    append(" vs "); append(teamLabel(series.opponent.code, series.opponent.name)); append(" · "); append(series.competition.name)
    append(" · "); append(formatLocalTime(series.startTimeEpochMillis))
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

private fun seriesOutcomeLabel(outcome: SeriesOutcome): String = when (outcome) {
    SeriesOutcome.WIN -> "W"
    SeriesOutcome.LOSS -> "L"
    SeriesOutcome.UNKNOWN -> "—"
}

private fun playerRoleLabel(role: PlayerRole?): String = when (role) {
    PlayerRole.TOP -> "TOP"
    PlayerRole.JUNGLE -> "JUG"
    PlayerRole.MID -> "MID"
    PlayerRole.BOT -> "BOT"
    PlayerRole.SUPPORT -> "SUP"
    PlayerRole.SUBSTITUTE -> "SUB"
    PlayerRole.UNKNOWN, null -> "ROLE?"
}

private fun roleOrder(role: PlayerRole?): Int = when (role) {
    PlayerRole.TOP -> 0
    PlayerRole.JUNGLE -> 1
    PlayerRole.MID -> 2
    PlayerRole.BOT -> 3
    PlayerRole.SUPPORT -> 4
    PlayerRole.SUBSTITUTE -> 5
    PlayerRole.UNKNOWN, null -> 99
}

private fun staffRoleLabel(role: StaffRole): String = when (role) {
    StaffRole.HEAD_COACH -> "主教练"
    StaffRole.ASSISTANT_COACH -> "助理教练"
    StaffRole.STRATEGIC_COACH -> "战术教练"
    StaffRole.POSITIONAL_COACH -> "位置教练"
    StaffRole.COACH -> "教练"
    StaffRole.ANALYST -> "分析师"
    StaffRole.GENERAL_MANAGER -> "总经理"
    StaffRole.ASSISTANT_MANAGER -> "助理经理"
    StaffRole.MANAGER -> "经理"
    StaffRole.LEADER -> "领队"
    StaffRole.SUPERVISOR -> "主管"
    StaffRole.ESPORTS_DIRECTOR -> "电竞总监"
    StaffRole.DIRECTOR -> "总监"
    StaffRole.MANAGING_DIRECTOR -> "执行总监"
    StaffRole.CEO -> "CEO"
    StaffRole.COO -> "COO"
    StaffRole.OWNER -> "Owner"
    StaffRole.CO_OWNER -> "Co-Owner"
    StaffRole.FOUNDER -> "创始人"
    StaffRole.FOUNDER_AND_CEO -> "创始人 / CEO"
    StaffRole.HEAD_OF_ESPORTS -> "电竞负责人"
    StaffRole.HEAD_OF_LOL -> "LoL 负责人"
    StaffRole.OTHER -> "Staff"
}

private fun teamLabel(code: String, name: String): String = code.ifBlank { name }

private fun formatLocalTime(epochMillis: Long): String =
    LOCAL_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val LOCAL_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd E HH:mm", Locale.SIMPLIFIED_CHINESE)
