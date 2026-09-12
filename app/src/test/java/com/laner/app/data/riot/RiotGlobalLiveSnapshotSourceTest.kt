package com.laner.app.data.riot

import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.MatchId
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RiotGlobalLiveSnapshotSourceTest {
    private val source = RiotGlobalLiveSnapshotSource(
        apiKey = "fixture-key",
        identityRepository = NoopIdentityRepository,
    )
    private val blue = TeamRef(TeamId("team:blg"), "BLG", "Bilibili Gaming")
    private val red = TeamRef(TeamId("team:al"), "AL", "Anyone's Legend")
    private val query = LiveMatchSourceQuery(
        matchId = MatchId("match:blg-al"),
        scheduledStartEpochMillis = 1000L,
        teams = listOf(blue, red),
    )

    @Test
    fun realFrameFixtureBecomesCanonicalSnapshotWithoutInventingMissingStats() {
        val frame = JSONObject(
            """{
              "gameState":"in_game",
              "blueTeam":{"totalGold":21345,"totalKills":5,"towers":2,"dragons":["infernal"],"barons":0,
                "participants":[{"participantId":1,"level":11,"kills":2,"deaths":1,"assists":3,"creepScore":181,"totalGold":8123}]},
              "redTeam":{"totalGold":20111,"totalKills":3,"towers":1,"dragons":[],
                "participants":[{"participantId":6,"level":10,"kills":1,"deaths":2,"assists":1,"creepScore":170,"totalGold":7600}]}
            }"""
        )
        val metadata = JSONObject(
            """{
              "blueTeamMetadata":{"esportsTeamId":"100","participantMetadata":[{"participantId":1,"summonerName":"Knight","championId":"Syndra"}]},
              "redTeamMetadata":{"esportsTeamId":"200","participantMetadata":[{"participantId":6,"summonerName":"Shanks","championId":"Azir"}]}
            }"""
        )

        val snapshot = source.parseSnapshot(
            query = query,
            gameNumber = 4,
            frame = frame,
            metadata = metadata,
            providerTeamMap = mapOf("100" to blue.id, "200" to red.id),
        )!!

        assertEquals("match:blg-al:game:4", snapshot.game.gameId.value)
        assertEquals(21345, snapshot.blue.gold)
        assertEquals(5, snapshot.blue.kills)
        assertEquals(1, snapshot.blue.dragons)
        assertEquals(0, snapshot.blue.barons)
        assertNull(snapshot.red.barons)
        assertEquals("Syndra", snapshot.players.first().championId)
        assertEquals(181, snapshot.players.first().creepScore)
    }

    @Test
    fun unknownProviderTeamIdentityRejectsFrame() {
        val frame = JSONObject("""{"blueTeam":{},"redTeam":{}}""")
        val metadata = JSONObject(
            """{"blueTeamMetadata":{"esportsTeamId":"x"},"redTeamMetadata":{"esportsTeamId":"y"}}"""
        )
        assertNull(source.parseSnapshot(query, 1, frame, metadata, emptyMap()))
    }

    private object NoopIdentityRepository : ProviderMatchIdentityRepository {
        override suspend fun find(providerId: String, matchId: MatchId) = null
        override suspend fun upsert(identity: com.laner.core.application.ProviderMatchIdentity) = Unit
    }
}
