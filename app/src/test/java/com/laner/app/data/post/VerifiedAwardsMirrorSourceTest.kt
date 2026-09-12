package com.laner.app.data.post

import com.laner.core.application.PostMatchQuery
import com.laner.core.domain.MatchId
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VerifiedAwardsMirrorSourceTest {
    private val source = VerifiedAwardsMirrorSource(endpoints = emptyList())
    private val matchId = MatchId("lol:series:worlds:test:t1-kt")

    @Test
    fun parserMatchesTeamsAndDateWithoutInventingGameId() {
        val query = PostMatchQuery(
            matchId = matchId,
            scheduledStartEpochMillis = Instant.parse("2025-11-09T10:00:00Z").toEpochMilli(),
            teams = listOf(
                TeamRef(TeamId("lol:team:t1"), "T1", "T1"),
                TeamRef(TeamId("lol:team:kt"), "KT", "KT Rolster"),
            ),
        )
        val root = JSONObject(
            """{
              "schemaVersion": 1,
              "awards": [{
                "date": "2025-11-09",
                "teams": ["T1", "KT"],
                "seriesMvp": {
                  "playerName": "Gumayusi",
                  "team": "T1",
                  "role": "BOT",
                  "award": "Finals MVP",
                  "source": "Liquipedia",
                  "sourceUrl": "https://example.invalid/series"
                },
                "gameMvps": [{
                  "game": 2,
                  "playerName": "BDD",
                  "team": "KT",
                  "role": "MID",
                  "award": "POG",
                  "sourceUrl": "https://example.invalid/game2"
                }]
              }]
            }"""
        )

        val awards = source.parse(root, query, observedAtEpochMillis = 1234L)

        assertEquals(2, awards.size)
        assertEquals("Gumayusi", awards[0].player.handle)
        assertNull(awards[0].gameId)
        assertNull(awards[0].gameNumber)
        assertEquals("BDD", awards[1].player.handle)
        assertNull(awards[1].gameId)
        assertEquals(2, awards[1].gameNumber)
        assertEquals("https://example.invalid/game2", awards[1].provenance.sourceUri)
    }

    @Test
    fun mismatchedTeamsDoNotProduceAwards() {
        val query = PostMatchQuery(
            matchId = matchId,
            scheduledStartEpochMillis = Instant.parse("2025-11-09T10:00:00Z").toEpochMilli(),
            teams = listOf(
                TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming"),
                TeamRef(TeamId("lol:team:gen"), "GEN", "Gen.G"),
            ),
        )
        val root = JSONObject(
            """{"schemaVersion":1,"awards":[{"date":"2025-11-09","teams":["T1","KT"],"seriesMvp":{"playerName":"Gumayusi","team":"T1"}}]}"""
        )

        assertTrue(source.parse(root, query, 1234L).isEmpty())
    }
}
