package com.laner.app.data.live

import com.laner.core.application.LiveTimelineRepository
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
import com.laner.core.domain.MatchEvent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Device-local normalized LIVE timeline archive.
 *
 * Provider payloads are deliberately absent from this schema. Only Laner Domain snapshots/events
 * are persisted. One file per GameId limits write blast radius and allows independent recovery.
 * Schema v2 adds conservative tactical event windows while retaining read compatibility with v1.
 * The first v1 -> v2 write preserves the exact v1 payload as a verified sibling recovery copy.
 */
class JsonLiveTimelineRepository(
    private val directory: File,
) : LiveTimelineRepository {
    override suspend fun read(gameId: GameId): GameTimeline? = withContext(Dispatchers.IO) {
        val file = fileFor(gameId)
        if (!file.exists()) return@withContext null
        decodeTimeline(JSONObject(file.readText(StandardCharsets.UTF_8))).also { timeline ->
            require(timeline.gameId == gameId) {
                "Timeline identity mismatch: requested=${gameId.value}, stored=${timeline.gameId.value}"
            }
        }
    }

    override suspend fun write(timeline: GameTimeline): Unit = withContext(Dispatchers.IO) {
        check(directory.exists() || directory.mkdirs()) {
            "Could not create LIVE timeline directory: ${directory.absolutePath}"
        }
        val target = fileFor(timeline.gameId)
        ensureV1RecoveryPoint(target)
        atomicWrite(target, encodeTimeline(timeline).toString())
    }

    private fun fileFor(gameId: GameId): File = File(directory, "${sha256(gameId.value)}.json")

    private fun recoveryFileFor(target: File): File = File(target.parentFile, "${target.name}.schema-v1.bak")

    private fun ensureV1RecoveryPoint(target: File) {
        if (!target.exists()) return
        val existingContent = target.readText(StandardCharsets.UTF_8)
        if (schemaVersionFromRaw(existingContent) != 1) return

        val recovery = recoveryFileFor(target)
        if (!recovery.exists()) {
            atomicWrite(recovery, existingContent)
        }
        check(recovery.readText(StandardCharsets.UTF_8) == existingContent) {
            "LIVE timeline v1 recovery copy verification failed: ${recovery.absolutePath}"
        }
    }

    private fun schemaVersionFromRaw(content: String): Int? =
        SCHEMA_VERSION_PATTERN.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun encodeTimeline(value: GameTimeline): JSONObject = JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("match_id", value.matchId.value)
        .put("game_id", value.gameId.value)
        .put("game_number", value.gameNumber)
        .put("completed", value.completed)
        .put("snapshots", JSONArray().apply { value.snapshots.forEach { put(encodeSnapshotPoint(it)) } })
        .put("events", JSONArray().apply { value.events.forEach { put(encodeEvent(it)) } })

    private fun decodeTimeline(root: JSONObject): GameTimeline {
        val version = root.optInt("schema_version", -1)
        require(version in SUPPORTED_SCHEMA_VERSIONS) { "Unsupported LIVE timeline schema: $version" }
        val matchId = MatchId(root.getString("match_id"))
        val gameId = GameId(root.getString("game_id"))
        return GameTimeline(
            matchId = matchId,
            gameId = gameId,
            gameNumber = root.getInt("game_number"),
            snapshots = decodeArray(root.optJSONArray("snapshots")) { decodeSnapshotPoint(it) },
            events = decodeArray(root.optJSONArray("events")) { decodeEvent(it) },
            completed = root.optBoolean("completed", false),
        )
    }

    private fun encodeSnapshotPoint(value: TimelineSnapshotPoint): JSONObject = JSONObject()
        .put("game_time_seconds", value.gameTimeSeconds)
        .put("snapshot", encodeSnapshot(value.snapshot))
        .put("provenance", encodeProvenance(value.provenance))

    private fun decodeSnapshotPoint(node: JSONObject): TimelineSnapshotPoint = TimelineSnapshotPoint(
        gameTimeSeconds = node.getInt("game_time_seconds"),
        snapshot = decodeSnapshot(node.getJSONObject("snapshot")),
        provenance = decodeProvenance(node.getJSONObject("provenance")),
    )

    private fun encodeSnapshot(value: LiveGameSnapshot): JSONObject = JSONObject()
        .put("game", encodeGameContext(value.game))
        .put("lifecycle", value.lifecycle.name)
        .put("elapsed_seconds", value.elapsedSeconds)
        .put("blue", encodeTeamState(value.blue))
        .put("red", encodeTeamState(value.red))
        .put("players", JSONArray().apply { value.players.forEach { put(encodePlayerState(it)) } })

    private fun decodeSnapshot(node: JSONObject): LiveGameSnapshot = LiveGameSnapshot(
        game = decodeGameContext(node.getJSONObject("game")),
        lifecycle = MatchLifecycleState.valueOf(node.getString("lifecycle")),
        elapsedSeconds = if (node.isNull("elapsed_seconds")) null else node.getInt("elapsed_seconds"),
        blue = decodeTeamState(node.getJSONObject("blue")),
        red = decodeTeamState(node.getJSONObject("red")),
        players = decodeArray(node.optJSONArray("players")) { decodePlayerState(it) },
    )

    private fun encodeGameContext(value: GameContext): JSONObject = JSONObject()
        .put("game_id", value.gameId.value)
        .put("match_id", value.matchId.value)
        .put("game_number", value.gameNumber)
        .put("blue_team_id", value.blueTeamId.value)
        .put("red_team_id", value.redTeamId.value)

    private fun decodeGameContext(node: JSONObject): GameContext = GameContext(
        gameId = GameId(node.getString("game_id")),
        matchId = MatchId(node.getString("match_id")),
        gameNumber = node.getInt("game_number"),
        blueTeamId = TeamId(node.getString("blue_team_id")),
        redTeamId = TeamId(node.getString("red_team_id")),
    )

    private fun encodeTeamState(value: TeamLiveState): JSONObject = JSONObject()
        .put("team_id", value.teamId.value)
        .put("gold", value.gold)
        .put("kills", value.kills)
        .put("towers", value.towers)
        .put("dragons", value.dragons)
        .put("barons", value.barons)

    private fun decodeTeamState(node: JSONObject): TeamLiveState = TeamLiveState(
        teamId = TeamId(node.getString("team_id")),
        gold = nullableInt(node, "gold"),
        kills = nullableInt(node, "kills"),
        towers = nullableInt(node, "towers"),
        dragons = nullableInt(node, "dragons"),
        barons = nullableInt(node, "barons"),
    )

    private fun encodePlayerState(value: PlayerLiveState): JSONObject = JSONObject()
        .put("player_id", value.playerId.value)
        .put("team_id", value.teamId.value)
        .put("level", value.level)
        .put("kills", value.kills)
        .put("deaths", value.deaths)
        .put("assists", value.assists)
        .put("creep_score", value.creepScore)
        .put("gold", value.gold)
        .put("champion_id", value.championId)

    private fun decodePlayerState(node: JSONObject): PlayerLiveState = PlayerLiveState(
        playerId = PlayerId(node.getString("player_id")),
        teamId = TeamId(node.getString("team_id")),
        level = nullableInt(node, "level"),
        kills = nullableInt(node, "kills"),
        deaths = nullableInt(node, "deaths"),
        assists = nullableInt(node, "assists"),
        creepScore = nullableInt(node, "creep_score"),
        gold = nullableInt(node, "gold"),
        championId = node.optString("champion_id").takeIf { it.isNotBlank() },
    )

    private fun encodeEvent(value: MatchEvent): JSONObject {
        val node = JSONObject()
            .put("match_id", value.matchId.value)
            .put("game_id", value.gameId?.value)
            .put("sequence", value.sequence)
            .put("game_time_seconds", value.gameTimeSeconds)
            .put("provenance", encodeProvenance(value.provenance))
            .put("evidence", value.evidence.name)
        when (value) {
            is MatchStateChanged -> node
                .put("type", "MATCH_STATE_CHANGED")
                .put("previous", value.previous.name)
                .put("current", value.current.name)
            is KillEvent -> node
                .put("type", "KILL")
                .put("killer_id", value.killerId?.value)
                .put("victim_id", value.victimId?.value)
                .put("assisting_player_ids", JSONArray().apply {
                    value.assistingPlayerIds.map { it.value }.sorted().forEach(::put)
                })
                .put("team_id", value.teamId?.value)
                .put("count", value.count)
                .put("observed_window_seconds", value.observedWindowSeconds)
            is MultiKillWindowEvent -> node
                .put("type", "MULTI_KILL_WINDOW")
                .put("player_id", value.playerId.value)
                .put("team_id", value.teamId.value)
                .put("kill_count", value.killCount)
                .put("window_seconds", value.windowSeconds)
            is TeamFightWindowEvent -> node
                .put("type", "TEAM_FIGHT_WINDOW")
                .put("blue_kill_delta", value.blueKillDelta)
                .put("red_kill_delta", value.redKillDelta)
                .put("window_seconds", value.windowSeconds)
            is ObjectiveTakenEvent -> node
                .put("type", "OBJECTIVE_TAKEN")
                .put("team_id", value.teamId.value)
                .put("objective", value.objective.name)
                .put("detail", value.detail)
                .put("count", value.count)
                .put("observed_window_seconds", value.observedWindowSeconds)
            is GoldLeadChangedEvent -> node
                .put("type", "GOLD_LEAD_CHANGED")
                .put("leading_team_id", value.leadingTeamId?.value)
                .put("gold_difference", value.goldDifference)
                .put("observed_window_seconds", value.observedWindowSeconds)
            is DraftChangedEvent -> node
                .put("type", "DRAFT_CHANGED")
                .put("action", value.action.name)
                .put("team_id", value.teamId?.value)
                .put("champion_id", value.championId)
        }
        return node
    }

    private fun decodeEvent(node: JSONObject): MatchEvent {
        val matchId = MatchId(node.getString("match_id"))
        val gameId = node.optString("game_id").takeIf { it.isNotBlank() }?.let(::GameId)
        val sequence = node.getLong("sequence")
        val gameTime = if (node.isNull("game_time_seconds")) null else node.getInt("game_time_seconds")
        val provenance = decodeProvenance(node.getJSONObject("provenance"))
        val evidence = EventEvidence.valueOf(node.getString("evidence"))
        return when (node.getString("type")) {
            "MATCH_STATE_CHANGED" -> MatchStateChanged(
                matchId = matchId,
                gameId = gameId,
                sequence = sequence,
                gameTimeSeconds = gameTime,
                provenance = provenance,
                evidence = evidence,
                previous = MatchLifecycleState.valueOf(node.getString("previous")),
                current = MatchLifecycleState.valueOf(node.getString("current")),
            )
            "KILL" -> KillEvent(
                matchId = matchId,
                gameId = requireNotNull(gameId) { "KillEvent requires game_id" },
                sequence = sequence,
                gameTimeSeconds = requireNotNull(gameTime) { "KillEvent requires game_time_seconds" },
                provenance = provenance,
                evidence = evidence,
                killerId = node.optString("killer_id").takeIf { it.isNotBlank() }?.let(::PlayerId),
                victimId = node.optString("victim_id").takeIf { it.isNotBlank() }?.let(::PlayerId),
                assistingPlayerIds = decodeStringSet(node.optJSONArray("assisting_player_ids")).map(::PlayerId).toSet(),
                teamId = node.optString("team_id").takeIf { it.isNotBlank() }?.let(::TeamId),
                count = node.optInt("count", 1),
                observedWindowSeconds = nullableInt(node, "observed_window_seconds"),
            )
            "MULTI_KILL_WINDOW" -> MultiKillWindowEvent(
                matchId = matchId,
                gameId = requireNotNull(gameId) { "MultiKillWindowEvent requires game_id" },
                sequence = sequence,
                gameTimeSeconds = requireNotNull(gameTime) { "MultiKillWindowEvent requires game_time_seconds" },
                provenance = provenance,
                evidence = evidence,
                playerId = PlayerId(node.getString("player_id")),
                teamId = TeamId(node.getString("team_id")),
                killCount = node.getInt("kill_count"),
                windowSeconds = node.getInt("window_seconds"),
            )
            "TEAM_FIGHT_WINDOW" -> TeamFightWindowEvent(
                matchId = matchId,
                gameId = requireNotNull(gameId) { "TeamFightWindowEvent requires game_id" },
                sequence = sequence,
                gameTimeSeconds = requireNotNull(gameTime) { "TeamFightWindowEvent requires game_time_seconds" },
                provenance = provenance,
                evidence = evidence,
                blueKillDelta = node.getInt("blue_kill_delta"),
                redKillDelta = node.getInt("red_kill_delta"),
                windowSeconds = node.getInt("window_seconds"),
            )
            "OBJECTIVE_TAKEN" -> ObjectiveTakenEvent(
                matchId = matchId,
                gameId = requireNotNull(gameId) { "ObjectiveTakenEvent requires game_id" },
                sequence = sequence,
                gameTimeSeconds = requireNotNull(gameTime) { "ObjectiveTakenEvent requires game_time_seconds" },
                provenance = provenance,
                evidence = evidence,
                teamId = TeamId(node.getString("team_id")),
                objective = ObjectiveType.valueOf(node.getString("objective")),
                detail = node.optString("detail").takeIf { it.isNotBlank() },
                count = node.optInt("count", 1),
                observedWindowSeconds = nullableInt(node, "observed_window_seconds"),
            )
            "GOLD_LEAD_CHANGED" -> GoldLeadChangedEvent(
                matchId = matchId,
                gameId = requireNotNull(gameId) { "GoldLeadChangedEvent requires game_id" },
                sequence = sequence,
                gameTimeSeconds = requireNotNull(gameTime) { "GoldLeadChangedEvent requires game_time_seconds" },
                provenance = provenance,
                evidence = evidence,
                leadingTeamId = node.optString("leading_team_id").takeIf { it.isNotBlank() }?.let(::TeamId),
                goldDifference = node.getInt("gold_difference"),
                observedWindowSeconds = nullableInt(node, "observed_window_seconds"),
            )
            "DRAFT_CHANGED" -> DraftChangedEvent(
                matchId = matchId,
                gameId = gameId,
                sequence = sequence,
                gameTimeSeconds = gameTime,
                provenance = provenance,
                evidence = evidence,
                action = DraftActionType.valueOf(node.getString("action")),
                teamId = node.optString("team_id").takeIf { it.isNotBlank() }?.let(::TeamId),
                championId = node.optString("champion_id").takeIf { it.isNotBlank() },
            )
            else -> error("Unsupported LIVE timeline event type: ${node.optString("type")}")
        }
    }

    private fun encodeProvenance(value: SourceProvenance): JSONObject = JSONObject()
        .put("provider_id", value.providerId)
        .put("source_class", value.sourceClass.name)
        .put("authority", value.authority.name)
        .put("freshness", value.freshnessClass.name)
        .put("observed_ms", value.observedAtEpochMillis)
        .put("source_timestamp_ms", value.sourceTimestampEpochMillis)
        .put("revision", value.revision)
        .put("source_uri", value.sourceUri)

    private fun decodeProvenance(node: JSONObject): SourceProvenance = SourceProvenance(
        providerId = node.getString("provider_id"),
        sourceClass = SourceClass.valueOf(node.getString("source_class")),
        authority = DataAuthority.valueOf(node.getString("authority")),
        freshnessClass = FreshnessClass.valueOf(node.getString("freshness")),
        observedAtEpochMillis = node.getLong("observed_ms"),
        sourceTimestampEpochMillis = if (node.isNull("source_timestamp_ms")) null else node.getLong("source_timestamp_ms"),
        revision = node.optLong("revision", 0L),
        sourceUri = node.optString("source_uri").takeIf { it.isNotBlank() },
    )

    private fun nullableInt(node: JSONObject, key: String): Int? =
        if (!node.has(key) || node.isNull(key)) null else node.getInt(key)

    private fun <T> decodeArray(array: JSONArray?, decoder: (JSONObject) -> T): List<T> = buildList {
        val source = array ?: return@buildList
        for (index in 0 until source.length()) add(decoder(source.getJSONObject(index)))
    }

    private fun decodeStringSet(array: JSONArray?): Set<String> = buildSet {
        val source = array ?: return@buildSet
        for (index in 0 until source.length()) {
            source.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun atomicWrite(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content, StandardCharsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val SCHEMA_VERSION = 2
        val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2)
        val SCHEMA_VERSION_PATTERN = Regex("\\\"schema_version\\\"\\s*:\\s*(\\d+)")
    }
}
