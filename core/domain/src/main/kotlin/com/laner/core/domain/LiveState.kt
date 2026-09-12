package com.laner.core.domain

/**
 * Evidence attached to a lifecycle signal. A provider-explicit state is stronger than a state
 * inferred from a verified frame; DERIVED is intentionally the weakest form.
 */
enum class LiveStateEvidence {
    PROVIDER_EXPLICIT,
    VERIFIED_FRAME,
    DERIVED,
}

data class LiveMatchState(
    val matchId: MatchId,
    val lifecycle: MatchLifecycleState = MatchLifecycleState.PRE_EVENT,
    val currentGameId: GameId? = null,
    val currentGameNumber: Int? = null,
    val lastObservedAtEpochMillis: Long = 0L,
    val provenance: SourceProvenance? = null,
) {
    init {
        require(currentGameNumber == null || currentGameNumber > 0)
        require(lastObservedAtEpochMillis >= 0)
    }

    val isTerminal: Boolean get() = lifecycle == MatchLifecycleState.SERIES_COMPLETE
}

data class LiveStateSignal(
    val matchId: MatchId,
    val lifecycle: MatchLifecycleState,
    val gameId: GameId? = null,
    val gameNumber: Int? = null,
    val observedAtEpochMillis: Long,
    val provenance: SourceProvenance,
    val evidence: LiveStateEvidence,
) {
    init {
        require(gameNumber == null || gameNumber > 0)
        require(observedAtEpochMillis >= 0)
        require(provenance.sourceClass == SourceClass.LIVE_MATCH_SOURCE) {
            "Live lifecycle signals must come from LIVE_MATCH_SOURCE"
        }
    }
}

enum class LiveStateIgnoreReason {
    DUPLICATE,
    STALE_GAME,
    STALE_OBSERVATION,
    TERMINAL_STATE,
    UNKNOWN_SIGNAL,
}

enum class LiveStateConflictReason {
    MATCH_MISMATCH,
    IMPOSSIBLE_TRANSITION,
    FUTURE_GAME_WHILE_CURRENT_GAME_ACTIVE,
    GAME_ID_CONFLICT,
}

sealed interface LiveStateTransitionResult {
    val state: LiveMatchState

    data class Applied(
        override val state: LiveMatchState,
        val previous: LiveMatchState,
    ) : LiveStateTransitionResult

    data class Ignored(
        override val state: LiveMatchState,
        val reason: LiveStateIgnoreReason,
    ) : LiveStateTransitionResult

    data class Conflict(
        override val state: LiveMatchState,
        val reason: LiveStateConflictReason,
        val signal: LiveStateSignal,
    ) : LiveStateTransitionResult
}

/**
 * Pure authoritative lifecycle reducer.
 *
 * Rules intentionally protect the legacy-proven behavior that EVENT_LIVE/BETWEEN_GAMES is not
 * IN_GAME. Providers may skip intermediate signals when they have explicit later evidence, but an
 * old-game or backwards signal can never rewind the current authoritative state.
 */
object LiveMatchStateReducer {
    fun reduce(
        current: LiveMatchState,
        signal: LiveStateSignal,
    ): LiveStateTransitionResult {
        if (current.matchId != signal.matchId) {
            return LiveStateTransitionResult.Conflict(
                state = current,
                reason = LiveStateConflictReason.MATCH_MISMATCH,
                signal = signal,
            )
        }

        if (signal.lifecycle == MatchLifecycleState.UNKNOWN) {
            return LiveStateTransitionResult.Ignored(current, LiveStateIgnoreReason.UNKNOWN_SIGNAL)
        }

        if (current.isTerminal) {
            return if (signal.lifecycle == MatchLifecycleState.SERIES_COMPLETE) {
                LiveStateTransitionResult.Ignored(current, LiveStateIgnoreReason.DUPLICATE)
            } else {
                LiveStateTransitionResult.Ignored(current, LiveStateIgnoreReason.TERMINAL_STATE)
            }
        }

        val gameRelation = compareGame(current, signal)
        if (gameRelation < 0) {
            return LiveStateTransitionResult.Ignored(current, LiveStateIgnoreReason.STALE_GAME)
        }
        if (gameRelation > 0) {
            if (current.lifecycle !in NEXT_GAME_BOUNDARY_STATES) {
                return LiveStateTransitionResult.Conflict(
                    current,
                    LiveStateConflictReason.FUTURE_GAME_WHILE_CURRENT_GAME_ACTIVE,
                    signal,
                )
            }
            if (signal.lifecycle !in NEXT_GAME_ENTRY_STATES) {
                return LiveStateTransitionResult.Conflict(
                    current,
                    LiveStateConflictReason.IMPOSSIBLE_TRANSITION,
                    signal,
                )
            }
            return apply(current, signal)
        }

        if (sameKnownGameNumber(current, signal) && hasConflictingKnownGameId(current, signal)) {
            return LiveStateTransitionResult.Conflict(
                current,
                LiveStateConflictReason.GAME_ID_CONFLICT,
                signal,
            )
        }

        if (current.lifecycle == signal.lifecycle && sameGameIdentity(current, signal)) {
            return LiveStateTransitionResult.Ignored(current, LiveStateIgnoreReason.DUPLICATE)
        }

        if (signal.observedAtEpochMillis < current.lastObservedAtEpochMillis &&
            lifecycleRank(signal.lifecycle) <= lifecycleRank(current.lifecycle)
        ) {
            return LiveStateTransitionResult.Ignored(current, LiveStateIgnoreReason.STALE_OBSERVATION)
        }

        if (!canTransition(current.lifecycle, signal.lifecycle)) {
            return LiveStateTransitionResult.Conflict(
                current,
                LiveStateConflictReason.IMPOSSIBLE_TRANSITION,
                signal,
            )
        }

        return apply(current, signal)
    }

    private fun apply(
        current: LiveMatchState,
        signal: LiveStateSignal,
    ): LiveStateTransitionResult.Applied {
        val next = current.copy(
            lifecycle = signal.lifecycle,
            currentGameId = signal.gameId ?: current.currentGameId,
            currentGameNumber = signal.gameNumber ?: current.currentGameNumber,
            lastObservedAtEpochMillis = maxOf(current.lastObservedAtEpochMillis, signal.observedAtEpochMillis),
            provenance = signal.provenance,
        )
        return LiveStateTransitionResult.Applied(state = next, previous = current)
    }

    private fun compareGame(current: LiveMatchState, signal: LiveStateSignal): Int {
        val currentNumber = current.currentGameNumber
        val signalNumber = signal.gameNumber
        if (currentNumber != null && signalNumber != null) {
            return signalNumber.compareTo(currentNumber)
        }
        if (current.currentGameId != null && signal.gameId != null && current.currentGameId != signal.gameId) {
            return if (current.lifecycle in NEXT_GAME_BOUNDARY_STATES && signal.lifecycle in NEXT_GAME_ENTRY_STATES) 1 else 0
        }
        return 0
    }

    private fun sameKnownGameNumber(current: LiveMatchState, signal: LiveStateSignal): Boolean =
        current.currentGameNumber != null && signal.gameNumber != null && current.currentGameNumber == signal.gameNumber

    private fun hasConflictingKnownGameId(current: LiveMatchState, signal: LiveStateSignal): Boolean =
        current.currentGameId != null && signal.gameId != null && current.currentGameId != signal.gameId

    private fun sameGameIdentity(current: LiveMatchState, signal: LiveStateSignal): Boolean {
        if (current.currentGameNumber != null && signal.gameNumber != null) {
            return current.currentGameNumber == signal.gameNumber
        }
        if (current.currentGameId != null && signal.gameId != null) {
            return current.currentGameId == signal.gameId
        }
        return true
    }

    private fun canTransition(from: MatchLifecycleState, to: MatchLifecycleState): Boolean = when (from) {
        MatchLifecycleState.PRE_EVENT -> to in setOf(
            MatchLifecycleState.EVENT_LIVE_PRE_GAME,
            MatchLifecycleState.DRAFT,
            MatchLifecycleState.LOADING,
            MatchLifecycleState.IN_GAME,
        )
        MatchLifecycleState.EVENT_LIVE_PRE_GAME -> to in setOf(
            MatchLifecycleState.DRAFT,
            MatchLifecycleState.LOADING,
            MatchLifecycleState.IN_GAME,
        )
        MatchLifecycleState.DRAFT -> to in setOf(MatchLifecycleState.LOADING, MatchLifecycleState.IN_GAME)
        MatchLifecycleState.LOADING -> to == MatchLifecycleState.IN_GAME
        MatchLifecycleState.IN_GAME -> to == MatchLifecycleState.POST_GAME
        MatchLifecycleState.POST_GAME -> to in setOf(
            MatchLifecycleState.BETWEEN_GAMES,
            MatchLifecycleState.SERIES_COMPLETE,
        )
        MatchLifecycleState.BETWEEN_GAMES -> to in NEXT_GAME_ENTRY_STATES + MatchLifecycleState.SERIES_COMPLETE
        MatchLifecycleState.SERIES_COMPLETE -> false
        MatchLifecycleState.UNKNOWN -> to != MatchLifecycleState.UNKNOWN
    }

    private fun lifecycleRank(state: MatchLifecycleState): Int = when (state) {
        MatchLifecycleState.PRE_EVENT -> 0
        MatchLifecycleState.EVENT_LIVE_PRE_GAME -> 1
        MatchLifecycleState.DRAFT -> 2
        MatchLifecycleState.LOADING -> 3
        MatchLifecycleState.IN_GAME -> 4
        MatchLifecycleState.POST_GAME -> 5
        MatchLifecycleState.BETWEEN_GAMES -> 6
        MatchLifecycleState.SERIES_COMPLETE -> 7
        MatchLifecycleState.UNKNOWN -> -1
    }

    private val NEXT_GAME_BOUNDARY_STATES = setOf(
        MatchLifecycleState.POST_GAME,
        MatchLifecycleState.BETWEEN_GAMES,
    )

    private val NEXT_GAME_ENTRY_STATES = setOf(
        MatchLifecycleState.DRAFT,
        MatchLifecycleState.LOADING,
        MatchLifecycleState.IN_GAME,
    )
}
