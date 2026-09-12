package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveMatchStateServiceTest {
    private val matchId = MatchId("lol:series:lpl:1:blg-we:bo5")
    private val context = SourceRequestContext(
        nowEpochMillis = 100_000L,
        correlationId = "live-state-test",
    )

    @Test
    fun freshVerifiedFrameBeatsStaleOfficialEventLive() = runSuspend {
        val repository = MemoryLiveStateRepository()
        val staleOfficial = FakeLiveSource(
            providerId = "official-schedule",
            authority = DataAuthority.OFFICIAL,
            result = success(eventStarted = true, observedAt = 50_000L),
        )
        val freshFrame = FakeLiveSource(
            providerId = "verified-frame",
            authority = DataAuthority.VERIFIED_PROVIDER,
            result = success(
                liveFrameObserved = true,
                gameId = GameId("lol:game:g1"),
                gameNumber = 1,
                observedAt = 90_000L,
            ),
        )

        val result = LiveMatchStateService(
            sources = listOf(staleOfficial, freshFrame),
            repository = repository,
        ).refresh(matchId, context)

        assertEquals(LiveStateLoadStatus.READY, result.status)
        assertEquals("verified-frame", result.selectedProviderId)
        assertEquals(MatchLifecycleState.IN_GAME, result.state.lifecycle)
        assertEquals(1, result.state.currentGameNumber)
        assertEquals(1, result.stateEvents.size)
        assertEquals(EventEvidence.VERIFIED_FRAME, result.stateEvents.single().evidence)
    }

    @Test
    fun verifiedFrameBeatsExplicitEventLiveInsideRealtimeWindow() = runSuspend {
        val repository = MemoryLiveStateRepository()
        val official = FakeLiveSource(
            providerId = "official-event",
            authority = DataAuthority.OFFICIAL,
            result = success(eventStarted = true, observedAt = 90_000L),
        )
        val frame = FakeLiveSource(
            providerId = "verified-frame",
            authority = DataAuthority.PROVIDER,
            result = success(
                liveFrameObserved = true,
                gameId = GameId("lol:game:g1"),
                gameNumber = 1,
                observedAt = 92_000L,
            ),
        )

        val result = LiveMatchStateService(
            sources = listOf(official, frame),
            repository = repository,
        ).refresh(matchId, context)

        assertEquals("verified-frame", result.selectedProviderId)
        assertEquals(MatchLifecycleState.IN_GAME, result.state.lifecycle)
    }

    @Test
    fun lowerQualitySeriesEndCannotFinishMatchWhenVerifiedFrameWins() = runSuspend {
        val repository = MemoryLiveStateRepository(
            LiveMatchState(
                matchId = matchId,
                lifecycle = MatchLifecycleState.IN_GAME,
                currentGameId = GameId("lol:game:g1"),
                currentGameNumber = 1,
                lastObservedAtEpochMillis = 80_000L,
            )
        )
        val officialSeriesEnd = FakeLiveSource(
            providerId = "official-text-status",
            authority = DataAuthority.OFFICIAL,
            result = success(
                seriesEnded = true,
                gameId = GameId("lol:game:g1"),
                gameNumber = 1,
                observedAt = 90_000L,
            ),
        )
        val verifiedFrame = FakeLiveSource(
            providerId = "verified-frame",
            authority = DataAuthority.VERIFIED_PROVIDER,
            result = success(
                liveFrameObserved = true,
                gameId = GameId("lol:game:g1"),
                gameNumber = 1,
                observedAt = 91_000L,
            ),
        )

        val result = LiveMatchStateService(
            sources = listOf(officialSeriesEnd, verifiedFrame),
            repository = repository,
        ).refresh(matchId, context)

        assertEquals("verified-frame", result.selectedProviderId)
        assertEquals(MatchLifecycleState.IN_GAME, result.state.lifecycle)
        assertTrue(result.appliedTransitions.isEmpty())
        assertTrue(result.stateEvents.isEmpty())
    }

    @Test
    fun betweenGamesObservationInsertsPostGameBeforeIntermission() = runSuspend {
        val repository = MemoryLiveStateRepository(
            LiveMatchState(
                matchId = matchId,
                lifecycle = MatchLifecycleState.IN_GAME,
                currentGameId = GameId("lol:game:g1"),
                currentGameNumber = 1,
                lastObservedAtEpochMillis = 80_000L,
            )
        )
        val source = FakeLiveSource(
            providerId = "official-live",
            authority = DataAuthority.OFFICIAL,
            result = success(
                betweenGames = true,
                gameId = GameId("lol:game:g1"),
                gameNumber = 1,
                observedAt = 90_000L,
            ),
        )

        val result = LiveMatchStateService(listOf(source), repository).refresh(matchId, context)

        assertEquals(MatchLifecycleState.BETWEEN_GAMES, result.state.lifecycle)
        assertEquals(
            listOf(
                MatchLifecycleState.IN_GAME to MatchLifecycleState.POST_GAME,
                MatchLifecycleState.POST_GAME to MatchLifecycleState.BETWEEN_GAMES,
            ),
            result.appliedTransitions,
        )
        assertEquals(2, result.stateEvents.size)
        assertEquals(EventEvidence.DERIVED_WINDOW, result.stateEvents[0].evidence)
        assertEquals(EventEvidence.PROVIDER_EXPLICIT, result.stateEvents[1].evidence)
        assertEquals(MatchLifecycleState.POST_GAME, result.stateEvents[0].current)
        assertEquals(MatchLifecycleState.BETWEEN_GAMES, result.stateEvents[1].current)
    }

    @Test
    fun validSourcePlusFailureIsDegradedButStillUpdatesState() = runSuspend {
        val repository = MemoryLiveStateRepository()
        val failure = FakeLiveSource(
            providerId = "broken-live",
            authority = DataAuthority.OFFICIAL,
            result = ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-LIVE-001"),
                    message = "provider unavailable",
                    retryable = true,
                )
            ),
        )
        val valid = FakeLiveSource(
            providerId = "backup-live",
            authority = DataAuthority.VERIFIED_PROVIDER,
            result = success(eventStarted = true, observedAt = 90_000L),
        )

        val result = LiveMatchStateService(listOf(failure, valid), repository).refresh(matchId, context)

        assertEquals(LiveStateLoadStatus.DEGRADED, result.status)
        assertEquals(MatchLifecycleState.EVENT_LIVE_PRE_GAME, result.state.lifecycle)
        assertEquals(1, result.failures.size)
    }

    @Test
    fun noSuccessfulSourcePreservesCurrentStateAndIsUnavailable() = runSuspend {
        val current = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.BETWEEN_GAMES,
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 80_000L,
        )
        val repository = MemoryLiveStateRepository(current)
        val failure = FakeLiveSource(
            providerId = "broken-live",
            authority = DataAuthority.OFFICIAL,
            result = ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-LIVE-002"),
                    message = "timeout",
                    retryable = true,
                )
            ),
        )

        val result = LiveMatchStateService(listOf(failure), repository).refresh(matchId, context)

        assertEquals(LiveStateLoadStatus.UNAVAILABLE, result.status)
        assertEquals(current, result.state)
        assertEquals(current, repository.state)
    }

    @Test
    fun wrongMatchObservationIsRejectedBeforeArbitration() = runSuspend {
        val repository = MemoryLiveStateRepository()
        val wrong = MatchId("lol:series:lpl:2:tes-ig:bo5")
        val source = FakeLiveSource(
            providerId = "wrong-match-source",
            authority = DataAuthority.OFFICIAL,
            result = ProviderRead.Success(
                ProviderLiveObservation(
                    matchId = wrong,
                    eventStarted = true,
                    observedAtEpochMillis = 90_000L,
                )
            ),
        )

        val result = LiveMatchStateService(listOf(source), repository).refresh(matchId, context)

        assertEquals(LiveStateLoadStatus.UNAVAILABLE, result.status)
        assertEquals("LNR-APP-LIVE-001", result.failures.single().code.value)
        assertEquals(MatchLifecycleState.PRE_EVENT, result.state.lifecycle)
    }

    private fun success(
        eventStarted: Boolean = false,
        draftStarted: Boolean = false,
        loadingObserved: Boolean = false,
        liveFrameObserved: Boolean = false,
        gameEnded: Boolean = false,
        betweenGames: Boolean = false,
        seriesEnded: Boolean = false,
        gameId: GameId? = null,
        gameNumber: Int? = null,
        observedAt: Long,
    ): ProviderRead<ProviderLiveObservation> = ProviderRead.Success(
        ProviderLiveObservation(
            matchId = matchId,
            eventStarted = eventStarted,
            draftStarted = draftStarted,
            loadingObserved = loadingObserved,
            liveFrameObserved = liveFrameObserved,
            gameEnded = gameEnded,
            betweenGames = betweenGames,
            seriesEnded = seriesEnded,
            gameId = gameId,
            gameNumber = gameNumber,
            observedAtEpochMillis = observedAt,
        )
    )

    private class FakeLiveSource(
        override val providerId: String,
        override val authority: DataAuthority,
        private val result: ProviderRead<ProviderLiveObservation>,
    ) : LiveStateSourcePort {
        override suspend fun readLiveState(
            matchId: MatchId,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveObservation> = result
    }

    private class MemoryLiveStateRepository(
        var state: LiveMatchState? = null,
    ) : LiveMatchStateRepository {
        override suspend fun read(matchId: MatchId): LiveMatchState? = state
        override suspend fun write(state: LiveMatchState) {
            this.state = state
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
        return result!!.getOrThrow()
    }
}
