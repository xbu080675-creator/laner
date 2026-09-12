package com.laner.app.data.post

import com.laner.core.application.PostArchiveSnapshot
import com.laner.core.domain.CompletedGameRecord
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameId
import com.laner.core.domain.MatchId
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerRef
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.PostDraftSide
import com.laner.core.domain.PostPlayerStats
import com.laner.core.domain.PostTeamStats
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class JsonPostMatchArchiveRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun roundTripPreservesResultGameStatsDraftAndOriginalProvenance() {
        runBlocking {
            val directory = temporaryFolder.newFolder("post-archive")
            val repository = JsonPostMatchArchiveRepository(directory)
            val matchId = MatchId("lol:series:lpl:post-archive")
            val blue = TeamId("lol:team:blg")
            val red = TeamId("lol:team:al")
            val provenance = provenance("tjstats-history", 100_000L, 99_000L, 4L)
            val result = SeriesResult(
                matchId = matchId,
                leftTeamId = blue,
                rightTeamId = red,
                leftWins = 3,
                rightWins = 1,
                bestOf = 5,
                state = SeriesResultState.FINAL,
                winnerTeamId = blue,
                provenance = provenance,
            )
            val player = PlayerRef(
                id = PlayerId("lol:player:knight"),
                handle = "knight",
                teamId = blue,
                role = PlayerRole.MID,
            )
            val game = CompletedGameRecord(
                matchId = matchId,
                gameId = GameId("lol:game:lpl:post-archive:g1"),
                gameNumber = 1,
                blueTeamId = blue,
                redTeamId = red,
                winnerTeamId = blue,
                durationSeconds = 1_842,
                blueStats = PostTeamStats(
                    teamId = blue,
                    kills = 16,
                    gold = 58_200,
                    towers = 9,
                    dragons = 3,
                    barons = 1,
                    heralds = 1,
                    atakhans = null,
                ),
                redStats = PostTeamStats(
                    teamId = red,
                    kills = 8,
                    gold = 49_700,
                    towers = 3,
                    dragons = 1,
                    barons = 0,
                    heralds = null,
                    atakhans = null,
                ),
                players = listOf(
                    PostPlayerStats(
                        player = player,
                        teamId = blue,
                        championId = "Syndra",
                        kills = 7,
                        deaths = 1,
                        assists = 5,
                        cs = 302,
                        gold = 13_500,
                        damageToChampions = 28_700,
                        visionScore = null,
                        items = listOf("item-1", "item-2"),
                        summonerSpellIds = listOf("flash", "teleport"),
                    )
                ),
                draftBlue = PostDraftSide(
                    teamId = blue,
                    picks = listOf("Renekton", "Vi", "Syndra", "Jinx", "Rakan"),
                    bans = listOf("Azir", "Kalista", "Poppy"),
                ),
                draftRed = PostDraftSide(
                    teamId = red,
                    picks = listOf("Gnar", "Sejuani", "Orianna", "Aphelios", "Nautilus"),
                    bans = listOf("Smolder", "Maokai", "Leona"),
                ),
                provenance = provenance,
            )

            repository.save(
                PostArchiveSnapshot(
                    matchId = matchId,
                    result = result,
                    games = listOf(game),
                    storedAtEpochMillis = 120_000L,
                )
            )

            val restored = repository.load(matchId)!!

            assertEquals(3, restored.result?.leftWins)
            assertEquals("tjstats-history", restored.result?.provenance?.providerId)
            assertEquals(1, restored.games.size)
            assertEquals(58_200, restored.games.single().blueStats.gold)
            assertNull(restored.games.single().blueStats.atakhans)
            assertEquals("knight", restored.games.single().players.single().player.handle)
            assertNull(restored.games.single().players.single().visionScore)
            assertEquals(listOf("Renekton", "Vi", "Syndra", "Jinx", "Rakan"), restored.games.single().draftBlue?.picks)
            assertEquals(99_000L, restored.games.single().provenance.sourceTimestampEpochMillis)
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        }
    }

    @Test
    fun corruptJsonFailsExplicitly() {
        val directory = temporaryFolder.newFolder("post-corrupt")
        val matchId = MatchId("lol:series:test:corrupt")
        File(directory, "${sha256(matchId.value)}.json").writeText("{ definitely-not-json")
        val repository = JsonPostMatchArchiveRepository(directory)

        assertThrows(Exception::class.java) {
            runBlocking { repository.load(matchId) }
        }
    }

    @Test
    fun unsupportedSchemaFailsExplicitly() {
        val directory = temporaryFolder.newFolder("post-schema")
        val matchId = MatchId("lol:series:test:schema")
        File(directory, "${sha256(matchId.value)}.json").writeText(
            """{"schema_version":999,"match_id":"${matchId.value}","stored_at_ms":1,"games":[]}"""
        )
        val repository = JsonPostMatchArchiveRepository(directory)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.load(matchId) }
        }
    }

    private fun provenance(
        provider: String,
        observedAt: Long,
        sourceAt: Long,
        revision: Long,
    ): SourceProvenance = SourceProvenance(
        providerId = provider,
        sourceClass = SourceClass.POST_MATCH_SOURCE,
        authority = DataAuthority.VERIFIED_PROVIDER,
        freshnessClass = FreshnessClass.STATIC,
        observedAtEpochMillis = observedAt,
        sourceTimestampEpochMillis = sourceAt,
        revision = revision,
        sourceUri = "https://example.test/post",
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
