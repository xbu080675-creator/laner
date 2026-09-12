package com.laner.core.application

import com.laner.core.domain.AwardKind
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerRef
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ReplayProvider
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.VerifiedPostAward
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostMatchServiceTest {
    private val matchId = MatchId("lol:series:lpl:2026:blg-al")
    private val otherMatchId = MatchId("lol:series:lpl:other")
    private val blue = TeamId("lol:team:blg")
    private val red = TeamId("lol:team:al")
    private val context = SourceRequestContext(100_000L, "post-test")

    @Test
    fun awardFailureDoesNotEraseValidSeriesResult() = runSuspend {
        val service = PostMatchService(
            resultSources = listOf(ResultSource("official", DataAuthority.OFFICIAL, ProviderRead.Success(finalResult("official")))),
            gameSources = emptyList(),
            awardSources = listOf(AwardSource("awards", DataAuthority.VERIFIED_PROVIDER, ProviderRead.Failure(failure("awards")))),
            replaySources = emptyList(),
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.DEGRADED, snapshot.status)
        assertEquals(3, snapshot.bundle.result?.leftWins)
        assertTrue(snapshot.bundle.awards.isEmpty())
        assertEquals(1, snapshot.failures.size)
    }

    @Test
    fun conflictingAwardWinnersAreVisibleAndStrongestFactIsSelected() = runSuspend {
        val bin = player("bin", "Bin", blue)
        val knight = player("knight", "knight", blue)
        val providerAward = award("provider-awards", DataAuthority.PROVIDER, bin)
        val officialAward = award("official-awards", DataAuthority.OFFICIAL, knight)
        val service = PostMatchService(
            resultSources = emptyList(),
            gameSources = emptyList(),
            awardSources = listOf(
                AwardSource("provider-awards", DataAuthority.PROVIDER, ProviderRead.Success(listOf(providerAward))),
                AwardSource("official-awards", DataAuthority.OFFICIAL, ProviderRead.Success(listOf(officialAward))),
            ),
            replaySources = emptyList(),
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.CONFLICT, snapshot.status)
        assertEquals(knight.id, snapshot.bundle.awards.single().player.id)
        assertEquals("AWARD", snapshot.conflicts.single().factType)
    }

    @Test
    fun wrongMatchFactIsRejectedBeforeAggregation() = runSuspend {
        val wrong = finalResult("bad-provider", id = otherMatchId)
        val service = PostMatchService(
            resultSources = listOf(ResultSource("bad-provider", DataAuthority.OFFICIAL, ProviderRead.Success(wrong))),
            gameSources = emptyList(),
            awardSources = emptyList(),
            replaySources = emptyList(),
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.UNAVAILABLE, snapshot.status)
        assertNull(snapshot.bundle.result)
        assertEquals("LNR-APP-POST-001", snapshot.failures.single().code.value)
    }

    @Test
    fun freshFinalResultCanBeatStaleHigherAuthorityPartialResult() = runSuspend {
        val staleOfficial = SeriesResult(
            matchId = matchId,
            leftTeamId = blue,
            rightTeamId = red,
            leftWins = 1,
            rightWins = 0,
            bestOf = 5,
            state = SeriesResultState.PARTIAL,
            provenance = provenance("official", DataAuthority.OFFICIAL, 10_000L),
        )
        val final = finalResult("verified-final", observedAt = 90_000L, authority = DataAuthority.VERIFIED_PROVIDER)
        val service = PostMatchService(
            resultSources = listOf(
                ResultSource("official", DataAuthority.OFFICIAL, ProviderRead.Success(staleOfficial)),
                ResultSource("verified-final", DataAuthority.VERIFIED_PROVIDER, ProviderRead.Success(final)),
            ),
            gameSources = emptyList(),
            awardSources = emptyList(),
            replaySources = emptyList(),
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(SeriesResultState.FINAL, snapshot.bundle.result?.state)
        assertEquals(3, snapshot.bundle.result?.leftWins)
        assertEquals(PostMatchLoadStatus.CONFLICT, snapshot.status)
    }

    @Test
    fun differentReplayProvidersRemainAvailableTogether() = runSuspend {
        val riot = replay("riot-vod", ReplayProvider.RIOT, "https://lolesports.com/vod/1")
        val bili = replay("bili-vod", ReplayProvider.BILIBILI, "https://www.bilibili.com/video/BV1")
        val service = PostMatchService(
            resultSources = emptyList(),
            gameSources = emptyList(),
            awardSources = emptyList(),
            replaySources = listOf(
                ReplaySource("riot-vod", DataAuthority.OFFICIAL, ProviderRead.Success(listOf(riot))),
                ReplaySource("bili-vod", DataAuthority.VERIFIED_PROVIDER, ProviderRead.Success(listOf(bili))),
            ),
        )

        val snapshot = service.load(PostMatchQuery(matchId), context)

        assertEquals(PostMatchLoadStatus.READY, snapshot.status)
        assertEquals(setOf(ReplayProvider.RIOT, ReplayProvider.BILIBILI), snapshot.bundle.replays.map { it.provider }.toSet())
    }

    private fun finalResult(
        provider: String,
        id: MatchId = matchId,
        observedAt: Long = 90_000L,
        authority: DataAuthority = DataAuthority.OFFICIAL,
    ): SeriesResult = SeriesResult(
        matchId = id,
        leftTeamId = blue,
        rightTeamId = red,
        leftWins = 3,
        rightWins = 1,
        bestOf = 5,
        state = SeriesResultState.FINAL,
        winnerTeamId = blue,
        provenance = provenance(provider, authority, observedAt),
    )

    private fun award(
        provider: String,
        authority: DataAuthority,
        player: PlayerRef,
    ): VerifiedPostAward = VerifiedPostAward(
        matchId = matchId,
        kind = AwardKind.SERIES_MVP,
        player = player,
        label = "Series MVP",
        provenance = provenance(provider, authority, 90_000L),
    )

    private fun replay(providerId: String, provider: ReplayProvider, url: String): ReplayAsset = ReplayAsset(
        matchId = matchId,
        gameNumber = 1,
        provider = provider,
        sourceUrl = url,
        provenance = provenance(providerId, if (provider == ReplayProvider.RIOT) DataAuthority.OFFICIAL else DataAuthority.VERIFIED_PROVIDER, 90_000L),
    )

    private fun player(id: String, handle: String, team: TeamId): PlayerRef = PlayerRef(
        id = PlayerId("lol:player:$id"),
        handle = handle,
        teamId = team,
        role = PlayerRole.UNKNOWN,
    )

    private fun provenance(provider: String, authority: DataAuthority, observedAt: Long): SourceProvenance = SourceProvenance(
        providerId = provider,
        sourceClass = SourceClass.POST_MATCH_SOURCE,
        authority = authority,
        freshnessClass = FreshnessClass.STATIC,
        observedAtEpochMillis = observedAt,
    )

    private fun failure(provider: String): DiagnosticFailure = DiagnosticFailure(
        code = ErrorCode("LNR-SRC-POST-TEST"),
        message = "$provider failed",
        retryable = true,
    )

    private data class ResultSource(
        override val providerId: String,
        override val authority: DataAuthority,
        val result: ProviderRead<SeriesResult?>,
    ) : PostResultSourcePort {
        override suspend fun readResult(query: PostMatchQuery, context: SourceRequestContext): ProviderRead<SeriesResult?> = result
    }

    private data class AwardSource(
        override val providerId: String,
        override val authority: DataAuthority,
        val result: ProviderRead<List<VerifiedPostAward>>,
    ) : PostAwardSourcePort {
        override suspend fun readAwards(query: PostMatchQuery, context: SourceRequestContext): ProviderRead<List<VerifiedPostAward>> = result
    }

    private data class ReplaySource(
        override val providerId: String,
        override val authority: DataAuthority,
        val result: ProviderRead<List<ReplayAsset>>,
    ) : ReplaySourcePort {
        override suspend fun readReplays(query: PostMatchQuery, context: SourceRequestContext): ProviderRead<List<ReplayAsset>> = result
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
