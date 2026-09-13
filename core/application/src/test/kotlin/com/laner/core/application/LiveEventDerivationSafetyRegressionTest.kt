package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.KillEvent
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerLiveState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamFightWindowEvent
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveEventDerivationSafetyRegressionTest {
    private val matchId = MatchId("lol:series:test:live-event-safety")
    private val gameId = GameId("lol:game:test:safety:g1")
    private val blue = TeamId("lol:team:blue")
    private val red = TeamId("lol:team:red")
    private val carry = PlayerId("lol:player:carry")
    private val game = GameContext(gameId, matchId, 1, blue, red)

    @Test
    fun oneSidedUnknownKillDeltaDoesNotBecomeZeroInTeamFightWindow() = runSuspend {
        val service = fixtureService()
        service.timeline.ingest(snapshot(100, 1, null), provenance(100_000L))
        service.timeline.ingest(snapshot(110, 4, null), provenance(110_000L))

        val reconciled = service.derivation.reconcile(requireNotNull(service.timeline.load(gameId)))
        val kill = assertIs<KillEvent>(reconciled.events.single { it is KillEvent })
        assertEquals(blue, kill.teamId)
        assertEquals(3, kill.count)
        assertNull(kill.killerId)
        assertTrue(reconciled.events.none { it is TeamFightWindowEvent })
    }

    @Test
    fun regressingTeamCounterDoesNotManufactureCombatEvents() = runSuspend {
        val service = fixtureService()
        service.timeline.ingest(snapshot(200, 5, 2), provenance(200_000L))
        service.timeline.ingest(snapshot(210, 4, 2), provenance(210_000L))

        val reconciled = service.derivation.reconcile(requireNotNull(service.timeline.load(gameId)))
        assertTrue(reconciled.events.none { it is KillEvent })
        assertTrue(reconciled.events.none { it is TeamFightWindowEvent })
    }

    @Test
    fun regressingPlayerCounterFallsBackToAggregateUnknownPlayer() = runSuspend {
        val service = fixtureService()
        service.timeline.ingest(snapshot(300, 2, 2, carryKills = 2), provenance(300_000L))
        service.timeline.ingest(snapshot(310, 3, 2, carryKills = 1), provenance(310_000L))

        val reconciled = service.derivation.reconcile(requireNotNull(service.timeline.load(gameId)))
        val kill = assertIs<KillEvent>(reconciled.events.single { it is KillEvent })
        assertEquals(1, kill.count)
        assertNull(kill.killerId)
        assertNull(kill.victimId)
    }

    @Test
    fun missingCurrentPlayerRowFallsBackToAggregateUnknownPlayer() = runSuspend {
        val service = fixtureService()
        service.timeline.ingest(snapshot(400, 2, 2, carryKills = 2), provenance(400_000L))
        service.timeline.ingest(snapshot(410, 3, 2, carryKills = null), provenance(410_000L))

        val reconciled = service.derivation.reconcile(requireNotNull(service.timeline.load(gameId)))
        val kill = assertIs<KillEvent>(reconciled.events.single { it is KillEvent })
        assertEquals(1, kill.count)
        assertNull(kill.killerId)
        assertNull(kill.victimId)
    }

    private fun fixtureService(): Fixture {
        val repository = MemoryTimelineRepository()
        val timeline = LiveTimelineService(repository)
        return Fixture(timeline, LiveEventDerivationService(timeline))
    }

    private fun snapshot(
        seconds: Int,
        blueKills: Int?,
        redKills: Int?,
        carryKills: Int? = null,
    ): LiveGameSnapshot = LiveGameSnapshot(
        game = game,
        lifecycle = MatchLifecycleState.IN_GAME,
        elapsedSeconds = seconds,
        blue = TeamLiveState(blue, kills = blueKills),
        red = TeamLiveState(red, kills = redKills),
        players = if (carryKills == null) emptyList() else listOf(
            PlayerLiveState(playerId = carry, teamId = blue, kills = carryKills),
        ),
    )

    private fun provenance(observedAt: Long): SourceProvenance = SourceProvenance(
        providerId = "fixture-verified",
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = DataAuthority.VERIFIED_PROVIDER,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = observedAt,
        sourceTimestampEpochMillis = observedAt,
    )

    private data class Fixture(
        val timeline: LiveTimelineService,
        val derivation: LiveEventDerivationService,
    )

    private class MemoryTimelineRepository : LiveTimelineRepository {
        private val values = mutableMapOf<GameId, GameTimeline>()
        override suspend fun read(gameId: GameId): GameTimeline? = values[gameId]
        override suspend fun write(timeline: GameTimeline) {
            values[timeline.gameId] = timeline
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
