package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PostMatchTest {
    private val matchId = MatchId("lol:series:lpl:2026:blg-al")
    private val blue = TeamId("lol:team:blg")
    private val red = TeamId("lol:team:al")
    private val player = PlayerRef(
        id = PlayerId("lol:player:bin"),
        handle = "Bin",
        teamId = blue,
        role = PlayerRole.TOP,
    )

    @Test
    fun finalSeriesMustDeclareWinnerThatMatchesScore() {
        val result = SeriesResult(
            matchId = matchId,
            leftTeamId = blue,
            rightTeamId = red,
            leftWins = 3,
            rightWins = 1,
            bestOf = 5,
            state = SeriesResultState.FINAL,
            winnerTeamId = blue,
            provenance = postProvenance(),
        )

        assertEquals(blue, result.winnerTeamId)
    }

    @Test
    fun tiedOrWinnerlessFinalSeriesIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SeriesResult(
                matchId = matchId,
                leftTeamId = blue,
                rightTeamId = red,
                leftWins = 2,
                rightWins = 2,
                bestOf = 5,
                state = SeriesResultState.FINAL,
                winnerTeamId = null,
                provenance = postProvenance(),
            )
        }
    }

    @Test
    fun partialSeriesCannotPretendItAlreadyHasWinner() {
        assertFailsWith<IllegalArgumentException> {
            SeriesResult(
                matchId = matchId,
                leftTeamId = blue,
                rightTeamId = red,
                leftWins = 1,
                rightWins = 0,
                bestOf = 5,
                state = SeriesResultState.PARTIAL,
                winnerTeamId = blue,
                provenance = postProvenance(),
            )
        }
    }

    @Test
    fun missingPlayerStatisticsRemainUnknownInsteadOfZero() {
        val stats = PostPlayerStats(
            player = player,
            teamId = blue,
            championId = "Camille",
        )

        assertNull(stats.kills)
        assertNull(stats.gold)
        assertNull(stats.damageToChampions)
    }

    @Test
    fun derivedStatisticsCannotPublishMvpOrPog() {
        assertFailsWith<IllegalArgumentException> {
            VerifiedPostAward(
                matchId = matchId,
                kind = AwardKind.SERIES_MVP,
                player = player,
                label = "Series MVP",
                provenance = postProvenance(authority = DataAuthority.DERIVED),
            )
        }
    }

    @Test
    fun replayMetadataIsPostFactNotPlayerImplementation() {
        val asset = ReplayAsset(
            matchId = matchId,
            gameNumber = 1,
            provider = ReplayProvider.BILIBILI,
            sourceUrl = "https://www.bilibili.com/video/BVtest",
            externalMediaId = "BVtest",
            externalPartId = "12345",
            offsetSeconds = 88,
            provenance = postProvenance(authority = DataAuthority.VERIFIED_PROVIDER),
        )

        assertEquals(ReplayProvider.BILIBILI, asset.provider)
        assertEquals(88, asset.offsetSeconds)
    }

    private fun postProvenance(
        authority: DataAuthority = DataAuthority.OFFICIAL,
    ): SourceProvenance = SourceProvenance(
        providerId = "test-post",
        sourceClass = SourceClass.POST_MATCH_SOURCE,
        authority = authority,
        freshnessClass = FreshnessClass.STATIC,
        observedAtEpochMillis = 1_000L,
    )
}
