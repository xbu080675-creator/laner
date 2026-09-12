package com.laner.app.data.riot

import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.LiveSnapshotSourcePort
import com.laner.core.application.ProviderLiveSnapshot
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerLiveState
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant

/** Global Riot LiveStats source for verified gameplay values, separate from lifecycle authority. */
class RiotGlobalLiveSnapshotSource(
    private val apiKey: String,
    private val identityRepository: ProviderMatchIdentityRepository,
    private val client: OkHttpClient = OkHttpClient(),
) : LiveSnapshotSourcePort {
    override val providerId: String = "riot-livestats-snapshot"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override suspend fun readSnapshot(
        query: LiveMatchSourceQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveSnapshot?> {
        if (apiKey.isBlank()) return ProviderRead.Failure(
            DiagnosticFailure(
                code = ErrorCode("LNR-SRC-LIVE-006"),
                message = "LoL Esports API credential is not configured for LIVE snapshot",
                retryable = false,
                context = mapOf("provider" to providerId),
            )
        )
        if (query.teams.size != 2) return ProviderRead.Success(null)

        return withContext(Dispatchers.IO) {
            try {
                val identity = identityRepository.find(RIOT_PROVIDER_ID, query.matchId)
                    ?: return@withContext ProviderRead.Success(null)
                val eventId = identity.externalEventId ?: return@withContext ProviderRead.Success(null)
                val eventRoot = getPersistedJson("$PERSISTED_BASE/getEventDetails?hl=en-US&id=${enc(eventId)}")
                val eventMatch = eventRoot.optJSONObject("data")?.optJSONObject("event")?.optJSONObject("match")
                    ?: return@withContext ProviderRead.Success(null)
                val game = activeGame(eventMatch.optJSONArray("games")) ?: return@withContext ProviderRead.Success(null)
                val gameNumber = game.optInt("number", -1).takeIf { it > 0 } ?: return@withContext ProviderRead.Success(null)
                val providerGameId = game.optString("id").trim().takeIf { it.isNotBlank() }
                    ?: return@withContext ProviderRead.Success(null)
                val liveRoot = getLiveJson("$LIVE_BASE/window/${enc(providerGameId)}")
                val frame = lastFrame(liveRoot.optJSONArray("frames")) ?: return@withContext ProviderRead.Success(null)
                if (frame.optString("gameState").equals("finished", true)) return@withContext ProviderRead.Success(null)
                val metadata = liveRoot.optJSONObject("gameMetadata") ?: JSONObject()
                val teamMap = providerTeamMap(eventMatch, query)
                val snapshot = parseSnapshot(query, gameNumber, frame, metadata, teamMap)
                    ?: return@withContext ProviderRead.Success(null)
                val sourceTime = frame.optString("rfc460Timestamp").takeIf { it.isNotBlank() }
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ProviderRead.Success(
                    ProviderLiveSnapshot(
                        snapshot = snapshot,
                        sourceTimestampEpochMillis = sourceTime,
                        observedAtEpochMillis = context.nowEpochMillis,
                        revision = sourceTime ?: context.nowEpochMillis,
                        sourceUri = "$LIVE_BASE/window/$providerGameId",
                    )
                )
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-LIVE-007"),
                        message = "Riot LIVE snapshot failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }
    }

    internal fun parseSnapshot(
        query: LiveMatchSourceQuery,
        gameNumber: Int,
        frame: JSONObject,
        metadata: JSONObject,
        providerTeamMap: Map<String, TeamId>,
    ): LiveGameSnapshot? {
        val blueFrame = frame.optJSONObject("blueTeam") ?: return null
        val redFrame = frame.optJSONObject("redTeam") ?: return null
        val blueMeta = metadata.optJSONObject("blueTeamMetadata") ?: JSONObject()
        val redMeta = metadata.optJSONObject("redTeamMetadata") ?: JSONObject()
        val blueTeamId = providerTeamMap[blueMeta.optString("esportsTeamId")] ?: return null
        val redTeamId = providerTeamMap[redMeta.optString("esportsTeamId")] ?: return null
        if (blueTeamId == redTeamId) return null
        val gameId = GameIdentity.canonical(query.matchId, gameNumber)
        return LiveGameSnapshot(
            game = GameContext(gameId, query.matchId, gameNumber, blueTeamId, redTeamId),
            lifecycle = MatchLifecycleState.IN_GAME,
            elapsedSeconds = intOrNull(frame, "gameTime") ?: intOrNull(frame, "gameTimeSeconds"),
            blue = parseTeamState(blueFrame, blueTeamId),
            red = parseTeamState(redFrame, redTeamId),
            players = parsePlayers(blueMeta, blueFrame, blueTeamId) + parsePlayers(redMeta, redFrame, redTeamId),
        )
    }

    internal fun providerTeamMap(eventMatch: JSONObject, query: LiveMatchSourceQuery): Map<String, TeamId> {
        val canonicalByToken = query.teams.associateBy { teamKey(it.code.ifBlank { it.name }) }
        val teams = eventMatch.optJSONArray("teams") ?: return emptyMap()
        return buildMap {
            for (index in 0 until teams.length()) {
                val team = teams.optJSONObject(index) ?: continue
                val externalId = team.optString("id").trim()
                val token = teamKey(team.optString("code").ifBlank { team.optString("name") })
                if (externalId.isNotBlank()) canonicalByToken[token]?.let { put(externalId, it.id) }
            }
        }
    }

    private fun parseTeamState(frame: JSONObject, teamId: TeamId) = TeamLiveState(
        teamId = teamId,
        gold = intOrNull(frame, "totalGold"),
        kills = intOrNull(frame, "totalKills"),
        towers = intOrNull(frame, "towers"),
        dragons = frame.optJSONArray("dragons")?.length() ?: intOrNull(frame, "dragons"),
        barons = intOrNull(frame, "barons"),
    )

    private fun parsePlayers(metadata: JSONObject, teamFrame: JSONObject, teamId: TeamId): List<PlayerLiveState> {
        val meta = metadata.optJSONArray("participantMetadata") ?: JSONArray()
        val metaById = buildMap<Int, JSONObject> {
            for (index in 0 until meta.length()) {
                val row = meta.optJSONObject(index) ?: continue
                val id = row.optInt("participantId", -1)
                if (id >= 0) put(id, row)
            }
        }
        val participants = teamFrame.optJSONArray("participants") ?: JSONArray()
        return buildList {
            for (index in 0 until participants.length()) {
                val row = participants.optJSONObject(index) ?: continue
                val participantId = row.optInt("participantId", -1)
                val info = metaById[participantId]
                val handle = info?.optString("summonerName").orEmpty().ifBlank { "P$participantId" }
                add(
                    PlayerLiveState(
                        playerId = PlayerId("lol:player:${canonicalToken(handle)}"),
                        teamId = teamId,
                        level = intOrNull(row, "level"),
                        kills = intOrNull(row, "kills"),
                        deaths = intOrNull(row, "deaths"),
                        assists = intOrNull(row, "assists"),
                        creepScore = intOrNull(row, "creepScore"),
                        gold = intOrNull(row, "totalGold"),
                        championId = info?.optString("championId")?.takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    private fun activeGame(games: JSONArray?): JSONObject? {
        if (games == null) return null
        for (index in 0 until games.length()) {
            val row = games.optJSONObject(index) ?: continue
            val state = token(row.optString("state"))
            if (state.contains("inprogress") || state == "live" || state == "started") return row
        }
        return null
    }

    private fun lastFrame(frames: JSONArray?): JSONObject? {
        if (frames == null) return null
        for (index in frames.length() - 1 downTo 0) frames.optJSONObject(index)?.let { return it }
        return null
    }

    private fun getPersistedJson(url: String): JSONObject = getJson(url, true)
    private fun getLiveJson(url: String): JSONObject = getJson(url, false)
    private fun getJson(url: String, includeApiKey: Boolean): JSONObject {
        val builder = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Laner/2")
        if (includeApiKey) builder.header("x-api-key", apiKey)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("empty response body")
            return JSONObject(body)
        }
    }

    private fun intOrNull(obj: JSONObject, key: String): Int? = if (!obj.has(key) || obj.isNull(key)) null else obj.optInt(key)
    private fun canonicalToken(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "unknown" }
    private fun teamKey(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    private fun token(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "")
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun safeMessage(error: Throwable): String = error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private companion object {
        const val RIOT_PROVIDER_ID = "riot-lolesports"
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val LIVE_BASE = "https://feed.lolesports.com/livestats/v1"
    }
}
