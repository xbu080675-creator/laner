package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PostMatchArchiveServiceTest {
    private val matchId = MatchId("lol:series:lpl:archive-test")
    private val blue = TeamId("lol:team:blue")
    private val red = TeamId("lol:team:red")
    private val context = SourceRequestContext(200_000L, "post-archive-test")

    @Test
    fun archiveRestoresVerifiedSeriesWhenExternalSourcesAreEmpty() = runSuspend {
        val archive = MemoryArchive(
            PostArchiveSnapshot(
                matchId = matchId,
                result = finalResult("official-history", 3, 1, 100_000L),
                storedAtEpochMillis = 110_000L,
            )
        )
        val service = PostMatchService(
            resultSources = emptyList(),
            gameSources = emptyList(),
            awardSources = emptyList(),
            replaySources = emptyList(),
            archiveRepository = archive,
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.READY, snapshot.status)
        assertEquals(3, snapshot.bundle.result?.leftWins)
        assertEquals("official-history", snapshot.bundle.result?.provenance?.providerId)
    }

    @Test
    fun freshExternalResultReplacesArchiveWithoutCreatingSelfConflict() = runSuspend {
        val archive = MemoryArchive(
            PostArchiveSnapshot(
                matchId = matchId,
                result = partialResult("old-history", 1, 0, 80_000L),
                storedAtEpochMillis = 90_000L,
            )
        )
        val service = PostMatchService(
            resultSources = listOf(
                ResultSource(
                    providerId = "official-final",
                    authority = DataAuthority.OFFICIAL,
                    result = ProviderRead.Success(finalResult("official-final", 3, 2, 190_000L)),
                )
            ),
            gameSources = emptyList(),
            awardSources = emptyList(),
            replaySources = emptyList(),
            archiveRepository = archive,
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.READY, snapshot.status)
        assertEquals(3, snapshot.bundle.result?.leftWins)
        assertEquals(2, snapshot.bundle.result?.rightWins)
        assertEquals(0, snapshot.conflicts.size)
        assertEquals(3, archive.saved?.result?.leftWins)
        assertEquals(2, archive.saved?.result?.rightWins)
    }

    @Test
    fun conflictingExternalResultsDoNotOverwriteLastKnownGoodArchive() = runSuspend {
        val original = PostArchiveSnapshot(
            matchId = matchId,
            result = finalResult("last-known-good", 3, 1, 100_000L),
            storedAtEpochMillis = 110_000L,
        )
        val archive = MemoryArchive(original)
        val service = PostMatchService(
            resultSources = listOf(
                ResultSource("provider-a", DataAuthority.OFFICIAL, ProviderRead.Success(finalResult("provider-a", 3, 1, 190_000L))),
                ResultSource("provider-b", DataAuthority.VERIFIED_PROVIDER, ProviderRead.Success(finalResult("provider-b", 2, 3, 191_000L))),
            ),
            gameSources = emptyList(),
            awardSources = emptyList(),
            replaySources = emptyList(),
            archiveRepository = archive,
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.CONFLICT, snapshot.status)
        assertEquals("SERIES_RESULT", snapshot.conflicts.single().factType)
        assertNull(archive.saved)
        assertEquals("last-known-good", archive.current?.result?.provenance?.providerId)
    }

    private fun finalResult(
        provider: String,
        leftWins: Int,
        rightWins: Int,
        observedAt: Long,
    ): SeriesResult {
        val winner = if (leftWins > rightWins) blue else red
        return SeriesResult(
            matchId = matchId,
            leftTeamId = blue,
            rightTeamId = red,
            leftWins = leftWins,
            rightWins = rightWins,
            bestOf = 5,
            state = SeriesResultState.FINAL,
            winnerTeamId = winner,
            provenance = provenance(provider, observedAt),
        )
    }

    private fun partialResult(
        provider: String,
        leftWins: Int,
        rightWins: Int,
        observedAt: Long,
    ): SeriesResult = SeriesResult(
        matchId = matchId,
        leftTeamId = blue,
        rightTeamId = red,
        leftWins = leftWins,
        rightWins = rightWins,
        bestOf = 5,
        state = SeriesResultState.PARTIAL,
        winnerTeamId = null,
        provenance = provenance(provider, observedAt),
    )

    private fun provenance(provider: String, observedAt: Long): SourceProvenance = SourceProvenance(
        providerId = provider,
        sourceClass = SourceClass.POST_MATCH_SOURCE,
        authority = DataAuthority.OFFICIAL,
        freshnessClass = FreshnessClass.STATIC,
        observedAtEpochMillis = observedAt,
    )

    private data class ResultSource(
        override val providerId: String,
        override val authority: DataAuthority,
        val result: ProviderRead<SeriesResult?>,
    ) : PostResultSourcePort {
        override suspend fun readResult(
            query: PostMatchQuery,
            context: SourceRequestContext,
        ): ProviderRead<SeriesResult?> = result
    }

    private class MemoryArchive(
        var current: PostArchiveSnapshot? = null,
    ) : PostMatchArchiveRepository {
        var saved: PostArchiveSnapshot? = null

        override suspend fun load(matchId: MatchId): PostArchiveSnapshot? = current

        override suspend fun save(snapshot: PostArchiveSnapshot) {
            saved = snapshot
            current = snapshot
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
        return assertNotNull(result).getOrThrow()
    }
}
