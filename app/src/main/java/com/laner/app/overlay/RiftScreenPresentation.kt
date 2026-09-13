package com.laner.app.overlay

import com.laner.core.application.LiveSnapshotResolution
import com.laner.core.application.LiveStateResolution
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.ScheduledSeries
import kotlin.math.abs

data class RiftScreenPresentation(
    val title: String,
    val timer: String,
    val leftTeam: String,
    val rightTeam: String,
    val center: String,
    val metrics: String,
    val details: String,
    val source: String,
    val status: String,
)

/** Maps Application truth into display-only text. No Provider payload or business arbitration here. */
object RiftScreenPresentationMapper {
    fun waiting(message: String): RiftScreenPresentation = RiftScreenPresentation(
        title = "等待数据",
        timer = "--:--",
        leftTeam = "—",
        rightTeam = "—",
        center = "VS",
        metrics = "K —   T —   D —",
        details = "GOLD — : —   LEAD —",
        source = "NO VERIFIED SOURCE",
        status = message,
    )

    fun from(
        match: ScheduledSeries,
        stateResolution: LiveStateResolution,
        snapshotResolution: LiveSnapshotResolution,
        timeline: GameTimeline?,
    ): RiftScreenPresentation {
        val snapshot = snapshotResolution.snapshot
        val scheduledLeft = match.teams[0].team
        val scheduledRight = match.teams[1].team
        val left = snapshot?.let { live -> match.teams.firstOrNull { it.team.id == live.blue.teamId }?.team } ?: scheduledLeft
        val right = snapshot?.let { live -> match.teams.firstOrNull { it.team.id == live.red.teamId }?.team } ?: scheduledRight
        val leftCode = left.code.ifBlank { left.name }
        val rightCode = right.code.ifBlank { right.name }

        val leftGold = snapshot?.blue?.gold
        val rightGold = snapshot?.red?.gold
        val center = if (leftGold != null && rightGold != null) {
            formatSignedDiff(leftGold - rightGold)
        } else {
            "VS"
        }

        val provider = snapshotResolution.selectedProviderId
            ?: stateResolution.selectedProviderId
            ?: "NO VERIFIED SOURCE"
        val timelineText = timeline?.let { "${it.snapshots.size} frames · ${it.events.size} events" }
            ?: "timeline pending"

        return RiftScreenPresentation(
            title = lifecycleTitle(stateResolution.state.lifecycle, snapshot?.game?.gameNumber),
            timer = snapshot?.elapsedSeconds?.let(::formatTime) ?: "--:--",
            leftTeam = leftCode,
            rightTeam = rightCode,
            center = center,
            metrics = "K ${value(snapshot?.blue?.kills)}:${value(snapshot?.red?.kills)}   T ${value(snapshot?.blue?.towers)}:${value(snapshot?.red?.towers)}   D ${value(snapshot?.blue?.dragons)}:${value(snapshot?.red?.dragons)}",
            details = "GOLD ${valueNumber(leftGold)} : ${valueNumber(rightGold)}   LEAD $center",
            source = provider,
            status = "${stateResolution.status.name} · ${snapshotResolution.status.name} · $timelineText",
        )
    }

    private fun lifecycleTitle(lifecycle: MatchLifecycleState, gameNumber: Int?): String = when (lifecycle) {
        MatchLifecycleState.UNKNOWN -> "状态未知"
        MatchLifecycleState.PRE_EVENT -> "等待比赛"
        MatchLifecycleState.EVENT_LIVE_PRE_GAME -> "等待本局"
        MatchLifecycleState.DRAFT -> "BP / DRAFT"
        MatchLifecycleState.LOADING -> "载入游戏"
        MatchLifecycleState.IN_GAME -> "LIVE${gameNumber?.let { " · G$it" } ?: ""}"
        MatchLifecycleState.POST_GAME -> "本局结束"
        MatchLifecycleState.BETWEEN_GAMES -> "等待本局"
        MatchLifecycleState.SERIES_COMPLETE -> "系列赛结束"
    }

    private fun value(value: Int?): String = value?.toString() ?: "—"

    private fun valueNumber(value: Int?): String = value?.let(::formatNumber) ?: "—"

    private fun formatSignedDiff(value: Int): String {
        if (value == 0) return "EVEN"
        val prefix = if (value > 0) "+" else "-"
        return prefix + formatNumber(abs(value))
    }

    private fun formatNumber(value: Int): String = if (value >= 1000) "%.1fK".format(value / 1000.0) else value.toString()

    private fun formatTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
}
