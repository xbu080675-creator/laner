package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import com.laner.core.domain.TeamRef
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LiveSnapshotServiceTest {
    private val matchId = MatchId("match:test")
    private val blue = TeamRef(TeamId("team:blue"), "BLU", "Blue")
    private val red = TeamRef(TeamId("team:red"), "RED", "Red")
    private val query = LiveMatchSourceQuery(matchId, 1_000L, listOf(blue, red))

    @Test
    fun validSnapshotIsIngestedIntoCanonicalTimeline() = runSuspend {
        val repo = MemoryTimelineRepository()
        val service = LiveSnapshotService(
            sources = listOf(FakeSource(snapshot())),
            timelineService = LiveTimelineService(repo),
        )

        val result = service.refresh(query, SourceRequestContext(2_000L, "test"))

        assertEquals(LiveSnapshotLoadStatus.READY, result.status)
        assertNotNull(result.snapshot)
        val stored = assertNotNull(repo.read(GameIdentity.canonical(matchId, 1)))
        assertEquals(1, stored.snapshots.size)
        assertEquals(21_000, stored.snapshots.single().snapshot.blue.gold)
    }

    @Test
    fun wrongCanonicalGameIdIsRejectedWithoutWritingTimeline() = runSuspend {
        val repo = MemoryTimelineRepository()
        val base = snapshot()
        val bad = base.copy(game = base.game.copy(gameId = GameId("provider-game-raw")))
        val service = LiveSnapshotService(
            sources = listOf(FakeSource(bad)),
            timelineService = LiveTimelineService(repo),
        )

        val result = service.refresh(query, SourceRequestContext(2_000L, "test"))

        assertEquals(LiveSnapshotLoadStatus.DEGRADED, result.status)
        assertNull(result.snapshot)
        assertEquals("LNR-APP-LIVE-002", result.failures.single().code.value)
        assertNull(repo.read(GameIdentity.canonical(matchId, 1)))
    }

    @Test
    fun wrongTeamsAreRejected() = runSuspend {
        val repo = MemoryTimelineRepository()
        val other = TeamId("team:other")
        val base = snapshot()
        val bad = base.copy(
            game = base.game.copy(redTeamId = other),
            red = TeamLiveState(other, gold = 20_000),
        )
        val service = LiveSnapshotService(
            sources = listOf(FakeSource(bad)),
            timelineService = LiveTimelineService(repo),
        )

        val result = service.refresh(query, SourceRequestContext(2_000L, "test"))

        assertEquals(LiveSnapshotLoadStatus.DEGRADED, result.status)
        assertNull(result.snapshot)
        assertNull(repo.read(GameIdentity.canonical(matchId, 1)))
    }

    private fun snapshot(): LiveGameSnapshot {
        val gameId = GameIdentity.canonical(matchId, 1)
        return LiveGameSnapshot(
            game = GameContext(gameId, matchId, 1, blue.id, red.id),
            lifecycle = MatchLifecycleState.IN_GAME,
            elapsedSeconds = 600,
            blue = TeamLiveState(blue.id, gold = 21_000, kills = 5, towers = 2, dragons = 1, barons = 0),
            red = TeamLiveState(red.id, gold = 20_000, kills = 3, towers = 1, dragons = 0, barons = 0),
        )
    }

    private class FakeSource(private val snapshot: LiveGameSnapshot?) : LiveSnapshotSourcePort {
        override val providerId = "fake-live-snapshot"
        override val authority = DataAuthority.OFFICIAL

        override suspend fun readSnapshot(
            query: LiveMatchSourceQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveSnapshot?> = ProviderRead.Success(
            snapshot?.let {
                ProviderLiveSnapshot(
                    snapshot = it,
                    sourceTimestampEpochMillis = 1_900L,
                    observedAtEpochMillis = context.nowEpochMillis,
                )
            }
        )
    }

    private class MemoryTimelineRepository : LiveTimelineRepository {
        private val values = mutableMapOf<GameId, GameTimeline>()
        override suspend fun read(gameId: GameId): GameTimeline? = values[gameId]
        override suspend fun write(timeline: GameTimeline) { values[timeline.gameId] = timeline }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return result!!.getOrThrow()
    }
}
