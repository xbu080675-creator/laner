package com.laner.core.domain

private val CORE_STARTING_ROLES = setOf(
    PlayerRole.TOP,
    PlayerRole.JUNGLE,
    PlayerRole.MID,
    PlayerRole.BOT,
    PlayerRole.SUPPORT,
)

enum class RosterEvidenceSource {
    TEAM_SOCIAL,
    LEAGUE_SOCIAL,
    OFFICIAL_SITE,
    OTHER_OFFICIAL,
}

enum class RosterEvidenceType {
    TEXT,
    IMAGE_OCR,
    CROSS_CONFIRMED,
}

enum class StaffRole {
    HEAD_COACH,
    ASSISTANT_COACH,
    STRATEGIC_COACH,
    POSITIONAL_COACH,
    COACH,
    ANALYST,
    GENERAL_MANAGER,
    ASSISTANT_MANAGER,
    MANAGER,
    LEADER,
    SUPERVISOR,
    ESPORTS_DIRECTOR,
    DIRECTOR,
    MANAGING_DIRECTOR,
    CEO,
    COO,
    OWNER,
    CO_OWNER,
    FOUNDER,
    FOUNDER_AND_CEO,
    HEAD_OF_ESPORTS,
    HEAD_OF_LOL,
    OTHER,
}

enum class SeriesOutcome {
    WIN,
    LOSS,
    UNKNOWN,
}

data class TeamRosterPool(
    val team: TeamRef,
    val members: List<PlayerRef>,
    val provenance: SourceProvenance,
) {
    init {
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE)
        require(members.all { it.teamId == null || it.teamId == team.id })
        require(members.distinctBy { it.id }.size == members.size) { "Roster pool contains duplicate player IDs" }
    }
}

data class OfficialStartingRoster(
    val team: TeamRef,
    val opponent: TeamRef,
    val matchDateLocal: String,
    val timezoneId: String,
    val starters: List<PlayerRef>,
    val evidenceSource: RosterEvidenceSource,
    val evidenceType: RosterEvidenceType,
    val platform: String,
    val account: String,
    val publishedAtEpochMillis: Long,
    val observedAtEpochMillis: Long,
    val confidence: Float,
    val sourceUri: String?,
    val provenance: SourceProvenance,
) {
    init {
        require(team.id != opponent.id)
        require(matchDateLocal.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")))
        require(timezoneId.isNotBlank())
        require(starters.size == 5) { "Official starting roster must contain exactly five players" }
        require(starters.mapNotNull { it.role }.toSet() == CORE_STARTING_ROLES) {
            "Official starting roster must contain TOP/JUNGLE/MID/BOT/SUPPORT exactly once"
        }
        require(starters.distinctBy { it.id }.size == 5) { "A player cannot occupy multiple starting roles" }
        require(starters.all { it.teamId == null || it.teamId == team.id })
        require(platform.isNotBlank())
        require(account.isNotBlank())
        require(publishedAtEpochMillis >= 0)
        require(observedAtEpochMillis >= 0)
        require(confidence in 0f..1f)
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE)
    }
}

sealed interface StartingRosterResolution {
    data object Unknown : StartingRosterResolution

    data class Confirmed(
        val roster: OfficialStartingRoster,
        val crossConfirmed: Boolean,
        val evidenceCount: Int,
    ) : StartingRosterResolution {
        init { require(evidenceCount >= 1) }
    }

    data class Conflict(
        val candidates: List<OfficialStartingRoster>,
    ) : StartingRosterResolution {
        init { require(candidates.size >= 2) }
    }
}

data class StaffMember(
    val displayName: String,
    val role: StaffRole,
    val realName: String? = null,
    val sourceLabel: String,
) {
    init {
        require(displayName.isNotBlank())
        require(sourceLabel.isNotBlank())
    }
}

data class TeamStaffSnapshot(
    val team: TeamRef,
    val management: List<StaffMember>,
    val coachingStaff: List<StaffMember>,
    val provenance: SourceProvenance,
) {
    init {
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE)
    }
}

data class RecentSeries(
    val matchId: MatchId,
    val competition: CompetitionRef,
    val perspectiveTeam: TeamRef,
    val opponent: TeamRef,
    val scoreFor: Int,
    val scoreAgainst: Int,
    val outcome: SeriesOutcome,
    val startTimeEpochMillis: Long,
    val provenance: SourceProvenance,
) {
    init {
        require(perspectiveTeam.id != opponent.id)
        require(scoreFor >= 0)
        require(scoreAgainst >= 0)
        require(startTimeEpochMillis >= 0)
    }
}

data class TeamPreMatchContext(
    val team: TeamRef,
    val rosterPool: TeamRosterPool?,
    val startingRoster: StartingRosterResolution,
    val staff: TeamStaffSnapshot?,
)
