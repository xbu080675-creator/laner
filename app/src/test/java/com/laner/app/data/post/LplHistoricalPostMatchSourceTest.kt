package com.laner.app.data.post

import com.laner.core.application.PostMatchQuery
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.MatchId
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LplHistoricalPostMatchSourceTest {
    private val matchId = MatchId("lol:series:lpl:fixture:blg-al")
    private val blg = TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming")
    private val al = TeamRef(TeamId("lol:team:al"), "AL", "Anyone's Legend")
    private val query = PostMatchQuery(
        matchId = matchId,
        competitionSlug = "lpl",
        scheduledStartEpochMillis = 1_757_664_000_000L,
        bestOf = 5,
        teams = listOf(blg, al),
    )

    @Test
    fun explicitSeriesAndGameWinnerProduceCanonicalPostFacts() {
        val source = LplHistoricalPostMatchSource(tjstatsAuth = "fixture")
        val data = JSONObject(FULL_FIXTURE)
        val resolved = source.parseMatchDetail(
            data = data,
            query = query,
            ref = LplHistoricalPostMatchSource.HistoricalMatchRef(
                bmid = "90001",
                matchName = "BLG vs AL",
                matchDate = "2025-09-12",
                scoreA = 3,
                scoreB = 1,
                matchStatus = 3,
                leftIsTeamA = true,
            ),
            observedAtEpochMillis = 200_000L,
        )

        assertEquals(SeriesResultState.FINAL, resolved.result?.state)
        assertEquals(3, resolved.result?.leftWins)
        assertEquals(blg.id, resolved.result?.winnerTeamId)
        assertEquals(1, resolved.games.size)
        val game = resolved.games.single()
        assertEquals(GameIdentity.canonical(matchId, 1), game.gameId)
        assertEquals(blg.id, game.blueTeamId)
        assertEquals(blg.id, game.winnerTeamId)
        assertEquals(0, game.redStats.barons)
        assertNull(game.redStats.heralds)
        assertEquals(2, game.players.size)
        assertEquals(PlayerRole.MID, game.players.first { it.player.handle == "knight" }.player.role)
        assertEquals(12_300, game.blueStats.gold)
        assertEquals(7, game.blueStats.kills)
        assertEquals("lpl-tjstats-history", game.provenance.providerId)
    }

    @Test
    fun gameWithoutExplicitWinnerIsNotInventedFromGoldOrKills() {
        val source = LplHistoricalPostMatchSource(tjstatsAuth = "fixture")
        val data = JSONObject(FULL_FIXTURE.replace("\"winnerTeamId\": 10,", ""))

        val resolved = source.parseMatchDetail(
            data = data,
            query = query,
            ref = LplHistoricalPostMatchSource.HistoricalMatchRef(
                bmid = "90001",
                matchName = "BLG vs AL",
                matchDate = "2025-09-12",
                scoreA = 3,
                scoreB = 1,
                matchStatus = 3,
                leftIsTeamA = true,
            ),
            observedAtEpochMillis = 200_000L,
        )

        assertEquals(SeriesResultState.FINAL, resolved.result?.state)
        assertEquals(0, resolved.games.size)
    }

    @Test
    fun swappedProviderSidesAreNormalizedToQueryTeamOrder() {
        val source = LplHistoricalPostMatchSource(tjstatsAuth = "fixture")
        val data = JSONObject(FULL_FIXTURE)
            .put("teamAScore", 1)
            .put("teamBScore", 3)

        val resolved = source.parseMatchDetail(
            data = data,
            query = query,
            ref = LplHistoricalPostMatchSource.HistoricalMatchRef(
                bmid = "90002",
                matchName = "AL vs BLG",
                matchDate = "2025-09-12",
                scoreA = 1,
                scoreB = 3,
                matchStatus = 3,
                leftIsTeamA = false,
            ),
            observedAtEpochMillis = 200_000L,
        )

        assertEquals(3, resolved.result?.leftWins)
        assertEquals(1, resolved.result?.rightWins)
        assertEquals(blg.id, resolved.result?.winnerTeamId)
    }

    private companion object {
        val FULL_FIXTURE = """
            {
              "teamAId": 10,
              "teamBId": 20,
              "teamAScore": 3,
              "teamBScore": 1,
              "matchStatus": 3,
              "matchInfos": [
                {
                  "bo": 1,
                  "gameTime": "30:42",
                  "blueTeamId": 10,
                  "winnerTeamId": 10,
                  "teamInfos": [
                    {
                      "teamId": 10,
                      "turretAmount": 9,
                      "dragonAmount": 3,
                      "baronAmount": 1,
                      "players": [
                        {
                          "playerName": "knight",
                          "playerLocation": "MID",
                          "championId": "Syndra",
                          "kills": 7,
                          "deaths": 1,
                          "assists": 5,
                          "gold": 12300,
                          "cs": 286
                        }
                      ]
                    },
                    {
                      "teamId": 20,
                      "gold": 10100,
                      "kills": 3,
                      "turretAmount": 2,
                      "dragonAmount": 1,
                      "baronAmount": 0,
                      "players": [
                        {
                          "playerName": "Shanks",
                          "playerLocation": "MID",
                          "championId": "Orianna",
                          "kills": 3,
                          "deaths": 7,
                          "assists": 2,
                          "gold": 10100,
                          "cs": 260
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
