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
import com.laner.core.domain.MatchPhase

@Composable
fun LanerRoot() {
    var selectedPhase by remember { mutableStateOf(MatchPhase.PRE_MATCH) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            LanerHeader()
            Spacer(Modifier.height(16.dp))
            PhaseSwitcher(
                selected = selectedPhase,
                onSelect = { selectedPhase = it },
            )
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = selectedPhase,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "laner-phase",
            ) { phase ->
                PhaseEmptyState(phase)
            }
        }
    }
}

@Composable
private fun LanerHeader() {
    Column {
        Text(
            text = "LANER",
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "GLOBAL ESPORTS COMPANION",
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhaseSwitcher(
    selected: MatchPhase,
    onSelect: (MatchPhase) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MatchPhase.entries.forEach { phase ->
            val active = phase == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(phase) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = phase.label,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhaseEmptyState(phase: MatchPhase) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Text(
            text = phase.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = phase.headline,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = phase.migrationMessage,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MatchPhase.label: String
    get() = when (this) {
        MatchPhase.PRE_MATCH -> "赛前"
        MatchPhase.LIVE_MATCH -> "赛中"
        MatchPhase.POST_MATCH -> "赛后"
    }

private val MatchPhase.headline: String
    get() = when (this) {
        MatchPhase.PRE_MATCH -> "准备看懂这场比赛"
        MatchPhase.LIVE_MATCH -> "比赛发生什么，为什么"
        MatchPhase.POST_MATCH -> "把结果还原成过程"
    }

private val MatchPhase.migrationMessage: String
    get() = when (this) {
        MatchPhase.PRE_MATCH -> "正在迁移赛程、首发、阵容、排名、资格路径与赛前情报。未接入的真实数据保持空缺，不使用假数据填充。"
        MatchPhase.LIVE_MATCH -> "正在迁移统一赛事状态、实时事件、Timeline 与 RiftScreen。只有经过 Source Orchestration 的事实才会进入这里。"
        MatchPhase.POST_MATCH -> "正在迁移终局数据、历史小局、Timeline、回放与复盘。当前骨架不会把未迁移能力伪装成可用。"
    }
