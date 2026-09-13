package com.riftlab.app.data

/**
 * dev.69 turns every Riot tournament/season into a durable edition entity.
 *
 * The archive is append/merge only: a later API window may enrich an edition, but it never deletes
 * an older edition simply because the current upstream response no longer includes it. Persisted
 * summaries therefore survive process/app restarts and can be enriched again when live providers
 * return matching schedule/standings data.
 */
enum class TournamentEditionSlotState(val label: String) {
    COMPLETE("完整"),
    PARTIAL("部分"),
    PENDING("待同步"),
    SOURCE_ERROR("来源异常")
}

data class TournamentEditionSlot(
    val key: String,
    val label: String,
    val state: TournamentEditionSlotState,
    val detail: String,
    val source: String = ""
)

data class TournamentQualificationArchive(
    val detail: String,
    val source: String,
    val savedAtEpochMs: Long = System.currentTimeMillis()
)

data class TournamentEditionArchiveRecord(
    val tournamentId: String,
    val slug: String,
    val leagueId: String,
    val leagueSlug: String,
    val leagueName: String,
    val family: String,
    val seasonYear: Int?,
    val editionKey: String,
    val displayName: String,
    val stage: String,
    val startDate: String,
    val endDate: String,
    val participantTeamCodes: List<String> = emptyList(),
    val scheduleSeriesCount: Int = 0,
    val patchVersions: List<String> = emptyList(),
    val archivedRules: TournamentRulesSnapshot? = null,
    val archivedDraw: TournamentDrawSnapshot? = null,
    val archivedQualification: TournamentQualificationArchive? = null,
    val archivedSlots: List<TournamentEditionSlot> = emptyList(),
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)

data class TournamentEditionDetail(
    val edition: TournamentEditionArchiveRecord,
    val matchedSeries: List<ScheduledEsportsMatch> = emptyList(),
    val standings: TournamentStandings? = null,
    val governance: TournamentGovernanceSnapshot? = null,
    val research: TournamentResearchSnapshot? = null,
    val slots: List<TournamentEditionSlot> = emptyList(),
    val provenance: List<DataProvenance> = emptyList()
)

data class TournamentEditionArchiveState(
    val editions: List<TournamentEditionArchiveRecord> = emptyList(),
    val selectedTournamentId: String = "",
    val selected: TournamentEditionDetail? = null,
    val followingCurrent: Boolean = true,
    val lastRefreshEpochMs: Long = 0L,
    val statusMessage: String = "年度赛事档案尚未同步"
)
