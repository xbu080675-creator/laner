package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.LiveMatchStateReducer
import com.laner.core.domain.LiveStateConflictReason
import com.laner.core.domain.LiveStateEvidence
import com.laner.core.domain.LiveStateSignal
import com.laner.core.domain.LiveStateTransitionResult
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import kotlin.math.abs

enum class LiveStateLoadStatus {
    READY,
    DEGRADED,
    CONFLICT,
    UNAVAILABLE,
}

data class LiveProviderConflict(
    val providerId: String,
    val reason: LiveStateConflictReason,
    val attemptedState: MatchLifecycleState,
)

data class LiveStateResolution(
    val state: LiveMatchState,
    val status: LiveStateLoadStatus,
    val selectedProviderId: String?,
    val failures: List<DiagnosticFailure>,
    val conflicts: List<LiveProviderConflict>,
    val appliedTransitions: List<Pair<MatchLifecycleState, MatchLifecycleState>>,
)

/**
 * Single Application authority for LIVE lifecycle.
 *
 * Freshness can beat nominal source authority when the difference exceeds the REALTIME freshness
 * window, and a verified live frame is stronger evidence of IN_GAME than a textual "event live"
 * flag. Only the selected candidate is allowed to mutate authoritative state.
 */
class LiveMatchStateService(
    private val sources: List<LiveStateSourcePort>,
    private val repository: LiveMatchStateRepository,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun refresh(
        matchId: MatchId,
        context: SourceRequestContext,
    ): LiveStateResolution {
        val current = repository.read(matchId) ?: LiveMatchState(matchId = matchId)
        val failures = mutableListOf<DiagnosticFailure>()
        val candidates = mutableListOf<SignalCandidate>()

        sources.forEach { source ->
            when (val read = source.readLiveState(matchId, context)) {
                is ProviderRead.Success -> {
                    val observation = read.value
                    if (observation.matchId != matchId) {
                        val failure = DiagnosticFailure(
                            code = ErrorCode("LNR-APP-LIVE-001"),
                            message = "Live provider returned an observation for another match",
                            retryable = false,
                            context = mapOf(
                                "provider" to source.providerId,
                                "requested_match" to matchId.value,
                                "returned_match" to observation.matchId.value,
                            ),
                        )
                        failures += failure
                        emitFailure(failure)
                    } else {
                        candidates += toCandidate(source, observation)
                    }
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emitFailure(read.failure)
                }
            }
        }

        if (candidates.isEmpty()) {
            return LiveStateResolution(
                state = current,
                status = LiveStateLoadStatus.UNAVAILABLE,
                selectedProviderId = null,
                failures = failures,
                conflicts = emptyList(),
                appliedTransitions = emptyList(),
            )
        }

        val selected = candidates.reduce { best, next ->
            if (prefer(next, best)) next else best
        }

        val conflicts = mutableListOf<LiveProviderConflict>()
        val applied = mutableListOf<Pair<MatchLifecycleState, MatchLifecycleState>>()
        var state = current

        transitionSequence(state, selected.signal).forEach { signal ->
            when (val result = LiveMatchStateReducer.reduce(state, signal)) {
                is LiveStateTransitionResult.Applied -> {
                    applied += result.previous.lifecycle to result.state.lifecycle
                    state = result.state
                }
                is LiveStateTransitionResult.Ignored -> state = result.state
                is LiveStateTransitionResult.Conflict -> {
                    conflicts += LiveProviderConflict(
                        providerId = signal.provenance.providerId,
                        reason = result.reason,
                        attemptedState = signal.lifecycle,
                    )
                    state = result.state
                }
            }
        }

        if (state != current) {
            repository.write(state)
        }

        val status = when {
            conflicts.isNotEmpty() -> LiveStateLoadStatus.CONFLICT
            failures.isNotEmpty() -> LiveStateLoadStatus.DEGRADED
            selected.signal.lifecycle == MatchLifecycleState.UNKNOWN -> LiveStateLoadStatus.DEGRADED
            else -> LiveStateLoadStatus.READY
        }

        diagnostics?.emit(
            DiagnosticEvent(
                module = "LIVE",
                level = when (status) {
                    LiveStateLoadStatus.READY -> LogLevel.INFO
                    LiveStateLoadStatus.DEGRADED -> LogLevel.WARN
                    LiveStateLoadStatus.CONFLICT -> LogLevel.WARN
                    LiveStateLoadStatus.UNAVAILABLE -> LogLevel.ERROR
                },
                message = "Live state ${status.name.lowercase()}",
                context = mapOf(
                    "match_id" to matchId.value,
                    "provider" to selected.source.providerId,
                    "lifecycle" to state.lifecycle.name,
                    "game" to (state.currentGameNumber?.toString() ?: "unknown"),
                    "failures" to failures.size.toString(),
                    "conflicts" to conflicts.size.toString(),
                ),
            )
        )

        return LiveStateResolution(
            state = state,
            status = status,
            selectedProviderId = selected.source.providerId,
            failures = failures,
            conflicts = conflicts,
            appliedTransitions = applied,
        )
    }

    private fun toCandidate(
        source: LiveStateSourcePort,
        observation: ProviderLiveObservation,
    ): SignalCandidate {
        val target = when {
            observation.seriesEnded -> MatchLifecycleState.SERIES_COMPLETE
            observation.betweenGames -> MatchLifecycleState.BETWEEN_GAMES
            observation.gameEnded -> MatchLifecycleState.POST_GAME
            observation.liveFrameObserved -> MatchLifecycleState.IN_GAME
            observation.loadingObserved -> MatchLifecycleState.LOADING
            observation.draftStarted -> MatchLifecycleState.DRAFT
            observation.eventStarted -> MatchLifecycleState.EVENT_LIVE_PRE_GAME
            else -> MatchLifecycleState.UNKNOWN
        }
        val evidence = when {
            observation.liveFrameObserved && target == MatchLifecycleState.IN_GAME -> LiveStateEvidence.VERIFIED_FRAME
            target == MatchLifecycleState.UNKNOWN -> LiveStateEvidence.DERIVED
            else -> LiveStateEvidence.PROVIDER_EXPLICIT
        }
        val provenance = SourceProvenance(
            providerId = source.providerId,
            sourceClass = SourceClass.LIVE_MATCH_SOURCE,
            authority = source.authority,
            freshnessClass = FreshnessClass.REALTIME,
            observedAtEpochMillis = observation.observedAtEpochMillis,
            sourceTimestampEpochMillis = observation.sourceTimestampEpochMillis,
            revision = observation.revision,
            sourceUri = observation.sourceUri,
        )
        return SignalCandidate(
            source = source,
            signal = LiveStateSignal(
                matchId = observation.matchId,
                lifecycle = target,
                gameId = observation.gameId,
                gameNumber = observation.gameNumber,
                observedAtEpochMillis = observation.observedAtEpochMillis,
                provenance = provenance,
                evidence = evidence,
            ),
        )
    }

    /**
     * BETWEEN_GAMES and SERIES_COMPLETE prove that the previous game ended. Application may insert
     * the missing POST_GAME transition, but that inserted step is explicitly DERIVED.
     */
    private fun transitionSequence(
        current: LiveMatchState,
        selected: LiveStateSignal,
    ): List<LiveStateSignal> = when {
        selected.lifecycle == MatchLifecycleState.BETWEEN_GAMES && current.lifecycle == MatchLifecycleState.IN_GAME ->
            listOf(
                selected.copy(
                    lifecycle = MatchLifecycleState.POST_GAME,
                    evidence = LiveStateEvidence.DERIVED,
                ),
                selected,
            )
        selected.lifecycle == MatchLifecycleState.SERIES_COMPLETE && current.lifecycle == MatchLifecycleState.IN_GAME ->
            listOf(
                selected.copy(
                    lifecycle = MatchLifecycleState.POST_GAME,
                    evidence = LiveStateEvidence.DERIVED,
                ),
                selected,
            )
        else -> listOf(selected)
    }

    private fun prefer(candidate: SignalCandidate, current: SignalCandidate): Boolean {
        val candidateTime = candidate.signal.provenance.sourceTimestampEpochMillis
            ?: candidate.signal.provenance.observedAtEpochMillis
        val currentTime = current.signal.provenance.sourceTimestampEpochMillis
            ?: current.signal.provenance.observedAtEpochMillis

        if (abs(candidateTime - currentTime) > REALTIME_OVERRIDE_MILLIS) {
            return candidateTime > currentTime
        }

        val evidenceComparison = evidenceStrength(candidate.signal.evidence)
            .compareTo(evidenceStrength(current.signal.evidence))
        if (evidenceComparison != 0) return evidenceComparison > 0

        val authorityComparison = candidate.signal.provenance.authority.weight
            .compareTo(current.signal.provenance.authority.weight)
        if (authorityComparison != 0) return authorityComparison > 0

        if (candidate.signal.provenance.revision != current.signal.provenance.revision) {
            return candidate.signal.provenance.revision > current.signal.provenance.revision
        }
        return candidateTime > currentTime
    }

    private fun evidenceStrength(evidence: LiveStateEvidence): Int = when (evidence) {
        LiveStateEvidence.VERIFIED_FRAME -> 3
        LiveStateEvidence.PROVIDER_EXPLICIT -> 2
        LiveStateEvidence.DERIVED -> 1
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

    private data class SignalCandidate(
        val source: LiveStateSourcePort,
        val signal: LiveStateSignal,
    )

    private companion object {
        const val REALTIME_OVERRIDE_MILLIS = 15_000L
    }
}
