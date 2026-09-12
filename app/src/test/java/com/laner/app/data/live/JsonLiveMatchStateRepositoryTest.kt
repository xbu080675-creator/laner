package com.laner.app.data.live

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonLiveMatchStateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun roundTripPreservesAuthoritativeStateAndLeavesNoTempFile() {
        runBlocking {
            val directory = temporaryFolder.newFolder("live-state")
            val repository = JsonLiveMatchStateRepository(directory)
            val state = LiveMatchState(
                matchId = MatchId("lol:series:lpl:2026:blg-al"),
                lifecycle = MatchLifecycleState.IN_GAME,
                currentGameId = GameId("lol:game:123"),
                currentGameNumber = 2,
                lastObservedAtEpochMillis = 123_456L,
                provenance = SourceProvenance(
                    providerId = "cito-rest",
                    sourceClass = SourceClass.LIVE_MATCH_SOURCE,
                    authority = DataAuthority.VERIFIED_PROVIDER,
                    freshnessClass = FreshnessClass.REALTIME,
                    observedAtEpochMillis = 123_456L,
                    sourceTimestampEpochMillis = 123_450L,
                    revision = 7L,
                    sourceUri = "https://example.invalid/live/123",
                ),
            )

            repository.write(state)

            assertEquals(state, repository.read(state.matchId))
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        }
    }

    @Test
    fun corruptJsonFailsInsteadOfSilentlyReturningEmptyState() {
        runBlocking {
            val directory = temporaryFolder.newFolder("corrupt-state")
            val repository = JsonLiveMatchStateRepository(directory)
            val matchId = MatchId("lol:series:test:corrupt")

            repository.write(LiveMatchState(matchId = matchId))
            val file = directory.listFiles().orEmpty().single { it.extension == "json" }
            file.writeText("{not-json")

            assertThrows(Throwable::class.java) {
                runBlocking { repository.read(matchId) }
            }
        }
    }

    @Test
    fun unsupportedSchemaFailsExplicitly() {
        runBlocking {
            val directory = temporaryFolder.newFolder("schema-state")
            val repository = JsonLiveMatchStateRepository(directory)
            val matchId = MatchId("lol:series:test:schema")

            repository.write(LiveMatchState(matchId = matchId))
            val file = directory.listFiles().orEmpty().single { it.extension == "json" }
            val text = file.readText().replace("\"schema_version\":1", "\"schema_version\":999")
            file.writeText(text)

            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.read(matchId) }
            }
            assertEquals("Unsupported LIVE state schema: 999", error.message)
        }
    }
}
