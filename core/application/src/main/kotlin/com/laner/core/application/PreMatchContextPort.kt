package com.laner.core.application

import com.laner.core.domain.DataAuthority
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

data class ProviderStartingRosterSnapshot(
    val evidence: List<ProviderStartingRosterEvidence>,
    val observedAtEpochMillis: Long,
    val sourceUri: String? = null,
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
