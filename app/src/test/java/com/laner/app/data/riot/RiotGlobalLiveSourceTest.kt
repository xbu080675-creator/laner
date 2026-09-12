package com.laner.app.data.riot

import com.laner.core.application.LiveSnapshotQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.MatchId
import com.laner.core.domain.SourceClass
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiotGlobalLiveSourceTest {
    private val repository = InMemoryIdentityRepository()
    private val source = RiotGlobalLiveSource(
        apiKey = "fixture-key",
        identityRepository = repository,
    )

    @Test
    fun realLiveFrameShapePreservesCanonicalIdentityAndStats() {
        val query = LiveSnapshotQuery(
            matchId = MatchId("lol:series:lpl:test:blg-al:bo5"),
            gameId = GameIdentity.canonical(MatchId("lol:series:lpl:test:blg-al:bo5"), 3),
            gameNumber = 3,
            teams = listOf(
                TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming"),
                TeamRef(TeamId("lol:team:al"), "AL", "Anyone's Legend"),
            ),
        )
        val mapping = mapOf(
            "riot-blg" to TeamId("lol:team:blg"),
            "riot-al" to TeamId("lol:team:al"),
        )
        val metadata = JSONObject(
            """{
              "blueTeamMetadata":{"esportsTeamId":"riot-blg","participantMetadata":[
                {"participantId":1,"summonerName":"Knight","championId":"Syndra"}
              ]},
              "redTeamMetadata":{"esportsTeamId":"riot-al","participantMetadata":[
                {"participantId":6,"summonerName":"Shanks","championId":"Ahri"}
              ]}
            }"""
        )
        val frame = JSONObject(
            """{
              "rfc460Timestamp":"2026-09-12T14:02:30Z",
              "gameTime":750,
              "blueTeam":{"totalGold":13500,"totalKills":7,"towers":3,"dragons":["mountain","infernal"],"barons":0,
                "participants":[{"participantId":1,"level":11,"kills":3,"deaths":1,"assists":4,"creepScore":132,"totalGold":5600}]},
              "redTeam":{"totalGold":12800,"totalKills":5,"towers":2,"dragons":["cloud"],"barons":0,
                "participants":[{"participantId":6,"level":10,"kills":2,"deaths":2,"assists":3,"creepScore":125,"totalGold":5250}]}
            }"""
        )

        val parsed = source.parseSnapshot(
            query = query,
            frame = frame,
            metadata = metadata,
            providerTeamMap = mapping,
            observedAtEpochMillis = 1_789_222_560_000L,
        ) ?: error("fixture should produce a live frame")

        val snapshot = parsed.snapshot
        assertEquals(query.gameId, snapshot.game.gameId)
        assertEquals(query.matchId, snapshot.game.matchId)
        assertEquals(3, snapshot.game.gameNumber)
        assertEquals(13_500, snapshot.blue.gold)
        assertEquals(7, snapshot.blue.kills)
        assertEquals(2, snapshot.blue.dragons)
        assertEquals(12_800, snapshot.red.gold)
        assertEquals("Syndra", snapshot.players.first { it.playerId.value.contains("knight") }.championId)
        assertEquals(132, snapshot.players.first { it.playerId.value.contains("knight") }.creepScore)
        assertEquals(SourceClass.LIVE_MATCH_SOURCE, parsed.provenance.sourceClass)
    }

    @Test
    fun missingProviderTeamMappingDoesNotInventLiveSnapshot() {
        val matchId = MatchId("lol:series:lck:test:t1-gen:bo3")
        val query = LiveSnapshotQuery(
            matchId = matchId,
            gameId = GameIdentity.canonical(matchId, 1),
            gameNumber = 1,
            teams = listOf(
                TeamRef(TeamId("lol:team:t1"), "T1", "T1"),
                TeamRef(TeamId("lol:team:gen"), "GEN", "Gen.G"),
            ),
        )
        val metadata = JSONObject(
            """{
              "blueTeamMetadata":{"esportsTeamId":"unknown-blue"},
              "redTeamMetadata":{"esportsTeamId":"unknown-red"}
            }"""
        )
        val frame = JSONObject(
            """{
              "blueTeam":{"totalGold":1000},
              "redTeam":{"totalGold":1000}
            }"""
        )

        assertNull(
            source.parseSnapshot(
                query = query,
                frame = frame,
                metadata = metadata,
                providerTeamMap = emptyMap(),
                observedAtEpochMillis = 20_000L,
            )
        )
    }

    private class InMemoryIdentityRepository : ProviderMatchIdentityRepository {
        override suspend fun upsert(identity: ProviderMatchIdentity) = Unit
        override suspend fun find(providerId: String, matchId: MatchId): ProviderMatchIdentity? = null
    }
}
