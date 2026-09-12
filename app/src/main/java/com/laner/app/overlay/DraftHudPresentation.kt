package com.laner.app.overlay

import com.laner.core.application.LiveStateResolution
import com.laner.core.domain.DraftActionType
import com.laner.core.domain.DraftChangedEvent
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.TeamId

enum class DraftHudSourceMode {
    VERIFIED,
    PREVIEW,
}

data class DraftHudPresentation(
    val active: Boolean,
    val sourceMode: DraftHudSourceMode,
    val leftTeam: String,
    val rightTeam: String,
    val leftPicks: List<String>,
    val rightPicks: List<String>,
    val leftBans: List<String>,
    val rightBans: List<String>,
    val latestAction: String,
    val matchup: String?,
    val step: Int,
    val totalSteps: Int?,
    val message: String,
    val sourceLabel: String,
) {
    companion object {
        fun inactive(): DraftHudPresentation = DraftHudPresentation(
            active = false,
            sourceMode = DraftHudSourceMode.VERIFIED,
            leftTeam = "—",
            rightTeam = "—",
            leftPicks = emptyList(),
            rightPicks = emptyList(),
            leftBans = emptyList(),
            rightBans = emptyList(),
            latestAction = "等待可信 Draft 事件",
            matchup = null,
            step = 0,
            totalSteps = null,
            message = "当前不在 Draft 生命周期",
            sourceLabel = "NO VERIFIED DRAFT SOURCE",
        )
    }
}

/**
 * Display-only Draft projection built from canonical Application/Domain truth.
 *
 * Scheduled team order is used only for left/right placement. It is deliberately not called
 * blue/red until a dedicated side-selection fact exists in Domain. Provider payloads never enter
 * this mapper, and missing role/matchup facts stay absent instead of being inferred.
 */
object DraftHudPresentationMapper {
    fun from(
        match: ScheduledSeries,
        stateResolution: LiveStateResolution,
        timeline: GameTimeline?,
    ): DraftHudPresentation {
        if (stateResolution.state.lifecycle != MatchLifecycleState.DRAFT) {
            return DraftHudPresentation.inactive()
        }

        val left = match.teams[0].team
        val right = match.teams[1].team
        val events = timeline?.events
            ?.filterIsInstance<DraftChangedEvent>()
            ?.sortedBy { it.sequence }
            .orEmpty()
        val projection = project(events, left.id, right.id)
        val latest = events.lastOrNull()
        val source = latest?.provenance?.providerId
            ?: stateResolution.selectedProviderId
            ?: "NO VERIFIED DRAFT SOURCE"

        return DraftHudPresentation(
            active = true,
            sourceMode = DraftHudSourceMode.VERIFIED,
            leftTeam = left.code.ifBlank { left.name },
            rightTeam = right.code.ifBlank { right.name },
            leftPicks = projection.leftPicks,
            rightPicks = projection.rightPicks,
            leftBans = projection.leftBans,
            rightBans = projection.rightBans,
            latestAction = latest?.let { eventLabel(it, left.id, right.id, left.code.ifBlank { left.name }, right.code.ifBlank { right.name }) }
                ?: "Draft 生命周期已确认 · 等待结构化 Pick/Ban 事件",
            matchup = null,
            step = events.size,
            totalSteps = null,
            message = if (events.isEmpty()) {
                "已确认 Draft 状态；尚无可信结构化 Pick/Ban。侧别未确认，不把赛程顺序伪装成蓝/红方。"
            } else {
                "${events.size} 条 canonical Draft 事件 · 角色/对位证据缺失时不推断。"
            },
            sourceLabel = source,
        )
    }

    private data class Projection(
        val leftPicks: List<String>,
        val rightPicks: List<String>,
        val leftBans: List<String>,
        val rightBans: List<String>,
    )

    private fun project(events: List<DraftChangedEvent>, leftId: TeamId, rightId: TeamId): Projection {
        val leftPicks = mutableListOf<String>()
        val rightPicks = mutableListOf<String>()
        val leftBans = mutableListOf<String>()
        val rightBans = mutableListOf<String>()

        events.forEach { event ->
            val champion = event.championId?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val picks = when (event.teamId) {
                leftId -> leftPicks
                rightId -> rightPicks
                else -> null
            }
            val bans = when (event.teamId) {
                leftId -> leftBans
                rightId -> rightBans
                else -> null
            }
            when (event.action) {
                DraftActionType.PICK,
                DraftActionType.LOCK -> if (picks != null && champion !in picks) picks += champion
                DraftActionType.BAN -> if (bans != null && champion !in bans) bans += champion
                DraftActionType.UNDO -> {
                    picks?.remove(champion)
                    bans?.remove(champion)
                }
                DraftActionType.OTHER -> Unit
            }
        }

        return Projection(leftPicks, rightPicks, leftBans, rightBans)
    }

    private fun eventLabel(
        event: DraftChangedEvent,
        leftId: TeamId,
        rightId: TeamId,
        leftLabel: String,
        rightLabel: String,
    ): String {
        val team = when (event.teamId) {
            leftId -> leftLabel
            rightId -> rightLabel
            else -> "未知队伍"
        }
        val action = when (event.action) {
            DraftActionType.PICK -> "选择"
            DraftActionType.BAN -> "禁用"
            DraftActionType.LOCK -> "锁定"
            DraftActionType.UNDO -> "撤销"
            DraftActionType.OTHER -> "Draft 变化"
        }
        return "$team · $action · ${event.championId ?: "未知英雄"}"
    }
}
