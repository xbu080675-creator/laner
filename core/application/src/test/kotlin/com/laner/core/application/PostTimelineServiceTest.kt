package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostTimelineServiceTest {
    private val matchId = MatchId("lol:series:lck:100:t1-gen:bo5")
    private val blue = TeamId("lol:team:t1")
    private val red = TeamId("lol:team:gen")
    private val query = PostMatchQuery(matchId = matchId, competitionSlug = "lck")

    @Test
    fun historicalPostFramesMergeIntoCanonicalTimelineAndMarkComplete() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val postService = PostTimelineService(
            sources = listOf(FakeSource(batch = batch(gameNumber = 2, complete = true))),
            timelineService = timelineService,
        )

        val resolution = postService.backfill(query, 2, SourceRequestContext(50_000L, "post-timeline"))

        val timeline = assertNotNull(resolution.timeline)
        assertEquals(PostTimelineLoadStatus.READY, resolution.status)
        assertEquals(GameIdentity.canonical(matchId, 2), timeline.gameId)
        assertEquals(listOf(100, 200), timeline.snapshots.map { it.gameTimeSeconds })
        assertTrue(timeline.completed)
        assertEquals(setOf("riot-history"), resolution.sourcesApplied)
    }

    @Test
    fun wrongCanonicalGameIdIsRejectedAndCannotPoisonRepository() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val wrong = GameId("lol:series:lck:other:game:2")
        val invalidBatch = HistoricalGameTimelineBatch(
            gameNumber = 2,
            frames = listOf(frame(100, wrong)),
            complete = true,
        )
        val postService = PostTimelineService(
            sources = listOf(FakeSource(invalidBatch)),
            timelineService = timelineService,
        )

        val resolution = postService.backfill(query, 2, SourceRequestContext(50_000L, "post-timeline-wrong"))

        assertEquals(PostTimelineLoadStatus.UNAVAILABLE, resolution.status)
        assertTrue(resolution.failures.any { it.code.value == "LNR-APP-POST-005" })
        assertEquals(null, repository.read(GameIdentity.canonical(matchId, 2)))
        assertEquals(null, repository.read(wrong))
    }

    @Test
    fun existingLiveFrameAndHistoricalPostFrameShareOneTimeline() = runSuspend {
        val repository = MemoryTimelineRepository()
        val timelineService = LiveTimelineService(repository)
        val gameId = GameIdentity.canonical(matchId, 1)
        val liveGame = GameContext(gameId, matchId, 1, blue, red)
        timelineService.ingest(
            LiveGameSnapshot(
                game = liveGame,
                lifecycle = MatchLifecycleState.IN_GAME,
                elapsedSeconds = 300,
                blue = TeamLiveState(blue, gold = 10_000),
                red = TeamLiveState(red, gold = 9_800),
            ),
            SourceProvenance(
                providerId = "live-provider",
                sourceClass = SourceClass.LIVE_MATCH_SOURCE,
                authority = DataAuthority.PROVIDER,
                freshnessClass = FreshnessClass.REALTIME,
                observedAtEpochMillis = 30_000L,
            ),
        )
        val postService = PostTimelineService(
            sources = listOf(FakeSource(batch(gameNumber = 1, complete = false))),
            timelineService = timelineService,
        )

        val resolution = postService.backfill(query, 1, SourceRequestContext(50_000L, "merge-live-post"))

        assertEquals(listOf(100, 200, 300), assertNotNull(resolution.timeline).snapshots.map { it.gameTimeSeconds })
    }

    private fun batch(gameNumber: Int, complete: Boolean): HistoricalGameTimelineBatch {
        val gameId = GameIdentity.canonical(matchId, gameNumber)
        return HistoricalGameTimelineBatch(
            gameNumber = gameNumber,
            frames = listOf(frame(100, gameId, gameNumber), frame(200, gameId, gameNumber)),
            complete = complete,
        )
    }

    private fun frame(seconds: Int, gameId: GameId, gameNumber: Int = 2): HistoricalTimelineFrame {
        val provenance = SourceProvenance(
            providerId = "riot-history",
            sourceClass = SourceClass.POST_MATCH_SOURCE,
            authority = DataAuthority.OFFICIAL,
            freshnessClass = FreshnessClass.STATIC,
            observedAtEpochMillis = 40_000L + seconds,
        )
        return HistoricalTimelineFrame(
            snapshot = LiveGameSnapshot(
                game = GameContext(gameId, matchId, gameNumber, blue, red),
                lifecycle = MatchLifecycleState.IN_GAME,
                elapsedSeconds = seconds,
                blue = TeamLiveState(blue, gold = 5_000 + seconds),
                red = TeamLiveState(red, gold = 4_900 + seconds),
            ),
            provenance = provenance,
        )
    }

    private class FakeSource(
        private val batch: HistoricalGameTimelineBatch?,
    ) : PostTimelineSourcePort {
        override val providerId = "riot-history"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readGameTimeline(
            query: PostMatchQuery,
            gameNumber: Int,
            context: SourceRequestContext,
        ): ProviderRead<HistoricalGameTimelineBatch?> = ProviderRead.Success(batch)
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
