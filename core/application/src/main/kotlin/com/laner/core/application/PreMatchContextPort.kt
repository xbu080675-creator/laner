package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.TeamRef

data class ProviderRosterPlayer(
    val externalId: String,
    val handle: String,
    val role: String,
) {
    init { require(handle.isNotBlank()) }
}

data class ProviderRosterPoolSnapshot(
    val players: List<ProviderRosterPlayer>,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val sourceUri: String? = null,
) {
    init {
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
    }
}

interface TeamRosterSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readTeam(
        team: TeamRef,
        context: SourceRequestContext,
    ): ProviderRead<ProviderRosterPoolSnapshot>
}

data class ProviderStaffEntry(
    val displayName: String,
    val role: String,
    val realName: String? = null,
    val sourceLabel: String,
) {
    init {
        require(displayName.isNotBlank())
        require(role.isNotBlank())
        require(sourceLabel.isNotBlank())
    }
}

data class ProviderStaffSnapshot(
    val management: List<ProviderStaffEntry>,
    val coachingStaff: List<ProviderStaffEntry>,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val sourceUri: String? = null,
) {
    init {
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
    }
}

interface TeamStaffSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readTeam(
        team: TeamRef,
        context: SourceRequestContext,
    ): ProviderRead<ProviderStaffSnapshot>
}

data class ProviderStartingPlayer(
    val externalId: String,
    val handle: String,
    val role: String,
) {
    init { require(handle.isNotBlank()) }
}

data class ProviderStartingRosterEvidence(
    val matchDateLocal: String,
    val timezoneId: String,
    val league: String,
    val team: String,
    val opponent: String,
    val starters: List<ProviderStartingPlayer>,
    val sourceCategory: String,
    val evidenceType: String,
    val platform: String,
    val account: String,
    val publishedAtEpochMillis: Long,
    val observedAtEpochMillis: Long,
    val confidence: Float,
    val sourceUri: String? = null,
) {
    init {
        require(team.isNotBlank())
        require(opponent.isNotBlank())
        require(timezoneId.isNotBlank())
        require(platform.isNotBlank())
        require(account.isNotBlank())
        require(publishedAtEpochMillis >= 0)
        require(observedAtEpochMillis >= 0)
        require(confidence in 0f..1f)
    }
}

/**
 * Official/social announcement discovered by the normalized collector but not necessarily parsed.
 * It is discovery metadata only and MUST NOT become a Starting Roster fact by itself.
 */
data class ProviderStartingRosterAnnouncement(
    val id: String,
    val league: String,
    val team: String,
    val platform: String,
    val account: String,
    val sourceCategory: String,
    val observedAtEpochMillis: Long,
    val publishedAtEpochMillis: Long? = null,
    val sourceUri: String? = null,
    val imageUrls: List<String> = emptyList(),
    val textSnippet: String = "",
    val parseStatus: String = "UNPARSED",
    val candidateBasis: String = "",
    val candidateTeams: List<String> = emptyList(),
    val candidateScore: Int = 0,
) {
    init {
        require(id.isNotBlank())
        require(team.isNotBlank())
        require(platform.isNotBlank())
        require(account.isNotBlank())
        require(observedAtEpochMillis >= 0)
        require(publishedAtEpochMillis == null || publishedAtEpochMillis >= 0)
        require(candidateScore >= 0)
    }
}

data class ProviderStartingRosterSnapshot(
    val evidence: List<ProviderStartingRosterEvidence>,
    val observedAtEpochMillis: Long,
    val sourceUri: String? = null,
    val announcements: List<ProviderStartingRosterAnnouncement> = emptyList(),
    val diagnostics: List<DiagnosticFailure> = emptyList(),
) {
    init { require(observedAtEpochMillis >= 0) }
}

interface StartingRosterSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun read(
        context: SourceRequestContext,
    ): ProviderRead<ProviderStartingRosterSnapshot>
}

/** Android/network/image recognition lives behind this port. Application remains the final verifier. */
interface StartingRosterAssistPort {
    val providerId: String
    val authority: DataAuthority

    suspend fun assist(
        match: ScheduledSeries,
        announcements: List<ProviderStartingRosterAnnouncement>,
        context: SourceRequestContext,
    ): ProviderRead<ProviderStartingRosterSnapshot>
}
