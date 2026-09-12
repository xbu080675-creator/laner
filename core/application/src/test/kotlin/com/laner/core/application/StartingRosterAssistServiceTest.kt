package com.laner.core.application

import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamOutcome
import com.laner.core.domain.TeamRef
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartingRosterAssistServiceTest {
    private val now = Instant.parse("2026-09-12T14:40:00Z").toEpochMilli()
    private val match = series(Instant.parse("2026-09-13T09:00:00Z").toEpochMilli())
    private val context = SourceRequestContext(now, "roster-assist-test")

    @Test
    fun lowScoreOfficialImageAnnouncementIsStillInspectableButNeverAutoConfirmed() = runSuspend {
        var visionCalls = 0
        val service = StartingRosterAssistService(
            normalizedSource = FakeSource(announcements = listOf(announcement(score = 35))),
            visionSources = listOf(FakeVisionSource { visionCalls += 1; partialInspection() }),
        )

        val result = service.inspect(listOf(match), context)

        assertEquals(1, visionCalls)
        assertEquals(RosterAssistStage.OCR_PARTIAL, result.stage)
        assertEquals(0, result.normalizedEvidenceCount)
        assertTrue(result.failures.any { it.code.value == "LNR-SRC-PRE-011" })
    }

    @Test
    fun completeOcrRemainsUnverifiedAndGetsExplicitDiagnostic() = runSuspend {
        val service = StartingRosterAssistService(
            normalizedSource = FakeSource(announcements = listOf(announcement(score = 90))),
            visionSources = listOf(FakeVisionSource { completeInspection() }),
        )

        val result = service.inspect(listOf(match), context)

        assertEquals(RosterAssistStage.OCR_COMPLETE_UNVERIFIED, result.stage)
        assertEquals(0, result.normalizedEvidenceCount)
        assertTrue(result.failures.any { it.code.value == "LNR-SRC-PRE-012" })
    }

    @Test
    fun normalizedEvidenceWinsWithoutCallingOcr() = runSuspend {
        var calls = 0
        val service = StartingRosterAssistService(
            normalizedSource = FakeSource(
                evidence = listOf(evidence()),
                announcements = listOf(announcement(score = 90)),
            ),
            visionSources = listOf(FakeVisionSource { calls += 1; completeInspection() }),
        )

        val result = service.inspect(listOf(match), context)

        assertEquals(RosterAssistStage.NORMALIZED_EVIDENCE_AVAILABLE, result.stage)
        assertEquals(1, result.normalizedEvidenceCount)
        assertEquals(0, calls)
    }

    @Test
    fun unrelatedOldAnnouncementDoesNotAttachToTarget() = runSuspend {
        val old = announcement(score = 90).copy(
            team = "T1",
            observedAtEpochMillis = match.startTimeEpochMillis - 5L * 24L * 60L * 60L * 1000L,
        )
        val service = StartingRosterAssistService(normalizedSource = FakeSource(announcements = listOf(old)))

        val result = service.inspect(listOf(match), context)

        assertEquals(RosterAssistStage.NO_ANNOUNCEMENT, result.stage)
        assertTrue(result.announcements.isEmpty())
    }

    private fun announcement(score: Int) = ProviderStartingRosterAnnouncement(
        id = "blg-post",
        league = "LPL",
        team = "BLG",
        platform = "OFFICIAL",
        account = "BLG电子竞技俱乐部",
        sourceCategory = "TEAM_SOCIAL",
        observedAtEpochMillis = now,
        sourceUri = "https://weibo.com/example",
        imageUrls = listOf("https://img.example/roster.jpg"),
        parseStatus = "UNPARSED",
        candidateBasis = "TEAM_IMAGE_ONLY_FALLBACK",
        candidateTeams = listOf("BLG"),
        candidateScore = score,
    )

    private fun evidence() = ProviderStartingRosterEvidence(
        matchDateLocal = "2026-09-13",
        timezoneId = "Asia/Shanghai",
        league = "LPL",
        team = "BLG",
        opponent = "AL",
        starters = listOf(
            player("Bin", "TOP"), player("Beichuan", "JUG"), player("knight", "MID"),
            player("Viper", "BOT"), player("ON", "SUP"),
        ),
        sourceCategory = "TEAM_SOCIAL",
        evidenceType = "IMAGE_OCR",
        platform = "WEIBO",
        account = "BLG电子竞技俱乐部",
        publishedAtEpochMillis = now,
        observedAtEpochMillis = now,
        confidence = 0.98f,
        sourceUri = "https://weibo.com/example",
    )

    private fun player(id: String, role: String) = ProviderStartingPlayer(id.lowercase(), id, role)

    private fun partialInspection() = RosterVisionInspection(
        announcementId = "blg-post",
        imageUrl = "https://img.example/roster.jpg",
        engines = listOf("latin", "chinese"),
        lineCount = 8,
        roleCandidates = mapOf("TOP" to listOf("Bin"), "MID" to listOf("knight")),
    )

    private fun completeInspection() = RosterVisionInspection(
        announcementId = "blg-post",
        imageUrl = "https://img.example/roster.jpg",
        engines = listOf("latin", "chinese"),
        lineCount = 16,
        roleCandidates = mapOf(
            "TOP" to listOf("Bin"), "JUG" to listOf("Beichuan"), "MID" to listOf("knight"),
            "BOT" to listOf("Viper"), "SUP" to listOf("ON"),
        ),
    )

    private fun series(start: Long): ScheduledSeries {
        val competition = CompetitionRef(CompetitionId("lol:competition:lpl"), "LPL", null)
        val blg = TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming")
        val al = TeamRef(TeamId("lol:team:al"), "AL", "Anyone's Legend")
        return ScheduledSeries(
            matchId = MatchId("blg-al"),
            competition = competition,
            competitionSlug = "lpl",
            competitionKind = CompetitionKind.REGIONAL,
            blockName = "Playoffs",
            startTimeEpochMillis = start,
            state = ScheduleState.UPCOMING,
            bestOf = 5,
            teams = listOf(ScheduledTeam(blg, 0, TeamOutcome.UNKNOWN), ScheduledTeam(al, 0, TeamOutcome.UNKNOWN)),
            provenance = SourceProvenance(
                providerId = "test",
                sourceClass = SourceClass.PRE_MATCH_SOURCE,
                authority = DataAuthority.OFFICIAL,
                freshnessClass = FreshnessClass.MINUTES,
                observedAtEpochMillis = now,
            ),
        )
    }

    private class FakeSource(
        private val evidence: List<ProviderStartingRosterEvidence> = emptyList(),
        private val announcements: List<ProviderStartingRosterAnnouncement> = emptyList(),
    ) : StartingRosterSourcePort {
        override val providerId = "fake-normalized"
        override val authority = DataAuthority.VERIFIED_PROVIDER
        override suspend fun read(context: SourceRequestContext) = ProviderRead.Success(
            ProviderStartingRosterSnapshot(evidence, context.nowEpochMillis, announcements = announcements)
        )
    }

    private class FakeVisionSource(
        private val response: () -> RosterVisionInspection,
    ) : StartingRosterVisionPort {
        override val providerId = "fake-vision"
        override val authority = DataAuthority.DERIVED
        override suspend fun inspect(
            match: ScheduledSeries,
            announcement: ProviderStartingRosterAnnouncement,
            context: SourceRequestContext,
        ) = ProviderRead.Success(listOf(response()))
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return requireNotNull(result).getOrThrow()
    }
}
