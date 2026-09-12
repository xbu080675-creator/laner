package com.laner.core.domain

enum class EventEvidence {
    PROVIDER_EXPLICIT,
    VERIFIED_FRAME,
    VERIFIED_DELTA,
    LOCAL_CAPTURE,
    DERIVED_WINDOW,
}

enum class ObjectiveType {
    DRAGON,
    SOUL,
    ELDER_DRAGON,
    BARON,
    HERALD,
    ATAKHAN,
    TOWER,
    OTHER,
}

enum class DraftActionType {
    PICK,
    BAN,
    LOCK,
    UNDO,
    OTHER,
}

sealed interface MatchEvent {
    val matchId: MatchId
    val gameId: GameId?
    val sequence: Long
    val gameTimeSeconds: Int?
    val provenance: SourceProvenance
    val evidence: EventEvidence
}

private fun validateEventPosition(sequence: Long, gameTimeSeconds: Int?) {
    require(sequence >= 0) { "Event sequence must be non-negative" }
    require(gameTimeSeconds == null || gameTimeSeconds >= 0) { "Game time must be non-negative" }
}

private fun validateObservedWindow(windowSeconds: Int?) {
    require(windowSeconds == null || windowSeconds > 0) { "Observed event window must be positive" }
}

data class MatchStateChanged(
    override val matchId: MatchId,
    override val gameId: GameId?,
    override val sequence: Long,
    override val gameTimeSeconds: Int?,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence,
    val previous: MatchLifecycleState,
    val current: MatchLifecycleState,
) : MatchEvent {
    init { validateEventPosition(sequence, gameTimeSeconds) }
}

/**
 * A verified kill-count fact. [count] may be greater than one when successive verified frames prove
 * that several kills happened inside one sampling window but do not prove exact transport events.
 * Nullable killer/victim fields must stay null unless the source evidence really identifies them.
 */
data class KillEvent(
    override val matchId: MatchId,
    override val gameId: GameId,
    override val sequence: Long,
    override val gameTimeSeconds: Int,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence,
    val killerId: PlayerId?,
    val victimId: PlayerId?,
    val assistingPlayerIds: Set<PlayerId> = emptySet(),
    val teamId: TeamId?,
    val count: Int = 1,
    val observedWindowSeconds: Int? = null,
) : MatchEvent {
    init {
        validateEventPosition(sequence, gameTimeSeconds)
        require(count > 0) { "Kill count must be positive" }
        validateObservedWindow(observedWindowSeconds)
    }
}

/**
 * A sampling-window multi-kill observation. This is deliberately not Riot's official Double/Triple/
 * Quadra/Penta classification; it only states that one verified player kill counter increased by
 * [killCount] inside [windowSeconds].
 */
data class MultiKillWindowEvent(
    override val matchId: MatchId,
    override val gameId: GameId,
    override val sequence: Long,
    override val gameTimeSeconds: Int,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence = EventEvidence.DERIVED_WINDOW,
    val playerId: PlayerId,
    val teamId: TeamId,
    val killCount: Int,
    val windowSeconds: Int,
) : MatchEvent {
    init {
        validateEventPosition(sequence, gameTimeSeconds)
        require(killCount >= 2) { "Multi-kill window requires at least two kills" }
        require(windowSeconds > 0) { "Multi-kill window must be positive" }
    }
}

/**
 * A conservative combat-cluster navigation hint derived from verified kill deltas. It must never be
 * presented as an official provider team-fight classification.
 */
data class TeamFightWindowEvent(
    override val matchId: MatchId,
    override val gameId: GameId,
    override val sequence: Long,
    override val gameTimeSeconds: Int,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence = EventEvidence.DERIVED_WINDOW,
    val blueKillDelta: Int,
    val redKillDelta: Int,
    val windowSeconds: Int,
) : MatchEvent {
    init {
        validateEventPosition(sequence, gameTimeSeconds)
        require(blueKillDelta >= 0 && redKillDelta >= 0)
        require(blueKillDelta + redKillDelta >= 3) { "Team-fight window requires at least three kills" }
        require(windowSeconds > 0) { "Team-fight window must be positive" }
    }
}

data class ObjectiveTakenEvent(
    override val matchId: MatchId,
    override val gameId: GameId,
    override val sequence: Long,
    override val gameTimeSeconds: Int,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence,
    val teamId: TeamId,
    val objective: ObjectiveType,
    val detail: String? = null,
    val count: Int = 1,
    val observedWindowSeconds: Int? = null,
) : MatchEvent {
    init {
        validateEventPosition(sequence, gameTimeSeconds)
        require(count > 0) { "Objective count must be positive" }
        validateObservedWindow(observedWindowSeconds)
    }
}

data class GoldLeadChangedEvent(
    override val matchId: MatchId,
    override val gameId: GameId,
    override val sequence: Long,
    override val gameTimeSeconds: Int,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence,
    val leadingTeamId: TeamId?,
    val goldDifference: Int,
    val observedWindowSeconds: Int? = null,
) : MatchEvent {
    init {
        validateEventPosition(sequence, gameTimeSeconds)
        validateObservedWindow(observedWindowSeconds)
    }
}

data class DraftChangedEvent(
    override val matchId: MatchId,
    override val gameId: GameId?,
    override val sequence: Long,
    override val gameTimeSeconds: Int?,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence,
    val action: DraftActionType,
    val teamId: TeamId?,
    val championId: String?,
) : MatchEvent {
    init { validateEventPosition(sequence, gameTimeSeconds) }
}
