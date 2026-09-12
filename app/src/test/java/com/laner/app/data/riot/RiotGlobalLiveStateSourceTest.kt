package com.laner.app.data.riot

import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.MatchId
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RiotGlobalLiveStateSourceTest {
    private val source = RiotGlobalLiveStateSource("fixture-key", InMemoryIdentityRepository())

    @Test
    fun sameDiscoveryParserHandlesLplAndLck() {
        val startIso = "2026-09-12T14:00:00Z"
        val start = java.time.Instant.parse(startIso).toEpochMilli()
        val root = JSONObject(
            """{
              "data":{"schedule":{"events":[
                {"id":"lpl-event","type":"match","startTime":"$startIso","match":{"id":"lpl-match","teams":[{"code":"BLG"},{"code":"AL"}]}},
                {"id":"lck-event","type":"match","startTime":"$startIso","match":{"id":"lck-match","teams":[{"code":"T1"},{"code":"GEN"}]}}
              ]}}}
            }"""
        )

        val lpl = source.parseScheduleCandidates(root, query(start, "BLG", "AL")).single()
        val lck = source.parseScheduleCandidates(root, query(start, "T1", "GEN")).single()

        assertEquals("lpl-event", lpl.eventId)
        assertEquals("lck-event", lck.eventId)
    }

    @Test
    fun wrongTeamsAreNotAcceptedEvenAtSameTime() {
        val startIso = "2026-09-12T14:00:00Z"
        val start = java.time.Instant.parse(startIso).toEpochMilli()
        val root = JSONObject(
            """{"data":{"schedule":{"events":[
              {"id":"event","type":"match","startTime":"$startIso","match":{"id":"match","teams":[{"code":"BLG"},{"code":"AL"}]}}
            ]}}}"""
        )

        assertEquals(0, source.parseScheduleCandidates(root, query(start, "BLG", "TES")).size)
    }

    private fun query(start: Long, left: String, right: String) = LiveMatchSourceQuery(
        matchId = MatchId("lol:series:test:${left.lowercase()}-${right.lowercase()}:bo5"),
        scheduledStartEpochMillis = start,
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
