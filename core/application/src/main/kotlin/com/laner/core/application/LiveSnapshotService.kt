package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance

/**
 * Application authority for verified LIVE gameplay snapshots.
 *
 * It does not advance Match Lifecycle. It only validates canonical identity, arbitrates sources and
 * writes verified facts through [LiveTimelineService].
 */
class LiveSnapshotService(
    private val sources: List<LiveSnapshotSourcePort>,
    private val timelineService: LiveTimelineService,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun refresh(
        query: LiveMatchSourceQuery,
        context: SourceRequestContext,
    ): LiveSnapshotResolution {
        val failures = mutableListOf<DiagnosticFailure>()
        val candidates = mutableListOf<Candidate>()

        sources.forEach { source ->
            when (val read = source.readSnapshot(query, context)) {
                is ProviderRead.Success -> read.value?.let { value ->
                    val snapshot = value.snapshot
                    val expectedGameId = snapshot.game.gameNumber
                        .takeIf { it > 0 }
                        ?.let { GameIdentity.canonical(query.matchId, it) }
                    val allowedTeams = query.teams.map { it.id }.toSet()
                    val invalidReason = when {
                        snapshot.game.matchId != query.matchId -> "snapshot match identity mismatch"
                        snapshot.game.gameId != expectedGameId -> "snapshot game identity is not canonical"
                        snapshot.lifecycle != MatchLifecycleState.IN_GAME -> "snapshot lifecycle is not IN_GAME"
                        query.teams.size == 2 && setOf(snapshot.game.blueTeamId, snapshot.game.redTeamId) != allowedTeams ->
                            "snapshot team identity mismatch"
                        else -> null
                    }
                    if (invalidReason != null) {
                        val failure = DiagnosticFailure(
                            code = ErrorCode("LNR-APP-LIVE-002"),
                            message = invalidReason,
                            retryable = false,
                            context = mapOf(
                                "provider" to source.providerId,
                                "match_id" to query.matchId.value,
                                "game" to snapshot.game.gameNumber.toString(),
                            ),
                        )
                        failures += failure
                        emitFailure(failure)
                    } else {
                        candidates += Candidate(source, value)
                    }
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emitFailure(read.failure)
                }
            }
        }

        if (candidates.isEmpty()) {
            return LiveSnapshotResolution(
                snapshot = null,
                provenance = null,
                timelineResult = null,
                status = if (failures.isEmpty()) LiveSnapshotLoadStatus.UNAVAILABLE else LiveSnapshotLoadStatus.DEGRADED,
                selectedProviderId = null,
                failures = failures,
            )
        }

        val selected = candidates.reduce { best, next -> if (prefer(next, best)) next else best }
        val provenance = SourceProvenance(
            providerId = selected.source.providerId,
            sourceClass = SourceClass.LIVE_MATCH_SOURCE,
            authority = selected.source.authority,
            freshnessClass = FreshnessClass.REALTIME,
            observedAtEpochMillis = selected.value.observedAtEpochMillis,
            sourceTimestampEpochMillis = selected.value.sourceTimestampEpochMillis,
            revision = selected.value.revision,
            sourceUri = selected.value.sourceUri,
        )
        val timelineResult = timelineService.ingest(
            snapshot = selected.value.snapshot,
            provenance = provenance,
        )
        val status = if (failures.isEmpty()) LiveSnapshotLoadStatus.READY else LiveSnapshotLoadStatus.DEGRADED

        diagnostics?.emit(
            DiagnosticEvent(
                module = "LIVE",
                level = if (status == LiveSnapshotLoadStatus.READY) LogLevel.INFO else LogLevel.WARN,
                message = "Live snapshot ${status.name.lowercase()}",
                context = mapOf(
                    "match_id" to query.matchId.value,
                    "provider" to selected.source.providerId,
                    "game" to selected.value.snapshot.game.gameNumber.toString(),
                    "elapsed" to (selected.value.snapshot.elapsedSeconds?.toString() ?: "unknown"),
                    "failures" to failures.size.toString(),
                ),
            )
        )

        return LiveSnapshotResolution(
            snapshot = selected.value.snapshot,
            provenance = provenance,
            timelineResult = timelineResult,
            status = status,
            selectedProviderId = selected.source.providerId,
            failures = failures,
        )
    }

    private fun prefer(candidate: Candidate, current: Candidate): Boolean {
        val authority = candidate.source.authority.weight.compareTo(current.source.authority.weight)
        if (authority != 0) return authority > 0
        val candidateTime = candidate.value.sourceTimestampEpochMillis ?: candidate.value.observedAtEpochMillis
        val currentTime = current.value.sourceTimestampEpochMillis ?: current.value.observedAtEpochMillis
        if (candidateTime != currentTime) return candidateTime > currentTime
        return candidate.value.revision > current.value.revision
    }

    private fun emitFailure(failure: DiagnosticFailure) {
        diagnostics?.emit(
            DiagnosticEvent(
                module = "LIVE",
                level = LogLevel.WARN,
                message = failure.message,
                context = failure.context,
                failure = failure,
            )
        )
    }

    private data class Candidate(
        val source: LiveSnapshotSourcePort,
        val value: ProviderLiveSnapshot,
    )
}
