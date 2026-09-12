package com.laner.core.domain

data class MatchContext(
    val matchId: MatchId,
    val competitionId: CompetitionId,
    val editionId: EditionId?,
    val stageId: StageId?,
    val bestOf: Int,
    val teams: List<TeamRef>,
) {
    init {
        require(bestOf > 0)
        require(teams.size == 2) { "A match must have exactly two active sides" }
        require(teams.map { it.id }.distinct().size == 2) { "A team cannot play against itself" }
    }
}

data class GameContext(
    val gameId: GameId,
    val matchId: MatchId,
    val gameNumber: Int,
    val blueTeamId: TeamId,
    val redTeamId: TeamId,
) {
    init {
        require(gameNumber > 0)
        require(blueTeamId != redTeamId)
    }
}

data class MatchState(
    val context: MatchContext,
    val lifecycle: MatchLifecycleState,
    val currentGame: GameContext? = null,
    val blueWins: Int = 0,
    val redWins: Int = 0,
) {
    init {
        require(blueWins >= 0 && redWins >= 0)
        currentGame?.let { require(it.matchId == context.matchId) }
    }

    val phase: MatchPhase? get() = lifecycle.phaseOrNull()
}

data class TeamLiveState(
    val teamId: TeamId,
    val gold: Int? = null,
    val kills: Int? = null,
    val towers: Int? = null,
    val dragons: Int? = null,
    val barons: Int? = null,
) {
    init {
        require(gold == null || gold >= 0)
        require(kills == null || kills >= 0)
        require(towers == null || towers >= 0)
        require(dragons == null || dragons >= 0)
        require(barons == null || barons >= 0)
    }
}

data class PlayerLiveState(
    val playerId: PlayerId,
    val teamId: TeamId,
    val level: Int? = null,
    val kills: Int? = null,
    val deaths: Int? = null,
    val assists: Int? = null,
    val creepScore: Int? = null,
    val gold: Int? = null,
    val championId: String? = null,
) {
    init {
        require(level == null || level in 1..30)
        require(kills == null || kills >= 0)
        require(deaths == null || deaths >= 0)
        require(assists == null || assists >= 0)
        require(creepScore == null || creepScore >= 0)
        require(gold == null || gold >= 0)
    }
}

data class LiveGameSnapshot(
    val game: GameContext,
    val lifecycle: MatchLifecycleState,
    val elapsedSeconds: Int?,
    val blue: TeamLiveState,
    val red: TeamLiveState,
    val players: List<PlayerLiveState> = emptyList(),
) {
    init {
        require(lifecycle.phaseOrNull() == MatchPhase.LIVE_MATCH) {
            "LiveGameSnapshot may only exist during LIVE_MATCH lifecycle states"
        }
        require(elapsedSeconds == null || elapsedSeconds >= 0)
        require(blue.teamId == game.blueTeamId)
        require(red.teamId == game.redTeamId)
        require(players.all { it.teamId == game.blueTeamId || it.teamId == game.redTeamId })
    }
}
