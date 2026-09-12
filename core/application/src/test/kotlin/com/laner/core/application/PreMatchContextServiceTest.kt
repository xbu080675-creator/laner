package com.laner.core.application

import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SeriesOutcome
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.StartingRosterResolution
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import com.laner.core.domain.TeamOutcome
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PreMatchContextServiceTest {
    private val lpl = CompetitionRef(
        id = CompetitionId("lol:competition:lpl"),
        name = "LPL",
        region = null,
    )
    private val ig = TeamRef(TeamId("lol:team:ig"), "IG", "Invictus Gaming")
    private val al = TeamRef(TeamId("lol:team:al"), "AL", "Anyone's Legend")
    private val blg = TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming")
    private val current = series(
        id = "current",
        left = ig,
        right = al,
        state = ScheduleState.UPCOMING,
        start = "2026-09-12T09:00:00Z",
    )
    private val context = SourceRequestContext(
        nowEpochMillis = Instant.parse("2026-09-12T08:00:00Z").toEpochMilli(),
        correlationId = "pre-context-test",
    )

    @Test
    fun fivePlayerRosterPoolNeverBecomesOfficialStartingRosterWithoutEvidence() = runSuspend {
        val service = PreMatchContextService(
            rosterSources = listOf(
                FakeRosterSource(
                    teamId = ig.id,
                    players = startingFive("IG"),
                )
            )
        )

        val result = service.load(current, emptyList(), context)

        assertEquals(5, result.left.rosterPool?.members?.size)
        assertIs<StartingRosterResolution.Unknown>(result.left.startingRoster)
    }

    @Test
    fun validOfficialFiveRoleEvidenceIsConfirmed() = runSuspend {
        val service = PreMatchContextService(
            startingRosterSources = listOf(
                FakeStartingRosterSource(
                    evidence = listOf(officialEvidence())
                )
            )
        )

        val result = service.load(current, emptyList(), context)
        val confirmed = assertIs<StartingRosterResolution.Confirmed>(result.left.startingRoster)

        assertEquals(5, confirmed.roster.starters.size)
        assertEquals(
            setOf(PlayerRole.TOP, PlayerRole.JUNGLE, PlayerRole.MID, PlayerRole.BOT, PlayerRole.SUPPORT),
            confirmed.roster.starters.mapNotNull { it.role }.toSet(),
        )
        assertEquals(false, confirmed.crossConfirmed)
        assertEquals(1, confirmed.evidenceCount)
    }

    @Test
    fun wrongDateOrOpponentEvidenceIsIgnored() = runSuspend {
        val wrongDate = officialEvidence(matchDate = "2026-09-11")
        val wrongOpponent = officialEvidence(opponent = "BLG")
        val service = PreMatchContextService(
            startingRosterSources = listOf(
                FakeStartingRosterSource(evidence = listOf(wrongDate, wrongOpponent))
            )
        )

        val result = service.load(current, emptyList(), context)

        assertIs<StartingRosterResolution.Unknown>(result.left.startingRoster)
    }

    @Test
    fun duplicateRoleOfficialEvidenceIsRejected() = runSuspend {
        val invalid = officialEvidence(
            starters = listOf(
                player("TheShy", "TOP"),
                player("Wei", "JUG"),
                player("Rookie", "MID"),
                player("JiaQi", "BOT"),
                player("Meiko", "BOT"),
            )
        )
        val service = PreMatchContextService(
            startingRosterSources = listOf(FakeStartingRosterSource(evidence = listOf(invalid)))
        )

        val result = service.load(current, emptyList(), context)

        assertIs<StartingRosterResolution.Unknown>(result.left.startingRoster)
    }

    @Test
    fun sameOfficialLineupFromTwoEvidenceRowsBecomesCrossConfirmed() = runSuspend {
        val first = officialEvidence(account = "IG 官方", evidenceType = "TEXT")
        val second = officialEvidence(
            account = "LPL 官方",
            sourceCategory = "LEAGUE_SOCIAL",
            evidenceType = "IMAGE_OCR",
            publishedAt = "2026-09-11T15:01:00Z",
        )
        val service = PreMatchContextService(
            startingRosterSources = listOf(
                FakeStartingRosterSource(evidence = listOf(first, second))
            )
        )

        val result = service.load(current, emptyList(), context)
        val confirmed = assertIs<StartingRosterResolution.Confirmed>(result.left.startingRoster)

        assertTrue(confirmed.crossConfirmed)
        assertEquals(2, confirmed.evidenceCount)
    }

    @Test
    fun conflictingEqualAuthorityOfficialLineupsRemainConflict() = runSuspend {
        val first = officialEvidence()
        val second = officialEvidence(
            account = "LPL 官方",
            sourceCategory = "LEAGUE_SOCIAL",
            starters = listOf(
                player("TheShy", "TOP"),
                player("Wei", "JUG"),
                player("Rookie", "MID"),
                player("JiaQi", "BOT"),
                player("Vampire", "SUP"),
            ),
        )
        val service = PreMatchContextService(
            startingRosterSources = listOf(
                FakeStartingRosterSource(evidence = listOf(first, second))
            )
        )

        val result = service.load(current, emptyList(), context)
        val conflict = assertIs<StartingRosterResolution.Conflict>(result.left.startingRoster)

        assertEquals(2, conflict.candidates.size)
    }

    @Test
    fun recentFormUsesCompletedSeriesOnlyAndExcludesCurrentMatch() = runSuspend {
        val completedWin = series(
            id = "old-win",
            left = ig,
            right = blg,
            leftWins = 2,
            rightWins = 1,
            state = ScheduleState.COMPLETED,
            start = "2026-09-10T09:00:00Z",
        )
        val upcoming = series(
            id = "future",
            left = ig,
            right = blg,
            state = ScheduleState.UPCOMING,
            start = "2026-09-13T09:00:00Z",
        )
        val service = PreMatchContextService()

        val result = service.load(current, listOf(current, completedWin, upcoming), context)

        assertEquals(1, result.leftRecentSeries.size)
        assertEquals(MatchId("old-win"), result.leftRecentSeries.single().matchId)
        assertEquals(SeriesOutcome.WIN, result.leftRecentSeries.single().outcome)
        assertEquals(2, result.leftRecentSeries.single().scoreFor)
        assertEquals(1, result.leftRecentSeries.single().scoreAgainst)
    }

    @Test
    fun headToHeadRequiresBothTeamsAndUsesLeftPerspective() = runSuspend {
        val igLoss = series(
            id = "h2h-1",
            left = ig,
            right = al,
            leftWins = 1,
            rightWins = 2,
            state = ScheduleState.COMPLETED,
            start = "2026-09-09T09:00:00Z",
        )
        val unrelated = series(
            id = "unrelated",
            left = ig,
            right = blg,
            leftWins = 2,
            rightWins = 0,
            state = ScheduleState.COMPLETED,
            start = "2026-09-10T09:00:00Z",
        )
        val service = PreMatchContextService()

        val result = service.load(current, listOf(igLoss, unrelated), context)

        assertEquals(1, result.recentHeadToHeadFromLeftPerspective.size)
        val h2h = result.recentHeadToHeadFromLeftPerspective.single()
        assertEquals(ig.id, h2h.perspectiveTeam.id)
        assertEquals(al.id, h2h.opponent.id)
        assertEquals(SeriesOutcome.LOSS, h2h.outcome)
        assertEquals(1, h2h.scoreFor)
        assertEquals(2, h2h.scoreAgainst)
    }

    private fun officialEvidence(
        matchDate: String = "2026-09-12",
        opponent: String = "AL",
        account: String = "IG 官方",
        sourceCategory: String = "TEAM_SOCIAL",
        evidenceType: String = "TEXT",
        publishedAt: String = "2026-09-11T15:00:00Z",
        starters: List<ProviderStartingPlayer> = startingFive("IG"),
    ) = ProviderStartingRosterEvidence(
        matchDateLocal = matchDate,
        timezoneId = "Asia/Shanghai",
        league = "LPL",
        team = "IG",
        opponent = opponent,
        starters = starters,
        sourceCategory = sourceCategory,
        evidenceType = evidenceType,
        platform = "WEIBO",
        account = account,
        publishedAtEpochMillis = Instant.parse(publishedAt).toEpochMilli(),
        observedAtEpochMillis = Instant.parse(publishedAt).plusSeconds(60).toEpochMilli(),
        confidence = 1f,
        sourceUri = "https://example.invalid/evidence",
    )

    private fun startingFive(prefix: String): List<ProviderStartingPlayer> = listOf(
        player("${prefix}Top", "TOP"),
        player("${prefix}Jungle", "JUG"),
        player("${prefix}Mid", "MID"),
        player("${prefix}Bot", "BOT"),
        player("${prefix}Support", "SUP"),
    )

    private fun player(handle: String, role: String) = ProviderStartingPlayer(
        externalId = handle.lowercase(),
        handle = handle,
        role = role,
    )

    private fun series(
        id: String,
        left: TeamRef,
        right: TeamRef,
        leftWins: Int = 0,
        rightWins: Int = 0,
        state: ScheduleState,
        start: String,
    ) = ScheduledSeries(
        matchId = MatchId(id),
        competition = lpl,
        competitionSlug = "lpl",
        competitionKind = CompetitionKind.REGIONAL,
        blockName = "Playoffs",
        startTimeEpochMillis = Instant.parse(start).toEpochMilli(),
        state = state,
        bestOf = 3,
        teams = listOf(
            ScheduledTeam(left, leftWins, if (leftWins > rightWins) TeamOutcome.WIN else TeamOutcome.UNKNOWN),
            ScheduledTeam(right, rightWins, if (rightWins > leftWins) TeamOutcome.WIN else TeamOutcome.UNKNOWN),
        ),
        provenance = SourceProvenance(
            providerId = "test-schedule",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.OFFICIAL,
            freshnessClass = FreshnessClass.DAILY,
            observedAtEpochMillis = Instant.parse(start).toEpochMilli(),
        ),
    )

    private class FakeRosterSource(
        private val teamId: TeamId,
        private val players: List<ProviderStartingPlayer>,
    ) : TeamRosterSourcePort {
        override val providerId: String = "fake-roster"
        override val authority: DataAuthority = DataAuthority.OFFICIAL

        override suspend fun readTeam(
            team: TeamRef,
            context: SourceRequestContext,
        ): ProviderRead<ProviderRosterPoolSnapshot> {
            if (team.id != teamId) {
                return ProviderRead.Success(
                    ProviderRosterPoolSnapshot(emptyList(), context.nowEpochMillis)
                )
            }
            return ProviderRead.Success(
                ProviderRosterPoolSnapshot(
                    players = players.map { ProviderRosterPlayer(it.externalId, it.handle, it.role) },
                    observedAtEpochMillis = context.nowEpochMillis,
                )
            )
        }
    }

    private class FakeStartingRosterSource(
        private val evidence: List<ProviderStartingRosterEvidence>,
    ) : StartingRosterSourcePort {
        override val providerId: String = "fake-official-roster"
        override val authority: DataAuthority = DataAuthority.OFFICIAL

        override suspend fun read(
            context: SourceRequestContext,
        ): ProviderRead<ProviderStartingRosterSnapshot> = ProviderRead.Success(
            ProviderStartingRosterSnapshot(
                evidence = evidence,
                observedAtEpochMillis = context.nowEpochMillis,
            )
        )
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<T>) {
                    result = value
                }
            }
        )
        return requireNotNull(result).getOrThrow()
    }
}
