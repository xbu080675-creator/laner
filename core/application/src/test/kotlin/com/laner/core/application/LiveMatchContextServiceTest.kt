package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.TeamLiveState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LiveMatchContextServiceTest {
    @Test
    fun readyContextOwnsTargetStateSnapshotAndTimelineOrdering() = runSuspend {
        val timelineRepository = MemoryTimelineRepository()
        val service = service(
            scheduleSource = FixtureScheduleSource(matches = listOf(fixtureMatch(rawState = "live"))),
            timelineRepository = timelineRepository,
        )

        val result = assertIs<LiveMatchContextResult.Ready>(
            service.load(SourceRequestContext(2_000L, "context-ready"))
        )

        assertEquals(MatchLifecycleState.IN_GAME, result.liveState.state.lifecycle)
        assertNotNull(result.snapshot.snapshot)
        assertEquals(result.snapshot.snapshot?.game?.gameId, result.timeline?.gameId)
        assertEquals(1, result.timeline?.snapshots?.size)
    }

    @Test
    fun emptyScheduleReturnsTypedNoTargetWithoutCallingLiveSources() = runSuspend {
        val service = service(
            scheduleSource = FixtureScheduleSource(matches = emptyList()),
            liveStateSource = ThrowingLiveStateSource(),
            snapshotSource = ThrowingSnapshotSource(),
        )

        val result = assertIs<LiveMatchContextResult.NoTarget>(
            service.load(SourceRequestContext(2_000L, "context-empty"))
        )

        assertEquals(LiveTargetUnavailableReason.NO_MATCHES, result.reason)
    }

    @Test
    fun completedOnlyScheduleReturnsNoEligibleTarget() = runSuspend {
        val completed = fixtureMatch(
            rawState = "completed",
            blueWins = 2,
            redWins = 0,
        )
        val service = service(scheduleSource = FixtureScheduleSource(matches = listOf(completed)))

        val result = assertIs<LiveMatchContextResult.NoTarget>(
            service.load(SourceRequestContext(2_000L, "context-completed"))
        )

        assertEquals(LiveTargetUnavailableReason.NO_ELIGIBLE_TARGET, result.reason)
    }

    @Test
    fun unexpectedApplicationFailureIsDiagnosedWithStableCode() = runSuspend {
        val diagnostics = RecordingDiagnostics()
        val service = service(
            scheduleSource = ThrowingScheduleSource(),
            diagnostics = diagnostics,
        )

        val result = assertIs<LiveMatchContextResult.Failed>(
            service.load(SourceRequestContext(2_000L, "context-failure"))
        )

        assertEquals("LNR-APP-LIVE-003", result.failure.code.value)
        assertEquals("context-failure", result.failure.context["correlation_id"])
        assertEquals("LNR-APP-LIVE-003", diagnostics.events.single().failure?.code?.value)
    }

    private fun service(
        scheduleSource: GlobalPreMatchSourcePort,
        liveStateSource: LiveStateSourcePort = FixtureLiveStateSource(),
        snapshotSource: LiveSnapshotSourcePort = FixtureSnapshotSource(),
        timelineRepository: LiveTimelineRepository = MemoryTimelineRepository(),
        diagnostics: DiagnosticsPort? = null,
    ): LiveMatchContextService {
        val timelineService = LiveTimelineService(timelineRepository)
        return LiveMatchContextService(
            scheduleService = GlobalScheduleService(listOf(scheduleSource), diagnostics),
            liveMatchStateService = LiveMatchStateService(
                sources = listOf(liveStateSource),
                repository = MemoryLiveStateRepository(),
                diagnostics = diagnostics,
            ),
            liveSnapshotService = LiveSnapshotService(
                sources = listOf(snapshotSource),
                timelineService = timelineService,
                diagnostics = diagnostics,
            ),
            liveTimelineService = timelineService,
            diagnostics = diagnostics,
        )
    }

    private fun fixtureMatch(
        rawState: String,
        blueWins: Int = 0,
        redWins: Int = 0,
    ): ProviderScheduleEntry = ProviderScheduleEntry(
        externalEventId = "event-1",
        externalMatchId = "match-1",
        competitionExternalId = "league-1",
        competitionSlug = "test-league",
        competitionName = "Test League",
        startTimeEpochMillis = 1_000L,
        rawState = rawState,
        bestOf = 3,
        teams = listOf(
            ProviderTeamEntry("blue-id", "blue", "BLU", "Blue", gameWins = blueWins),
            ProviderTeamEntry("red-id", "red", "RED", "Red", gameWins = redWins),
        ),
    )

    private class FixtureScheduleSource(
        private val matches: List<ProviderScheduleEntry>,
    ) : GlobalPreMatchSourcePort {
        override val providerId = "fixture-pre"
        override val authority = DataAuthority.OFFICIAL

        override suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot> =
            ProviderRead.Success(
                ProviderPreMatchSnapshot(
                    competitions = emptyList(),
                    matches = matches,
                    observedAtEpochMillis = context.nowEpochMillis,
                )
            )
    }

    private class ThrowingScheduleSource : GlobalPreMatchSourcePort {
        override val providerId = "throwing-pre"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot> {
            error("fixture schedule failure")
        }
    }

    private class FixtureLiveStateSource : TargetAwareLiveStateSourcePort {
        override val providerId = "fixture-live-state"
        override val authority = DataAuthority.OFFICIAL

        override suspend fun readLiveState(
            query: LiveMatchSourceQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveObservation> = ProviderRead.Success(
            ProviderLiveObservation(
                matchId = query.matchId,
                eventStarted = true,
                draftStarted = true,
                loadingObserved = true,
                liveFrameObserved = true,
                gameId = GameIdentity.canonical(query.matchId, 1),
                gameNumber = 1,
                observedAtEpochMillis = context.nowEpochMillis,
            )
        )
    }

    private class ThrowingLiveStateSource : TargetAwareLiveStateSourcePort {
        override val providerId = "throwing-live-state"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readLiveState(
            query: LiveMatchSourceQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveObservation> {
            error("LIVE source must not be called")
        }
    }

    private class FixtureSnapshotSource : LiveSnapshotSourcePort {
        override val providerId = "fixture-live-snapshot"
        override val authority = DataAuthority.OFFICIAL

        override suspend fun readSnapshot(
            query: LiveMatchSourceQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveSnapshot?> {
            val blue = query.teams[0].id
            val red = query.teams[1].id
            val gameId = GameIdentity.canonical(query.matchId, 1)
            return ProviderRead.Success(
                ProviderLiveSnapshot(
                    snapshot = LiveGameSnapshot(
                        game = GameContext(gameId, query.matchId, 1, blue, red),
                        lifecycle = MatchLifecycleState.IN_GAME,
                        elapsedSeconds = 300,
                        blue = TeamLiveState(blue, gold = 12_000),
                        red = TeamLiveState(red, gold = 11_500),
                    ),
                    observedAtEpochMillis = context.nowEpochMillis,
                )
            )
        }
    }

    private class ThrowingSnapshotSource : LiveSnapshotSourcePort {
        override val providerId = "throwing-live-snapshot"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readSnapshot(
            query: LiveMatchSourceQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveSnapshot?> {
            error("snapshot source must not be called")
        }
    }

    private class MemoryLiveStateRepository : LiveMatchStateRepository {
        private val values = mutableMapOf<MatchId, LiveMatchState>()
        override suspend fun read(matchId: MatchId): LiveMatchState? = values[matchId]
        override suspend fun write(state: LiveMatchState) {
            values[state.matchId] = state
        }
    }

    private class MemoryTimelineRepository : LiveTimelineRepository {
        private val values = mutableMapOf<GameId, GameTimeline>()
        override suspend fun read(gameId: GameId): GameTimeline? = values[gameId]
        override suspend fun write(timeline: GameTimeline) {
            values[timeline.gameId] = timeline
        }
    }

    private class RecordingDiagnostics : DiagnosticsPort {
        val events = mutableListOf<DiagnosticEvent>()
        override fun emit(event: DiagnosticEvent) {
            if (event.failure?.code?.value == "LNR-APP-LIVE-003") events += event
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) {
                result = value
            }
        })
        return result!!.getOrThrow()
    }
}
