package com.laner.core.domain

/**
 * Laner's first-level product phase. Every user-facing match capability must resolve to one of these phases.
 */
enum class MatchPhase {
    PRE_MATCH,
    LIVE_MATCH,
    POST_MATCH,
}

/**
 * Fine-grained lifecycle state. The state belongs to the match domain; UI must not infer it from provider text.
 */
enum class MatchLifecycleState {
    PRE_EVENT,
    EVENT_LIVE_PRE_GAME,
    DRAFT,
    LOADING,
    IN_GAME,
    POST_GAME,
    BETWEEN_GAMES,
    SERIES_COMPLETE,
    UNKNOWN,
}

fun MatchLifecycleState.phaseOrNull(): MatchPhase? = when (this) {
    MatchLifecycleState.PRE_EVENT -> MatchPhase.PRE_MATCH
    MatchLifecycleState.EVENT_LIVE_PRE_GAME,
    MatchLifecycleState.DRAFT,
    MatchLifecycleState.LOADING,
    MatchLifecycleState.IN_GAME,
    MatchLifecycleState.POST_GAME,
    MatchLifecycleState.BETWEEN_GAMES,
    -> MatchPhase.LIVE_MATCH

    MatchLifecycleState.SERIES_COMPLETE -> MatchPhase.POST_MATCH
    MatchLifecycleState.UNKNOWN -> null
}
