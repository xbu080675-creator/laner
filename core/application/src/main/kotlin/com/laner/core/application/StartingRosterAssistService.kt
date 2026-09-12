package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import java.time.Duration

private const val ANNOUNCEMENT_LOOKBACK_MS = 40L * 60L * 60L * 1000L
private const val ANNOUNCEMENT_AFTER_START_MS = 4L * 60L * 60L * 1000L

enum class RosterAssistStage {
    NO_TARGET,
    NO_ANNOUNCEMENT,
    ANNOUNCEMENT_DISCOVERED,
    OCR_FAILED,
    OCR_PARTIAL,
    OCR_COMPLETE_UNVERIFIED,
    NORMALIZED_EVIDENCE_AVAILABLE,
}

data class RosterVisionInspection(
    val announcementId: String,
    val imageUrl: String,
    val engines: List<String>,
    val lineCount: Int,
    val roleCandidates: Map<String, List<String>>,
    val leftRoleCandidates: Map<String, List<String>> = emptyMap(),
    val rightRoleCandidates: Map<String, List<String>> = emptyMap(),
    val layoutMode: String = "TEXT_ONLY",
    val textPreview: String = "",
    val error: String? = null,
) {
    val complete: Boolean
        get() = isComplete(roleCandidates) || isComplete(leftRoleCandidates) || isComplete(rightRoleCandidates)

    private fun isComplete(map: Map<String, List<String>>): Boolean {
        val roles = listOf("TOP", "JUG", "MID", "BOT", "SUP")
        val ids = roles.mapNotNull { map[it]?.singleOrNull()?.lowercase() }
        return ids.size == roles.size && ids.distinct().size == roles.size
    }
}

interface StartingRosterVisionPort {
    val providerId: String
    val authority: DataAuthority

    suspend fun inspect(
        match: ScheduledSeries,
        announcement: ProviderStartingRosterAnnouncement,
        context: SourceRequestContext,
    ): ProviderRead<List<RosterVisionInspection>>
}

data class StartingRosterAssistSnapshot(
    val match: ScheduledSeries?,
    val stage: RosterAssistStage,
    val normalizedEvidenceCount: Int,
    val announcements: List<ProviderStartingRosterAnnouncement>,
    val inspections: List<RosterVisionInspection>,
    val failures: List<DiagnosticFailure>,
    val loadedAtEpochMillis: Long,
)

/**
 * Diagnostic/assist orchestration only. OCR output never becomes OfficialStartingRoster here.
 * Normalized official evidence remains subject to PreMatchContextService validation.
 */
class StartingRosterAssistService(
    private val normalizedSource: StartingRosterSourcePort,
    private val visionSources: List<StartingRosterVisionPort> = emptyList(),
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun inspect(
        schedule: List<ScheduledSeries>,
        context: SourceRequestContext,
    ): StartingRosterAssistSnapshot {
        val failures = mutableListOf<DiagnosticFailure>()
        val sourceSnapshot = when (val read = normalizedSource.read(context)) {
            is ProviderRead.Success -> read.value
            is ProviderRead.Failure -> {
                failures += read.failure
                return StartingRosterAssistSnapshot(
                    match = focusMatch(schedule, context.nowEpochMillis),
                    stage = RosterAssistStage.NO_ANNOUNCEMENT,
                    normalizedEvidenceCount = 0,
                    announcements = emptyList(),
                    inspections = emptyList(),
                    failures = failures,
                    loadedAtEpochMillis = context.nowEpochMillis,
                )
            }
        }

        val target = chooseTarget(schedule, sourceSnapshot.announcements, context.nowEpochMillis)
            ?: focusMatch(schedule, context.nowEpochMillis)
        if (target == null) {
            return StartingRosterAssistSnapshot(
                match = null,
                stage = RosterAssistStage.NO_TARGET,
                normalizedEvidenceCount = 0,
                announcements = emptyList(),
                inspections = emptyList(),
                failures = failures,
                loadedAtEpochMillis = context.nowEpochMillis,
            )
        }

        val matchedEvidence = sourceSnapshot.evidence.filter { evidenceMatches(it, target) }
        val matchedAnnouncements = sourceSnapshot.announcements
            .filter { announcementMatches(it, target) }
            .sortedByDescending { it.publishedAtEpochMillis ?: it.observedAtEpochMillis }
            .take(4)

        if (matchedEvidence.isNotEmpty()) {
            return finish(
                target = target,
                stage = RosterAssistStage.NORMALIZED_EVIDENCE_AVAILABLE,
                evidenceCount = matchedEvidence.size,
                announcements = matchedAnnouncements,
                inspections = emptyList(),
                failures = failures,
                now = context.nowEpochMillis,
            )
        }

        if (matchedAnnouncements.isEmpty()) {
            return finish(
                target = target,
                stage = RosterAssistStage.NO_ANNOUNCEMENT,
                evidenceCount = 0,
                announcements = emptyList(),
                inspections = emptyList(),
                failures = failures,
                now = context.nowEpochMillis,
            )
        }

        if (visionSources.isEmpty()) {
            failures += diagnostic(
                "LNR-SRC-PRE-010",
                "Official roster announcement discovered; local OCR adapter unavailable",
                false,
                target,
                matchedAnnouncements.first(),
            )
            return finish(target, RosterAssistStage.ANNOUNCEMENT_DISCOVERED, 0, matchedAnnouncements, emptyList(), failures, context.nowEpochMillis)
        }

        val inspections = mutableListOf<RosterVisionInspection>()
        matchedAnnouncements.take(2).forEach { announcement ->
            if (announcement.imageUrls.isEmpty()) {
                failures += diagnostic(
                    "LNR-SRC-PRE-011",
                    "Official roster announcement has no OCR-capable image",
                    false,
                    target,
                    announcement,
                )
                return@forEach
            }
            visionSources.forEach { source ->
                when (val read = source.inspect(target, announcement, context)) {
                    is ProviderRead.Success -> inspections += read.value
                    is ProviderRead.Failure -> failures += read.failure
                }
            }
        }

        val stage = when {
            inspections.any { it.complete } -> RosterAssistStage.OCR_COMPLETE_UNVERIFIED
            inspections.any { it.error == null && it.roleCandidates.values.any(List<String>::isNotEmpty) } ||
                inspections.any { it.error == null && (it.leftRoleCandidates.values.any(List<String>::isNotEmpty) || it.rightRoleCandidates.values.any(List<String>::isNotEmpty)) } ->
                RosterAssistStage.OCR_PARTIAL
            inspections.isNotEmpty() && inspections.all { it.error != null } -> RosterAssistStage.OCR_FAILED
            failures.any { it.code.value == "LNR-SRC-PRE-013" } -> RosterAssistStage.OCR_FAILED
            else -> RosterAssistStage.ANNOUNCEMENT_DISCOVERED
        }

        if (stage == RosterAssistStage.OCR_PARTIAL) {
            failures += diagnostic(
                "LNR-SRC-PRE-011",
                "Official roster image OCR is incomplete or ambiguous; no lineup fact was promoted",
                true,
                target,
                matchedAnnouncements.first(),
            )
        } else if (stage == RosterAssistStage.OCR_COMPLETE_UNVERIFIED) {
            failures += diagnostic(
                "LNR-SRC-PRE-012",
                "OCR found five-role candidates; waiting for normalized matchup/date evidence before confirmation",
                true,
                target,
                matchedAnnouncements.first(),
            )
        }

        return finish(target, stage, 0, matchedAnnouncements, inspections, failures, context.nowEpochMillis)
    }

    private fun finish(
        target: ScheduledSeries,
        stage: RosterAssistStage,
        evidenceCount: Int,
        announcements: List<ProviderStartingRosterAnnouncement>,
        inspections: List<RosterVisionInspection>,
        failures: List<DiagnosticFailure>,
        now: Long,
    ): StartingRosterAssistSnapshot {
        diagnostics?.emit(
            DiagnosticEvent(
                module = "PRE",
                level = if (stage == RosterAssistStage.NORMALIZED_EVIDENCE_AVAILABLE) LogLevel.INFO else LogLevel.WARN,
                message = "Starting roster assist ${stage.name.lowercase()}",
                context = mapOf(
                    "match" to target.matchId.value,
                    "announcements" to announcements.size.toString(),
                    "ocr_inspections" to inspections.size.toString(),
                    "normalized_evidence" to evidenceCount.toString(),
                    "failures" to failures.size.toString(),
                ),
            )
        )
        return StartingRosterAssistSnapshot(target, stage, evidenceCount, announcements, inspections, failures, now)
    }

    internal fun chooseTarget(
        schedule: List<ScheduledSeries>,
        announcements: List<ProviderStartingRosterAnnouncement>,
        now: Long,
    ): ScheduledSeries? = schedule
        .asSequence()
        .filter { it.state != ScheduleState.COMPLETED }
        .map { match -> match to announcements.count { announcementMatches(it, match) } }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<ScheduledSeries, Int>> { it.second }.thenBy { kotlin.math.abs(it.first.startTimeEpochMillis - now) })
        .firstOrNull()?.first

    internal fun announcementMatches(
        announcement: ProviderStartingRosterAnnouncement,
        match: ScheduledSeries,
    ): Boolean {
        val teamTokens = match.teams.flatMap { aliases(it.team.code, it.team.name, it.team.id.value) }.toSet()
        val announcementTokens = aliases(announcement.team) + announcement.candidateTeams.flatMap(::aliases)
        if (announcementTokens.none { it in teamTokens }) return false
        if (announcement.league.isNotBlank()) {
            val leagueToken = token(announcement.league)
            val matchLeagueTokens = aliases(match.competitionSlug, match.competition.name, match.competition.id.value)
            if (leagueToken !in matchLeagueTokens) return false
        }
        val observed = announcement.publishedAtEpochMillis ?: announcement.observedAtEpochMillis
        return observed >= match.startTimeEpochMillis - ANNOUNCEMENT_LOOKBACK_MS &&
            observed <= match.startTimeEpochMillis + ANNOUNCEMENT_AFTER_START_MS
    }

    internal fun evidenceMatches(
        evidence: ProviderStartingRosterEvidence,
        match: ScheduledSeries,
    ): Boolean {
        val left = match.teams[0].team
        val right = match.teams[1].team
        val teamToken = token(evidence.team)
        val opponentToken = token(evidence.opponent)
        val leftAliases = aliases(left.code, left.name, left.id.value)
        val rightAliases = aliases(right.code, right.name, right.id.value)
        val pairMatches = (teamToken in leftAliases && opponentToken in rightAliases) ||
            (teamToken in rightAliases && opponentToken in leftAliases)
        if (!pairMatches) return false
        if (evidence.league.isBlank()) return true
        return token(evidence.league) in aliases(match.competitionSlug, match.competition.name, match.competition.id.value)
    }

    private fun focusMatch(schedule: List<ScheduledSeries>, now: Long): ScheduledSeries? =
        schedule.filter { it.state != ScheduleState.COMPLETED }
            .minByOrNull { kotlin.math.abs(it.startTimeEpochMillis - now) }

    private fun aliases(vararg values: String): Set<String> = values.flatMap(::aliases).toSet()
    private fun aliases(value: String): List<String> = listOf(token(value)).filter { it.isNotBlank() }
    private fun token(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9\\p{L}\\p{N}]+"), "")

    private fun diagnostic(
        code: String,
        message: String,
        retryable: Boolean,
        match: ScheduledSeries,
        announcement: ProviderStartingRosterAnnouncement,
    ) = DiagnosticFailure(
        code = ErrorCode(code),
        message = message,
        retryable = retryable,
        context = mapOf(
            "match" to match.matchId.value,
            "announcement" to announcement.id,
            "team" to announcement.team,
            "candidate_score" to announcement.candidateScore.toString(),
            "parse_status" to announcement.parseStatus,
        ),
    )
}
