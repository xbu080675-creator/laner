package com.laner.core.domain

/** A provenance-bearing snapshot point stored independently from Provider payload formats. */
data class TimelineSnapshotPoint(
    val gameTimeSeconds: Int,
    val snapshot: LiveGameSnapshot,
    val provenance: SourceProvenance,
) {
    init {
        require(gameTimeSeconds >= 0)
        require(snapshot.game.gameId == snapshot.game.gameId)
        require(provenance.sourceClass == SourceClass.LIVE_MATCH_SOURCE)
    }
}

data class GameTimeline(
    val matchId: MatchId,
    val gameId: GameId,
    val gameNumber: Int,
    val snapshots: List<TimelineSnapshotPoint> = emptyList(),
    val events: List<MatchEvent> = emptyList(),
    val completed: Boolean = false,
) {
    init {
        require(gameNumber > 0)
        require(snapshots.all { it.snapshot.game.matchId == matchId && it.snapshot.game.gameId == gameId })
        require(events.all { it.matchId == matchId && it.gameId == gameId })
    }

    fun stateAt(seconds: Int): LiveGameSnapshot? {
        require(seconds >= 0)
        return snapshots
            .filter { it.gameTimeSeconds <= seconds }
            .maxByOrNull { it.gameTimeSeconds }
            ?.snapshot
            ?: snapshots.minByOrNull { it.gameTimeSeconds }?.snapshot
    }
}

/**
 * Provider-independent event identity used for reconnect/idempotency handling.
 * Sequence and provenance are intentionally excluded because different providers/reconnects may
 * assign different transport sequence numbers to the same factual event.
 */
fun MatchEvent.semanticKey(): String = when (this) {
    is MatchStateChanged -> listOf(
        "state", matchId.value, gameId?.value.orEmpty(), gameTimeSeconds?.toString().orEmpty(), previous.name, current.name,
    )
    is KillEvent -> listOf(
        "kill", matchId.value, gameId.value, gameTimeSeconds.toString(), killerId?.value.orEmpty(),
        victimId?.value.orEmpty(), teamId?.value.orEmpty(), assistingPlayerIds.map { it.value }.sorted().joinToString(","),
    )
    is ObjectiveTakenEvent -> listOf(
        "objective", matchId.value, gameId.value, gameTimeSeconds.toString(), teamId.value, objective.name, detail.orEmpty(),
    )
    is GoldLeadChangedEvent -> listOf(
        "gold-lead", matchId.value, gameId.value, gameTimeSeconds.toString(), leadingTeamId?.value.orEmpty(), goldDifference.toString(),
    )
    is DraftChangedEvent -> listOf(
        "draft", matchId.value, gameId?.value.orEmpty(), gameTimeSeconds?.toString().orEmpty(), action,
        teamId?.value.orEmpty(), championId.orEmpty(),
    )
}.joinToString("|")
