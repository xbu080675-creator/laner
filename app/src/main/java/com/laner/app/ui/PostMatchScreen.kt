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
import com.laner.core.application.PostMatchLoadStatus
import com.laner.core.application.PostMatchQuery
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostMatchSnapshot
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.VerifiedPostAward
import kotlin.math.abs

private sealed interface PostScreenState {
    data object Loading : PostScreenState
    data class NoTarget(val reason: String) : PostScreenState
    data class Ready(val match: ScheduledSeries, val snapshot: PostMatchSnapshot) : PostScreenState
    data class Failed(val message: String) : PostScreenState
}

@Composable
fun PostMatchScreen(
    scheduleService: GlobalScheduleService,
    postMatchService: PostMatchService,
    modifier: Modifier = Modifier,
) {
    var refreshNonce by remember { mutableIntStateOf(0) }
    val state by produceState<PostScreenState>(
        initialValue = PostScreenState.Loading,
        key1 = scheduleService,
        key2 = postMatchService,
        key3 = refreshNonce,
    ) {
        value = try {
            val now = System.currentTimeMillis()
            val context = SourceRequestContext(now, "post-$now-$refreshNonce")
            val schedule = scheduleService.load(context)
            val target = selectPostTarget(schedule.matches, now)
            if (target == null) {
                PostScreenState.NoTarget("当前没有已验证完成的系列赛，POST 不会拿未结束比赛冒充赛后目标。")
            } else {
                PostScreenState.Ready(
                    match = target,
                    snapshot = postMatchService.load(PostMatchQuery.from(target), context),
                )
            }
        } catch (error: Throwable) {
            PostScreenState.Failed(error.message?.take(180)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PostHeaderCard(onRefresh = { refreshNonce += 1 }) }
        when (val current = state) {
            PostScreenState.Loading -> item { PostMessageCard("正在建立赛后上下文", "只读取已完成比赛和 POST_MATCH_SOURCE。") }
            is PostScreenState.NoTarget -> item { PostMessageCard("暂无赛后目标", current.reason) }
            is PostScreenState.Failed -> item { PostMessageCard("赛后数据读取失败", current.message) }
            is PostScreenState.Ready -> {
                item { PostTargetCard(current.match) }
                item { PostSummaryCard(current.snapshot) }
                item { PostCapabilityCard(current.snapshot) }
                item { ReplayCard(current.snapshot.bundle.replays) }
                item { AwardsCard(current.snapshot.bundle.awards) }
            }
        }
    }
}

@Composable
private fun PostHeaderCard(onRefresh: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("POST / 赛后", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("把结果还原成过程", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Series Result、Game Archive、Stats、Awards、Replay 分开取证；缺数据就留空，不从其他字段猜。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onRefresh) { Text("刷新") }
        }
    }
}

@Composable
private fun PostTargetCard(match: ScheduledSeries) {
    val left = match.teams[0].team.code.ifBlank { match.teams[0].team.name }
    val right = match.teams[1].team.code.ifBlank { match.teams[1].team.name }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("MATCH TARGET / 最近完赛", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("$left  vs  $right", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(match.competition.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PostSummaryCard(snapshot: PostMatchSnapshot) {
    val result = snapshot.bundle.result
    val status = snapshot.status.name
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("SERIES RESULT / 系列赛结果", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (result == null) {
                Text("暂无已验证 Series Result", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Source $status · 不使用 PRE 比分或小局数量反推终局。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${result.leftWins} : ${result.rightWins}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${result.state.name} · ${result.provenance.providerId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PostCapabilityCard(snapshot: PostMatchSnapshot) {
    val bundle = snapshot.bundle
    val note = when (snapshot.status) {
        PostMatchLoadStatus.READY -> "POST facts ready"
        PostMatchLoadStatus.DEGRADED -> "部分赛后来源不可用，已保留可验证事实"
        PostMatchLoadStatus.CONFLICT -> "赛后事实存在冲突，未静默覆盖"
        PostMatchLoadStatus.UNAVAILABLE -> "当前尚未接入足够的可验证 POST Source"
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("POST COVERAGE / 数据覆盖", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Games ${bundle.games.size} · Awards ${bundle.awards.size} · Replays ${bundle.replays.size}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (snapshot.failures.isNotEmpty() || snapshot.conflicts.isNotEmpty()) {
                Text("Failures ${snapshot.failures.size} · Conflicts ${snapshot.conflicts.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReplayCard(replays: List<ReplayAsset>) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("REPLAY / 官方录像元数据", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (replays.isEmpty()) {
                Text("暂无已验证 Replay metadata。录像缺失不会影响赛果和其他赛后事实。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                replays.forEachIndexed { index, replay ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "${replay.gameNumber?.let { "G$it" } ?: "SERIES"} · ${replay.provider.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(replay.locale, replay.externalMediaId).joinToString(" · ").ifBlank { "官方录像入口" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${replay.provenance.providerId} · offset ${replay.offsetSeconds}s",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AwardsCard(awards: List<VerifiedPostAward>) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("VERIFIED AWARDS / 已核实奖项", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (awards.isEmpty()) {
                Text("暂无已核实 MVP / POG 记录。不会根据 KDA、伤害或评分自动推奖项。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                awards.forEachIndexed { index, award ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(awardTitle(award), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${award.player.handle} · ${award.player.teamId?.value ?: "team unknown"}",
                        fontSize = 13.sp,
                    )
                    Text(
                        "${award.label} · ${award.provenance.providerId}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun awardTitle(award: VerifiedPostAward): String {
    val gameId = award.gameId
    return when {
        award.gameNumber != null -> "G${award.gameNumber} · ${award.kind.name}"
        gameId != null -> "${gameId.value} · ${award.kind.name}"
        else -> "SERIES · ${award.kind.name}"
    }
}

@Composable
private fun PostMessageCard(title: String, body: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun selectPostTarget(matches: List<ScheduledSeries>, nowEpochMillis: Long): ScheduledSeries? =
    matches
        .filter { it.state == ScheduleState.COMPLETED }
        .minByOrNull { abs(nowEpochMillis - it.startTimeEpochMillis) }
