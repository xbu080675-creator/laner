package com.laner.app.data.riot

import com.laner.core.application.PostMatchQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.MatchId
import com.laner.core.domain.SourceClass
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RiotGlobalHistoricalTimelineSourceTest {
    private val source = RiotGlobalHistoricalTimelineSource(
        apiKey = "fixture-key",
        identityRepository = InMemoryIdentityRepository(),
    )

    @Test
    fun sameTeamMappingContractHandlesLckAndWorlds() {
        val lckQuery = query("lck", "T1", "GEN")
        val worldsQuery = query("worlds", "BLG", "G2")

        val lckMatch = JSONObject(
            """{"teams":[
              {"id":"riot-t1","code":"T1","name":"T1"},
              {"id":"riot-gen","code":"GEN","name":"Gen.G"}
            ]}"""
        )
        val worldsMatch = JSONObject(
            """{"teams":[
              {"id":"riot-blg","code":"BLG","name":"Bilibili Gaming"},
              {"id":"riot-g2","code":"G2","name":"G2 Esports"}
            ]}"""
        )

        assertEquals(TeamId("lol:team:t1"), source.providerTeamMap(lckMatch, lckQuery)["riot-t1"])
        assertEquals(TeamId("lol:team:gen"), source.providerTeamMap(lckMatch, lckQuery)["riot-gen"])
        assertEquals(TeamId("lol:team:blg"), source.providerTeamMap(worldsMatch, worldsQuery)["riot-blg"])
        assertEquals(TeamId("lol:team:g2"), source.providerTeamMap(worldsMatch, worldsQuery)["riot-g2"])
    }

    @Test
    fun realFrameShapeBecomesCanonicalHistoricalSnapshot() {
        val query = query("lck", "T1", "GEN")
        val mapping = mapOf(
            "riot-t1" to TeamId("lol:team:t1"),
            "riot-gen" to TeamId("lol:team:gen"),
        )
        val metadata = JSONObject(
            """{
              "blueTeamMetadata":{"esportsTeamId":"riot-t1","participantMetadata":[
                {"participantId":1,"summonerName":"Faker","championId":"Ahri"}
              ]},
              "redTeamMetadata":{"esportsTeamId":"riot-gen","participantMetadata":[
                {"participantId":6,"summonerName":"Chovy","championId":"Syndra"}
              ]}
            }"""
        )
        val frame = JSONObject(
            """{
              "blueTeam":{"totalGold":10200,"totalKills":4,"towers":2,"dragons":["infernal"],"barons":0,
                "participants":[{"participantId":1,"level":9,"kills":2,"deaths":1,"assists":1,"creepScore":101,"totalGold":4550}]},
              "redTeam":{"totalGold":9800,"totalKills":3,"towers":1,"dragons":[],"barons":0,
                "participants":[{"participantId":6,"level":9,"kills":1,"deaths":2,"assists":2,"creepScore":98,"totalGold":4300}]}
            }"""
        )

        val historical = source.parseFrame(
            query = query,
            gameNumber = 2,
            frame = frame,
            metadata = metadata,
            providerTeamMap = mapping,
            elapsedSeconds = 600,
            observedAtEpochMillis = 20_000L,
            sourceTimestampEpochMillis = 19_500L,
        ) ?: error("fixture should produce a historical frame")

        val snapshot = historical.snapshot
        assertEquals(GameIdentity.canonical(query.matchId, 2), snapshot.game.gameId)
        assertEquals(10_200, snapshot.blue.gold)
        assertEquals(1, snapshot.blue.dragons)
        assertEquals(9_800, snapshot.red.gold)
        assertEquals("Ahri", snapshot.players.first { it.playerId.value.contains("faker") }.championId)
        assertEquals(SourceClass.POST_MATCH_SOURCE, historical.provenance.sourceClass)
    }

    private fun query(slug: String, left: String, right: String): PostMatchQuery = PostMatchQuery(
        matchId = MatchId("lol:series:$slug:test:${left.lowercase()}-${right.lowercase()}:bo5"),
        competitionSlug = slug,
        scheduledStartEpochMillis = 1_800_000_000_000L,
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
