package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.GoldLeadChangedEvent
import com.laner.core.domain.KillEvent
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.MultiKillWindowEvent
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ObjectiveType
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

class LiveEventDerivationServiceTest {
    private val matchId = MatchId("lol:series:test:live-events")
    private val gameId = GameId("lol:game:test:g1")
    private val blue = TeamId("lol:team:blue")
    private val red = TeamId("lol:team:red")
    private val carry = PlayerId("lol:player:carry")
    private val game = GameContext(gameId, matchId, 1, blue, red)

    @Test
    fun verifiedDeltasProduceConservativeEventsWithoutInventingVictim() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val service = LiveEventDerivationService(timelineService)
        timelineService.ingest(
            snapshot(100, 10_000, 10_500, 1, 1, blueDragons = 0, carryKills = 0),
            provenance(DataAuthority.VERIFIED_PROVIDER, 100_000L),
        )
        timelineService.ingest(
            snapshot(110, 11_000, 10_000, 4, 1, blueDragons = 1, carryKills = 3),
            provenance(DataAuthority.VERIFIED_PROVIDER, 110_000L),
        )

        val reconciled = service.reconcile(requireNotNull(timelineService.load(gameId)))
        val playerKill = assertIs<KillEvent>(reconciled.events.first { it is KillEvent })
        assertEquals(carry, playerKill.killerId)
        assertNull(playerKill.victimId)
        assertEquals(3, playerKill.count)
        assertEquals(10, playerKill.observedWindowSeconds)
        assertTrue(reconciled.events.any { it is MultiKillWindowEvent && it.killCount == 3 })
        assertTrue(reconciled.events.any { it is TeamFightWindowEvent && it.blueKillDelta == 3 && it.redKillDelta == 0 })
        assertTrue(reconciled.events.any { it is ObjectiveTakenEvent && it.objective == ObjectiveType.DRAGON && it.count == 1 })
        assertTrue(reconciled.events.any { it is GoldLeadChangedEvent && it.leadingTeamId == blue && it.goldDifference == 1_000 })
        assertTrue(reconciled.events.all { it.provenance.authority == DataAuthority.DERIVED })
    }

    @Test
    fun aggregateKillDeltaDoesNotPretendOnePlayerOrVictimIsKnown() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val service = LiveEventDerivationService(timelineService)
        timelineService.ingest(snapshot(200, 12_000, 12_000, 2, 2), provenance(DataAuthority.PROVIDER, 200_000L))
        timelineService.ingest(snapshot(210, 12_500, 12_200, 4, 2), provenance(DataAuthority.PROVIDER, 210_000L))

        val reconciled = service.reconcile(requireNotNull(timelineService.load(gameId)))
        val kill = assertIs<KillEvent>(reconciled.events.single { it is KillEvent })
        assertEquals(2, kill.count)
        assertNull(kill.killerId)
        assertNull(kill.victimId)
        assertTrue(reconciled.events.none { it is MultiKillWindowEvent })
    }

    @Test
    fun strongerSameSecondCorrectionRemovesStaleTeamFightAndRecomputesKillDelta() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val service = LiveEventDerivationService(timelineService)
        timelineService.ingest(snapshot(300, 15_000, 15_000, 0, 0), provenance(DataAuthority.PROVIDER, 300_000L))
        timelineService.ingest(snapshot(310, 16_000, 15_000, 3, 0), provenance(DataAuthority.PROVIDER, 310_000L))
        var reconciled = service.reconcile(requireNotNull(timelineService.load(gameId)))
        assertTrue(reconciled.events.any { it is TeamFightWindowEvent })

        timelineService.ingest(snapshot(310, 15_300, 15_000, 1, 0), provenance(DataAuthority.OFFICIAL, 309_000L))
        reconciled = service.reconcile(requireNotNull(timelineService.load(gameId)))

        assertTrue(reconciled.events.none { it is TeamFightWindowEvent })
        val kill = assertIs<KillEvent>(reconciled.events.single { it is KillEvent })
        assertEquals(1, kill.count)
    }

    @Test
    fun missingCountersDoNotManufactureEvents() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val service = LiveEventDerivationService(timelineService)
        timelineService.ingest(
            snapshot(400, null, null, null, null, blueDragons = null),
            provenance(DataAuthority.VERIFIED_PROVIDER, 400_000L),
        )
        timelineService.ingest(
            snapshot(410, null, null, null, null, blueDragons = null),
            provenance(DataAuthority.VERIFIED_PROVIDER, 410_000L),
        )

        val reconciled = service.reconcile(requireNotNull(timelineService.load(gameId)))
        assertTrue(reconciled.events.isEmpty())
    }

    private fun snapshot(
        seconds: Int,
        blueGold: Int?,
        redGold: Int?,
        blueKills: Int?,
        redKills: Int?,
        blueDragons: Int? = 0,
        carryKills: Int? = null,
    ): LiveGameSnapshot = LiveGameSnapshot(
        game = game,
        lifecycle = MatchLifecycleState.IN_GAME,
        elapsedSeconds = seconds,
        blue = TeamLiveState(blue, gold = blueGold, kills = blueKills, dragons = blueDragons),
        red = TeamLiveState(red, gold = redGold, kills = redKills, dragons = 0),
        players = if (carryKills == null) emptyList() else listOf(
            PlayerLiveState(playerId = carry, teamId = blue, kills = carryKills)
        ),
    )

    private fun provenance(authority: DataAuthority, observedAt: Long): SourceProvenance = SourceProvenance(
        providerId = "fixture-${authority.name.lowercase()}",
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = authority,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = observedAt,
        sourceTimestampEpochMillis = observedAt,
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
