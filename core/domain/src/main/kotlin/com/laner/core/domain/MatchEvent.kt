package com.laner.core.domain

enum class EventEvidence {
    PROVIDER_EXPLICIT,
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
) : MatchEvent {
    init { validateEventPosition(sequence, gameTimeSeconds) }
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
) : MatchEvent {
    init { validateEventPosition(sequence, gameTimeSeconds) }
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
) : MatchEvent {
    init { validateEventPosition(sequence, gameTimeSeconds) }
}

data class DraftChangedEvent(
    override val matchId: MatchId,
    override val gameId: GameId?,
    override val sequence: Long,
    override val gameTimeSeconds: Int?,
    override val provenance: SourceProvenance,
    override val evidence: EventEvidence,
    val action: String,
    val teamId: TeamId?,
    val championId: String?,
) : MatchEvent {
    init {
        validateEventPosition(sequence, gameTimeSeconds)
        require(action.isNotBlank())
    }
}
