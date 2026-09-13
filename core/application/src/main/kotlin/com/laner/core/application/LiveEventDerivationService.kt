package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.GoldLeadChangedEvent
import com.laner.core.domain.KillEvent
import com.laner.core.domain.MatchEvent
import com.laner.core.domain.MultiKillWindowEvent
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ObjectiveType
import com.laner.core.domain.PlayerLiveState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamFightWindowEvent
import com.laner.core.domain.TeamId
import com.laner.core.domain.TimelineSnapshotPoint

/**
 * Deterministically derives conservative LIVE events from the canonical Timeline snapshots.
 *
 * This service never invents killer/victim pairings, dragon subtype, skill cooldown, HP, position or
 * official multi-kill/team-fight classifications. Reconciliation regenerates only this service's own
 * events so late/out-of-order or stronger same-second snapshots cannot leave stale derived facts.
 */
class LiveEventDerivationService(
    private val timelineService: LiveTimelineService,
) {
    suspend fun reconcile(timeline: GameTimeline): GameTimeline {
        val derived = derive(timeline)
        return timelineService.reconcileGeneratedEvents(
            gameId = timeline.gameId,
            generatorProviderId = DERIVED_PROVIDER_ID,
            events = derived,
        )?.timeline ?: timeline
    }

    internal fun derive(timeline: GameTimeline): List<MatchEvent> {
        if (timeline.snapshots.size < 2) return emptyList()
        val output = mutableListOf<MatchEvent>()
        val externalEvents = timeline.events.filterNot { it.provenance.providerId == DERIVED_PROVIDER_ID }
        val ordered = timeline.snapshots.sortedBy { it.gameTimeSeconds }

        ordered.zipWithNext().forEach { (previous, current) ->
            if (!isLiveComparable(previous, current)) return@forEach
            val interval = current.gameTimeSeconds - previous.gameTimeSeconds
            if (interval <= 0) return@forEach
            var offset = 0L
            fun nextSequence(): Long = current.gameTimeSeconds.toLong() * 100L + offset++
            val provenance = derivedProvenance(current)

            val blueKillDelta = nonNegativeDelta(previous.snapshot.blue.kills, current.snapshot.blue.kills)
            val redKillDelta = nonNegativeDelta(previous.snapshot.red.kills, current.snapshot.red.kills)
            appendKillEvents(
                output = output,
                timeline = timeline,
                externalEvents = externalEvents,
                previous = previous,
                current = current,
                teamId = current.snapshot.blue.teamId,
                teamDelta = blueKillDelta,
                intervalSeconds = interval,
                provenance = provenance,
                nextSequence = ::nextSequence,
            )
            appendKillEvents(
                output = output,
                timeline = timeline,
                externalEvents = externalEvents,
                previous = previous,
                current = current,
                teamId = current.snapshot.red.teamId,
                teamDelta = redKillDelta,
                intervalSeconds = interval,
                provenance = provenance,
                nextSequence = ::nextSequence,
            )

            if (interval <= TEAM_FIGHT_MAX_WINDOW_SECONDS &&
                blueKillDelta != null &&
                redKillDelta != null &&
                blueKillDelta + redKillDelta >= TEAM_FIGHT_MIN_KILLS
            ) {
                output += TeamFightWindowEvent(
                    matchId = timeline.matchId,
                    gameId = timeline.gameId,
                    sequence = nextSequence(),
                    gameTimeSeconds = current.gameTimeSeconds,
                    provenance = provenance,
                    blueKillDelta = blueKillDelta,
                    redKillDelta = redKillDelta,
                    windowSeconds = interval,
                )
            }

            appendObjectiveDelta(
                output, timeline, externalEvents, current, provenance, current.snapshot.blue.teamId,
                ObjectiveType.TOWER, previous.snapshot.blue.towers, current.snapshot.blue.towers, interval, ::nextSequence,
            )
            appendObjectiveDelta(
                output, timeline, externalEvents, current, provenance, current.snapshot.red.teamId,
                ObjectiveType.TOWER, previous.snapshot.red.towers, current.snapshot.red.towers, interval, ::nextSequence,
            )
            appendObjectiveDelta(
                output, timeline, externalEvents, current, provenance, current.snapshot.blue.teamId,
                ObjectiveType.DRAGON, previous.snapshot.blue.dragons, current.snapshot.blue.dragons, interval, ::nextSequence,
            )
            appendObjectiveDelta(
                output, timeline, externalEvents, current, provenance, current.snapshot.red.teamId,
                ObjectiveType.DRAGON, previous.snapshot.red.dragons, current.snapshot.red.dragons, interval, ::nextSequence,
            )
            appendObjectiveDelta(
                output, timeline, externalEvents, current, provenance, current.snapshot.blue.teamId,
                ObjectiveType.BARON, previous.snapshot.blue.barons, current.snapshot.blue.barons, interval, ::nextSequence,
            )
            appendObjectiveDelta(
                output, timeline, externalEvents, current, provenance, current.snapshot.red.teamId,
                ObjectiveType.BARON, previous.snapshot.red.barons, current.snapshot.red.barons, interval, ::nextSequence,
            )

            appendGoldLeadChange(
                output = output,
                timeline = timeline,
                externalEvents = externalEvents,
                previous = previous,
                current = current,
                intervalSeconds = interval,
                provenance = provenance,
                sequence = nextSequence(),
            )
        }
        return output
    }

    private fun appendKillEvents(
        output: MutableList<MatchEvent>,
        timeline: GameTimeline,
        externalEvents: List<MatchEvent>,
        previous: TimelineSnapshotPoint,
        current: TimelineSnapshotPoint,
        teamId: TeamId,
        teamDelta: Int?,
        intervalSeconds: Int,
        provenance: SourceProvenance,
        nextSequence: () -> Long,
    ) {
        val delta = teamDelta ?: return
        if (delta <= 0) return
        val externallyExplained = externalEvents
            .filterIsInstance<KillEvent>()
            .filter { it.teamId == teamId && isInsideInterval(it.gameTimeSeconds, previous, current) }
            .sumOf { it.count }
            .coerceAtMost(delta)
        val remaining = delta - externallyExplained
        if (remaining <= 0) return

        val playerDeltas = if (externallyExplained == 0) {
            exactPlayerKillDeltas(previous.snapshot.players, current.snapshot.players, teamId)
        } else {
            emptyList()
        }
        if (playerDeltas.isNotEmpty() && playerDeltas.sumOf { it.second } == delta) {
            playerDeltas.forEach { (player, amount) ->
                output += KillEvent(
                    matchId = timeline.matchId,
                    gameId = timeline.gameId,
                    sequence = nextSequence(),
                    gameTimeSeconds = current.gameTimeSeconds,
                    provenance = provenance,
                    evidence = EventEvidence.VERIFIED_DELTA,
                    killerId = player.playerId,
                    victimId = null,
                    teamId = teamId,
                    count = amount,
                    observedWindowSeconds = intervalSeconds,
                )
                if (amount >= 2 && intervalSeconds <= MULTI_KILL_MAX_WINDOW_SECONDS) {
                    output += MultiKillWindowEvent(
                        matchId = timeline.matchId,
                        gameId = timeline.gameId,
                        sequence = nextSequence(),
                        gameTimeSeconds = current.gameTimeSeconds,
                        provenance = provenance,
                        playerId = player.playerId,
                        teamId = teamId,
                        killCount = amount,
                        windowSeconds = intervalSeconds,
                    )
                }
            }
        } else {
            output += KillEvent(
                matchId = timeline.matchId,
                gameId = timeline.gameId,
                sequence = nextSequence(),
                gameTimeSeconds = current.gameTimeSeconds,
                provenance = provenance,
                evidence = EventEvidence.VERIFIED_DELTA,
                killerId = null,
                victimId = null,
                teamId = teamId,
                count = remaining,
                observedWindowSeconds = intervalSeconds,
            )
        }
    }

    private fun appendObjectiveDelta(
        output: MutableList<MatchEvent>,
        timeline: GameTimeline,
        externalEvents: List<MatchEvent>,
        current: TimelineSnapshotPoint,
        provenance: SourceProvenance,
        teamId: TeamId,
        objective: ObjectiveType,
        previousCount: Int?,
        currentCount: Int?,
        intervalSeconds: Int,
        nextSequence: () -> Long,
    ) {
        val delta = nonNegativeDelta(previousCount, currentCount) ?: return
        if (delta <= 0) return
        val previousSecond = current.gameTimeSeconds - intervalSeconds
        val externallyExplained = externalEvents
            .filterIsInstance<ObjectiveTakenEvent>()
            .filter {
                it.teamId == teamId && it.objective == objective &&
                    it.gameTimeSeconds > previousSecond && it.gameTimeSeconds <= current.gameTimeSeconds
            }
            .sumOf { it.count }
            .coerceAtMost(delta)
        val remaining = delta - externallyExplained
        if (remaining <= 0) return
        output += ObjectiveTakenEvent(
            matchId = timeline.matchId,
            gameId = timeline.gameId,
            sequence = nextSequence(),
            gameTimeSeconds = current.gameTimeSeconds,
            provenance = provenance,
            evidence = EventEvidence.VERIFIED_DELTA,
            teamId = teamId,
            objective = objective,
            detail = if (objective == ObjectiveType.DRAGON) "龙种未知；仅由总数差分确认" else null,
            count = remaining,
            observedWindowSeconds = intervalSeconds,
        )
    }

    private fun appendGoldLeadChange(
        output: MutableList<MatchEvent>,
        timeline: GameTimeline,
        externalEvents: List<MatchEvent>,
        previous: TimelineSnapshotPoint,
        current: TimelineSnapshotPoint,
        intervalSeconds: Int,
        provenance: SourceProvenance,
        sequence: Long,
    ) {
        val oldBlue = previous.snapshot.blue.gold ?: return
        val oldRed = previous.snapshot.red.gold ?: return
        val newBlue = current.snapshot.blue.gold ?: return
        val newRed = current.snapshot.red.gold ?: return
        val oldDiff = oldBlue - oldRed
        val newDiff = newBlue - newRed
        val changedHands = (oldDiff > GOLD_LEAD_DEADBAND && newDiff < -GOLD_LEAD_DEADBAND) ||
            (oldDiff < -GOLD_LEAD_DEADBAND && newDiff > GOLD_LEAD_DEADBAND)
        if (!changedHands) return
        if (externalEvents.filterIsInstance<GoldLeadChangedEvent>().any {
                isInsideInterval(it.gameTimeSeconds, previous, current)
            }) return
        val leader = if (newDiff > 0) current.snapshot.blue.teamId else current.snapshot.red.teamId
        output += GoldLeadChangedEvent(
            matchId = timeline.matchId,
            gameId = timeline.gameId,
            sequence = sequence,
            gameTimeSeconds = current.gameTimeSeconds,
            provenance = provenance,
            evidence = EventEvidence.VERIFIED_DELTA,
            leadingTeamId = leader,
            goldDifference = kotlin.math.abs(newDiff),
            observedWindowSeconds = intervalSeconds,
        )
    }

    private fun exactPlayerKillDeltas(
        previousPlayers: List<PlayerLiveState>,
        currentPlayers: List<PlayerLiveState>,
        teamId: TeamId,
    ): List<Pair<PlayerLiveState, Int>> {
        val beforeById = previousPlayers.filter { it.teamId == teamId }.associateBy { it.playerId }
        return currentPlayers
            .filter { it.teamId == teamId }
            .mapNotNull { player ->
                val before = beforeById[player.playerId] ?: return@mapNotNull null
                val oldKills = before.kills ?: return@mapNotNull null
                val newKills = player.kills ?: return@mapNotNull null
                val delta = newKills - oldKills
                if (delta > 0) player to delta else null
            }
    }

    private fun nonNegativeDelta(previous: Int?, current: Int?): Int? {
        val before = previous ?: return null
        val after = current ?: return null
        if (after < before) return null
        return after - before
    }

    private fun isInsideInterval(
        eventSecond: Int,
        previous: TimelineSnapshotPoint,
        current: TimelineSnapshotPoint,
    ): Boolean = eventSecond > previous.gameTimeSeconds && eventSecond <= current.gameTimeSeconds

    private fun isLiveComparable(previous: TimelineSnapshotPoint, current: TimelineSnapshotPoint): Boolean =
        previous.provenance.sourceClass == SourceClass.LIVE_MATCH_SOURCE &&
            current.provenance.sourceClass == SourceClass.LIVE_MATCH_SOURCE &&
            previous.snapshot.game == current.snapshot.game

    private fun derivedProvenance(current: TimelineSnapshotPoint): SourceProvenance = SourceProvenance(
        providerId = DERIVED_PROVIDER_ID,
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = DataAuthority.DERIVED,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = current.provenance.observedAtEpochMillis,
        sourceTimestampEpochMillis = current.provenance.sourceTimestampEpochMillis,
        revision = current.provenance.revision,
        sourceUri = current.provenance.sourceUri,
    )

    companion object {
        const val DERIVED_PROVIDER_ID = "laner-live-event-derivation"
        const val MULTI_KILL_MAX_WINDOW_SECONDS = 20
        const val TEAM_FIGHT_MAX_WINDOW_SECONDS = 20
        const val TEAM_FIGHT_MIN_KILLS = 3
        const val GOLD_LEAD_DEADBAND = 250
    }
}
