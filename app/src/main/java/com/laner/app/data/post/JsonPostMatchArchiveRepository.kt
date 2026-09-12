package com.laner.app.data.post

import com.laner.core.application.PostArchiveSnapshot
import com.laner.core.application.PostMatchArchiveRepository
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
 * Device-local cache of already verified POST result/game facts.
 *
 * The JSON schema is an adapter detail. Original fact provenance is preserved; loading this file
 * must never relabel cached facts as official/local authority. Corruption and unknown schemas fail
 * explicitly so Application can degrade without silently treating bad data as an empty archive.
 */
class JsonPostMatchArchiveRepository(
    private val directory: File,
) : PostMatchArchiveRepository {
    override suspend fun load(matchId: MatchId): PostArchiveSnapshot? = withContext(Dispatchers.IO) {
        val file = fileFor(matchId)
        if (!file.exists()) return@withContext null
        decode(JSONObject(file.readText(StandardCharsets.UTF_8))).also { decoded ->
            require(decoded.matchId == matchId) {
                "POST archive identity mismatch: requested=${matchId.value}, stored=${decoded.matchId.value}"
            }
        }
    }

    override suspend fun save(snapshot: PostArchiveSnapshot): Unit = withContext(Dispatchers.IO) {
        check(directory.exists() || directory.mkdirs()) {
            "Could not create POST archive directory: ${directory.absolutePath}"
        }
        atomicWrite(fileFor(snapshot.matchId), encode(snapshot).toString())
    }

    private fun fileFor(matchId: MatchId): File =
        File(directory, "${sha256(matchId.value)}.json")

    private fun encode(snapshot: PostArchiveSnapshot): JSONObject = JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("match_id", snapshot.matchId.value)
        .put("stored_at_ms", snapshot.storedAtEpochMillis)
        .put("result", snapshot.result?.let(::encodeResult))
        .put("games", JSONArray().apply { snapshot.games.forEach { put(encodeGame(it)) } })

    private fun decode(root: JSONObject): PostArchiveSnapshot {
        val version = root.optInt("schema_version", -1)
        require(version == SCHEMA_VERSION) { "Unsupported POST archive schema: $version" }
        val matchId = MatchId(root.getString("match_id"))
        val gamesNode = root.optJSONArray("games") ?: JSONArray()
        val games = buildList {
            for (index in 0 until gamesNode.length()) {
                add(decodeGame(gamesNode.getJSONObject(index)))
            }
        }
        return PostArchiveSnapshot(
            matchId = matchId,
            result = root.optJSONObject("result")?.let(::decodeResult),
            games = games,
            storedAtEpochMillis = root.getLong("stored_at_ms"),
        )
    }

    private fun encodeResult(value: SeriesResult): JSONObject = JSONObject()
        .put("match_id", value.matchId.value)
        .put("left_team_id", value.leftTeamId.value)
        .put("right_team_id", value.rightTeamId.value)
        .put("left_wins", value.leftWins)
        .put("right_wins", value.rightWins)
        .put("best_of", value.bestOf)
        .put("state", value.state.name)
        .put("winner_team_id", value.winnerTeamId?.value)
        .put("provenance", encodeProvenance(value.provenance))

    private fun decodeResult(node: JSONObject): SeriesResult = SeriesResult(
        matchId = MatchId(node.getString("match_id")),
        leftTeamId = TeamId(node.getString("left_team_id")),
        rightTeamId = TeamId(node.getString("right_team_id")),
        leftWins = node.getInt("left_wins"),
        rightWins = node.getInt("right_wins"),
        bestOf = nullableInt(node, "best_of"),
        state = SeriesResultState.valueOf(node.getString("state")),
        winnerTeamId = node.optString("winner_team_id").takeIf { it.isNotBlank() }?.let(::TeamId),
        provenance = decodeProvenance(node.getJSONObject("provenance")),
    )

    private fun encodeGame(value: CompletedGameRecord): JSONObject = JSONObject()
        .put("match_id", value.matchId.value)
        .put("game_id", value.gameId.value)
        .put("game_number", value.gameNumber)
        .put("blue_team_id", value.blueTeamId.value)
        .put("red_team_id", value.redTeamId.value)
        .put("winner_team_id", value.winnerTeamId.value)
        .put("duration_seconds", value.durationSeconds)
        .put("blue_stats", encodeTeamStats(value.blueStats))
        .put("red_stats", encodeTeamStats(value.redStats))
        .put("players", JSONArray().apply { value.players.forEach { put(encodePlayerStats(it)) } })
        .put("draft_blue", value.draftBlue?.let(::encodeDraft))
        .put("draft_red", value.draftRed?.let(::encodeDraft))
        .put("provenance", encodeProvenance(value.provenance))

    private fun decodeGame(node: JSONObject): CompletedGameRecord {
        val playersNode = node.optJSONArray("players") ?: JSONArray()
        val players = buildList {
            for (index in 0 until playersNode.length()) {
                add(decodePlayerStats(playersNode.getJSONObject(index)))
            }
        }
        return CompletedGameRecord(
            matchId = MatchId(node.getString("match_id")),
            gameId = GameId(node.getString("game_id")),
            gameNumber = node.getInt("game_number"),
            blueTeamId = TeamId(node.getString("blue_team_id")),
            redTeamId = TeamId(node.getString("red_team_id")),
            winnerTeamId = TeamId(node.getString("winner_team_id")),
            durationSeconds = nullableInt(node, "duration_seconds"),
            blueStats = decodeTeamStats(node.getJSONObject("blue_stats")),
            redStats = decodeTeamStats(node.getJSONObject("red_stats")),
            players = players,
            draftBlue = node.optJSONObject("draft_blue")?.let(::decodeDraft),
            draftRed = node.optJSONObject("draft_red")?.let(::decodeDraft),
            provenance = decodeProvenance(node.getJSONObject("provenance")),
        )
    }

    private fun encodeTeamStats(value: PostTeamStats): JSONObject = JSONObject()
        .put("team_id", value.teamId.value)
        .put("kills", value.kills)
        .put("gold", value.gold)
        .put("towers", value.towers)
        .put("dragons", value.dragons)
        .put("barons", value.barons)
        .put("heralds", value.heralds)
        .put("atakhans", value.atakhans)

    private fun decodeTeamStats(node: JSONObject): PostTeamStats = PostTeamStats(
        teamId = TeamId(node.getString("team_id")),
        kills = nullableInt(node, "kills"),
        gold = nullableInt(node, "gold"),
        towers = nullableInt(node, "towers"),
        dragons = nullableInt(node, "dragons"),
        barons = nullableInt(node, "barons"),
        heralds = nullableInt(node, "heralds"),
        atakhans = nullableInt(node, "atakhans"),
    )

    private fun encodePlayerStats(value: PostPlayerStats): JSONObject = JSONObject()
        .put("player", encodePlayer(value.player))
        .put("team_id", value.teamId.value)
        .put("champion_id", value.championId)
        .put("kills", value.kills)
        .put("deaths", value.deaths)
        .put("assists", value.assists)
        .put("cs", value.cs)
        .put("gold", value.gold)
        .put("damage_to_champions", value.damageToChampions)
        .put("vision_score", value.visionScore)
        .put("items", JSONArray(value.items))
        .put("summoner_spell_ids", JSONArray(value.summonerSpellIds))

    private fun decodePlayerStats(node: JSONObject): PostPlayerStats = PostPlayerStats(
        player = decodePlayer(node.getJSONObject("player")),
        teamId = TeamId(node.getString("team_id")),
        championId = node.getString("champion_id"),
        kills = nullableInt(node, "kills"),
        deaths = nullableInt(node, "deaths"),
        assists = nullableInt(node, "assists"),
        cs = nullableInt(node, "cs"),
        gold = nullableInt(node, "gold"),
        damageToChampions = nullableInt(node, "damage_to_champions"),
        visionScore = nullableInt(node, "vision_score"),
        items = stringList(node.optJSONArray("items")),
        summonerSpellIds = stringList(node.optJSONArray("summoner_spell_ids")),
    )

    private fun encodePlayer(value: PlayerRef): JSONObject = JSONObject()
        .put("player_id", value.id.value)
        .put("handle", value.handle)
        .put("team_id", value.teamId?.value)
        .put("role", value.role?.name)

    private fun decodePlayer(node: JSONObject): PlayerRef = PlayerRef(
        id = PlayerId(node.getString("player_id")),
        handle = node.getString("handle"),
        teamId = node.optString("team_id").takeIf { it.isNotBlank() }?.let(::TeamId),
        role = node.optString("role").takeIf { it.isNotBlank() }?.let(PlayerRole::valueOf),
    )

    private fun encodeDraft(value: PostDraftSide): JSONObject = JSONObject()
        .put("team_id", value.teamId.value)
        .put("picks", JSONArray(value.picks))
        .put("bans", JSONArray(value.bans))

    private fun decodeDraft(node: JSONObject): PostDraftSide = PostDraftSide(
        teamId = TeamId(node.getString("team_id")),
        picks = stringList(node.optJSONArray("picks")),
        bans = stringList(node.optJSONArray("bans")),
    )

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
        sourceTimestampEpochMillis = nullableLong(node, "source_timestamp_ms"),
        revision = node.optLong("revision", 0L),
        sourceUri = node.optString("source_uri").takeIf { it.isNotBlank() },
    )

    private fun nullableInt(node: JSONObject, key: String): Int? =
        if (!node.has(key) || node.isNull(key)) null else node.getInt(key)

    private fun nullableLong(node: JSONObject, key: String): Long? =
        if (!node.has(key) || node.isNull(key)) null else node.getLong(key)

    private fun stringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
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
        const val SCHEMA_VERSION = 1
    }
}
