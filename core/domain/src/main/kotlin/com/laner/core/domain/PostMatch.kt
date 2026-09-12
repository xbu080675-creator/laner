package com.laner.core.domain

enum class SeriesResultState {
    PARTIAL,
    FINAL,
}

data class SeriesResult(
    val matchId: MatchId,
    val leftTeamId: TeamId,
    val rightTeamId: TeamId,
    val leftWins: Int,
    val rightWins: Int,
    val bestOf: Int?,
    val state: SeriesResultState,
    val winnerTeamId: TeamId? = null,
    val provenance: SourceProvenance,
) {
    init {
        require(leftTeamId != rightTeamId)
        require(leftWins >= 0 && rightWins >= 0)
        require(bestOf == null || bestOf > 0)
        require(provenance.sourceClass == SourceClass.POST_MATCH_SOURCE)
        require(winnerTeamId == null || winnerTeamId == leftTeamId || winnerTeamId == rightTeamId)
        if (state == SeriesResultState.FINAL) {
            require(winnerTeamId != null) { "A final series result must identify the winner" }
            require(leftWins != rightWins) { "A final series cannot be tied" }
            require(
                (winnerTeamId == leftTeamId && leftWins > rightWins) ||
                    (winnerTeamId == rightTeamId && rightWins > leftWins)
            ) { "Winner must match the final score" }
        } else {
            require(winnerTeamId == null) { "A partial series result cannot declare a winner" }
        }
    }
}

data class PostTeamStats(
    val teamId: TeamId,
    val kills: Int? = null,
    val gold: Int? = null,
    val towers: Int? = null,
    val dragons: Int? = null,
    val barons: Int? = null,
    val heralds: Int? = null,
    val atakhans: Int? = null,
) {
    init {
        listOf(kills, gold, towers, dragons, barons, heralds, atakhans)
            .filterNotNull()
            .forEach { require(it >= 0) }
    }
}

data class PostPlayerStats(
    val player: PlayerRef,
    val teamId: TeamId,
    val championId: String,
    val kills: Int? = null,
    val deaths: Int? = null,
    val assists: Int? = null,
    val cs: Int? = null,
    val gold: Int? = null,
    val damageToChampions: Int? = null,
    val visionScore: Int? = null,
    val items: List<String> = emptyList(),
    val summonerSpellIds: List<String> = emptyList(),
) {
    init {
        require(championId.isNotBlank())
        require(player.teamId == null || player.teamId == teamId)
        listOf(kills, deaths, assists, cs, gold, damageToChampions, visionScore)
            .filterNotNull()
            .forEach { require(it >= 0) }
        require(items.none { it.isBlank() })
        require(summonerSpellIds.none { it.isBlank() })
    }
}

data class PostDraftSide(
    val teamId: TeamId,
    val picks: List<String>,
    val bans: List<String> = emptyList(),
) {
    init {
        require(picks.none { it.isBlank() })
        require(bans.none { it.isBlank() })
        require(picks.distinct().size == picks.size)
        require(bans.distinct().size == bans.size)
    }
}

data class CompletedGameRecord(
    val matchId: MatchId,
    val gameId: GameId,
    val gameNumber: Int,
    val blueTeamId: TeamId,
    val redTeamId: TeamId,
    val winnerTeamId: TeamId,
    val durationSeconds: Int? = null,
    val blueStats: PostTeamStats,
    val redStats: PostTeamStats,
    val players: List<PostPlayerStats> = emptyList(),
    val draftBlue: PostDraftSide? = null,
    val draftRed: PostDraftSide? = null,
    val provenance: SourceProvenance,
) {
    init {
        require(gameNumber > 0)
        require(blueTeamId != redTeamId)
        require(winnerTeamId == blueTeamId || winnerTeamId == redTeamId)
        require(durationSeconds == null || durationSeconds >= 0)
        require(blueStats.teamId == blueTeamId)
        require(redStats.teamId == redTeamId)
        require(players.all { it.teamId == blueTeamId || it.teamId == redTeamId })
        require(players.map { it.player.id }.distinct().size == players.size)
        require(draftBlue == null || draftBlue.teamId == blueTeamId)
        require(draftRed == null || draftRed.teamId == redTeamId)
        require(provenance.sourceClass == SourceClass.POST_MATCH_SOURCE)
    }
}

enum class AwardKind {
    SERIES_MVP,
    GAME_MVP,
    POG,
    OTHER,
}

data class VerifiedPostAward(
    val matchId: MatchId,
    val gameId: GameId? = null,
    val gameNumber: Int? = null,
    val kind: AwardKind,
    val player: PlayerRef,
    val label: String,
    val provenance: SourceProvenance,
) {
    init {
        require(gameNumber == null || gameNumber > 0)
        require(label.isNotBlank())
        require(provenance.sourceClass == SourceClass.POST_MATCH_SOURCE)
        require(provenance.authority != DataAuthority.DERIVED) {
            "Awards must be explicitly sourced; statistics cannot derive MVP/POG"
        }
    }
}

enum class ReplayProvider {
    RIOT,
    BILIBILI,
    YOUTUBE,
    OTHER,
}

data class ReplayAsset(
    val matchId: MatchId,
    val gameId: GameId? = null,
    val gameNumber: Int? = null,
    val provider: ReplayProvider,
    val locale: String? = null,
    val sourceUrl: String,
    val externalMediaId: String? = null,
    val externalPartId: String? = null,
    val offsetSeconds: Int = 0,
    val title: String? = null,
    val provenance: SourceProvenance,
) {
    init {
        require(gameNumber == null || gameNumber > 0)
        require(sourceUrl.isNotBlank())
        require(externalMediaId == null || externalMediaId.isNotBlank())
        require(externalPartId == null || externalPartId.isNotBlank())
        require(offsetSeconds >= 0)
        require(title == null || title.isNotBlank())
        require(provenance.sourceClass == SourceClass.POST_MATCH_SOURCE)
    }
}

data class PostMatchBundle(
    val matchId: MatchId,
    val result: SeriesResult?,
    val games: List<CompletedGameRecord>,
    val awards: List<VerifiedPostAward>,
    val replays: List<ReplayAsset>,
) {
    init {
        require(result == null || result.matchId == matchId)
        require(games.all { it.matchId == matchId })
        require(awards.all { it.matchId == matchId })
        require(replays.all { it.matchId == matchId })
        require(games.map { it.gameId }.distinct().size == games.size)
        require(games.map { it.gameNumber }.distinct().size == games.size)
    }
}
