package com.laner.core.domain

/**
 * A metric reported inside one tournament/stage standings table.
 *
 * This is deliberately not Championship Points. Even when a provider calls a stage field "points",
 * the value remains scoped to this standings table unless an independent qualification source says
 * it is annual Championship Points.
 */
enum class StandingMetricKind {
    SERIES_WINS,
    GAME_WINS,
    STAGE_POINTS,
    PROVIDER_SCORE,
    UNKNOWN,
}

data class StandingMetric(
    val kind: StandingMetricKind,
    val value: Int,
    val label: String,
) {
    init {
        require(value >= 0)
        require(label.isNotBlank())
    }
}

data class StandingEntry(
    val ordinal: Int,
    val team: TeamRef,
    val seriesWins: Int,
    val seriesLosses: Int,
    val metrics: List<StandingMetric> = emptyList(),
) {
    init {
        require(ordinal > 0)
        require(seriesWins >= 0)
        require(seriesLosses >= 0)
        require(metrics.map { it.kind to it.label }.distinct().size == metrics.size) {
            "A standings row cannot publish the same metric twice"
        }
    }
}

data class TournamentStandingsSnapshot(
    val editionId: EditionId,
    val competition: CompetitionRef,
    val stageId: StageId?,
    val stageName: String,
    val sectionName: String,
    val entries: List<StandingEntry>,
    val provenance: SourceProvenance,
) {
    init {
        require(stageName.isNotBlank() || sectionName.isNotBlank())
        require(entries.map { it.team.id }.distinct().size == entries.size) {
            "A team may appear only once in one standings section"
        }
        require(entries.map { it.ordinal }.distinct().size == entries.size) {
            "Standings ordinals must be unique inside one section"
        }
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE) {
            "Standings facts must come from PRE_MATCH_SOURCE"
        }
    }
}

data class ChampionshipPointSplit(
    val label: String,
    val points: Int,
    val isGuaranteedFloor: Boolean = false,
) {
    init {
        require(label.isNotBlank())
        require(points >= 0)
    }
}

data class ChampionshipPointEntry(
    val team: TeamRef,
    val totalPoints: Int,
    val splits: List<ChampionshipPointSplit>,
) {
    init {
        require(totalPoints >= 0)
        require(splits.map { it.label }.distinct().size == splits.size) {
            "Championship point split labels must be unique"
        }
        require(totalPoints >= splits.sumOf { it.points }) {
            "Total Championship Points cannot be lower than the published split sum"
        }
    }
}

data class ChampionshipPointsSnapshot(
    val competition: CompetitionRef,
    val seasonYear: Int,
    val targetEventName: String,
    val updatedThrough: String,
    val entries: List<ChampionshipPointEntry>,
    val provenance: SourceProvenance,
) {
    init {
        require(seasonYear in 2010..2200)
        require(targetEventName.isNotBlank())
        require(updatedThrough.isNotBlank())
        require(entries.map { it.team.id }.distinct().size == entries.size) {
            "A team may appear only once in one Championship Points snapshot"
        }
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE) {
            "Championship Points must come from PRE_MATCH_SOURCE"
        }
    }
}

enum class QualificationTeamState {
    LOCKED,
    CONTENDING,
    ELIMINATED,
    PENDING,
}

enum class QualificationEvidenceKind {
    OFFICIAL,
    PROVIDER,
    DERIVED,
    PENDING,
}

enum class QualificationNodeState {
    CONFIRMED,
    AVAILABLE,
    BLOCKED,
    PENDING,
}

enum class QualificationMechanism {
    CHAMPIONSHIP_POINTS,
    DIRECT_PLACEMENT,
    REGIONAL_QUALIFIER,
    PARTICIPANT_ORIGIN,
    MIXED,
    UNKNOWN,
}

/**
 * Typed inputs stop a tournament standing from silently becoming annual Championship Points.
 */
sealed interface QualificationInput {
    data class ChampionshipPoints(
        val points: Int,
        val updatedThrough: String,
    ) : QualificationInput {
        init {
            require(points >= 0)
            require(updatedThrough.isNotBlank())
        }
    }

    data class TournamentPlacement(
        val editionId: EditionId,
        val ordinal: Int,
        val stageName: String,
    ) : QualificationInput {
        init {
            require(ordinal > 0)
            require(stageName.isNotBlank())
        }
    }

    data class QualifierResult(
        val qualifierName: String,
        val detail: String,
    ) : QualificationInput {
        init {
            require(qualifierName.isNotBlank())
            require(detail.isNotBlank())
        }
    }

    data class ParticipantOrigin(
        val originLabel: String,
    ) : QualificationInput {
        init { require(originLabel.isNotBlank()) }
    }
}

data class QualificationRouteNode(
    val id: String,
    val label: String,
    val detail: String,
    val state: QualificationNodeState,
    val evidence: QualificationEvidenceKind,
    val sourceLabel: String,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(detail.isNotBlank())
        if (evidence != QualificationEvidenceKind.PENDING) {
            require(sourceLabel.isNotBlank())
        }
    }
}

data class TeamQualificationRoute(
    val team: TeamRef,
    val targetEventName: String,
    val status: QualificationTeamState,
    val mechanism: QualificationMechanism,
    val evidence: QualificationEvidenceKind,
    val inputs: List<QualificationInput> = emptyList(),
    val nodes: List<QualificationRouteNode> = emptyList(),
    val updatedThrough: String? = null,
    val provenance: SourceProvenance? = null,
) {
    init {
        require(targetEventName.isNotBlank())
        require(nodes.map { it.id }.distinct().size == nodes.size) {
            "Qualification route node ids must be unique"
        }
        if (evidence != QualificationEvidenceKind.PENDING) {
            require(provenance != null) { "Non-pending qualification evidence requires provenance" }
        }
        if (provenance != null) {
            require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE) {
                "Qualification facts must come from PRE_MATCH_SOURCE"
            }
        }
        if (mechanism == QualificationMechanism.UNKNOWN) {
            require(inputs.isEmpty()) {
                "UNKNOWN qualification mechanism cannot publish typed qualification inputs"
            }
        }
        if (mechanism == QualificationMechanism.PARTICIPANT_ORIGIN) {
            require(inputs.none { it is QualificationInput.ChampionshipPoints }) {
                "Participant-origin qualification must not invent Championship Points"
            }
        }
    }
}

data class QualificationSnapshot(
    val editionId: EditionId,
    val title: String,
    val targetEventName: String,
    val mechanism: QualificationMechanism,
    val mechanismEvidence: QualificationEvidenceKind,
    val routes: List<TeamQualificationRoute>,
    val note: String,
    val provenance: SourceProvenance?,
) {
    init {
        require(title.isNotBlank())
        require(targetEventName.isNotBlank())
        require(routes.map { it.team.id }.distinct().size == routes.size)
        if (mechanismEvidence != QualificationEvidenceKind.PENDING) {
            require(provenance != null) { "Verified qualification mechanism requires provenance" }
        }
        if (mechanism == QualificationMechanism.UNKNOWN) {
            require(routes.all { it.mechanism == QualificationMechanism.UNKNOWN }) {
                "UNKNOWN mechanism snapshot cannot contain routes that assert another mechanism"
            }
        }
    }
}

enum class TournamentEditionSlotState {
    COMPLETE,
    PARTIAL,
    PENDING,
    SOURCE_ERROR,
}

data class TournamentEditionSlot(
    val key: String,
    val label: String,
    val state: TournamentEditionSlotState,
    val detail: String,
    val sourceLabel: String? = null,
) {
    init {
        require(key.isNotBlank())
        require(label.isNotBlank())
        require(detail.isNotBlank())
    }
}

data class TournamentEdition(
    val id: EditionId,
    val competition: CompetitionRef,
    val externalTournamentId: String,
    val slug: String,
    val family: String,
    val seasonYear: Int?,
    val displayName: String,
    val stageName: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val participantTeamIds: Set<TeamId> = emptySet(),
    val scheduleSeriesCount: Int = 0,
    val slots: List<TournamentEditionSlot> = emptyList(),
    val firstSeenEpochMillis: Long,
    val lastSeenEpochMillis: Long,
    val provenance: SourceProvenance,
) {
    init {
        require(externalTournamentId.isNotBlank())
        require(slug.isNotBlank())
        require(family.isNotBlank())
        require(displayName.isNotBlank())
        require(startEpochMillis >= 0)
        require(endEpochMillis >= startEpochMillis)
        require(scheduleSeriesCount >= 0)
        require(firstSeenEpochMillis >= 0)
        require(lastSeenEpochMillis >= firstSeenEpochMillis)
        require(slots.map { it.key }.distinct().size == slots.size)
        require(provenance.sourceClass == SourceClass.PRE_MATCH_SOURCE) {
            "Tournament Edition facts must come from PRE_MATCH_SOURCE"
        }
    }
}
