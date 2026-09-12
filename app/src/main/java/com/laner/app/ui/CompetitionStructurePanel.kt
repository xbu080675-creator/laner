package com.laner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.CompetitionStructureStatus
import com.laner.core.application.EditionArchiveSnapshot
import com.laner.core.application.EditionKnowledgeSnapshot
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.EditionId
import com.laner.core.domain.QualificationEvidenceKind
import com.laner.core.domain.TournamentEdition

private sealed interface EditionArchiveLoadState {
    data object Loading : EditionArchiveLoadState
    data class Ready(val snapshot: EditionArchiveSnapshot) : EditionArchiveLoadState
    data class Failed(val message: String) : EditionArchiveLoadState
}

private sealed interface EditionKnowledgeLoadState {
    data object Idle : EditionKnowledgeLoadState
    data object Loading : EditionKnowledgeLoadState
    data class Ready(val snapshot: EditionKnowledgeSnapshot) : EditionKnowledgeLoadState
    data class Failed(val message: String) : EditionKnowledgeLoadState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompetitionStructurePanel(
    service: CompetitionStructureService,
    modifier: Modifier = Modifier,
) {
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedEditionId by remember { mutableStateOf<EditionId?>(null) }

    val archiveState by produceState<EditionArchiveLoadState>(
        initialValue = EditionArchiveLoadState.Loading,
        key1 = service,
        key2 = refreshNonce,
    ) {
        value = try {
            val now = System.currentTimeMillis()
            EditionArchiveLoadState.Ready(
                service.refreshEditions(
                    SourceRequestContext(
                        nowEpochMillis = now,
                        correlationId = "pre-editions-$now-$refreshNonce",
                    )
                )
            )
        } catch (error: Throwable) {
            EditionArchiveLoadState.Failed(error.safeUiMessage())
        }
    }

    val archive = (archiveState as? EditionArchiveLoadState.Ready)?.snapshot
    val editions = archive?.editions.orEmpty().sortedWith(
        compareByDescending<TournamentEdition> { it.seasonYear ?: Int.MIN_VALUE }
            .thenByDescending { it.startEpochMillis }
    )

    LaunchedEffect(editions, selectedEditionId) {
        if (editions.none { it.id == selectedEditionId }) {
            selectedEditionId = editions.firstOrNull()?.id
        }
    }

    val knowledgeState by produceState<EditionKnowledgeLoadState>(
        initialValue = EditionKnowledgeLoadState.Idle,
        key1 = service,
        key2 = selectedEditionId,
        key3 = refreshNonce,
    ) {
        val id = selectedEditionId ?: run {
            value = EditionKnowledgeLoadState.Idle
            return@produceState
        }
        value = EditionKnowledgeLoadState.Loading
        value = try {
            val now = System.currentTimeMillis()
            val result = service.loadEdition(
                editionId = id,
                context = SourceRequestContext(
                    nowEpochMillis = now,
                    correlationId = "pre-edition-${id.value}-$now-$refreshNonce",
                ),
            )
            if (result == null) EditionKnowledgeLoadState.Failed("赛事届次已经不在当前档案中")
            else EditionKnowledgeLoadState.Ready(result)
        } catch (error: Throwable) {
            EditionKnowledgeLoadState.Failed(error.safeUiMessage())
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "TOURNAMENT STRUCTURE / 赛事结构",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "届次 · Standings · 年度积分 · 晋级路径",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    onClick = { refreshNonce += 1 },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "同步",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            when (val state = archiveState) {
                EditionArchiveLoadState.Loading -> StructureMutedLine("正在同步 Riot Tournament Edition，并与本地历史档案合并。")
                is EditionArchiveLoadState.Failed -> StructureErrorLine("届次档案加载失败 · ${state.message}")
                is EditionArchiveLoadState.Ready -> {
                    val statusLabel = when (state.snapshot.status) {
                        CompetitionStructureStatus.READY -> "正常"
                        CompetitionStructureStatus.DEGRADED -> "降级"
                        CompetitionStructureStatus.UNAVAILABLE -> "不可用"
                    }
                    StructureMutedLine("Edition Archive · $statusLabel · ${state.snapshot.editions.size} 届；历史届次不会因单次 API 缺页自动删除。")
                }
            }

            if (editions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    editions.take(MAX_EDITION_CHIPS).forEach { edition ->
                        FilterChip(
                            selected = edition.id == selectedEditionId,
                            onClick = { selectedEditionId = edition.id },
                            label = { Text(editionChipLabel(edition), maxLines = 1) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            when (val state = knowledgeState) {
                EditionKnowledgeLoadState.Idle -> StructureMutedLine("暂无可选择赛事届次。")
                EditionKnowledgeLoadState.Loading -> StructureMutedLine("正在读取该届 Standings 与资格证据。")
                is EditionKnowledgeLoadState.Failed -> StructureErrorLine("赛事结构加载失败 · ${state.message}")
                is EditionKnowledgeLoadState.Ready -> EditionKnowledgeBody(state.snapshot)
            }
        }
    }
}

@Composable
private fun EditionKnowledgeBody(snapshot: EditionKnowledgeSnapshot) {
    Text(
        text = snapshot.edition.displayName,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurface,
    )
    StructureMutedLine(
        "${snapshot.edition.stageName.ifBlank { snapshot.edition.family }} · " +
            "${snapshot.edition.provenance.providerId} · ${snapshot.status.name}"
    )

    Spacer(Modifier.height(10.dp))
    Text("STANDINGS / 当前赛事排名", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (snapshot.standings.isEmpty()) {
        StructureMutedLine("该届当前没有可核实 Standings。")
    } else {
        snapshot.standings.take(2).forEach { section ->
            StructureMutedLine("${section.stageName} · ${section.sectionName}")
            section.entries.sortedBy { it.ordinal }.take(6).forEach { row ->
                val extra = row.metrics.joinToString(" · ") { "${it.label} ${it.value}" }
                StructureMutedLine(
                    "#${row.ordinal} ${teamText(row.team.code, row.team.name)} · ${row.seriesWins}-${row.seriesLosses}" +
                        if (extra.isBlank()) "" else " · $extra"
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("CHAMPIONSHIP POINTS / 年度资格积分", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val points = snapshot.championshipPoints
    if (points == null) {
        StructureMutedLine("暂无独立可信 Championship Points 来源；不会拿 Standings points / 胜场冒充年度资格积分。")
    } else {
        StructureMutedLine("${points.targetEventName} · 更新至 ${points.updatedThrough}")
        points.entries.sortedByDescending { it.totalPoints }.take(6).forEach { row ->
            StructureMutedLine("${teamText(row.team.code, row.team.name)} · ${row.totalPoints} pts")
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("QUALIFICATION / 晋级路径", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val qualification = snapshot.qualification
    if (qualification.mechanismEvidence == QualificationEvidenceKind.PENDING) {
        StructureMutedLine(qualification.note)
    } else {
        StructureMutedLine("${qualification.targetEventName} · ${qualification.mechanism.name} · ${qualification.mechanismEvidence.name}")
        qualification.routes.take(6).forEach { route ->
            StructureMutedLine("${teamText(route.team.code, route.team.name)} · ${route.status.name} · ${route.mechanism.name}")
        }
        if (qualification.note.isNotBlank()) StructureMutedLine(qualification.note)
    }

    snapshot.failures.firstOrNull()?.let { failure ->
        Spacer(Modifier.height(8.dp))
        StructureErrorLine("${failure.code} · ${failure.message}")
    }
}

@Composable
private fun StructureMutedLine(text: String) {
    Text(text, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StructureErrorLine(text: String) {
    Text(text, fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.error)
}

private fun editionChipLabel(edition: TournamentEdition): String = buildString {
    edition.seasonYear?.let { append(it); append(' ') }
    append(edition.competition.name)
    if (edition.stageName.isNotBlank()) {
        append(" · ")
        append(edition.stageName)
    }
}

private fun teamText(code: String, name: String): String = code.ifBlank { name }

private fun Throwable.safeUiMessage(): String =
    message?.take(160)?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

private const val MAX_EDITION_CHIPS = 12
