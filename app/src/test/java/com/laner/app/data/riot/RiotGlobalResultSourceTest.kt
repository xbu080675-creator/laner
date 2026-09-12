package com.laner.app.data.riot

import com.laner.core.application.PostMatchQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.MatchId
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RiotGlobalResultSourceTest {
    private val source = RiotGlobalResultSource("fixture-key", InMemoryIdentityRepository())

    @Test
    fun sameParserHandlesLckAndWorldsFinalScores() {
        val startIso = "2027-01-15T08:00:00Z"
        val start = java.time.Instant.parse(startIso).toEpochMilli()
        val root = JSONObject(
            """{
              "data":{"schedule":{"events":[
                {"id":"lck-e","type":"match","state":"completed","startTime":"$startIso","league":{"slug":"lck"},"match":{"id":"lck-m","teams":[
                  {"code":"T1","result":{"gameWins":3,"outcome":"win"}},
                  {"code":"GEN","result":{"gameWins":1,"outcome":"loss"}}
                ]}},
                {"id":"worlds-e","type":"match","state":"completed","startTime":"$startIso","league":{"slug":"worlds"},"match":{"id":"worlds-m","teams":[
                  {"code":"BLG","result":{"gameWins":2,"outcome":"loss"}},
                  {"code":"G2","result":{"gameWins":3,"outcome":"win"}}
                ]}}
              ]}}}
            }"""
        )

        val lckCandidate = source.parseCandidates(root, query("lck", start, "T1", "GEN")).single()
        val worldsCandidate = source.parseCandidates(root, query("worlds", start, "BLG", "G2")).single()

        assertEquals(3, lckCandidate.leftWins)
        assertEquals(1, lckCandidate.rightWins)
        assertEquals(2, worldsCandidate.leftWins)
        assertEquals(3, worldsCandidate.rightWins)
    }

    @Test
    fun completedCandidateCarriesExplicitWinnerEvidence() {
        val startIso = "2027-01-15T08:00:00Z"
        val start = java.time.Instant.parse(startIso).toEpochMilli()
        val root = JSONObject(
            """{
              "data":{"schedule":{"events":[
                {"id":"lck-e","type":"match","state":"completed","startTime":"$startIso","league":{"slug":"lck"},"match":{"id":"lck-m","teams":[
                  {"code":"T1","result":{"gameWins":3,"outcome":"win"}},
                  {"code":"GEN","result":{"gameWins":2,"outcome":"loss"}}
                ]}}
              ]}}}
            }"""
        )
        val candidate = source.parseCandidates(root, query("lck", start, "T1", "GEN")).single()
        assertEquals("win", candidate.leftOutcome)
        assertEquals("loss", candidate.rightOutcome)
        assertEquals(true, candidate.completed)
        assertNotNull(candidate.eventId)
    }

    private fun query(slug: String, start: Long, left: String, right: String): PostMatchQuery = PostMatchQuery(
        matchId = MatchId("lol:series:$slug:test:$left-$right:bo5"),
        competitionSlug = slug,
        scheduledStartEpochMillis = start,
        bestOf = 5,
        teams = listOf(
            TeamRef(TeamId("lol:team:${left.lowercase()}"), left, left),
            TeamRef(TeamId("lol:team:${right.lowercase()}"), right, right),
        ),
    )

    private class InMemoryIdentityRepository : ProviderMatchIdentityRepository {
        override suspend fun upsert(identity: ProviderMatchIdentity) = Unit
        override suspend fun find(providerId: String, matchId: MatchId): ProviderMatchIdentity? = null
    }
}
