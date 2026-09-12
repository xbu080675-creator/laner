package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ObjectiveType
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveTimelineServiceTest {
    private val matchId = MatchId("lol:series:lpl:1:blg-we:bo5")
    private val gameId = GameId("lol:game:g1")
    private val blue = TeamId("lol:team:blg")
    private val red = TeamId("lol:team:we")
    private val game = GameContext(
        gameId = gameId,
        matchId = matchId,
        gameNumber = 1,
        blueTeamId = blue,
        redTeamId = red,
    )

    @Test
    fun reconnectDuplicateEventIsStoredOnceEvenWhenDetailDiffers() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)
        val first = objective(
            seconds = 600,
            sequence = 10,
            evidence = EventEvidence.VERIFIED_DELTA,
            authority = DataAuthority.PROVIDER,
            detail = "dragon",
        )
        val duplicateFromOfficial = objective(
            seconds = 600,
            sequence = 999,
            evidence = EventEvidence.PROVIDER_EXPLICIT,
            authority = DataAuthority.OFFICIAL,
            detail = "Hextech Drake",
        )

        service.ingest(snapshot(600), provenance(DataAuthority.PROVIDER, 10_000L), listOf(first))
        val result = service.ingest(
            snapshot(601),
            provenance(DataAuthority.OFFICIAL, 11_000L),
            listOf(duplicateFromOfficial),
        )

        assertEquals(1, result.timeline.events.size)
        assertEquals(EventEvidence.PROVIDER_EXPLICIT, result.timeline.events.single().evidence)
        assertEquals("Hextech Drake", (result.timeline.events.single() as ObjectiveTakenEvent).detail)
    }

    @Test
    fun lateOutOfOrderEventIsInsertedAtCorrectGameTime() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)

        service.ingest(
            snapshot(900),
            provenance(DataAuthority.VERIFIED_PROVIDER, 20_000L),
            listOf(objective(900, 2, EventEvidence.PROVIDER_EXPLICIT, DataAuthority.VERIFIED_PROVIDER, "baron", ObjectiveType.BARON)),
        )
        val result = service.ingest(
            snapshot(910),
            provenance(DataAuthority.VERIFIED_PROVIDER, 21_000L),
            listOf(objective(300, 1, EventEvidence.PROVIDER_EXPLICIT, DataAuthority.VERIFIED_PROVIDER, "dragon")),
        )

        assertEquals(listOf(300, 900), result.timeline.events.mapNotNull { it.gameTimeSeconds })
    }

    @Test
    fun weakerSameSecondSnapshotCannotOverwriteStrongerSnapshot() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)

        service.ingest(
            snapshot(seconds = 300, blueGold = 10_000),
            provenance(DataAuthority.OFFICIAL, 20_000L),
        )
        val result = service.ingest(
            snapshot(seconds = 300, blueGold = 9_000),
            provenance(DataAuthority.PROVIDER, 21_000L),
        )

        assertFalse(result.snapshotAddedOrReplaced)
        assertEquals(10_000, result.timeline.snapshots.single().snapshot.blue.gold)
    }

    @Test
    fun strongerSameSecondSnapshotReplacesWeakerSnapshot() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)

        service.ingest(
            snapshot(seconds = 300, blueGold = 9_000),
            provenance(DataAuthority.PROVIDER, 20_000L),
        )
        val result = service.ingest(
            snapshot(seconds = 300, blueGold = 10_000),
            provenance(DataAuthority.OFFICIAL, 19_000L),
        )

        assertTrue(result.snapshotAddedOrReplaced)
        assertEquals(10_000, result.timeline.snapshots.single().snapshot.blue.gold)
    }

    @Test
    fun wrongGameEventIsRejectedWithoutPoisoningTimeline() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)
        val wrongGame = GameId("lol:game:g2")
        val invalid = ObjectiveTakenEvent(
            matchId = matchId,
            gameId = wrongGame,
            sequence = 1,
            gameTimeSeconds = 100,
            provenance = provenance(DataAuthority.OFFICIAL, 20_000L),
            evidence = EventEvidence.PROVIDER_EXPLICIT,
            teamId = blue,
            objective = ObjectiveType.DRAGON,
        )

        val result = service.ingest(snapshot(100), provenance(DataAuthority.OFFICIAL, 20_000L), listOf(invalid))

        assertEquals(1, result.invalidEventsRejected)
        assertTrue(result.timeline.events.isEmpty())
    }

    @Test
    fun replayStateAtUsesLatestSnapshotNotAfterRequestedTime() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)
        service.ingest(snapshot(100, blueGold = 3_000), provenance(DataAuthority.OFFICIAL, 10_000L))
        service.ingest(snapshot(200, blueGold = 6_000), provenance(DataAuthority.OFFICIAL, 20_000L))
        service.ingest(snapshot(300, blueGold = 9_000), provenance(DataAuthority.OFFICIAL, 30_000L))

        val timeline = assertNotNull(repository.read(gameId))
        assertEquals(6_000, timeline.stateAt(250)?.blue?.gold)
    }

    @Test
    fun completionIsExplicitAndIdempotent() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveTimelineService(repository)
        service.ingest(snapshot(300), provenance(DataAuthority.OFFICIAL, 30_000L))

        val completed = assertNotNull(service.markCompleted(gameId))
        val repeated = assertNotNull(service.markCompleted(gameId))

        assertTrue(completed.completed)
        assertEquals(completed, repeated)
    }

    private fun snapshot(
        seconds: Int,
        blueGold: Int = 5_000,
    ): LiveGameSnapshot = LiveGameSnapshot(
        game = game,
        lifecycle = MatchLifecycleState.IN_GAME,
        elapsedSeconds = seconds,
        blue = TeamLiveState(teamId = blue, gold = blueGold),
        red = TeamLiveState(teamId = red, gold = 5_000),
    )

    private fun objective(
        seconds: Int,
        sequence: Long,
        evidence: EventEvidence,
        authority: DataAuthority,
        detail: String,
        objective: ObjectiveType = ObjectiveType.DRAGON,
    ): ObjectiveTakenEvent = ObjectiveTakenEvent(
        matchId = matchId,
        gameId = gameId,
        sequence = sequence,
        gameTimeSeconds = seconds,
        provenance = provenance(authority, 10_000L + sequence),
        evidence = evidence,
        teamId = blue,
        objective = objective,
        detail = detail,
    )

    private fun provenance(
        authority: DataAuthority,
        observedAt: Long,
    ): SourceProvenance = SourceProvenance(
        providerId = "test-${authority.name.lowercase()}",
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = authority,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = observedAt,
    )

    private class MemoryTimelineRepository : LiveTimelineRepository {
        private val timelines = mutableMapOf<GameId, GameTimeline>()

        override suspend fun read(gameId: GameId): GameTimeline? = timelines[gameId]

        override suspend fun write(timeline: GameTimeline) {
            timelines[timeline.gameId] = timeline
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
