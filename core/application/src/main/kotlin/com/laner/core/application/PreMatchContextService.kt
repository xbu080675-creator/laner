package com.laner.core.application

import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.OfficialStartingRoster
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerRef
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.RecentSeries
import com.laner.core.domain.RosterEvidenceSource
import com.laner.core.domain.RosterEvidenceType
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.SeriesOutcome
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.StaffMember
import com.laner.core.domain.StaffRole
import com.laner.core.domain.StartingRosterResolution
import com.laner.core.domain.TeamPreMatchContext
import com.laner.core.domain.TeamRef
import com.laner.core.domain.TeamRosterPool
import com.laner.core.domain.TeamStaffSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val RECENT_SERIES_LIMIT = 5

enum class PreMatchContextStatus {
    READY,
    DEGRADED,
}

data class MatchPreContextSnapshot(
    val match: ScheduledSeries,
    val left: TeamPreMatchContext,
    val right: TeamPreMatchContext,
    val leftRecentSeries: List<RecentSeries>,
    val rightRecentSeries: List<RecentSeries>,
    val recentHeadToHeadFromLeftPerspective: List<RecentSeries>,
    val status: PreMatchContextStatus,
    val failures: List<DiagnosticFailure>,
    val loadedAtEpochMillis: Long,
)

class PreMatchContextService(
    private val rosterSources: List<TeamRosterSourcePort> = emptyList(),
    private val staffSources: List<TeamStaffSourcePort> = emptyList(),
    private val startingRosterSources: List<StartingRosterSourcePort> = emptyList(),
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun load(
        match: ScheduledSeries,
        scheduleHistory: List<ScheduledSeries>,
        context: SourceRequestContext,
    ): MatchPreContextSnapshot {
        val leftTeam = match.teams[0].team
        val rightTeam = match.teams[1].team
        val failures = mutableListOf<DiagnosticFailure>()

        val leftRoster = readRosterPool(leftTeam, context, failures)
        val rightRoster = readRosterPool(rightTeam, context, failures)
        val leftStaff = readStaff(leftTeam, context, failures)
        val rightStaff = readStaff(rightTeam, context, failures)
        val rosterEvidence = readStartingRosterEvidence(context, failures)

        val leftStarting = resolveStartingRoster(
            match = match,
            team = leftTeam,
            opponent = rightTeam,
            candidates = rosterEvidence,
        )
        val rightStarting = resolveStartingRoster(
            match = match,
            team = rightTeam,
            opponent = leftTeam,
            candidates = rosterEvidence,
        )

        val snapshot = MatchPreContextSnapshot(
            match = match,
            left = TeamPreMatchContext(
                team = leftTeam,
                rosterPool = leftRoster,
                startingRoster = leftStarting,
                staff = leftStaff,
            ),
            right = TeamPreMatchContext(
                team = rightTeam,
                rosterPool = rightRoster,
                startingRoster = rightStarting,
                staff = rightStaff,
            ),
            leftRecentSeries = deriveRecentSeries(
                perspective = leftTeam,
                currentMatch = match,
                scheduleHistory = scheduleHistory,
            ),
            rightRecentSeries = deriveRecentSeries(
                perspective = rightTeam,
                currentMatch = match,
                scheduleHistory = scheduleHistory,
            ),
            recentHeadToHeadFromLeftPerspective = deriveHeadToHead(
                left = leftTeam,
                right = rightTeam,
                currentMatch = match,
                scheduleHistory = scheduleHistory,
            ),
            status = if (failures.isEmpty()) PreMatchContextStatus.READY else PreMatchContextStatus.DEGRADED,
            failures = failures.toList(),
            loadedAtEpochMillis = context.nowEpochMillis,
        )

        diagnostics?.emit(
            DiagnosticEvent(
                module = "PRE",
                level = if (failures.isEmpty()) LogLevel.INFO else LogLevel.WARN,
                message = "Pre-match context ${snapshot.status.name.lowercase()}",
                context = mapOf(
                    "match" to match.matchId.value,
                    "left_roster" to (leftRoster?.members?.size ?: 0).toString(),
                    "right_roster" to (rightRoster?.members?.size ?: 0).toString(),
                    "left_starting" to leftStarting::class.simpleName.orEmpty(),
                    "right_starting" to rightStarting::class.simpleName.orEmpty(),
                    "failures" to failures.size.toString(),
                ),
            )
        )
        return snapshot
    }

    private suspend fun readRosterPool(
        team: TeamRef,
        context: SourceRequestContext,
        failures: MutableList<DiagnosticFailure>,
    ): TeamRosterPool? {
        val candidates = mutableListOf<RosterPoolCandidate>()
        rosterSources.forEach { source ->
            when (val read = source.readTeam(team, context)) {
                is ProviderRead.Success -> normalizeRosterPool(source, team, read.value)?.let(candidates::add)
                is ProviderRead.Failure -> failures += read.failure
            }
        }
        return candidates.maxWithOrNull(
            compareBy<RosterPoolCandidate> { it.authority.weight }
                .thenBy { it.sourceTimestampEpochMillis ?: it.observedAtEpochMillis }
        )?.value
    }

    private fun normalizeRosterPool(
        source: TeamRosterSourcePort,
        team: TeamRef,
        snapshot: ProviderRosterPoolSnapshot,
    ): RosterPoolCandidate? {
        val members = snapshot.players.mapNotNull { raw ->
            val role = normalizePlayerRole(raw.role) ?: PlayerRole.UNKNOWN
            val handle = raw.handle.trim()
            if (handle.isBlank()) return@mapNotNull null
            PlayerRef(
                id = PlayerId(playerId(team, raw.externalId, handle)),
                handle = handle,
                teamId = team.id,
                role = role,
            )
        }.distinctBy { it.id }
        if (members.isEmpty()) return null

        val provenance = SourceProvenance(
            providerId = source.providerId,
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = source.authority,
            freshnessClass = FreshnessClass.DAILY,
            observedAtEpochMillis = snapshot.observedAtEpochMillis,
            sourceTimestampEpochMillis = snapshot.sourceTimestampEpochMillis,
            sourceUri = snapshot.sourceUri,
        )
        return RosterPoolCandidate(
            authority = source.authority,
            observedAtEpochMillis = snapshot.observedAtEpochMillis,
            sourceTimestampEpochMillis = snapshot.sourceTimestampEpochMillis,
            value = TeamRosterPool(team, members, provenance),
        )
    }

    private suspend fun readStaff(
        team: TeamRef,
        context: SourceRequestContext,
        failures: MutableList<DiagnosticFailure>,
    ): TeamStaffSnapshot? {
        val candidates = mutableListOf<StaffCandidate>()
        staffSources.forEach { source ->
            when (val read = source.readTeam(team, context)) {
                is ProviderRead.Success -> normalizeStaff(source, team, read.value)?.let(candidates::add)
                is ProviderRead.Failure -> failures += read.failure
            }
        }
        return candidates.maxWithOrNull(
            compareBy<StaffCandidate> { it.authority.weight }
                .thenBy { it.sourceTimestampEpochMillis ?: it.observedAtEpochMillis }
        )?.value
    }

    private fun normalizeStaff(
        source: TeamStaffSourcePort,
        team: TeamRef,
        snapshot: ProviderStaffSnapshot,
    ): StaffCandidate? {
        val management = snapshot.management.mapNotNull(::normalizeStaffMember).distinctBy(::staffIdentity)
        val coaching = snapshot.coachingStaff.mapNotNull(::normalizeStaffMember).distinctBy(::staffIdentity)
        if (management.isEmpty() && coaching.isEmpty()) return null

        val provenance = SourceProvenance(
            providerId = source.providerId,
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = source.authority,
            freshnessClass = FreshnessClass.DAILY,
            observedAtEpochMillis = snapshot.observedAtEpochMillis,
            sourceTimestampEpochMillis = snapshot.sourceTimestampEpochMillis,
            sourceUri = snapshot.sourceUri,
        )
        return StaffCandidate(
            authority = source.authority,
            observedAtEpochMillis = snapshot.observedAtEpochMillis,
            sourceTimestampEpochMillis = snapshot.sourceTimestampEpochMillis,
            value = TeamStaffSnapshot(
                team = team,
                management = management,
                coachingStaff = coaching,
                provenance = provenance,
            ),
        )
    }

    private fun normalizeStaffMember(raw: ProviderStaffEntry): StaffMember? {
        val name = raw.displayName.trim()
        if (name.isBlank()) return null
        return StaffMember(
            displayName = name,
            role = normalizeStaffRole(raw.role),
            realName = raw.realName?.trim()?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
            sourceLabel = raw.sourceLabel,
        )
    }

    private suspend fun readStartingRosterEvidence(
        context: SourceRequestContext,
        failures: MutableList<DiagnosticFailure>,
    ): List<StartingEvidenceCandidate> {
        val output = mutableListOf<StartingEvidenceCandidate>()
        startingRosterSources.forEach { source ->
            when (val read = source.read(context)) {
                is ProviderRead.Success -> read.value.evidence.forEach { raw ->
                    output += StartingEvidenceCandidate(source, raw)
                }
                is ProviderRead.Failure -> failures += read.failure
            }
        }
        return output
    }

    private fun resolveStartingRoster(
        match: ScheduledSeries,
        team: TeamRef,
        opponent: TeamRef,
        candidates: List<StartingEvidenceCandidate>,
    ): StartingRosterResolution {
        val valid = candidates.mapNotNull { candidate ->
            normalizeStartingRoster(match, team, opponent, candidate)
        }
        if (valid.isEmpty()) return StartingRosterResolution.Unknown

        val highestAuthority = valid.maxOf { it.provenance.authority.weight }
        val authoritative = valid.filter { it.provenance.authority.weight == highestAuthority }
        val byLineup = authoritative.groupBy(::lineupKey)

        if (byLineup.size > 1) {
            return StartingRosterResolution.Conflict(
                candidates = authoritative
                    .map { it.roster }
                    .distinctBy(::lineupKey),
            )
        }

        val sameLineup = byLineup.values.single()
        val newest = sameLineup.maxWithOrNull(
            compareBy<ResolvedStartingRoster> { it.roster.publishedAtEpochMillis }
                .thenBy { it.roster.observedAtEpochMillis }
        ) ?: return StartingRosterResolution.Unknown
        val crossConfirmed = sameLineup.size >= 2 || sameLineup.any {
            it.roster.evidenceType == RosterEvidenceType.CROSS_CONFIRMED
        }
        return StartingRosterResolution.Confirmed(
            roster = newest.roster,
            crossConfirmed = crossConfirmed,
            evidenceCount = sameLineup.size,
        )
    }

    private fun normalizeStartingRoster(
        match: ScheduledSeries,
        team: TeamRef,
        opponent: TeamRef,
        candidate: StartingEvidenceCandidate,
    ): ResolvedStartingRoster? {
        val raw = candidate.raw
        val zoneId = runCatching { ZoneId.of(raw.timezoneId) }.getOrNull() ?: return null
        val matchDate = DATE_FORMATTER.format(Instant.ofEpochMilli(match.startTimeEpochMillis).atZone(zoneId))
        if (raw.matchDateLocal != matchDate) return null
        if (!matchesIdentity(raw.team, team)) return null
        if (!matchesIdentity(raw.opponent, opponent)) return null
        if (raw.league.isNotBlank() && !matchesCompetition(raw.league, match.competition, match.competitionSlug)) return null

        val starters = raw.starters.mapNotNull { starter ->
            val role = normalizePlayerRole(starter.role) ?: return@mapNotNull null
            if (role !in CORE_ROLES) return@mapNotNull null
            val handle = starter.handle.trim()
            if (handle.isBlank()) return@mapNotNull null
            PlayerRef(
                id = PlayerId(playerId(team, starter.externalId, handle)),
                handle = handle,
                teamId = team.id,
                role = role,
            )
        }
        if (starters.size != 5) return null
        if (starters.mapNotNull { it.role }.toSet() != CORE_ROLES) return null
        if (starters.distinctBy { it.id }.size != 5) return null

        val sourceCategory = normalizeEvidenceSource(raw.sourceCategory) ?: return null
        val evidenceType = normalizeEvidenceType(raw.evidenceType) ?: return null
        val provenance = SourceProvenance(
            providerId = candidate.source.providerId,
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = candidate.source.authority,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = raw.observedAtEpochMillis,
            sourceTimestampEpochMillis = raw.publishedAtEpochMillis,
            sourceUri = raw.sourceUri,
        )
        val roster = runCatching {
            OfficialStartingRoster(
                team = team,
                opponent = opponent,
                matchDateLocal = raw.matchDateLocal,
                timezoneId = raw.timezoneId,
                starters = starters.sortedBy { roleOrder(requireNotNull(it.role)) },
                evidenceSource = sourceCategory,
                evidenceType = evidenceType,
                platform = raw.platform,
                account = raw.account,
                publishedAtEpochMillis = raw.publishedAtEpochMillis,
                observedAtEpochMillis = raw.observedAtEpochMillis,
                confidence = raw.confidence,
                sourceUri = raw.sourceUri,
                provenance = provenance,
            )
        }.getOrNull() ?: return null
        return ResolvedStartingRoster(roster, provenance)
    }

    private fun deriveRecentSeries(
        perspective: TeamRef,
        currentMatch: ScheduledSeries,
        scheduleHistory: List<ScheduledSeries>,
    ): List<RecentSeries> = scheduleHistory.asSequence()
        .filter { it.state == ScheduleState.COMPLETED }
        .filter { it.matchId != currentMatch.matchId }
        .filter { series -> series.teams.any { sameTeam(it.team, perspective) } }
        .sortedByDescending { it.startTimeEpochMillis }
        .mapNotNull { toRecentSeries(it, perspective) }
        .take(RECENT_SERIES_LIMIT)
        .toList()

    private fun deriveHeadToHead(
        left: TeamRef,
        right: TeamRef,
        currentMatch: ScheduledSeries,
        scheduleHistory: List<ScheduledSeries>,
    ): List<RecentSeries> = scheduleHistory.asSequence()
        .filter { it.state == ScheduleState.COMPLETED }
        .filter { it.matchId != currentMatch.matchId }
        .filter { series ->
            series.teams.any { sameTeam(it.team, left) } &&
                series.teams.any { sameTeam(it.team, right) }
        }
        .sortedByDescending { it.startTimeEpochMillis }
        .mapNotNull { toRecentSeries(it, left) }
        .take(RECENT_SERIES_LIMIT)
        .toList()

    private fun toRecentSeries(
        series: ScheduledSeries,
        perspective: TeamRef,
    ): RecentSeries? {
        val self = series.teams.firstOrNull { sameTeam(it.team, perspective) } ?: return null
        val opponent = series.teams.firstOrNull { !sameTeam(it.team, perspective) } ?: return null
        val outcome = when {
            self.gameWins > opponent.gameWins -> SeriesOutcome.WIN
            self.gameWins < opponent.gameWins -> SeriesOutcome.LOSS
            else -> SeriesOutcome.UNKNOWN
        }
        return RecentSeries(
            matchId = series.matchId,
            competition = series.competition,
            perspectiveTeam = perspective,
            opponent = opponent.team,
            scoreFor = self.gameWins,
            scoreAgainst = opponent.gameWins,
            outcome = outcome,
            startTimeEpochMillis = series.startTimeEpochMillis,
            provenance = series.provenance,
        )
    }

    private fun sameTeam(a: TeamRef, b: TeamRef): Boolean =
        a.id == b.id || aliases(a).intersect(aliases(b)).isNotEmpty()

    private fun matchesIdentity(raw: String, team: TeamRef): Boolean {
        val token = identityToken(raw)
        return token.isNotBlank() && token in aliases(team)
    }

    private fun aliases(team: TeamRef): Set<String> = buildSet {
        add(identityToken(team.id.value.substringAfterLast(':')))
        add(identityToken(team.code))
        add(identityToken(team.name))
    }.filter { it.isNotBlank() }.toSet()

    private fun matchesCompetition(raw: String, competition: CompetitionRef, slug: String): Boolean {
        val token = identityToken(raw)
        return token == identityToken(slug) || token == identityToken(competition.name)
    }

    private fun identityToken(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9\\p{L}\\p{N}]+"), "")

    private fun playerId(team: TeamRef, externalId: String, handle: String): String {
        val token = identityToken(externalId.ifBlank { handle }).lowercase()
        return "${team.id.value}:player:${token.ifBlank { "unknown" }}"
    }

    private fun normalizePlayerRole(raw: String): PlayerRole? = when (identityToken(raw)) {
        "TOP", "上单" -> PlayerRole.TOP
        "JUG", "JUNGLE", "JGL", "打野" -> PlayerRole.JUNGLE
        "MID", "中单" -> PlayerRole.MID
        "BOT", "ADC", "BOTTOM", "AD", "下路" -> PlayerRole.BOT
        "SUP", "SUPPORT", "辅助" -> PlayerRole.SUPPORT
        "SUB", "SUBSTITUTE", "替补" -> PlayerRole.SUBSTITUTE
        "UNKNOWN" -> PlayerRole.UNKNOWN
        else -> null
    }

    private fun normalizeEvidenceSource(raw: String): RosterEvidenceSource? = when (identityToken(raw)) {
        "TEAMSOCIAL", "TEAMWEIBO" -> RosterEvidenceSource.TEAM_SOCIAL
        "LEAGUESOCIAL", "LPLWEIBO" -> RosterEvidenceSource.LEAGUE_SOCIAL
        "OFFICIALSITE" -> RosterEvidenceSource.OFFICIAL_SITE
        "OTHEROFFICIAL" -> RosterEvidenceSource.OTHER_OFFICIAL
        else -> null
    }

    private fun normalizeEvidenceType(raw: String): RosterEvidenceType? = when (identityToken(raw)) {
        "TEXT" -> RosterEvidenceType.TEXT
        "IMAGEOCR" -> RosterEvidenceType.IMAGE_OCR
        "CROSSCONFIRMED" -> RosterEvidenceType.CROSS_CONFIRMED
        else -> null
    }

    private fun normalizeStaffRole(raw: String): StaffRole {
        val key = identityToken(raw)
        return when {
            key.contains("HEADCOACH") -> StaffRole.HEAD_COACH
            key.contains("ASSISTANTCOACH") || key.contains("ASSISTCOACH") -> StaffRole.ASSISTANT_COACH
            key.contains("STRATEGICCOACH") || key.contains("STRATEGYCOACH") -> StaffRole.STRATEGIC_COACH
            key.contains("POSITIONALCOACH") -> StaffRole.POSITIONAL_COACH
            key.contains("COACH") -> StaffRole.COACH
            key.contains("ANALYST") -> StaffRole.ANALYST
            key.contains("GENERALMANAGER") -> StaffRole.GENERAL_MANAGER
            key.contains("ASSISTANTMANAGER") -> StaffRole.ASSISTANT_MANAGER
            key.contains("MANAGER") -> StaffRole.MANAGER
            key == "LEADER" || key.contains("TEAMLEADER") -> StaffRole.LEADER
            key.contains("SUPERVISOR") -> StaffRole.SUPERVISOR
            key.contains("ESPORTSDIRECTOR") -> StaffRole.ESPORTS_DIRECTOR
            key.contains("MANAGINGDIRECTOR") -> StaffRole.MANAGING_DIRECTOR
            key == "DIRECTOR" || key.endsWith("DIRECTOR") -> StaffRole.DIRECTOR
            key == "CEO" || key.contains("CHIEFEXECUTIVEOFFICER") -> StaffRole.CEO
            key == "COO" || key.contains("CHIEFOPERATINGOFFICER") -> StaffRole.COO
            key.contains("COOWNER") -> StaffRole.CO_OWNER
            key == "OWNER" -> StaffRole.OWNER
            key.contains("FOUNDER") && key.contains("CEO") -> StaffRole.FOUNDER_AND_CEO
            key.contains("FOUNDER") -> StaffRole.FOUNDER
            key.contains("HEADOFESPORTS") -> StaffRole.HEAD_OF_ESPORTS
            key.contains("HEADOFLOL") || key.contains("HEADOFLEAGUEOFLEGENDS") -> StaffRole.HEAD_OF_LOL
            else -> StaffRole.OTHER
        }
    }

    private fun staffIdentity(member: StaffMember): String =
        "${identityToken(member.displayName)}|${member.role.name}"

    private fun lineupKey(roster: OfficialStartingRoster): String =
        roster.starters.sortedBy { roleOrder(requireNotNull(it.role)) }
            .joinToString("|") { "${it.role}:${identityToken(it.handle)}" }

    private fun lineupKey(candidate: ResolvedStartingRoster): String = lineupKey(candidate.roster)

    private fun roleOrder(role: PlayerRole): Int = when (role) {
        PlayerRole.TOP -> 0
        PlayerRole.JUNGLE -> 1
        PlayerRole.MID -> 2
        PlayerRole.BOT -> 3
        PlayerRole.SUPPORT -> 4
        PlayerRole.SUBSTITUTE -> 5
        PlayerRole.UNKNOWN -> 6
    }

    private data class RosterPoolCandidate(
        val authority: DataAuthority,
        val observedAtEpochMillis: Long,
        val sourceTimestampEpochMillis: Long?,
        val value: TeamRosterPool,
    )

    private data class StaffCandidate(
        val authority: DataAuthority,
        val observedAtEpochMillis: Long,
        val sourceTimestampEpochMillis: Long?,
        val value: TeamStaffSnapshot,
    )

    private data class StartingEvidenceCandidate(
        val source: StartingRosterSourcePort,
        val raw: ProviderStartingRosterEvidence,
    )

    private data class ResolvedStartingRoster(
        val roster: OfficialStartingRoster,
        val provenance: SourceProvenance,
    )

    private companion object {
        val CORE_ROLES = setOf(
            PlayerRole.TOP,
            PlayerRole.JUNGLE,
            PlayerRole.MID,
            PlayerRole.BOT,
            PlayerRole.SUPPORT,
        )
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
