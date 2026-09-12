package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.SourceClass

enum class LiveSnapshotLoadStatus {
    READY,
    DEGRADED,
    UNAVAILABLE,
}

data class LiveSnapshotResolution(
    val status: LiveSnapshotLoadStatus,
    val snapshot: LiveGameSnapshot?,
    val selectedProviderId: String?,
    val failures: List<DiagnosticFailure>,
    val timeline: com.laner.core.domain.GameTimeline?,
)

/**
 * Application authority for current verified gameplay frames.
 *
 * Adapters may fetch provider-specific realtime payloads, but only this service validates canonical
 * Match/Game identity and writes accepted snapshots into the shared GameTimeline.
 */
class LiveSnapshotService(
    private val sources: List<LiveSnapshotSourcePort>,
    private val timelineService: LiveTimelineService,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun refresh(
        query: LiveSnapshotQuery,
        context: SourceRequestContext,
    ): LiveSnapshotResolution {
        val failures = mutableListOf<DiagnosticFailure>()
        val candidates = mutableListOf<Pair<LiveSnapshotSourcePort, ProviderLiveSnapshot>>()

        sources.forEach { source ->
            when (val read = source.readSnapshot(query, context)) {
                is ProviderRead.Success -> {
                    val value = read.value ?: return@forEach
                    val snapshot = value.snapshot
                    val valid = snapshot.game.matchId == query.matchId &&
                        snapshot.game.gameId == query.gameId &&
                        snapshot.game.gameNumber == query.gameNumber &&
                        value.provenance.sourceClass == SourceClass.LIVE_MATCH_SOURCE
                    if (!valid) {
                        val failure = DiagnosticFailure(
                            code = ErrorCode("LNR-APP-LIVE-002"),
                            message = "Live snapshot provider returned mismatched canonical identity",
                            retryable = false,
                            context = mapOf(
                                "provider" to source.providerId,
                                "match_id" to query.matchId.value,
                                "game_id" to query.gameId.value,
                                "game_number" to query.gameNumber.toString(),
                            ),
                        )
                        failures += failure
                        emitFailure(failure)
                    } else {
                        candidates += source to value
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
                status = LiveSnapshotLoadStatus.UNAVAILABLE,
                snapshot = null,
                selectedProviderId = null,
                failures = failures,
                timeline = timelineService.load(query.gameId),
            )
        }

        val selected = candidates.reduce { best, next ->
            if (prefer(next.second, best.second)) next else best
        }
        val ingest = timelineService.ingest(
            snapshot = selected.second.snapshot,
            provenance = selected.second.provenance,
        )
        val status = if (failures.isEmpty()) LiveSnapshotLoadStatus.READY else LiveSnapshotLoadStatus.DEGRADED

        diagnostics?.emit(
            DiagnosticEvent(
                module = "LIVE",
                level = if (status == LiveSnapshotLoadStatus.READY) LogLevel.INFO else LogLevel.WARN,
                message = "Live snapshot ${status.name.lowercase()}",
                context = mapOf(
                    "match_id" to query.matchId.value,
                    "game_id" to query.gameId.value,
                    "game_number" to query.gameNumber.toString(),
                    "provider" to selected.first.providerId,
                    "elapsed" to (selected.second.snapshot.elapsedSeconds?.toString() ?: "unknown"),
                    "failures" to failures.size.toString(),
                ),
            )
        )

        return LiveSnapshotResolution(
            status = status,
            snapshot = selected.second.snapshot,
            selectedProviderId = selected.first.providerId,
            failures = failures,
            timeline = ingest.timeline,
        )
    }

    private fun prefer(candidate: ProviderLiveSnapshot, current: ProviderLiveSnapshot): Boolean {
        val authority = candidate.provenance.authority.weight.compareTo(current.provenance.authority.weight)
        if (authority != 0) return authority > 0
        val candidateTime = candidate.provenance.sourceTimestampEpochMillis ?: candidate.provenance.observedAtEpochMillis
        val currentTime = current.provenance.sourceTimestampEpochMillis ?: current.provenance.observedAtEpochMillis
        if (candidateTime != currentTime) return candidateTime > currentTime
        return candidate.provenance.revision > current.provenance.revision
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
}
