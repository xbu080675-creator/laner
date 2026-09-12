package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.ScheduleState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalScheduleServiceTest {
    private val context = SourceRequestContext(
        nowEpochMillis = 1_800_000_000_000L,
        correlationId = "test-schedule",
    )

    @Test
    fun prematureCompletedFlagIsDowngradedWithoutWinningEvidence() = runSuspend {
        val source = FakeSource(
            authority = DataAuthority.OFFICIAL,
            snapshot = snapshot(
                matches = listOf(
                    match(
                        rawState = "completed",
                        bestOf = 5,
                        blueWins = 1,
                        redWins = 1,
                    )
                )
            ),
        )

        val result = GlobalScheduleService(listOf(source)).load(context)

        assertEquals(ScheduleState.UPCOMING, result.matches.single().state)
    }

    @Test
    fun completedFlagIsAcceptedWhenScoreProvesSeriesWinner() = runSuspend {
        val source = FakeSource(
            authority = DataAuthority.OFFICIAL,
            snapshot = snapshot(
                matches = listOf(
                    match(
                        rawState = "completed",
                        bestOf = 5,
                        blueWins = 3,
                        redWins = 1,
                    )
                )
            ),
        )

        val result = GlobalScheduleService(listOf(source)).load(context)

        assertEquals(ScheduleState.COMPLETED, result.matches.single().state)
    }

    @Test
    fun eventLiveScheduleStateDoesNotClaimInGameState() = runSuspend {
        val result = GlobalScheduleService(
            listOf(
                FakeSource(
                    authority = DataAuthority.OFFICIAL,
                    snapshot = snapshot(matches = listOf(match(rawState = "inProgress"))),
                )
            )
        ).load(context)

        assertEquals(ScheduleState.EVENT_LIVE, result.matches.single().state)
        // ScheduleState deliberately has no IN_GAME member. Live source owns that transition.
        assertTrue(ScheduleState.entries.none { it.name == "IN_GAME" })
    }

    @Test
    fun duplicateSeriesUsesHigherAuthoritySource() = runSuspend {
        val provider = FakeSource(
            providerId = "provider",
            authority = DataAuthority.PROVIDER,
            snapshot = snapshot(
                observedAt = 1_800_000_000_500L,
                matches = listOf(match(rawState = "scheduled")),
            ),
        )
        val official = FakeSource(
            providerId = "official",
            authority = DataAuthority.OFFICIAL,
            snapshot = snapshot(
                observedAt = 1_800_000_000_000L,
                matches = listOf(match(rawState = "inProgress")),
            ),
        )

        val result = GlobalScheduleService(listOf(provider, official)).load(context)

        assertEquals(1, result.matches.size)
        assertEquals("official", result.matches.single().provenance.providerId)
        assertEquals(ScheduleState.EVENT_LIVE, result.matches.single().state)
    }

    @Test
    fun catalogFallsBackToCompetitionsObservedInSchedule() = runSuspend {
        val result = GlobalScheduleService(
            listOf(
                FakeSource(
                    authority = DataAuthority.OFFICIAL,
                    snapshot = snapshot(competitions = emptyList(), matches = listOf(match())),
                )
            )
        ).load(context)

        assertEquals(1, result.catalog.size)
        assertEquals("lpl", result.catalog.single().slug)
    }

    @Test
    fun allSourcesFailReturnsUnavailableWithoutInventingData() = runSuspend {
        val failure = DiagnosticFailure(
            code = ErrorCode("LNR-SRC-PRE-001"),
            message = "missing credential",
            retryable = false,
        )
        val result = GlobalScheduleService(
            listOf(FakeSource(failure = failure))
        ).load(context)

        assertEquals(ScheduleLoadStatus.UNAVAILABLE, result.status)
        assertTrue(result.catalog.isEmpty())
        assertTrue(result.matches.isEmpty())
        assertEquals(failure, result.failures.single())
    }

    private fun snapshot(
        competitions: List<ProviderCompetitionEntry> = listOf(
            ProviderCompetitionEntry(
                externalId = "98767991314006698",
                slug = "lpl",
                name = "LPL",
                regionCode = "CN",
                regionName = "中国",
            )
        ),
        matches: List<ProviderScheduleEntry> = listOf(match()),
        observedAt: Long = 1_800_000_000_000L,
    ) = ProviderPreMatchSnapshot(
        competitions = competitions,
        matches = matches,
        observedAtEpochMillis = observedAt,
        sourceUri = "https://example.invalid/schedule",
    )

    private fun match(
        rawState: String = "scheduled",
        bestOf: Int? = 3,
        blueWins: Int = 0,
        redWins: Int = 0,
    ) = ProviderScheduleEntry(
        externalEventId = "event-1",
        externalMatchId = "match-1",
        competitionExternalId = "98767991314006698",
        competitionSlug = "lpl",
        competitionName = "LPL",
        regionCode = "CN",
        regionName = "中国",
        blockName = "常规赛",
        startTimeEpochMillis = 1_800_000_600_000L,
        rawState = rawState,
        bestOf = bestOf,
        teams = listOf(
            ProviderTeamEntry(
                externalId = "blue",
                slug = "bilibili-gaming",
                code = "BLG",
                name = "Bilibili Gaming",
                gameWins = blueWins,
            ),
            ProviderTeamEntry(
                externalId = "red",
                slug = "anyones-legend",
                code = "AL",
                name = "Anyone's Legend",
                gameWins = redWins,
            ),
        ),
    )

    private class FakeSource(
        override val providerId: String = "fake",
        override val authority: DataAuthority = DataAuthority.PROVIDER,
        private val snapshot: ProviderPreMatchSnapshot? = null,
        private val failure: DiagnosticFailure? = null,
    ) : GlobalPreMatchSourcePort {
        override suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot> {
            failure?.let { return ProviderRead.Failure(it) }
            return ProviderRead.Success(requireNotNull(snapshot))
        }
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
