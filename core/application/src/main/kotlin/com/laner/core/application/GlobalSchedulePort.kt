package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure

data class ProviderCompetitionEntry(
    val externalId: String,
    val slug: String,
    val name: String,
    val regionCode: String? = null,
    val regionName: String? = null,
) {
    init {
        require(slug.isNotBlank() || name.isNotBlank())
    }
}

data class ProviderTeamEntry(
    val externalId: String,
    val slug: String,
    val code: String,
    val name: String,
    val gameWins: Int = 0,
    val outcome: String = "",
) {
    init {
        require(slug.isNotBlank() || code.isNotBlank() || name.isNotBlank())
        require(gameWins >= 0)
    }
}

data class ProviderScheduleEntry(
    val externalEventId: String,
    val externalMatchId: String,
    val competitionExternalId: String,
    val competitionSlug: String,
    val competitionName: String,
    val regionCode: String? = null,
    val regionName: String? = null,
    val blockName: String = "",
    val startTimeEpochMillis: Long,
    val rawState: String,
    val bestOf: Int?,
    val teams: List<ProviderTeamEntry>,
) {
    init {
        require(competitionSlug.isNotBlank() || competitionName.isNotBlank())
        require(startTimeEpochMillis >= 0)
        require(bestOf == null || bestOf > 0)
        require(teams.size == 2)
    }
}

data class ProviderPreMatchSnapshot(
    val competitions: List<ProviderCompetitionEntry>,
    val matches: List<ProviderScheduleEntry>,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val revision: Long = 0,
    val sourceUri: String? = null,
) {
    init {
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
        require(revision >= 0)
    }
}

sealed interface ProviderRead<out T> {
    data class Success<T>(val value: T) : ProviderRead<T>
    data class Failure(val failure: DiagnosticFailure) : ProviderRead<Nothing>
}

/**
 * PRE_MATCH source boundary for global competition catalogue + schedule.
 * Implementations live in adapters; Application owns normalization, identity and arbitration.
 */
interface GlobalPreMatchSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot>
}
