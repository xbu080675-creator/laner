package com.laner.core.domain

enum class CompetitionKind {
    REGIONAL,
    INTERNATIONAL,
    WORLD_CHAMPIONSHIP,
    OTHER,
}

/**
 * Schedule visibility state only. It MUST NOT be treated as proof that an in-game session exists.
 */
enum class ScheduleState {
    UPCOMING,
    EVENT_LIVE,
    COMPLETED,
    UNKNOWN,
}

enum class TeamOutcome {
    WIN,
    LOSS,
    UNKNOWN,
}

data class CompetitionCatalogEntry(
    val competition: CompetitionRef,
    val slug: String,
    val kind: CompetitionKind,
) {
    init {
        require(slug.isNotBlank())
    }
}

data class ScheduledTeam(
    val team: TeamRef,
    val gameWins: Int = 0,
    val outcome: TeamOutcome = TeamOutcome.UNKNOWN,
) {
    init {
        require(gameWins >= 0)
    }
}

data class ScheduledSeries(
    val matchId: MatchId,
    val competition: CompetitionRef,
    val competitionSlug: String,
    val competitionKind: CompetitionKind,
    val blockName: String,
    val startTimeEpochMillis: Long,
    val state: ScheduleState,
    val bestOf: Int?,
    val teams: List<ScheduledTeam>,
    val provenance: SourceProvenance,
) {
    init {
        require(competitionSlug.isNotBlank())
        require(startTimeEpochMillis >= 0)
        require(bestOf == null || bestOf > 0)
        require(teams.size == 2) { "A scheduled series must contain exactly two active teams" }
        require(teams.map { it.team.id }.distinct().size == 2) { "A team cannot play against itself" }
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE) {
            "Schedule facts must come from PRE_MATCH_SOURCE"
        }
    }
}
