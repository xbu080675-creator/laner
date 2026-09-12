package com.laner.app.data.live

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DraftActionType
import com.laner.core.domain.DraftChangedEvent
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.GoldLeadChangedEvent
import com.laner.core.domain.KillEvent
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.MatchStateChanged
import com.laner.core.domain.MultiKillWindowEvent
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ObjectiveType
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerLiveState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamFightWindowEvent
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import com.laner.core.domain.TimelineSnapshotPoint
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonLiveTimelineRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun roundTripPreservesSnapshotsEventsEvidenceAndProvenance() {
        runBlocking {
            val directory = temporaryFolder.newFolder("timeline")
            val repository = JsonLiveTimelineRepository(directory)
            val matchId = MatchId("lol:series:lpl:100:blg-al:bo5")
            val gameId = GameId("lol:game:g2")
            val blue = TeamId("lol:team:blg")
            val red = TeamId("lol:team:al")
            val knight = PlayerId("lol:player:knight")
            val provenance = provenance("cito-rest", 50_000L, 49_900L, 3L)
            val game = GameContext(gameId, matchId, 2, blue, red)
            val snapshot = LiveGameSnapshot(
                game = game,
                lifecycle = MatchLifecycleState.IN_GAME,
                elapsedSeconds = 600,
                blue = TeamLiveState(blue, gold = 20_100, kills = 4, towers = 2, dragons = 1, barons = 0),
                red = TeamLiveState(red, gold = 19_700, kills = 3, towers = 1, dragons = 0, barons = 0),
                players = listOf(
                    PlayerLiveState(
                        playerId = knight,
                        teamId = blue,
                        level = 9,
                        kills = 2,
                        deaths = 1,
                        assists = 1,
                        creepScore = 98,
                        gold = 4_500,
                        championId = "Syndra",
                    )
                ),
            )
            val timeline = GameTimeline(
                matchId = matchId,
                gameId = gameId,
                gameNumber = 2,
                snapshots = listOf(TimelineSnapshotPoint(600, snapshot, provenance)),
                events = listOf(
                    MatchStateChanged(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 1,
                        gameTimeSeconds = 0,
                        provenance = provenance,
                        evidence = EventEvidence.VERIFIED_FRAME,
                        previous = MatchLifecycleState.LOADING,
                        current = MatchLifecycleState.IN_GAME,
                    ),
                    KillEvent(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 2,
                        gameTimeSeconds = 510,
                        provenance = provenance,
                        evidence = EventEvidence.VERIFIED_DELTA,
                        killerId = knight,
                        victimId = null,
                        teamId = blue,
                        count = 2,
                        observedWindowSeconds = 10,
                    ),
                    MultiKillWindowEvent(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 3,
                        gameTimeSeconds = 510,
                        provenance = provenance.copy(authority = DataAuthority.DERIVED),
                        playerId = knight,
                        teamId = blue,
                        killCount = 2,
                        windowSeconds = 10,
                    ),
                    TeamFightWindowEvent(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 4,
                        gameTimeSeconds = 520,
                        provenance = provenance.copy(authority = DataAuthority.DERIVED),
                        blueKillDelta = 2,
                        redKillDelta = 1,
                        windowSeconds = 12,
                    ),
                    ObjectiveTakenEvent(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 5,
                        gameTimeSeconds = 540,
                        provenance = provenance,
                        evidence = EventEvidence.VERIFIED_DELTA,
                        teamId = blue,
                        objective = ObjectiveType.DRAGON,
                        detail = "dragon type unknown",
                        count = 1,
                        observedWindowSeconds = 10,
                    ),
                    GoldLeadChangedEvent(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 6,
                        gameTimeSeconds = 560,
                        provenance = provenance,
                        evidence = EventEvidence.VERIFIED_DELTA,
                        leadingTeamId = blue,
                        goldDifference = 400,
                        observedWindowSeconds = 10,
                    ),
                    DraftChangedEvent(
                        matchId = matchId,
                        gameId = gameId,
                        sequence = 7,
                        gameTimeSeconds = null,
                        provenance = provenance,
                        evidence = EventEvidence.PROVIDER_EXPLICIT,
                        action = DraftActionType.LOCK,
                        teamId = red,
                        championId = "Sejuani",
                    ),
                ),
                completed = true,
            )

            repository.write(timeline)

            assertEquals(timeline, repository.read(gameId))
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
            assertEquals(2, JSONObject(directory.listFiles().single { it.extension == "json" }.readText()).getInt("schema_version"))
        }
    }

    @Test
    fun legacyV1TimelineReadsWithSafeDefaultsAndMigratesOnNextWrite() {
        runBlocking {
            val directory = temporaryFolder.newFolder("legacy-v1")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)
            val file = directory.listFiles().single { it.extension == "json" }
            val legacyProvenance = JSONObject()
                .put("provider_id", "legacy-live")
                .put("source_class", "LIVE_MATCH_SOURCE")
                .put("authority", "VERIFIED_PROVIDER")
                .put("freshness", "REALTIME")
                .put("observed_ms", 1_000L)
                .put("revision", 0L)
            file.writeText(
                JSONObject()
                    .put("schema_version", 1)
                    .put("match_id", timeline.matchId.value)
                    .put("game_id", timeline.gameId.value)
                    .put("game_number", 1)
                    .put("completed", false)
                    .put("snapshots", JSONArray())
                    .put("events", JSONArray().put(
                        JSONObject()
                            .put("match_id", timeline.matchId.value)
                            .put("game_id", timeline.gameId.value)
                            .put("sequence", 1L)
                            .put("game_time_seconds", 30)
                            .put("provenance", legacyProvenance)
                            .put("evidence", "VERIFIED_DELTA")
                            .put("type", "KILL")
                            .put("assisting_player_ids", JSONArray())
                            .put("team_id", "lol:team:blue")
                    ))
                    .toString()
            )

            val loaded = requireNotNull(repository.read(timeline.gameId))
            val kill = loaded.events.single() as KillEvent
            assertEquals(1, kill.count)
            assertNull(kill.observedWindowSeconds)

            repository.write(loaded)
            assertEquals(2, JSONObject(file.readText()).getInt("schema_version"))
        }
    }

    @Test
    fun corruptTimelineFailsExplicitly() {
        runBlocking {
            val directory = temporaryFolder.newFolder("corrupt-timeline")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)
            directory.listFiles().orEmpty().single { it.extension == "json" }.writeText("{broken")

            assertThrows(Throwable::class.java) {
                runBlocking { repository.read(timeline.gameId) }
            }
        }
    }

    @Test
    fun unsupportedTimelineSchemaFailsExplicitly() {
        runBlocking {
            val directory = temporaryFolder.newFolder("schema-timeline")
            val repository = JsonLiveTimelineRepository(directory)
            val timeline = minimalTimeline()
            repository.write(timeline)
            val file = directory.listFiles().orEmpty().single { it.extension == "json" }
            file.writeText(file.readText().replace("\"schema_version\":2", "\"schema_version\":999"))

            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.read(timeline.gameId) }
            }
            assertEquals("Unsupported LIVE timeline schema: 999", error.message)
        }
    }

    private fun minimalTimeline(): GameTimeline = GameTimeline(
        matchId = MatchId("lol:series:test:timeline"),
        gameId = GameId("lol:game:test"),
        gameNumber = 1,
    )

    private fun provenance(
        provider: String,
        observedAt: Long,
        sourceAt: Long,
        revision: Long,
    ): SourceProvenance = SourceProvenance(
        providerId = provider,
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = DataAuthority.VERIFIED_PROVIDER,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = observedAt,
        sourceTimestampEpochMillis = sourceAt,
        revision = revision,
        sourceUri = "https://example.invalid/live",
    )
}
