package com.laner.app.data.archive

import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.domain.MatchId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonProviderMatchIdentityRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun roundTripPreservesProviderIdsAndNewestObservationWins() {
        runBlocking {
            val file = File(temporaryFolder.newFolder("identity"), "provider-match.json")
            val repository = JsonProviderMatchIdentityRepository(file)
            val matchId = MatchId("lol:series:lck:100:t1-gen:bo5")
            repository.upsert(ProviderMatchIdentity("riot-lolesports", matchId, "event-old", "match-old", 100L))
            repository.upsert(ProviderMatchIdentity("riot-lolesports", matchId, "event-new", "match-new", 200L))
            repository.upsert(ProviderMatchIdentity("riot-lolesports", matchId, "event-stale", "match-stale", 150L))

            val restored = repository.find("riot-lolesports", matchId)!!
            assertEquals("event-new", restored.externalEventId)
            assertEquals("match-new", restored.externalMatchId)
            assertEquals(200L, restored.observedAtEpochMillis)
        }
    }

    @Test
    fun unsupportedSchemaFailsLoudly() {
        runBlocking {
            val file = File(temporaryFolder.newFolder("identity-corrupt"), "provider-match.json")
            file.writeText("""{"schema_version":99,"mappings":[]}""")
            val repository = JsonProviderMatchIdentityRepository(file)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.find("riot-lolesports", MatchId("lol:series:test")) }
            }
        }
    }
}
