package com.laner.core.application

import com.laner.core.domain.ChampionshipPointEntry
import com.laner.core.domain.ChampionshipPointSplit
import com.laner.core.domain.ChampionshipPointsSnapshot
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.EditionId
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.QualificationEvidenceKind
import com.laner.core.domain.QualificationInput
import com.laner.core.domain.QualificationMechanism
import com.laner.core.domain.QualificationRouteNode
import com.laner.core.domain.QualificationSnapshot
import com.laner.core.domain.RegionRef
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.StageId
import com.laner.core.domain.StandingEntry
import com.laner.core.domain.StandingMetric
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamQualificationRoute
import com.laner.core.domain.TeamRef
import com.laner.core.domain.TournamentEdition
import com.laner.core.domain.TournamentEditionSlot
import com.laner.core.domain.TournamentEditionSlotState
import com.laner.core.domain.TournamentStandingsSnapshot

enum class CompetitionStructureStatus {
    READY,
    DEGRADED,
    UNAVAILABLE,
}

data class EditionArchiveSnapshot(
    val editions: List<TournamentEdition>,
    val status: CompetitionStructureStatus,
    val failures: List<DiagnosticFailure>,
    val lastUpdatedEpochMillis: Long,
)

data class EditionKnowledgeSnapshot(
    val edition: TournamentEdition,
    val standings: List<TournamentStandingsSnapshot>,
    val championshipPoints: ChampionshipPointsSnapshot?,
    val qualification: QualificationSnapshot,
    val status: CompetitionStructureStatus,
    val failures: List<DiagnosticFailure>,
    val lastUpdatedEpochMillis: Long,
)

class CompetitionStructureService(
    private val editionSources: List<TournamentEditionSourcePort>,
    private val standingsSources: List<TournamentStandingsSourcePort>,
    private val championshipPointsSources: List<ChampionshipPointsSourcePort>,
    private val qualificationSources: List<QualificationSourcePort>,
    private val archiveRepository: TournamentEditionArchiveRepository,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun refreshEditions(context: SourceRequestContext): EditionArchiveSnapshot {
        val failures = mutableListOf<DiagnosticFailure>()
        val previous = runCatching { archiveRepository.load() }
            .getOrElse { error ->
                val failure = DiagnosticFailure(
                    code = ErrorCode("LNR-STOR-PRE-001"),
                    message = "Tournament Edition archive read failed: ${error.message ?: error::class.java.simpleName}",
                    retryable = true,
                )
                failures += failure
                emitFailure("Edition archive read failed", failure)
                emptyList()
            }

        val incoming = mutableListOf<TournamentEdition>()
        var successfulSources = 0

        editionSources.forEach { source ->
            when (val read = source.readEditions(context)) {
                is ProviderRead.Success -> {
                    successfulSources += 1
                    read.value.editions.forEach { row ->
                        normalizeEdition(source, read.value, row, context)?.let(incoming::add)
                            ?: run {
                                val failure = DiagnosticFailure(
                                    code = ErrorCode("LNR-APP-PRE-010"),
                                    message = "Rejected invalid Tournament Edition from ${source.providerId}",
                                    retryable = false,
                                )
                                failures += failure
                                emitFailure("Edition normalization rejected", failure)
                            }
                    }
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emitFailure("Edition source failed: ${source.providerId}", read.failure)
                }
            }
        }

        val mergedIncoming = mergeEditionArchive(emptyList(), incoming)
        val archived = mergeEditionArchive(previous, mergedIncoming)

        if (incoming.isNotEmpty()) {
            runCatching { archiveRepository.save(archived) }
                .onFailure { error ->
                    val failure = DiagnosticFailure(
                        code = ErrorCode("LNR-STOR-PRE-002"),
                        message = "Tournament Edition archive write failed: ${error.message ?: error::class.java.simpleName}",
                        retryable = true,
                    )
                    failures += failure
                    emitFailure("Edition archive write failed", failure)
                }
        }

        val status = when {
            archived.isEmpty() && successfulSources == 0 -> CompetitionStructureStatus.UNAVAILABLE
            failures.isNotEmpty() || successfulSources < editionSources.size -> CompetitionStructureStatus.DEGRADED
            else -> CompetitionStructureStatus.READY
        }

        diagnostics?.emit(
            DiagnosticEvent(
                module = "PRE",
                level = if (status == CompetitionStructureStatus.UNAVAILABLE) LogLevel.WARN else LogLevel.INFO,
                message = "Tournament Edition archive ${status.name.lowercase()}",
                context = mapOf(
                    "editions" to archived.size.toString(),
                    "sources_ok" to successfulSources.toString(),
                    "failures" to failures.size.toString(),
                ),
            )
        )

        return EditionArchiveSnapshot(
            editions = archived.sortedWith(compareBy<TournamentEdition> { it.seasonYear ?: Int.MIN_VALUE }.thenBy { it.startEpochMillis }),
            status = status,
            failures = failures,
            lastUpdatedEpochMillis = context.nowEpochMillis,
        )
    }

    suspend fun loadEdition(
        editionId: EditionId,
        context: SourceRequestContext,
    ): EditionKnowledgeSnapshot? {
        val archive = runCatching { archiveRepository.load() }.getOrDefault(emptyList())
        val edition = archive.firstOrNull { it.id == editionId }
            ?: refreshEditions(context).editions.firstOrNull { it.id == editionId }
            ?: return null

        val failures = mutableListOf<DiagnosticFailure>()
        val standingsCandidates = mutableListOf<TournamentStandingsSnapshot>()
        standingsSources.forEach { source ->
            when (val read = source.readStandings(edition, context)) {
                is ProviderRead.Success -> {
                    read.value.sections.forEach { section ->
                        normalizeStandings(source, read.value, edition, section)?.let(standingsCandidates::add)
                    }
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emitFailure("Standings source failed: ${source.providerId}", read.failure)
                }
            }
        }
        val standings = mergeStandings(standingsCandidates)

        val championshipPoints = edition.seasonYear?.let { year ->
            val candidates = mutableListOf<ChampionshipPointsSnapshot>()
            championshipPointsSources.forEach { source ->
                when (val read = source.readChampionshipPoints(edition.competition, year, context)) {
                    is ProviderRead.Success -> normalizeChampionshipPoints(source, read.value, edition.competition, year)
                        ?.let(candidates::add)
                    is ProviderRead.Failure -> {
                        failures += read.failure
                        emitFailure("Championship Points source failed: ${source.providerId}", read.failure)
                    }
                }
            }
            candidates.maxWithOrNull(::compareByProvenance)
        }

        val qualificationCandidates = mutableListOf<QualificationSnapshot>()
        qualificationSources.forEach { source ->
            when (val read = source.readQualification(edition, context)) {
                is ProviderRead.Success -> normalizeQualification(source, read.value, edition)?.let(qualificationCandidates::add)
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emitFailure("Qualification source failed: ${source.providerId}", read.failure)
                }
            }
        }
        val qualification = qualificationCandidates.maxWithOrNull(::compareQualification)
            ?: pendingQualification(edition)

        val status = when {
            standings.isEmpty() && championshipPoints == null && qualification.provenance == null && failures.isNotEmpty() ->
                CompetitionStructureStatus.UNAVAILABLE
            failures.isNotEmpty() || standings.isEmpty() -> CompetitionStructureStatus.DEGRADED
            else -> CompetitionStructureStatus.READY
        }

        return EditionKnowledgeSnapshot(
            edition = edition,
            standings = standings,
            championshipPoints = championshipPoints,
            qualification = qualification,
            status = status,
            failures = failures,
            lastUpdatedEpochMillis = context.nowEpochMillis,
        )
    }

    private fun normalizeEdition(
        source: TournamentEditionSourcePort,
        snapshot: ProviderEditionSnapshot,
        row: ProviderEditionEntry,
        context: SourceRequestContext,
    ): TournamentEdition? = runCatching {
        val competitionSlug = canonicalToken(row.competitionSlug.ifBlank { row.competitionName })
        val editionSlug = canonicalToken(row.tournamentSlug.ifBlank { row.displayName })
        if (competitionSlug.isBlank() || editionSlug.isBlank()) return null
        val competition = CompetitionRef(
            id = CompetitionId("lol:competition:$competitionSlug"),
            name = row.competitionName.trim().ifBlank { row.competitionSlug.trim() },
            region = regionRef(row.regionCode, row.regionName),
        )
        val yearToken = row.seasonYear?.toString() ?: "unknown"
        val participantIds = row.participantTeams.mapNotNull(::teamId).toSet()
        val provenance = provenance(
            providerId = source.providerId,
            authority = source.authority,
            freshness = FreshnessClass.DAILY,
            observedAt = snapshot.observedAtEpochMillis,
            sourceTimestamp = snapshot.sourceTimestampEpochMillis,
            revision = snapshot.revision,
            sourceUri = snapshot.sourceUri,
        )
        TournamentEdition(
            id = EditionId("lol:edition:$competitionSlug:$yearToken:$editionSlug"),
            competition = competition,
            slug = editionSlug,
            family = row.family.trim(),
            seasonYear = row.seasonYear,
            displayName = row.displayName.trim(),
            stageName = row.stageName.trim(),
            startEpochMillis = row.startEpochMillis,
            endEpochMillis = row.endEpochMillis,
            participantTeamIds = participantIds,
            scheduleSeriesCount = row.scheduleSeriesCount,
            firstSeenEpochMillis = minOf(context.nowEpochMillis, snapshot.observedAtEpochMillis),
            lastSeenEpochMillis = maxOf(context.nowEpochMillis, snapshot.observedAtEpochMillis),
            provenance = provenance,
        )
    }.getOrNull()

    private fun normalizeStandings(
        source: TournamentStandingsSourcePort,
        snapshot: ProviderStandingsSnapshot,
        edition: TournamentEdition,
        section: ProviderStandingsSection,
    ): TournamentStandingsSnapshot? = runCatching {
        val entries = section.rows.mapNotNull { row ->
            val team = teamRef(row.team) ?: return@mapNotNull null
            StandingEntry(
                ordinal = row.ordinal,
                team = team,
                seriesWins = row.seriesWins,
                seriesLosses = row.seriesLosses,
                metrics = row.metrics.map { metric ->
                    StandingMetric(metric.kind, metric.value, metric.label)
                },
            )
        }
        if (entries.isEmpty()) return null
        val stageToken = canonicalToken(section.stageKey.ifBlank { section.stageName })
        TournamentStandingsSnapshot(
            editionId = edition.id,
            competition = edition.competition,
            stageId = stageToken.takeIf { it.isNotBlank() }?.let {
                StageId("lol:stage:${edition.id.value.substringAfter("lol:edition:")}:$it")
            },
            stageName = section.stageName.trim(),
            sectionName = section.sectionName.trim(),
            entries = entries,
            provenance = provenance(
                providerId = source.providerId,
                authority = source.authority,
                freshness = FreshnessClass.HOURLY,
                observedAt = snapshot.observedAtEpochMillis,
                sourceTimestamp = snapshot.sourceTimestampEpochMillis,
                revision = snapshot.revision,
                sourceUri = snapshot.sourceUri,
            ),
        )
    }.getOrNull()

    private fun normalizeChampionshipPoints(
        source: ChampionshipPointsSourcePort,
        snapshot: ProviderChampionshipPointsSnapshot,
        competition: CompetitionRef,
        seasonYear: Int,
    ): ChampionshipPointsSnapshot? = runCatching {
        val entries = snapshot.rows.mapNotNull { row ->
            val team = teamRef(row.team) ?: return@mapNotNull null
            ChampionshipPointEntry(
                team = team,
                totalPoints = row.totalPoints,
                splits = row.splits.map { split ->
                    ChampionshipPointSplit(
                        label = split.label,
                        points = split.points,
                        isGuaranteedFloor = split.isGuaranteedFloor,
                    )
                },
            )
        }
        if (entries.isEmpty()) return null
        ChampionshipPointsSnapshot(
            competition = competition,
            seasonYear = seasonYear,
            targetEventName = snapshot.targetEventName,
            updatedThrough = snapshot.updatedThrough,
            entries = entries,
            provenance = provenance(
                providerId = source.providerId,
                authority = source.authority,
                freshness = FreshnessClass.DAILY,
                observedAt = snapshot.observedAtEpochMillis,
                sourceTimestamp = snapshot.sourceTimestampEpochMillis,
                revision = snapshot.revision,
                sourceUri = snapshot.sourceUri,
            ),
        )
    }.getOrNull()

    private fun normalizeQualification(
        source: QualificationSourcePort,
        snapshot: ProviderQualificationSnapshot,
        edition: TournamentEdition,
    ): QualificationSnapshot? = runCatching {
        val sharedProvenance = provenance(
            providerId = source.providerId,
            authority = source.authority,
            freshness = FreshnessClass.DAILY,
            observedAt = snapshot.observedAtEpochMillis,
            sourceTimestamp = snapshot.sourceTimestampEpochMillis,
            revision = snapshot.revision,
            sourceUri = snapshot.sourceUri,
        )
        val routes = snapshot.routes.mapNotNull { row ->
            val team = teamRef(row.team) ?: return@mapNotNull null
            val inputs = row.inputs.map { input ->
                when (input) {
                    is ProviderQualificationInput.ChampionshipPoints -> QualificationInput.ChampionshipPoints(
                        points = input.points,
                        updatedThrough = input.updatedThrough,
                    )
                    is ProviderQualificationInput.TournamentPlacement -> QualificationInput.TournamentPlacement(
                        editionId = edition.id,
                        ordinal = input.ordinal,
                        stageName = input.stageName,
                    )
                    is ProviderQualificationInput.QualifierResult -> QualificationInput.QualifierResult(
                        qualifierName = input.qualifierName,
                        detail = input.detail,
                    )
                    is ProviderQualificationInput.ParticipantOrigin -> QualificationInput.ParticipantOrigin(
                        originLabel = input.originLabel,
                    )
                }
            }
            val nodes = row.nodes.map { node ->
                QualificationRouteNode(
                    id = node.id,
                    label = node.label,
                    detail = node.detail,
                    state = node.state,
                    evidence = node.evidence,
                    sourceLabel = node.sourceLabel,
                )
            }
            TeamQualificationRoute(
                team = team,
                targetEventName = row.targetEventName,
                status = row.status,
                mechanism = row.mechanism,
                evidence = row.evidence,
                inputs = inputs,
                nodes = nodes,
                updatedThrough = row.updatedThrough,
                provenance = sharedProvenance,
            )
        }
        QualificationSnapshot(
            editionId = edition.id,
            title = snapshot.title,
            targetEventName = snapshot.targetEventName,
            mechanism = snapshot.mechanism,
            mechanismEvidence = snapshot.mechanismEvidence,
            routes = routes,
            note = snapshot.note,
            provenance = sharedProvenance,
        )
    }.getOrNull()

    private fun pendingQualification(edition: TournamentEdition): QualificationSnapshot = QualificationSnapshot(
        editionId = edition.id,
        title = "${edition.displayName} · 资格体系",
        targetEventName = "资格目标待确认",
        mechanism = QualificationMechanism.UNKNOWN,
        mechanismEvidence = QualificationEvidenceKind.PENDING,
        routes = emptyList(),
        note = "当前没有可信来源确认该届采用 Championship Points、名次直通、资格赛或其他机制；Laner 不根据 Standings 自行猜测。",
        provenance = null,
    )

    private fun mergeStandings(candidates: List<TournamentStandingsSnapshot>): List<TournamentStandingsSnapshot> {
        val chosen = linkedMapOf<String, TournamentStandingsSnapshot>()
        candidates.forEach { candidate ->
            val key = listOf(
                candidate.editionId.value,
                canonicalToken(candidate.stageName),
                canonicalToken(candidate.sectionName),
            ).joinToString("|")
            val current = chosen[key]
            if (current == null || prefer(candidate.provenance, current.provenance)) {
                chosen[key] = candidate
            }
        }
        return chosen.values.sortedWith(
            compareBy<TournamentStandingsSnapshot> { it.stageName.lowercase() }
                .thenBy { it.sectionName.lowercase() }
        )
    }

    private fun compareByProvenance(a: ChampionshipPointsSnapshot, b: ChampionshipPointsSnapshot): Int =
        compareProvenance(a.provenance, b.provenance)

    private fun compareQualification(a: QualificationSnapshot, b: QualificationSnapshot): Int {
        val ap = a.provenance
        val bp = b.provenance
        if (ap == null && bp == null) return 0
        if (ap == null) return -1
        if (bp == null) return 1
        return compareProvenance(ap, bp)
    }

    private fun provenance(
        providerId: String,
        authority: DataAuthority,
        freshness: FreshnessClass,
        observedAt: Long,
        sourceTimestamp: Long?,
        revision: Long,
        sourceUri: String?,
    ): SourceProvenance = SourceProvenance(
        providerId = providerId,
        sourceClass = SourceClass.PRE_MATCH_SOURCE,
        authority = authority,
        freshnessClass = freshness,
        observedAtEpochMillis = observedAt,
        sourceTimestampEpochMillis = sourceTimestamp,
        revision = revision,
        sourceUri = sourceUri,
    )

    private fun teamId(row: ProviderTeamIdentity): TeamId? {
        val token = canonicalToken(row.slug.ifBlank { row.code.ifBlank { row.name } })
        return token.takeIf { it.isNotBlank() }?.let { TeamId("lol:team:$it") }
    }

    private fun teamRef(row: ProviderTeamIdentity): TeamRef? {
        val id = teamId(row) ?: return null
        val code = row.code.trim().ifBlank { row.name.trim().take(8).uppercase() }
        val name = row.name.trim().ifBlank { code }
        if (code.isBlank() && name.isBlank()) return null
        return TeamRef(id = id, code = code, name = name)
    }

    private fun regionRef(code: String?, name: String?): RegionRef? {
        val normalizedCode = code.orEmpty().trim()
        val normalizedName = name.orEmpty().trim()
        if (normalizedCode.isBlank() && normalizedName.isBlank()) return null
        return RegionRef(
            code = normalizedCode.ifBlank { canonicalToken(normalizedName).uppercase().ifBlank { "GLOBAL" } },
            displayName = normalizedName.ifBlank { normalizedCode.uppercase().ifBlank { "Global" } },
        )
    }

    private fun emitFailure(message: String, failure: DiagnosticFailure) {
        diagnostics?.emit(
            DiagnosticEvent(
                module = "PRE",
                level = LogLevel.WARN,
                message = message,
                failure = failure,
            )
        )
    }

    private fun canonicalToken(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

internal fun mergeEditionArchive(
    previous: List<TournamentEdition>,
    incoming: List<TournamentEdition>,
): List<TournamentEdition> {
    val merged = previous.associateBy { it.id }.toMutableMap()
    incoming.forEach { candidate ->
        val current = merged[candidate.id]
        merged[candidate.id] = if (current == null) candidate else mergeEdition(current, candidate)
    }
    return merged.values.sortedWith(
        compareBy<TournamentEdition> { it.seasonYear ?: Int.MIN_VALUE }
            .thenBy { it.startEpochMillis }
            .thenBy { it.displayName.lowercase() }
    )
}

private fun mergeEdition(current: TournamentEdition, candidate: TournamentEdition): TournamentEdition {
    val preferred = if (prefer(candidate.provenance, current.provenance)) candidate else current
    return preferred.copy(
        participantTeamIds = current.participantTeamIds + candidate.participantTeamIds,
        scheduleSeriesCount = maxOf(current.scheduleSeriesCount, candidate.scheduleSeriesCount),
        slots = mergeSlots(current.slots, candidate.slots),
        firstSeenEpochMillis = minOf(current.firstSeenEpochMillis, candidate.firstSeenEpochMillis),
        lastSeenEpochMillis = maxOf(current.lastSeenEpochMillis, candidate.lastSeenEpochMillis),
        provenance = if (prefer(candidate.provenance, current.provenance)) candidate.provenance else current.provenance,
    )
}

private fun mergeSlots(
    previous: List<TournamentEditionSlot>,
    incoming: List<TournamentEditionSlot>,
): List<TournamentEditionSlot> {
    val merged = previous.associateBy { it.key }.toMutableMap()
    incoming.forEach { candidate ->
        val current = merged[candidate.key]
        if (current == null || slotRank(candidate.state) >= slotRank(current.state)) {
            merged[candidate.key] = candidate
        }
    }
    return merged.values.sortedBy { it.key }
}

private fun slotRank(state: TournamentEditionSlotState): Int = when (state) {
    TournamentEditionSlotState.COMPLETE -> 4
    TournamentEditionSlotState.PARTIAL -> 3
    TournamentEditionSlotState.PENDING -> 2
    TournamentEditionSlotState.SOURCE_ERROR -> 1
}

private fun prefer(candidate: SourceProvenance, current: SourceProvenance): Boolean =
    compareProvenance(candidate, current) > 0

private fun compareProvenance(a: SourceProvenance, b: SourceProvenance): Int {
    val authority = a.authority.weight.compareTo(b.authority.weight)
    if (authority != 0) return authority
    val at = a.sourceTimestampEpochMillis ?: a.observedAtEpochMillis
    val bt = b.sourceTimestampEpochMillis ?: b.observedAtEpochMillis
    if (at != bt) return at.compareTo(bt)
    return a.revision.compareTo(b.revision)
}
