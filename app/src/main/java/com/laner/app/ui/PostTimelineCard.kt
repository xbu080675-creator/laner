package com.laner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laner.core.application.PostMatchQuery
import com.laner.core.application.PostMatchSnapshot
import com.laner.core.application.PostTimelineResolution
import com.laner.core.application.PostTimelineService
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.ScheduledSeries
import kotlinx.coroutines.launch

private sealed interface TimelineUiState {
    data object Idle : TimelineUiState
    data class Loading(val gameNumber: Int) : TimelineUiState
    data class Ready(val gameNumber: Int, val resolution: PostTimelineResolution) : TimelineUiState
    data class Failed(val gameNumber: Int, val message: String) : TimelineUiState
}

@Composable
fun PostTimelineCard(
    match: ScheduledSeries,
    snapshot: PostMatchSnapshot,
    service: PostTimelineService,
) {
    val games = remember(match.matchId, snapshot.bundle) {
        buildSet {
            snapshot.bundle.games.mapTo(this) { it.gameNumber }
            snapshot.bundle.replays.mapNotNullTo(this) { it.gameNumber }
            snapshot.bundle.result?.let { result ->
                val played = result.leftWins + result.rightWins
                if (played > 0) addAll(1..played)
            }
        }.sorted()
    }
    var state by remember(match.matchId) { mutableStateOf<TimelineUiState>(TimelineUiState.Idle) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "HISTORICAL TIMELINE / 历史过程",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "按需读取 Riot 保存的真实 LiveStats 帧；不插值、不自动扫描整场。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (games.isEmpty()) {
                Text("当前还没有可定位的小局编号。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { gameNumber ->
                        Button(
                            onClick = {
                                scope.launch {
                                    state = TimelineUiState.Loading(gameNumber)
                                    state = try {
                                        val now = System.currentTimeMillis()
                                        TimelineUiState.Ready(
                                            gameNumber,
                                            service.backfill(
                                                query = PostMatchQuery.from(match),
                                                gameNumber = gameNumber,
                                                context = SourceRequestContext(now, "post-history-${match.matchId.value}-g$gameNumber-$now"),
                                            ),
                                        )
                                    } catch (error: Throwable) {
                                        TimelineUiState.Failed(
                                            gameNumber,
                                            error.message?.take(160)?.takeIf { it.isNotBlank() }
                                                ?: error::class.java.simpleName,
                                        )
                                    }
                                }
                            }
                        ) { Text("G$gameNumber") }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            when (val current = state) {
                TimelineUiState.Idle -> Text(
                    "选择一局后才会请求历史过程。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is TimelineUiState.Loading -> Text("G${current.gameNumber} · 正在恢复真实过程帧…", fontSize = 12.sp)
                is TimelineUiState.Failed -> Text(
                    "G${current.gameNumber} · 恢复失败 · ${current.message}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is TimelineUiState.Ready -> {
                    val timeline = current.resolution.timeline
                    if (timeline == null) {
                        Text(
                            "G${current.gameNumber} · ${current.resolution.status.name} · Riot 当前未保留可用历史窗口。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val first = timeline.snapshots.minOfOrNull { it.gameTimeSeconds }
                        val last = timeline.snapshots.maxOfOrNull { it.gameTimeSeconds }
                        Text(
                            "G${current.gameNumber} · ${timeline.snapshots.size} 帧 · ${first ?: 0}s → ${last ?: 0}s",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${if (timeline.completed) "过程完整" else "可用过程 / 上游可能已截断"} · ${current.resolution.sourcesApplied.joinToString().ifBlank { "no source" }} · Failures ${current.resolution.failures.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
