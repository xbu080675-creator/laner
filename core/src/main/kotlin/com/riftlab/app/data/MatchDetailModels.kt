package com.riftlab.app.data

/** Stable identity shared by schedule, post-match and MVP/vote/BP providers. */
data class MatchDetailKey(
    val riotEventId: String,
    val riotMatchId: String,
    val startTimeIso: String,
    val teamAId: String,
    val teamBId: String
) {
    val stableId: String
        get() = riotEventId.ifBlank { riotMatchId }.ifBlank {
            listOf(teamAId, teamBId, startTimeIso).joinToString("|")
        }

    companion object {
        fun from(match: ScheduledEsportsMatch): MatchDetailKey = MatchDetailKey(
            riotEventId = match.eventId,
            riotMatchId = match.matchId,
            startTimeIso = match.startTimeIso,
            teamAId = match.teams.getOrNull(0)?.id.orEmpty(),
            teamBId = match.teams.getOrNull(1)?.id.orEmpty()
        )
    }
}

data class OfficialMvpRecord(
    val game: Int?,
    val playerName: String,
    val team: String,
    val role: String = "",
    val source: String
)

data class VoteOptionRecord(
    val label: String,
    val votes: Long,
    val percent: Double? = null
)

data class OfficialVoteRecord(
    val title: String,
    val options: List<VoteOptionRecord>,
    val totalVotes: Long? = null,
    val source: String
)

data class DraftPickRecord(
    val game: Int,
    val blueBans: List<String> = emptyList(),
    val redBans: List<String> = emptyList(),
    val bluePicks: List<String> = emptyList(),
    val redPicks: List<String> = emptyList(),
    val source: String
)

data class MatchDetailState(
    val key: MatchDetailKey? = null,
    val match: ScheduledEsportsMatch? = null,
    val loading: Boolean = false,
    val series: CompletedSeriesSnapshot? = null,
    val liveGame: LiveSnapshot? = null,
    val seriesMvp: OfficialMvpRecord? = null,
    val gameMvps: List<OfficialMvpRecord> = emptyList(),
    val votes: List<OfficialVoteRecord> = emptyList(),
    val drafts: List<DraftPickRecord> = emptyList(),
    val status: String = "选择一场比赛查看详情",
    val errorMessage: String? = null,
    val updatedAtEpochMs: Long = 0L
)
