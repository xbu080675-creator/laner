package com.laner.app.data.riot

import com.laner.core.application.PostMatchQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.MatchId
import com.laner.core.domain.ReplayProvider
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiotGlobalReplaySourceTest {
    private val repository = InMemoryIdentityRepository()
    private val source = RiotGlobalReplaySource(apiKey = "fixture-key", identityRepository = repository)

    @Test
    fun scheduleIdentityResolutionUsesSameContractForLckAndWorlds() {
        val start = 1_800_000_000_000L
        val root = JSONObject(
            """{
              "data":{"schedule":{"events":[
                {"id":"event-lck","type":"match","startTime":"2027-01-15T08:00:00Z","league":{"slug":"lck"},"match":{"id":"match-lck","teams":[{"code":"T1"},{"code":"GEN"}]}},
                {"id":"event-worlds","type":"match","startTime":"2027-01-15T08:00:00Z","league":{"slug":"worlds"},"match":{"id":"match-worlds","teams":[{"code":"BLG"},{"code":"G2"}]}}
              ]}}}
            }"""
        )
        val lck = query("lck", start, "T1", "GEN")
        val worlds = query("worlds", start, "BLG", "G2")

        assertEquals("event-lck", source.parseScheduleCandidates(root, lck).single().eventId)
        assertEquals("event-worlds", source.parseScheduleCandidates(root, worlds).single().eventId)
    }

    @Test
    fun eventDetailsProducesCanonicalGameReplayWithoutProviderGameIdLeakage() {
        val query = query("lck", 1_800_000_000_000L, "T1", "GEN")
        val root = JSONObject(
            """{
              "data":{"event":{"match":{"games":[
                {"id":"provider-game-999","number":2,"vods":[
                  {"provider":"youtube","locale":"en-US","parameter":"abc123","offset":18}
                ]}
              ]}}}
            }"""
        )

        val replay = source.parseEventDetails(root, query, 10_000L, "event-lck").single()

        assertEquals(2, replay.gameNumber)
        assertEquals(GameIdentity.canonical(query.matchId, 2), replay.gameId)
        assertEquals(ReplayProvider.YOUTUBE, replay.provider)
        assertEquals("abc123", replay.externalMediaId)
        assertEquals("https://www.youtube.com/watch?v=abc123", replay.sourceUrl)
        assertEquals(18, replay.offsetSeconds)
        assertTrue(replay.gameId!!.value.contains(query.matchId.value))
        assertTrue(!replay.gameId!!.value.contains("provider-game-999"))
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
        private val values = mutableMapOf<String, ProviderMatchIdentity>()
        override suspend fun upsert(identity: ProviderMatchIdentity) {
            values["${identity.providerId}|${identity.matchId.value}"] = identity
        }
        override suspend fun find(providerId: String, matchId: MatchId): ProviderMatchIdentity? =
            values["$providerId|${matchId.value}"]
    }
}
