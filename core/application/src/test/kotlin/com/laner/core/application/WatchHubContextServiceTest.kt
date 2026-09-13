package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WatchHubContextServiceTest {
    @Test
    fun readyContextUsesCanonicalTargetAndLifecycleWithoutGameplayQuery() = runSuspend {
        val service = WatchHubContextService(
            scheduleService = GlobalScheduleService(listOf(FixtureScheduleSource(live = true))),
            liveMatchStateService = LiveMatchStateService(
                sources = listOf(FixtureLiveStateSource()),
                repository = MemoryLiveStateRepository(),
            ),
        )

        val result = assertIs<WatchHubContextResult.Ready>(
            service.load(SourceRequestContext(2_000L, "watch-ready"))
        )

        assertEquals("BLU", result.match.teams[0].team.code)
        assertEquals("RED", result.match.teams[1].team.code)
        assertEquals(MatchLifecycleState.IN_GAME, result.liveState.state.lifecycle)
    }

    @Test
    fun noTargetDoesNotCallLiveSource() = runSuspend {
        val service = WatchHubContextService(
            scheduleService = GlobalScheduleService(listOf(FixtureScheduleSource(live = false))),
            liveMatchStateService = LiveMatchStateService(
                sources = listOf(ThrowingLiveStateSource()),
                repository = MemoryLiveStateRepository(),
            ),
        )

        val result = assertIs<WatchHubContextResult.NoTarget>(
            service.load(SourceRequestContext(2_000L, "watch-empty"))
        )
        assertEquals(LiveTargetUnavailableReason.NO_MATCHES, result.reason)
    }

    @Test
    fun unexpectedFailureUsesStableWatchErrorCode() = runSuspend {
        val diagnostics = RecordingDiagnostics()
        val service = WatchHubContextService(
            scheduleService = GlobalScheduleService(listOf(ThrowingScheduleSource())),
            liveMatchStateService = LiveMatchStateService(
                sources = emptyList(),
                repository = MemoryLiveStateRepository(),
            ),
            diagnostics = diagnostics,
        )

        val result = assertIs<WatchHubContextResult.Failed>(
            service.load(SourceRequestContext(2_000L, "watch-failure"))
        )
        assertEquals("LNR-APP-WATCH-001", result.failure.code.value)
        assertEquals("LNR-APP-WATCH-001", diagnostics.events.single().failure?.code?.value)
    }

    private class FixtureScheduleSource(
        private val live: Boolean,
    ) : GlobalPreMatchSourcePort {
        override val providerId = "fixture-watch-pre"
        override val authority = DataAuthority.OFFICIAL

        override suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot> =
            ProviderRead.Success(
                ProviderPreMatchSnapshot(
                    competitions = emptyList(),
                    matches = if (live) listOf(fixtureMatch()) else emptyList(),
                    observedAtEpochMillis = context.nowEpochMillis,
                )
            )

        private fun fixtureMatch() = ProviderScheduleEntry(
            externalEventId = "event-watch",
            externalMatchId = "match-watch",
            competitionExternalId = "league-watch",
            competitionSlug = "watch-league",
            competitionName = "Watch League",
            startTimeEpochMillis = 1_000L,
            rawState = "live",
            bestOf = 3,
            teams = listOf(
                ProviderTeamEntry("blue-id", "blue", "BLU", "Blue"),
                ProviderTeamEntry("red-id", "red", "RED", "Red"),
            ),
        )
    }

    private class ThrowingScheduleSource : GlobalPreMatchSourcePort {
        override val providerId = "throwing-watch-pre"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot> {
            error("fixture schedule failure")
        }
    }

    private class FixtureLiveStateSource : TargetAwareLiveStateSourcePort {
        override val providerId = "fixture-watch-live"
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
        override val providerId = "throwing-watch-live"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readLiveState(
            query: LiveMatchSourceQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveObservation> {
            error("live source must not be called without target")
        }
    }

    private class MemoryLiveStateRepository : LiveMatchStateRepository {
        private val values = mutableMapOf<MatchId, LiveMatchState>()
        override suspend fun read(matchId: MatchId): LiveMatchState? = values[matchId]
        override suspend fun write(state: LiveMatchState) {
            values[state.matchId] = state
        }
    }

    private class RecordingDiagnostics : DiagnosticsPort {
        val events = mutableListOf<DiagnosticEvent>()
        override fun emit(event: DiagnosticEvent) {
            if (event.failure?.code?.value == "LNR-APP-WATCH-001") events += event
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
