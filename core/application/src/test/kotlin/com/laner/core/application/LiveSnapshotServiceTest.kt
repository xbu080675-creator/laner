package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
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
import kotlin.test.assertTrue

class LiveSnapshotServiceTest {
    private val matchId = MatchId("lol:series:lpl:test:blg-al:bo5")
    private val gameId = GameId("${matchId.value}:game:1")
    private val blg = TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming")
    private val al = TeamRef(TeamId("lol:team:al"), "AL", "Anyone's Legend")
    private val query = LiveSnapshotQuery(matchId, gameId, 1, listOf(blg, al))

    @Test
    fun verifiedLiveSnapshotIsPersistedThroughCanonicalTimeline() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveSnapshotService(
            sources = listOf(FixedSource(snapshot = snapshot(), authority = DataAuthority.OFFICIAL)),
            timelineService = LiveTimelineService(repository),
        )

        val result = service.refresh(query, SourceRequestContext(20_000L, "test-live-snapshot"))

        assertEquals(LiveSnapshotLoadStatus.READY, result.status)
        assertEquals("fixture-live", result.selectedProviderId)
        assertNotNull(result.snapshot)
        assertEquals(11_000, result.snapshot.blue.gold)
        assertEquals(1, result.timeline?.snapshots?.size)
        assertEquals(600, result.timeline?.snapshots?.single()?.gameTimeSeconds)
    }

    @Test
    fun wrongCanonicalGameIdentityIsRejectedAndNeverPersisted() = runSuspend {
        val repository = MemoryTimelineRepository()
        val wrong = snapshot().copy(
            game = snapshot().game.copy(gameId = GameId("lol:wrong:game")),
        )
        val service = LiveSnapshotService(
            sources = listOf(FixedSource(snapshot = wrong, authority = DataAuthority.OFFICIAL)),
            timelineService = LiveTimelineService(repository),
        )

        val result = service.refresh(query, SourceRequestContext(20_000L, "wrong-id"))

        assertEquals(LiveSnapshotLoadStatus.UNAVAILABLE, result.status)
        assertTrue(result.failures.any { it.code.value == "LNR-APP-LIVE-002" })
        assertNull(repository.read(gameId))
    }

    @Test
    fun oneSourceFailureDoesNotEraseValidSnapshotFromAnotherSource() = runSuspend {
        val repository = MemoryTimelineRepository()
        val service = LiveSnapshotService(
            sources = listOf(
                FailureSource,
                FixedSource(snapshot = snapshot(), authority = DataAuthority.OFFICIAL),
            ),
            timelineService = LiveTimelineService(repository),
        )

        val result = service.refresh(query, SourceRequestContext(20_000L, "degraded"))

        assertEquals(LiveSnapshotLoadStatus.DEGRADED, result.status)
        assertNotNull(result.snapshot)
        assertEquals(1, result.failures.size)
    }

    private fun snapshot(): LiveGameSnapshot = LiveGameSnapshot(
        game = GameContext(
            gameId = gameId,
            matchId = matchId,
            gameNumber = 1,
            blueTeamId = blg.id,
            redTeamId = al.id,
        ),
        lifecycle = MatchLifecycleState.IN_GAME,
        elapsedSeconds = 600,
        blue = TeamLiveState(blg.id, gold = 11_000, kills = 5, towers = 2, dragons = 1, barons = 0),
        red = TeamLiveState(al.id, gold = 10_500, kills = 4, towers = 1, dragons = 0, barons = 0),
    )

    private inner class FixedSource(
        private val snapshot: LiveGameSnapshot,
        override val authority: DataAuthority,
    ) : LiveSnapshotSourcePort {
        override val providerId: String = "fixture-live"

        override suspend fun readSnapshot(
            query: LiveSnapshotQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveSnapshot?> = ProviderRead.Success(
            ProviderLiveSnapshot(
                snapshot = snapshot,
                provenance = SourceProvenance(
                    providerId = providerId,
                    sourceClass = SourceClass.LIVE_MATCH_SOURCE,
                    authority = authority,
                    freshnessClass = FreshnessClass.REALTIME,
                    observedAtEpochMillis = context.nowEpochMillis,
                    sourceTimestampEpochMillis = context.nowEpochMillis - 1_000L,
                ),
            )
        )
    }

    private object FailureSource : LiveSnapshotSourcePort {
        override val providerId: String = "failed-live"
        override val authority: DataAuthority = DataAuthority.VERIFIED_PROVIDER
        override suspend fun readSnapshot(
            query: LiveSnapshotQuery,
            context: SourceRequestContext,
        ): ProviderRead<ProviderLiveSnapshot?> = ProviderRead.Failure(
            DiagnosticFailure(
                code = ErrorCode("LNR-SRC-LIVE-999"),
                message = "fixture failure",
                retryable = true,
            )
        )
    }

    private class MemoryTimelineRepository : LiveTimelineRepository {
        private val values = mutableMapOf<GameId, GameTimeline>()
        override suspend fun read(gameId: GameId): GameTimeline? = values[gameId]
        override suspend fun write(timeline: GameTimeline) {
            values[timeline.gameId] = timeline
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return outcome!!.getOrThrow()
    }
}
