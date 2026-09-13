package com.laner.app.data.live

import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.MatchId
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonLiveTimelineMigrationRecoveryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun firstV1ToV2WritePreservesExactVerifiedRecoveryCopy() {
        runBlocking {
            val directory = temporaryFolder.newFolder("timeline-v1-recovery")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)

            val target = directory.listFiles().single { it.extension == "json" }
            val legacyContent = JSONObject(target.readText())
                .put("schema_version", 1)
                .toString()
            target.writeText(legacyContent)

            val loaded = requireNotNull(repository.read(timeline.gameId))
            repository.write(loaded)

            val recovery = File(directory, "${target.name}.schema-v1.bak")
            assertTrue(recovery.exists())
            assertEquals(legacyContent, recovery.readText())
            assertEquals(timeline.gameId.value, JSONObject(recovery.readText()).getString("game_id"))
            assertEquals(2, JSONObject(target.readText()).getInt("schema_version"))

            repository.write(loaded.copy(completed = true))
            assertEquals(legacyContent, recovery.readText())
        }
    }

    @Test
    fun mismatchedExistingRecoveryCopyBlocksV1Overwrite() {
        runBlocking {
            val directory = temporaryFolder.newFolder("timeline-v1-recovery-mismatch")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)

            val target = directory.listFiles().single { it.extension == "json" }
            val legacyContent = JSONObject(target.readText())
                .put("schema_version", 1)
                .toString()
            target.writeText(legacyContent)
            File(directory, "${target.name}.schema-v1.bak").writeText("different")
            val loaded = requireNotNull(repository.read(timeline.gameId))

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.write(loaded) }
            }
            assertEquals(1, JSONObject(target.readText()).getInt("schema_version"))
        }
    }

    @Test
    fun wrongIdentityV1FileCannotBecomeRecoveryPointOrBeOverwritten() {
        runBlocking {
            val directory = temporaryFolder.newFolder("timeline-v1-wrong-identity")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)

            val target = directory.listFiles().single { it.extension == "json" }
            val wrongIdentityV1 = JSONObject(target.readText())
                .put("schema_version", 1)
                .put("game_id", "lol:game:test:other:g9")
                .toString()
            target.writeText(wrongIdentityV1)
            val recovery = File(directory, "${target.name}.schema-v1.bak")

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.write(timeline) }
            }
            assertEquals(wrongIdentityV1, target.readText())
            assertFalse(recovery.exists())
        }
    }

    @Test
    fun malformedV1FileCannotBecomeRecoveryPointOrBeOverwritten() {
        runBlocking {
            val directory = temporaryFolder.newFolder("timeline-v1-malformed")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)

            val target = directory.listFiles().single { it.extension == "json" }
            val malformedV1 = "{\"schema_version\":1,\"match_id\":\"broken\""
            target.writeText(malformedV1)
            val recovery = File(directory, "${target.name}.schema-v1.bak")

            assertThrows(JSONException::class.java) {
                runBlocking { repository.write(timeline) }
            }
            assertEquals(malformedV1, target.readText())
            assertFalse(recovery.exists())
        }
    }

    private fun minimalTimeline(): GameTimeline = GameTimeline(
        matchId = MatchId("lol:series:test:timeline-migration"),
        gameId = GameId("lol:game:test:timeline-migration:g1"),
        gameNumber = 1,
    )
}
