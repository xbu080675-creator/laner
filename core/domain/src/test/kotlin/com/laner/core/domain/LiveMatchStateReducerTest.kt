package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LiveMatchStateReducerTest {
    private val matchId = MatchId("lol:series:lpl:1:blg-we:bo5")

    @Test
    fun eventLiveDoesNotMeanGameLive() {
        val initial = LiveMatchState(matchId = matchId)

        val result = LiveMatchStateReducer.reduce(
            initial,
            signal(MatchLifecycleState.EVENT_LIVE_PRE_GAME, observedAt = 1_000L),
        )

        val applied = assertIs<LiveStateTransitionResult.Applied>(result)
        assertEquals(MatchLifecycleState.EVENT_LIVE_PRE_GAME, applied.state.lifecycle)
        assertEquals(null, applied.state.currentGameId)
    }

    @Test
    fun verifiedGameStartCanAdvancePastMissingDraftAndLoadingSignals() {
        val preGame = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.EVENT_LIVE_PRE_GAME,
            lastObservedAtEpochMillis = 1_000L,
        )

        val result = LiveMatchStateReducer.reduce(
            preGame,
            signal(
                lifecycle = MatchLifecycleState.IN_GAME,
                gameId = GameId("lol:game:g1"),
                gameNumber = 1,
                observedAt = 2_000L,
                evidence = LiveStateEvidence.VERIFIED_FRAME,
            ),
        )

        val applied = assertIs<LiveStateTransitionResult.Applied>(result)
        assertEquals(MatchLifecycleState.IN_GAME, applied.state.lifecycle)
        assertEquals(GameId("lol:game:g1"), applied.state.currentGameId)
        assertEquals(1, applied.state.currentGameNumber)
    }

    @Test
    fun betweenGamesRequiresGameEndBeforeNextGame() {
        val inGame = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.IN_GAME,
            currentGameId = GameId("lol:game:g1"),
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 10_000L,
        )

        val ended = LiveMatchStateReducer.reduce(
            inGame,
            signal(MatchLifecycleState.POST_GAME, GameId("lol:game:g1"), 1, 11_000L),
        )
        val post = assertIs<LiveStateTransitionResult.Applied>(ended).state
        val intermission = LiveMatchStateReducer.reduce(
            post,
            signal(MatchLifecycleState.BETWEEN_GAMES, GameId("lol:game:g1"), 1, 12_000L),
        )
        val between = assertIs<LiveStateTransitionResult.Applied>(intermission).state
        assertEquals(MatchLifecycleState.BETWEEN_GAMES, between.lifecycle)

        val next = LiveMatchStateReducer.reduce(
            between,
            signal(
                MatchLifecycleState.IN_GAME,
                GameId("lol:game:g2"),
                2,
                20_000L,
                LiveStateEvidence.VERIFIED_FRAME,
            ),
        )
        val game2 = assertIs<LiveStateTransitionResult.Applied>(next).state
        assertEquals(MatchLifecycleState.IN_GAME, game2.lifecycle)
        assertEquals(2, game2.currentGameNumber)
        assertEquals(GameId("lol:game:g2"), game2.currentGameId)
    }

    @Test
    fun nextGameWithoutProviderGameIdClearsPreviousGameId() {
        val between = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.BETWEEN_GAMES,
            currentGameId = GameId("lol:game:g1"),
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 12_000L,
        )

        val result = LiveMatchStateReducer.reduce(
            between,
            signal(
                lifecycle = MatchLifecycleState.IN_GAME,
                gameId = null,
                gameNumber = 2,
                observedAt = 20_000L,
                evidence = LiveStateEvidence.VERIFIED_FRAME,
            ),
        )

        val applied = assertIs<LiveStateTransitionResult.Applied>(result)
        assertEquals(2, applied.state.currentGameNumber)
        assertEquals(null, applied.state.currentGameId)
    }

    @Test
    fun delayedOldGameCannotRewindNewGame() {
        val game2 = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.IN_GAME,
            currentGameId = GameId("lol:game:g2"),
            currentGameNumber = 2,
            lastObservedAtEpochMillis = 20_000L,
        )

        val result = LiveMatchStateReducer.reduce(
            game2,
            signal(MatchLifecycleState.POST_GAME, GameId("lol:game:g1"), 1, 21_000L),
        )

        val ignored = assertIs<LiveStateTransitionResult.Ignored>(result)
        assertEquals(LiveStateIgnoreReason.STALE_GAME, ignored.reason)
        assertEquals(MatchLifecycleState.IN_GAME, ignored.state.lifecycle)
        assertEquals(2, ignored.state.currentGameNumber)
    }

    @Test
    fun futureGameSignalDuringCurrentGameIsConflict() {
        val game1 = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.IN_GAME,
            currentGameId = GameId("lol:game:g1"),
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 10_000L,
        )

        val result = LiveMatchStateReducer.reduce(
            game1,
            signal(MatchLifecycleState.IN_GAME, GameId("lol:game:g2"), 2, 11_000L),
        )

        val conflict = assertIs<LiveStateTransitionResult.Conflict>(result)
        assertEquals(LiveStateConflictReason.FUTURE_GAME_WHILE_CURRENT_GAME_ACTIVE, conflict.reason)
    }

    @Test
    fun duplicateSignalRefreshesHeartbeatWithoutCreatingTransition() {
        val game1 = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.IN_GAME,
            currentGameId = GameId("lol:game:g1"),
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 10_000L,
        )

        val result = LiveMatchStateReducer.reduce(
            game1,
            signal(MatchLifecycleState.IN_GAME, GameId("lol:game:g1"), 1, 15_000L),
        )

        val ignored = assertIs<LiveStateTransitionResult.Ignored>(result)
        assertEquals(LiveStateIgnoreReason.DUPLICATE, ignored.reason)
        assertEquals(15_000L, ignored.state.lastObservedAtEpochMillis)
    }

    @Test
    fun delayedPostGameAfterNewerInGameHeartbeatIsIgnored() {
        val game1 = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.IN_GAME,
            currentGameId = GameId("lol:game:g1"),
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 10_000L,
        )

        val heartbeat = LiveMatchStateReducer.reduce(
            game1,
            signal(
                MatchLifecycleState.IN_GAME,
                GameId("lol:game:g1"),
                1,
                20_000L,
                LiveStateEvidence.VERIFIED_FRAME,
            ),
        )
        val refreshed = assertIs<LiveStateTransitionResult.Ignored>(heartbeat).state

        val delayed = LiveMatchStateReducer.reduce(
            refreshed,
            signal(MatchLifecycleState.POST_GAME, GameId("lol:game:g1"), 1, 15_000L),
        )

        val ignored = assertIs<LiveStateTransitionResult.Ignored>(delayed)
        assertEquals(LiveStateIgnoreReason.STALE_OBSERVATION, ignored.reason)
        assertEquals(MatchLifecycleState.IN_GAME, ignored.state.lifecycle)
        assertEquals(20_000L, ignored.state.lastObservedAtEpochMillis)
    }

    @Test
    fun seriesCompleteIsTerminal() {
        val post = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.POST_GAME,
            currentGameId = GameId("lol:game:g5"),
            currentGameNumber = 5,
            lastObservedAtEpochMillis = 100_000L,
        )
        val completed = LiveMatchStateReducer.reduce(
            post,
            signal(MatchLifecycleState.SERIES_COMPLETE, GameId("lol:game:g5"), 5, 101_000L),
        )
        val terminal = assertIs<LiveStateTransitionResult.Applied>(completed).state
        assertEquals(MatchLifecycleState.SERIES_COMPLETE, terminal.lifecycle)

        val delayed = LiveMatchStateReducer.reduce(
            terminal,
            signal(MatchLifecycleState.IN_GAME, GameId("lol:game:g5"), 5, 102_000L),
        )
        val ignored = assertIs<LiveStateTransitionResult.Ignored>(delayed)
        assertEquals(LiveStateIgnoreReason.TERMINAL_STATE, ignored.reason)
    }

    private fun signal(
        lifecycle: MatchLifecycleState,
        gameId: GameId? = null,
        gameNumber: Int? = null,
        observedAt: Long,
        evidence: LiveStateEvidence = LiveStateEvidence.PROVIDER_EXPLICIT,
    ): LiveStateSignal = LiveStateSignal(
        matchId = matchId,
        lifecycle = lifecycle,
        gameId = gameId,
        gameNumber = gameNumber,
        observedAtEpochMillis = observedAt,
        provenance = SourceProvenance(
            providerId = "test-live",
            sourceClass = SourceClass.LIVE_MATCH_SOURCE,
            authority = DataAuthority.OFFICIAL,
            freshnessClass = FreshnessClass.REALTIME,
            observedAtEpochMillis = observedAt,
        ),
        evidence = evidence,
    )
}
