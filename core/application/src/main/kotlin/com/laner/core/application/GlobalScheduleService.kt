package com.laner.core.application

import com.laner.core.domain.CompetitionCatalogEntry
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.RegionRef
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamOutcome
import com.laner.core.domain.TeamRef
import kotlin.math.abs

enum class ScheduleLoadStatus {
    READY,
    DEGRADED,
    UNAVAILABLE,
}

data class GlobalScheduleSnapshot(
    val catalog: List<CompetitionCatalogEntry>,
    val matches: List<ScheduledSeries>,
    val status: ScheduleLoadStatus,
    val failures: List<DiagnosticFailure>,
    val lastUpdatedEpochMillis: Long,
)

class GlobalScheduleService(
    private val sources: List<GlobalPreMatchSourcePort>,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun load(context: SourceRequestContext): GlobalScheduleSnapshot {
        val successes = mutableListOf<Pair<GlobalPreMatchSourcePort, ProviderPreMatchSnapshot>>()
        val failures = mutableListOf<DiagnosticFailure>()

        sources.forEach { source ->
            when (val result = source.readGlobal(context)) {
                is ProviderRead.Success -> successes += source to result.value
                is ProviderRead.Failure -> {
                    failures += result.failure
                    diagnostics?.emit(
                        DiagnosticEvent(
                            module = "SRC",
                            level = LogLevel.WARN,
                            message = "PRE source failed: ${source.providerId}",
                            context = mapOf("provider" to source.providerId),
                            failure = result.failure,
                        )
                    )
                }
            }
        }

        val catalogCandidates = successes.flatMap { (source, snapshot) ->
            snapshot.competitions.mapNotNull { row ->
                normalizeCompetition(row)?.let { source to it }
            }
        }

        val scheduleCandidates = successes.flatMap { (source, snapshot) ->
            snapshot.matches.mapNotNull { row ->
                normalizeSeries(source, snapshot, row)
            }
        }

        val catalog = mergeCatalog(catalogCandidates, scheduleCandidates)
        val matches = mergeSchedule(scheduleCandidates)
        val status = when {
            successes.isEmpty() -> ScheduleLoadStatus.UNAVAILABLE
            failures.isEmpty() -> ScheduleLoadStatus.READY
            else -> ScheduleLoadStatus.DEGRADED
        }

        diagnostics?.emit(
            DiagnosticEvent(
                module = "PRE",
                level = if (status == ScheduleLoadStatus.UNAVAILABLE) LogLevel.WARN else LogLevel.INFO,
                message = "Global schedule load ${status.name.lowercase()}",
                context = mapOf(
                    "sources_ok" to successes.size.toString(),
                    "sources_failed" to failures.size.toString(),
                    "competitions" to catalog.size.toString(),
                    "matches" to matches.size.toString(),
                ),
            )
        )

        return GlobalScheduleSnapshot(
            catalog = catalog,
            matches = matches,
            status = status,
            failures = failures,
            lastUpdatedEpochMillis = context.nowEpochMillis,
        )
    }

    private fun normalizeCompetition(row: ProviderCompetitionEntry): CompetitionCatalogEntry? {
        val slug = canonicalToken(row.slug.ifBlank { row.name })
        if (slug.isBlank()) return null
        val name = row.name.trim().ifBlank { row.slug.trim() }
        if (name.isBlank()) return null
        val region = regionRef(row.regionCode, row.regionName)
        return CompetitionCatalogEntry(
            competition = CompetitionRef(
                id = CompetitionId("lol:competition:$slug"),
                name = name,
                region = region,
            ),
            slug = slug,
            kind = classifyCompetition(slug, name),
        )
    }

    private fun normalizeSeries(
        source: GlobalPreMatchSourcePort,
        snapshot: ProviderPreMatchSnapshot,
        row: ProviderScheduleEntry,
    ): ScheduledSeries? {
        if (row.teams.size != 2) return null
        val competitionSlug = canonicalToken(row.competitionSlug.ifBlank { row.competitionName })
        if (competitionSlug.isBlank()) return null
        val competitionName = row.competitionName.trim().ifBlank { row.competitionSlug.trim() }
        if (competitionName.isBlank()) return null

        val teams = row.teams.mapNotNull(::normalizeTeam)
        if (teams.size != 2 || teams.map { it.team.id }.distinct().size != 2) return null

        val competition = CompetitionRef(
            id = CompetitionId("lol:competition:$competitionSlug"),
            name = competitionName,
            region = regionRef(row.regionCode, row.regionName),
        )
        val provenance = SourceProvenance(
            providerId = source.providerId,
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = source.authority,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = snapshot.observedAtEpochMillis,
            sourceTimestampEpochMillis = snapshot.sourceTimestampEpochMillis,
            revision = snapshot.revision,
            sourceUri = snapshot.sourceUri,
        )
        val state = verifiedScheduleState(row.rawState, row.bestOf, teams)
        val matchId = canonicalMatchId(
            competitionSlug = competitionSlug,
            startTimeEpochMillis = row.startTimeEpochMillis,
            bestOf = row.bestOf,
            teams = teams,
        )

        return ScheduledSeries(
            matchId = matchId,
            competition = competition,
            competitionSlug = competitionSlug,
            competitionKind = classifyCompetition(competitionSlug, competitionName),
            blockName = row.blockName.trim(),
            startTimeEpochMillis = row.startTimeEpochMillis,
            state = state,
            bestOf = row.bestOf,
            teams = teams,
            provenance = provenance,
        )
    }

    private fun normalizeTeam(row: ProviderTeamEntry): ScheduledTeam? {
        val token = canonicalToken(
            row.slug.ifBlank { row.code.ifBlank { row.name } }
        )
        if (token.isBlank()) return null
        val code = row.code.trim().ifBlank { row.name.trim().take(6).uppercase() }
        val name = row.name.trim().ifBlank { code }
        if (code.isBlank() && name.isBlank()) return null
        return ScheduledTeam(
            team = TeamRef(
                id = TeamId("lol:team:$token"),
                code = code,
                name = name,
            ),
            gameWins = row.gameWins,
            outcome = when (canonicalToken(row.outcome)) {
                "win", "winner" -> TeamOutcome.WIN
                "loss", "lose", "loser" -> TeamOutcome.LOSS
                else -> TeamOutcome.UNKNOWN
            },
        )
    }

    private fun verifiedScheduleState(
        rawState: String,
        bestOf: Int?,
        teams: List<ScheduledTeam>,
    ): ScheduleState {
        val state = canonicalToken(rawState).replace("-", "")
        val claimsCompleted = state.contains("complete") || state == "finished"
        if (claimsCompleted) {
            val requiredWins = bestOf?.let { it / 2 + 1 }
            val scoreProvesCompletion = requiredWins != null &&
                (teams.maxOfOrNull { it.gameWins } ?: 0) >= requiredWins
            val outcomeProvesCompletion = teams.any { it.outcome == TeamOutcome.WIN }
            return if (scoreProvesCompletion || outcomeProvesCompletion) {
                ScheduleState.COMPLETED
            } else {
                // Preserve legacy safeguard: a premature "completed" flag must never finish a series.
                ScheduleState.UPCOMING
            }
        }

        return when (state) {
            "inprogress", "live", "started" -> ScheduleState.EVENT_LIVE
            "unstarted", "upcoming", "scheduled", "pending" -> ScheduleState.UPCOMING
            else -> ScheduleState.UNKNOWN
        }
    }

    private fun mergeCatalog(
        candidates: List<Pair<GlobalPreMatchSourcePort, CompetitionCatalogEntry>>,
        matches: List<ScheduledSeries>,
    ): List<CompetitionCatalogEntry> {
        val chosen = linkedMapOf<CompetitionId, Pair<Int, CompetitionCatalogEntry>>()
        candidates.forEach { (source, entry) ->
            val current = chosen[entry.competition.id]
            if (current == null || source.authority.weight > current.first) {
                chosen[entry.competition.id] = source.authority.weight to entry
            }
        }

        matches.forEach { match ->
            if (match.competition.id !in chosen) {
                chosen[match.competition.id] = match.provenance.authority.weight to CompetitionCatalogEntry(
                    competition = match.competition,
                    slug = match.competitionSlug,
                    kind = match.competitionKind,
                )
            }
        }

        return chosen.values
            .map { it.second }
            .sortedWith(compareBy<CompetitionCatalogEntry> { it.kind.ordinal }.thenBy { it.competition.name.lowercase() })
    }

    private fun mergeSchedule(candidates: List<ScheduledSeries>): List<ScheduledSeries> {
        val output = mutableListOf<ScheduledSeries>()
        candidates.sortedBy { it.startTimeEpochMillis }.forEach { candidate ->
            val index = output.indexOfFirst { existing -> sameSeries(existing, candidate) }
            if (index < 0) {
                output += candidate
            } else if (prefer(candidate, output[index])) {
                output[index] = candidate
            }
        }
        return output.sortedBy { it.startTimeEpochMillis }
    }

    private fun sameSeries(a: ScheduledSeries, b: ScheduledSeries): Boolean {
        if (a.competition.id != b.competition.id) return false
        if (a.bestOf != null && b.bestOf != null && a.bestOf != b.bestOf) return false
        if (abs(a.startTimeEpochMillis - b.startTimeEpochMillis) > SERIES_TIME_TOLERANCE_MILLIS) return false
        return a.teams.map { it.team.id }.toSet() == b.teams.map { it.team.id }.toSet()
    }

    private fun prefer(candidate: ScheduledSeries, current: ScheduledSeries): Boolean {
        val authority = candidate.provenance.authority.weight.compareTo(current.provenance.authority.weight)
        if (authority != 0) return authority > 0

        val candidateTimestamp = candidate.provenance.sourceTimestampEpochMillis
            ?: candidate.provenance.observedAtEpochMillis
        val currentTimestamp = current.provenance.sourceTimestampEpochMillis
            ?: current.provenance.observedAtEpochMillis
        if (candidateTimestamp != currentTimestamp) return candidateTimestamp > currentTimestamp

        if (candidate.provenance.revision != current.provenance.revision) {
            return candidate.provenance.revision > current.provenance.revision
        }

        return scheduleStateRank(candidate.state) > scheduleStateRank(current.state)
    }

    private fun scheduleStateRank(state: ScheduleState): Int = when (state) {
        ScheduleState.COMPLETED -> 4
        ScheduleState.EVENT_LIVE -> 3
        ScheduleState.UPCOMING -> 2
        ScheduleState.UNKNOWN -> 1
    }

    private fun canonicalMatchId(
        competitionSlug: String,
        startTimeEpochMillis: Long,
        bestOf: Int?,
        teams: List<ScheduledTeam>,
    ): MatchId {
        val minute = startTimeEpochMillis / 60_000L
        val teamPart = teams.map { it.team.id.value.substringAfterLast(':') }.sorted().joinToString("-")
        return MatchId("lol:series:$competitionSlug:$minute:$teamPart:bo${bestOf ?: 0}")
    }

    private fun regionRef(code: String?, name: String?): RegionRef? {
        val normalizedCode = code.orEmpty().trim()
        val normalizedName = name.orEmpty().trim()
        if (normalizedCode.isBlank() && normalizedName.isBlank()) return null
        return RegionRef(
            code = normalizedCode.ifBlank { canonicalToken(normalizedName).uppercase() },
            displayName = normalizedName.ifBlank { normalizedCode.uppercase() },
        )
    }

    private fun classifyCompetition(slug: String, name: String): CompetitionKind {
        val token = canonicalToken("$slug-$name")
        return when {
            token.contains("worlds") || token.contains("world-championship") -> CompetitionKind.WORLD_CHAMPIONSHIP
            INTERNATIONAL_MARKERS.any(token::contains) -> CompetitionKind.INTERNATIONAL
            token.isBlank() -> CompetitionKind.OTHER
            else -> CompetitionKind.REGIONAL
        }
    }

    private fun canonicalToken(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private companion object {
        const val SERIES_TIME_TOLERANCE_MILLIS = 90L * 60L * 1000L
        val INTERNATIONAL_MARKERS = listOf(
            "msi",
            "mid-season",
            "first-stand",
            "first-stand-tournament",
            "ewc",
            "esports-world-cup",
            "americas-cup",
            "international",
        )
    }
}
