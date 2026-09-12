package com.laner.app.overlay

import com.laner.core.application.LiveSnapshotResolution
import com.laner.core.application.LiveStateResolution
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.GoldLeadChangedEvent
import com.laner.core.domain.KillEvent
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchEvent
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.MultiKillWindowEvent
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ObjectiveType
import com.laner.core.domain.PlayerId
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.TeamFightWindowEvent
import com.laner.core.domain.TeamId
import kotlin.math.abs

enum class TacticalHudSourceMode { VERIFIED, PREVIEW }
enum class TacticalHudPhase { GLOBAL, FIGHT }

data class TacticalHudEvidence(val label: String, val value: String)
data class TacticalHudPlayer(val label: String, val champion: String?, val summary: String)

data class TacticalHudPresentation(
    val active: Boolean,
    val sourceMode: TacticalHudSourceMode,
    val phase: TacticalHudPhase,
    val clock: String,
    val matchLabel: String,
    val headline: String,
    val explanation: String,
    val evidence: List<TacticalHudEvidence>,
    val players: List<TacticalHudPlayer>,
    val sourceLabel: String,
    val validUntilEpochMillis: Long? = null,
) {
    fun isDisplayableAt(nowEpochMillis: Long): Boolean {
        if (!active) return false
        if (sourceMode == TacticalHudSourceMode.PREVIEW) return true
        val validUntil = validUntilEpochMillis ?: return false
        return nowEpochMillis <= validUntil
    }

    companion object {
        fun inactive(): TacticalHudPresentation = TacticalHudPresentation(
            active = false,
            sourceMode = TacticalHudSourceMode.VERIFIED,
            phase = TacticalHudPhase.GLOBAL,
            clock = "--:--",
            matchLabel = "—",
            headline = "等待可证明的战术事件",
            explanation = "只有 canonical Timeline 中近期、可追溯的标准事件才会激活 Tactical HUD。",
            evidence = emptyList(),
            players = emptyList(),
            sourceLabel = "NO TACTICAL EVENT",
            validUntilEpochMillis = null,
        )
    }
}

/** Display-only mapper. It explains canonical events; it never derives match facts in Presentation. */
object TacticalHudPresentationMapper {
    const val EVENT_TTL_SECONDS = 25
    const val VERIFIED_WALL_CLOCK_TTL_MILLIS = 30_000L

    fun from(
        match: ScheduledSeries,
        stateResolution: LiveStateResolution,
        snapshotResolution: LiveSnapshotResolution,
        timeline: GameTimeline?,
    ): TacticalHudPresentation {
        if (stateResolution.state.lifecycle != MatchLifecycleState.IN_GAME || timeline == null) {
            return TacticalHudPresentation.inactive()
        }
        val snapshot = snapshotResolution.snapshot ?: timeline.snapshots.lastOrNull()?.snapshot
        val currentSecond = snapshot?.elapsedSeconds ?: timeline.snapshots.maxOfOrNull { it.gameTimeSeconds }
            ?: return TacticalHudPresentation.inactive()
        val event = timeline.events
            .asSequence()
            .filter(::isTacticalEvent)
            .filter { event ->
                val second = event.gameTimeSeconds ?: return@filter false
                second <= currentSecond && currentSecond - second <= EVENT_TTL_SECONDS
            }
            .maxWithOrNull(compareBy<MatchEvent> { it.gameTimeSeconds ?: -1 }.thenBy(::priority))
            ?: return TacticalHudPresentation.inactive()

        val left = match.teams.getOrNull(0)?.team
        val right = match.teams.getOrNull(1)?.team
        val matchLabel = "${left?.code?.ifBlank { left.name } ?: "LEFT"} vs ${right?.code?.ifBlank { right.name } ?: "RIGHT"} · G${timeline.gameNumber}"
        val clock = formatSeconds(event.gameTimeSeconds ?: currentSecond)
        val state = snapshot ?: timeline.snapshots.lastOrNull()?.snapshot
        val eventTeamLabel: (TeamId?) -> String = { teamId -> teamLabel(match, teamId) }
        val baseEvidence = mutableListOf(
            TacticalHudEvidence("EVIDENCE", event.evidence.name),
            TacticalHudEvidence("SOURCE", event.provenance.providerId),
        )

        val phase: TacticalHudPhase
        val headline: String
        val explanation: String
        val playerIds = mutableListOf<PlayerId>()
        when (event) {
            is TeamFightWindowEvent -> {
                phase = TacticalHudPhase.FIGHT
                val blueLabel = state?.game?.blueTeamId?.let { eventTeamLabel(it) } ?: "BLUE"
                val redLabel = state?.game?.redTeamId?.let { eventTeamLabel(it) } ?: "RED"
                headline = "团战窗口 · $blueLabel +${event.blueKillDelta} / $redLabel +${event.redKillDelta}"
                explanation = "连续可信帧在 ${event.windowSeconds}s 采样窗内记录到 ${event.blueKillDelta + event.redKillDelta} 个击杀变化；仅作战斗窗口提示，不声称官方团战分类。"
                baseEvidence += TacticalHudEvidence("WINDOW", "${event.windowSeconds}s")
                baseEvidence += TacticalHudEvidence("KILL Δ", "+${event.blueKillDelta + event.redKillDelta}")
            }
            is MultiKillWindowEvent -> {
                phase = TacticalHudPhase.FIGHT
                headline = "采样窗口多杀 · ${eventTeamLabel(event.teamId)} · +${event.killCount}"
                explanation = "选手击杀计数在 ${event.windowSeconds}s 内增加 ${event.killCount}；这是 verified delta，不等同官方 Double / Triple / Quadra / Penta Kill 判定。"
                playerIds += event.playerId
                baseEvidence += TacticalHudEvidence("WINDOW", "${event.windowSeconds}s")
                baseEvidence += TacticalHudEvidence("PLAYER", shortId(event.playerId.value))
            }
            is GoldLeadChangedEvent -> {
                phase = TacticalHudPhase.GLOBAL
                headline = "经济领先易手 · ${eventTeamLabel(event.leadingTeamId)} +${formatNumber(abs(event.goldDifference))}"
                explanation = "前后可信帧均越过 ±250g 防抖阈值，领先方确认发生切换；只展示已验证差分，不补插值。"
                event.observedWindowSeconds?.let { baseEvidence += TacticalHudEvidence("WINDOW", "${it}s") }
                baseEvidence += TacticalHudEvidence("LEAD", "+${formatNumber(abs(event.goldDifference))}")
            }
            is ObjectiveTakenEvent -> {
                phase = TacticalHudPhase.GLOBAL
                headline = "${objectiveLabel(event.objective)}变化 · ${eventTeamLabel(event.teamId)} +${event.count}"
                explanation = if (event.objective == ObjectiveType.DRAGON) {
                    "连续可信帧只证明小龙总数增加；上游没有龙种证据，因此不推断龙种、龙魂或远古龙。"
                } else {
                    "连续可信资源计数确认该目标变化；HUD 不重复伪造未提供的目标细节。"
                }
                event.observedWindowSeconds?.let { baseEvidence += TacticalHudEvidence("WINDOW", "${it}s") }
                baseEvidence += TacticalHudEvidence("OBJECTIVE", objectiveLabel(event.objective))
            }
            is KillEvent -> {
                phase = TacticalHudPhase.GLOBAL
                headline = "击杀变化 · ${eventTeamLabel(event.teamId)} +${event.count}"
                explanation = "队伍/选手计数差分确认击杀变化；没有明确配对证据时 killer / victim 保持未知。"
                event.killerId?.let(playerIds::add)
                event.victimId?.let(playerIds::add)
                event.observedWindowSeconds?.let { baseEvidence += TacticalHudEvidence("WINDOW", "${it}s") }
                baseEvidence += TacticalHudEvidence("KILL Δ", "+${event.count}")
            }
            else -> return TacticalHudPresentation.inactive()
        }

        return TacticalHudPresentation(
            active = true,
            sourceMode = TacticalHudSourceMode.VERIFIED,
            phase = phase,
            clock = clock,
            matchLabel = matchLabel,
            headline = headline,
            explanation = explanation,
            evidence = baseEvidence.take(4),
            players = playerIds.distinct().take(3).map { playerPresentation(it, state) },
            sourceLabel = "${event.evidence.name} · ${event.provenance.providerId}",
            validUntilEpochMillis = event.provenance.observedAtEpochMillis + VERIFIED_WALL_CLOCK_TTL_MILLIS,
        )
    }

    private fun isTacticalEvent(event: MatchEvent): Boolean = when (event) {
        is TeamFightWindowEvent,
        is MultiKillWindowEvent,
        is GoldLeadChangedEvent,
        is ObjectiveTakenEvent,
        is KillEvent -> true
        else -> false
    }

    private fun priority(event: MatchEvent): Int = when (event) {
        is TeamFightWindowEvent -> 5
        is MultiKillWindowEvent -> 4
        is GoldLeadChangedEvent -> 3
        is ObjectiveTakenEvent -> 2
        is KillEvent -> 1
        else -> 0
    }

    private fun playerPresentation(playerId: PlayerId, snapshot: LiveGameSnapshot?): TacticalHudPlayer {
        val player = snapshot?.players?.firstOrNull { it.playerId == playerId }
        val summary = if (player == null) {
            "当前帧无更多选手字段"
        } else {
            "Lv${player.level ?: "?"} · ${player.kills ?: "?"}/${player.deaths ?: "?"}/${player.assists ?: "?"} · CS ${player.creepScore ?: "?"}"
        }
        return TacticalHudPlayer(
            label = shortId(playerId.value),
            champion = player?.championId,
            summary = summary,
        )
    }

    private fun teamLabel(match: ScheduledSeries, teamId: TeamId?): String {
        if (teamId == null) return "未知队伍"
        val team = match.teams.firstOrNull { it.team.id == teamId }?.team
        return team?.code?.ifBlank { team.name } ?: shortId(teamId.value)
    }

    private fun objectiveLabel(type: ObjectiveType): String = when (type) {
        ObjectiveType.DRAGON -> "小龙"
        ObjectiveType.SOUL -> "龙魂"
        ObjectiveType.ELDER_DRAGON -> "远古龙"
        ObjectiveType.BARON -> "男爵"
        ObjectiveType.HERALD -> "先锋"
        ObjectiveType.ATAKHAN -> "厄塔汗"
        ObjectiveType.TOWER -> "防御塔"
        ObjectiveType.OTHER -> "目标"
    }

    private fun shortId(value: String): String = value.substringAfterLast(':').ifBlank { value }
    private fun formatSeconds(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
    private fun formatNumber(value: Int): String = if (value >= 1_000) "%.1fk".format(value / 1_000.0) else value.toString()
}
