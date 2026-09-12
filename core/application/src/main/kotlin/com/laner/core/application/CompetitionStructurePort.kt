package com.laner.core.application

import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EditionId
import com.laner.core.domain.QualificationEvidenceKind
import com.laner.core.domain.QualificationMechanism
import com.laner.core.domain.QualificationNodeState
import com.laner.core.domain.QualificationTeamState
import com.laner.core.domain.StandingMetricKind
import com.laner.core.domain.TournamentEdition

data class ProviderEditionEntry(
    val competitionSlug: String,
    val competitionName: String,
    val regionCode: String? = null,
    val regionName: String? = null,
    val tournamentSlug: String,
    val family: String,
    val seasonYear: Int?,
    val displayName: String,
    val stageName: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val participantTeams: List<ProviderTeamIdentity> = emptyList(),
    val scheduleSeriesCount: Int = 0,
) {
    init {
        require(competitionSlug.isNotBlank() || competitionName.isNotBlank())
        require(tournamentSlug.isNotBlank() || displayName.isNotBlank())
        require(family.isNotBlank())
        require(displayName.isNotBlank())
        require(startEpochMillis >= 0)
        require(endEpochMillis >= startEpochMillis)
        require(scheduleSeriesCount >= 0)
    }
}

data class ProviderTeamIdentity(
    val slug: String = "",
    val code: String = "",
    val name: String = "",
) {
    init { require(slug.isNotBlank() || code.isNotBlank() || name.isNotBlank()) }
}

data class ProviderEditionSnapshot(
    val editions: List<ProviderEditionEntry>,
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

interface TournamentEditionSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readEditions(context: SourceRequestContext): ProviderRead<ProviderEditionSnapshot>
}

data class ProviderStandingMetric(
    val kind: StandingMetricKind,
    val value: Int,
    val label: String,
) {
    init {
        require(value >= 0)
        require(label.isNotBlank())
    }
}

data class ProviderStandingRow(
    val ordinal: Int,
    val team: ProviderTeamIdentity,
    val seriesWins: Int,
    val seriesLosses: Int,
    val metrics: List<ProviderStandingMetric> = emptyList(),
) {
    init {
        require(ordinal > 0)
        require(seriesWins >= 0)
        require(seriesLosses >= 0)
    }
}

data class ProviderStandingsSection(
    val stageKey: String = "",
    val stageName: String,
    val sectionName: String,
    val rows: List<ProviderStandingRow>,
) {
    init { require(stageName.isNotBlank() || sectionName.isNotBlank()) }
}

data class ProviderStandingsSnapshot(
    val sections: List<ProviderStandingsSection>,
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

interface TournamentStandingsSourcePort {
    val providerId: String
    val authority: DataAuthority

    /** Adapter resolves its own external tournament key from the canonical edition metadata. */
    suspend fun readStandings(
        edition: TournamentEdition,
        context: SourceRequestContext,
    ): ProviderRead<ProviderStandingsSnapshot>
}

data class ProviderChampionshipPointSplit(
    val label: String,
    val points: Int,
    val isGuaranteedFloor: Boolean = false,
)

data class ProviderChampionshipPointRow(
    val team: ProviderTeamIdentity,
    val totalPoints: Int,
    val splits: List<ProviderChampionshipPointSplit>,
)

data class ProviderChampionshipPointsSnapshot(
    val targetEventName: String,
    val updatedThrough: String,
    val rows: List<ProviderChampionshipPointRow>,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val revision: Long = 0,
    val sourceUri: String? = null,
) {
    init {
        require(targetEventName.isNotBlank())
        require(updatedThrough.isNotBlank())
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
        require(revision >= 0)
    }
}

interface ChampionshipPointsSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readChampionshipPoints(
        competition: CompetitionRef,
        seasonYear: Int,
        context: SourceRequestContext,
    ): ProviderRead<ProviderChampionshipPointsSnapshot>
}

sealed interface ProviderQualificationInput {
    data class ChampionshipPoints(
        val points: Int,
        val updatedThrough: String,
    ) : ProviderQualificationInput

    data class TournamentPlacement(
        val ordinal: Int,
        val stageName: String,
    ) : ProviderQualificationInput

    data class QualifierResult(
        val qualifierName: String,
        val detail: String,
    ) : ProviderQualificationInput

    data class ParticipantOrigin(
        val originLabel: String,
    ) : ProviderQualificationInput
}

data class ProviderQualificationNode(
    val id: String,
    val label: String,
    val detail: String,
    val state: QualificationNodeState,
    val evidence: QualificationEvidenceKind,
    val sourceLabel: String,
)

data class ProviderQualificationRoute(
    val team: ProviderTeamIdentity,
    val targetEventName: String,
    val status: QualificationTeamState,
    val mechanism: QualificationMechanism,
    val evidence: QualificationEvidenceKind,
    val inputs: List<ProviderQualificationInput> = emptyList(),
    val nodes: List<ProviderQualificationNode> = emptyList(),
    val updatedThrough: String? = null,
)

data class ProviderQualificationSnapshot(
    val title: String,
    val targetEventName: String,
    val mechanism: QualificationMechanism,
    val mechanismEvidence: QualificationEvidenceKind,
    val routes: List<ProviderQualificationRoute>,
    val note: String,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val revision: Long = 0,
    val sourceUri: String? = null,
) {
    init {
        require(title.isNotBlank())
        require(targetEventName.isNotBlank())
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
        require(revision >= 0)
    }
}

interface QualificationSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readQualification(
        edition: TournamentEdition,
        context: SourceRequestContext,
    ): ProviderRead<ProviderQualificationSnapshot>
}

/**
 * Persisted archive contains canonical editions only. Provider raw tournament ids stay inside adapters.
 */
interface TournamentEditionArchiveRepository {
    suspend fun load(): List<TournamentEdition>
    suspend fun save(editions: List<TournamentEdition>)
}

data class CompetitionEditionSelection(
    val editionId: EditionId,
)
