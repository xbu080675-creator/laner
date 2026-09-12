package com.laner.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.laner.core.application.RosterAssistStage
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.StartingRosterAssistService
import com.laner.core.application.StartingRosterAssistSnapshot
import kotlinx.coroutines.delay

private sealed interface RosterAssistUiState {
    data object Loading : RosterAssistUiState
    data class Ready(val snapshot: StartingRosterAssistSnapshot) : RosterAssistUiState
    data class Failed(val message: String) : RosterAssistUiState
}

@Composable
fun StartingRosterAssistPanel(
    scheduleService: GlobalScheduleService,
    assistService: StartingRosterAssistService,
    modifier: Modifier = Modifier,
) {
    var refreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            refreshNonce += 1
        }
    }

    val state by produceState<RosterAssistUiState>(
        initialValue = RosterAssistUiState.Loading,
        key1 = scheduleService,
        key2 = assistService,
        key3 = refreshNonce,
    ) {
        value = try {
            val now = System.currentTimeMillis()
            val schedule = scheduleService.load(
                SourceRequestContext(nowEpochMillis = now, correlationId = "roster-assist-schedule-$now-$refreshNonce")
            )
            RosterAssistUiState.Ready(
                assistService.inspect(
                    schedule = schedule.matches,
                    context = SourceRequestContext(nowEpochMillis = now, correlationId = "roster-assist-$now-$refreshNonce"),
                )
            )
        } catch (error: Throwable) {
            RosterAssistUiState.Failed(error.message?.take(160) ?: error::class.java.simpleName)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("STARTING ROSTER PIPELINE / 首发识别链", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            when (val current = state) {
                RosterAssistUiState.Loading -> Text("正在检查官宣与本机 OCR…", fontSize = 12.sp)
                is RosterAssistUiState.Failed -> Text("识别链异常 · ${current.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                is RosterAssistUiState.Ready -> RosterAssistDetails(current.snapshot)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { refreshNonce += 1 },
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text("立即重查", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RosterAssistDetails(snapshot: StartingRosterAssistSnapshot) {
    val match = snapshot.match
    val target = match?.teams?.let { teams ->
        if (teams.size >= 2) "${rosterTeamLabel(teams[0].team.code, teams[0].team.name)} vs ${rosterTeamLabel(teams[1].team.code, teams[1].team.name)}" else match.matchId.value
    } ?: "暂无目标比赛"
    Text(target, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Text(stageText(snapshot.stage), fontSize = 12.sp, color = stageColor(snapshot.stage))
    Text(
        "正式证据 ${snapshot.normalizedEvidenceCount} · 官宣 ${snapshot.announcements.size} · OCR ${snapshot.inspections.size}",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    snapshot.announcements.firstOrNull()?.let { announcement ->
        Spacer(Modifier.height(5.dp))
        Text(
            "发现 · ${announcement.account} · ${announcement.platform} · score ${announcement.candidateScore} · ${announcement.parseStatus}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("图片 ${announcement.imageUrls.size} · ${announcement.candidateBasis.ifBlank { "未标注候选依据" }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    snapshot.inspections.firstOrNull()?.let { inspection ->
        Spacer(Modifier.height(5.dp))
        if (inspection.error != null) {
            Text("OCR · ${inspection.error.take(120)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
        } else {
            val roles = inspection.roleCandidates.filterValues { it.isNotEmpty() }.keys.joinToString("/")
            Text(
                "OCR · ${inspection.engines.joinToString("+")} · ${inspection.layoutMode} · ${inspection.lineCount} 行 · $roles",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (inspection.textPreview.isNotBlank()) {
                Text(inspection.textPreview.take(180), fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    snapshot.failures.take(3).forEach { failure ->
        Text("${failure.code.value} · ${failure.message}", fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun stageColor(stage: RosterAssistStage) = when (stage) {
    RosterAssistStage.NORMALIZED_EVIDENCE_AVAILABLE -> MaterialTheme.colorScheme.primary
    RosterAssistStage.OCR_COMPLETE_UNVERIFIED,
    RosterAssistStage.OCR_PARTIAL,
    RosterAssistStage.ANNOUNCEMENT_DISCOVERED -> MaterialTheme.colorScheme.tertiary
    RosterAssistStage.OCR_FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun stageText(stage: RosterAssistStage): String = when (stage) {
    RosterAssistStage.NO_TARGET -> "未找到可关联的目标比赛"
    RosterAssistStage.NO_ANNOUNCEMENT -> "目标已知 · 尚未发现匹配官宣"
    RosterAssistStage.ANNOUNCEMENT_DISCOVERED -> "已发现官宣 · 等待/准备 OCR"
    RosterAssistStage.OCR_FAILED -> "已发现官宣 · 图片下载或 OCR 失败"
    RosterAssistStage.OCR_PARTIAL -> "已发现官宣 · OCR 部分识别，尚不足以确认"
    RosterAssistStage.OCR_COMPLETE_UNVERIFIED -> "OCR 已得到完整五位置候选 · 等待正式证据校验"
    RosterAssistStage.NORMALIZED_EVIDENCE_AVAILABLE -> "正式 normalized 首发证据已到 · 可进入 Application 校验"
}

private fun rosterTeamLabel(code: String, name: String): String = code.trim().ifBlank { name.trim().ifBlank { "TBD" } }
