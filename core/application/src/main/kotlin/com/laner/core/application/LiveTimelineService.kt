package com.laner.core.application

import com.laner.core.domain.EventEvidence
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchEvent
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TimelineSnapshotPoint
import com.laner.core.domain.semanticKey

data class TimelineIngestResult(
    val timeline: GameTimeline,
    val snapshotAddedOrReplaced: Boolean,
    val eventsAddedOrReplaced: Int,
    val duplicateEventsIgnored: Int,
    val invalidEventsRejected: Int,
)

data class TimelineReconcileResult(
    val timeline: GameTimeline,
    val generatedEventsStored: Int,
    val previousGeneratedEventsRemoved: Int,
    val invalidEventsRejected: Int,
)

/**
 * Provider-neutral canonical timeline ingestion and query service.
 *
 * Real-time LIVE frames and historical POST frames share the same GameTimeline. PRE and AI sources
 * are rejected. Events are deduplicated by factual semantic identity rather than transport sequence.
 * Out-of-order arrival is accepted and the persisted timeline is re-sorted by game time. Same-second
 * snapshots are arbitrated by provenance instead of last-write-wins. UI reads through [load] and
 * never touches a platform repository directly.
 */
class LiveTimelineService(
    private val repository: LiveTimelineRepository,
) {
    suspend fun load(gameId: GameId): GameTimeline? = repository.read(gameId)

    suspend fun ingest(
        snapshot: LiveGameSnapshot,
        provenance: SourceProvenance,
        events: List<MatchEvent> = emptyList(),
    ): TimelineIngestResult {
        require(isTimelineFactSource(provenance.sourceClass)) {
            "Timeline facts require LIVE_MATCH_SOURCE or POST_MATCH_SOURCE"
        }
        val game = snapshot.game
        val existing = repository.read(game.gameId)
            ?: GameTimeline(
                matchId = game.matchId,
                gameId = game.gameId,
                gameNumber = game.gameNumber,
            )
        require(existing.matchId == game.matchId && existing.gameId == game.gameId && existing.gameNumber == game.gameNumber) {
            "Timeline repository returned a different game for ${game.gameId}"
        }

        val elapsed = snapshot.elapsedSeconds
        var snapshotChanged = false
        val snapshots = if (elapsed == null) {
            existing.snapshots
        } else {
            val incoming = TimelineSnapshotPoint(
                gameTimeSeconds = elapsed,
                snapshot = snapshot,
                provenance = provenance,
            )
            val atSameSecond = existing.snapshots.firstOrNull { it.gameTimeSeconds == elapsed }
            when {
                atSameSecond == null -> {
                    snapshotChanged = true
                    (existing.snapshots + incoming).sortedBy { it.gameTimeSeconds }
                }
                prefer(incoming.provenance, atSameSecond.provenance) -> {
                    snapshotChanged = true
                    existing.snapshots
                        .filterNot { it.gameTimeSeconds == elapsed }
                        .plus(incoming)
                        .sortedBy { it.gameTimeSeconds }
                }
                else -> existing.snapshots
            }
        }

        val validIncoming = events.filter {
            it.matchId == game.matchId && it.gameId == game.gameId &&
                isTimelineFactSource(it.provenance.sourceClass)
        }
        val invalidCount = events.size - validIncoming.size
        val byKey = existing.events.associateBy { it.semanticKey() }.toMutableMap()
        var changedEvents = 0
        var duplicates = 0

        validIncoming.forEach { event ->
            val key = event.semanticKey()
            val current = byKey[key]
            when {
                current == null -> {
                    byKey[key] = event
                    changedEvents += 1
                }
                prefer(event, current) -> {
                    byKey[key] = event
                    changedEvents += 1
                }
                else -> duplicates += 1
            }
        }

        val updated = existing.copy(
            snapshots = snapshots,
            events = sortEvents(byKey.values),
        )
        if (updated != existing) repository.write(updated)

        return TimelineIngestResult(
            timeline = updated,
            snapshotAddedOrReplaced = snapshotChanged,
            eventsAddedOrReplaced = changedEvents,
            duplicateEventsIgnored = duplicates,
            invalidEventsRejected = invalidCount,
        )
    }

    /**
     * Replaces only the events owned by [generatorProviderId] and preserves all provider-explicit or
     * independently sourced events. This makes deterministic snapshot-delta derivation safe across
     * reconnects, late/out-of-order snapshots and stronger same-second snapshot replacement.
     */
    suspend fun reconcileGeneratedEvents(
        gameId: GameId,
        generatorProviderId: String,
        events: List<MatchEvent>,
    ): TimelineReconcileResult? {
        require(generatorProviderId.isNotBlank())
        val existing = repository.read(gameId) ?: return null
        val valid = events.filter {
            it.matchId == existing.matchId && it.gameId == existing.gameId &&
                it.provenance.providerId == generatorProviderId &&
                it.provenance.sourceClass == SourceClass.LIVE_MATCH_SOURCE
        }
        val invalidCount = events.size - valid.size
        val preserved = existing.events.filterNot { it.provenance.providerId == generatorProviderId }
        val byKey = preserved.associateBy { it.semanticKey() }.toMutableMap()

        valid.forEach { event ->
            val key = event.semanticKey()
            val current = byKey[key]
            if (current == null || prefer(event, current)) byKey[key] = event
        }

        val updated = existing.copy(events = sortEvents(byKey.values))
        if (updated != existing) repository.write(updated)
        return TimelineReconcileResult(
            timeline = updated,
            generatedEventsStored = updated.events.count { it.provenance.providerId == generatorProviderId },
            previousGeneratedEventsRemoved = existing.events.count { it.provenance.providerId == generatorProviderId },
            invalidEventsRejected = invalidCount,
        )
    }

    suspend fun markCompleted(gameId: GameId): GameTimeline? {
        val existing = repository.read(gameId) ?: return null
        if (existing.completed) return existing
        val updated = existing.copy(completed = true)
        repository.write(updated)
        return updated
    }

    private fun sortEvents(events: Collection<MatchEvent>): List<MatchEvent> = events.sortedWith(
        compareBy<MatchEvent> { it.gameTimeSeconds ?: Int.MIN_VALUE }
            .thenBy { it.sequence }
            .thenBy { it.semanticKey() }
    )

    private fun isTimelineFactSource(sourceClass: SourceClass): Boolean =
        sourceClass == SourceClass.LIVE_MATCH_SOURCE || sourceClass == SourceClass.POST_MATCH_SOURCE

    private fun prefer(candidate: MatchEvent, current: MatchEvent): Boolean {
        val evidence = evidenceStrength(candidate.evidence).compareTo(evidenceStrength(current.evidence))
        if (evidence != 0) return evidence > 0
        return prefer(candidate.provenance, current.provenance)
    }

    private fun prefer(candidate: SourceProvenance, current: SourceProvenance): Boolean {
        val authority = candidate.authority.weight.compareTo(current.authority.weight)
        if (authority != 0) return authority > 0
        val candidateTime = candidate.sourceTimestampEpochMillis ?: candidate.observedAtEpochMillis
        val currentTime = current.sourceTimestampEpochMillis ?: current.observedAtEpochMillis
        if (candidateTime != currentTime) return candidateTime > currentTime
        return candidate.revision > current.revision
    }

    private fun evidenceStrength(value: EventEvidence): Int = when (value) {
        EventEvidence.VERIFIED_FRAME -> 5
        EventEvidence.PROVIDER_EXPLICIT -> 4
        EventEvidence.VERIFIED_DELTA -> 3
        EventEvidence.LOCAL_CAPTURE -> 2
        EventEvidence.DERIVED_WINDOW -> 1
    }
}
