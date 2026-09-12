package com.laner.app.data.riot

import com.laner.core.application.HistoricalGameTimelineBatch
import com.laner.core.application.HistoricalTimelineFrame
import com.laner.core.application.PostMatchQuery
import com.laner.core.application.PostTimelineSourcePort
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerLiveState
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
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
import java.time.temporal.ChronoUnit

/**
 * Global historical process source backed by Riot LoL Esports LiveStats.
 *
 * No interpolation is performed. Only real frames returned by Riot are emitted. If Riot no longer
 * retains a game's windows, the source returns no timeline and Application leaves the historical gap.
 */
class RiotGlobalHistoricalTimelineSource(
    private val apiKey: String,
    private val identityRepository: ProviderMatchIdentityRepository,
    private val client: OkHttpClient = OkHttpClient(),
) : PostTimelineSourcePort {
    override val providerId: String = "riot-livestats-history"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override fun supports(query: PostMatchQuery): Boolean = query.teams.size == 2

    override suspend fun readGameTimeline(
        query: PostMatchQuery,
        gameNumber: Int,
        context: SourceRequestContext,
    ): ProviderRead<HistoricalGameTimelineBatch?> {
        if (apiKey.isBlank()) {
            return ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-POST-024"),
                    message = "LoL Esports API credential is not configured for historical timeline",
                    retryable = false,
                    context = mapOf("provider" to providerId),
                )
            )
        }
        if (!supports(query) || gameNumber <= 0) return ProviderRead.Success(null)

        return withContext(Dispatchers.IO) {
            try {
                val identity = identityRepository.find(RIOT_PROVIDER_ID, query.matchId)
                    ?: return@withContext ProviderRead.Success(null)
                val eventId = identity.externalEventId ?: return@withContext ProviderRead.Success(null)
                val eventRoot = getPersistedJson(
                    "$PERSISTED_BASE/getEventDetails?hl=en-US&id=${enc(eventId)}"
                )
                val eventMatch = eventRoot.optJSONObject("data")
                    ?.optJSONObject("event")
                    ?.optJSONObject("match")
                    ?: return@withContext ProviderRead.Success(null)
                val providerTeamMap = providerTeamMap(eventMatch, query)
                if (providerTeamMap.size < 2) return@withContext ProviderRead.Success(null)
                val gameNode = findGame(eventMatch.optJSONArray("games"), gameNumber)
                    ?: return@withContext ProviderRead.Success(null)
                val providerGameId = gameNode.optString("id").trim()
                if (providerGameId.isBlank()) return@withContext ProviderRead.Success(null)

                val kickoff = runCatching { getLiveJson("$LIVE_BASE/window/${enc(providerGameId)}") }.getOrNull()
                    ?: return@withContext ProviderRead.Success(null)
                val kickoffFrames = kickoff.optJSONArray("frames") ?: JSONArray()
                val firstTimestamp = firstTimestamp(kickoffFrames)
                    ?: return@withContext ProviderRead.Success(null)

                var metadata = kickoff.optJSONObject("gameMetadata") ?: JSONObject()
                var cursor = align10(firstTimestamp)
                var emptyWindows = 0
                var requests = 0
                var finished = false
                val framesBySecond = linkedMapOf<Int, HistoricalTimelineFrame>()

                while (requests < MAX_WINDOWS && !finished) {
                    val startingTime = cursor.truncatedTo(ChronoUnit.SECONDS).toString()
                    val root = runCatching {
                        getLiveJson("$LIVE_BASE/window/${enc(providerGameId)}?startingTime=${enc(startingTime)}")
                    }.getOrNull()
                    if (root == null) {
                        emptyWindows += 1
                        if (emptyWindows >= MAX_EMPTY_WINDOWS) break
                        cursor = cursor.plusSeconds(WINDOW_SECONDS)
                        requests += 1
                        continue
                    }

                    val rawFrames = root.optJSONArray("frames") ?: JSONArray()
                    if (rawFrames.length() == 0) {
                        emptyWindows += 1
                        if (emptyWindows >= MAX_EMPTY_WINDOWS) break
                    } else {
                        emptyWindows = 0
                        root.optJSONObject("gameMetadata")?.takeIf { it.length() > 0 }?.let { metadata = it }
                        for (index in 0 until rawFrames.length()) {
                            val frame = rawFrames.optJSONObject(index) ?: continue
                            val timestamp = parseInstant(frame.optString("rfc460Timestamp")) ?: continue
                            val elapsed = ((timestamp.toEpochMilli() - firstTimestamp.toEpochMilli()) / 1000L).toInt()
                            if (elapsed < 0 || elapsed % SAMPLE_SECONDS != 0) continue
                            parseFrame(
                                query = query,
                                gameNumber = gameNumber,
                                frame = frame,
                                metadata = metadata,
                                providerTeamMap = providerTeamMap,
                                elapsedSeconds = elapsed,
                                observedAtEpochMillis = context.nowEpochMillis,
                                sourceTimestampEpochMillis = timestamp.toEpochMilli(),
                            )?.let { parsed -> framesBySecond[elapsed] = parsed }
                            if (frame.optString("gameState").equals("finished", ignoreCase = true)) {
                                finished = true
                                break
                            }
                        }
                    }
                    cursor = cursor.plusSeconds(WINDOW_SECONDS)
                    requests += 1
                }

                if (framesBySecond.isEmpty()) return@withContext ProviderRead.Success(null)
                ProviderRead.Success(
                    HistoricalGameTimelineBatch(
                        gameNumber = gameNumber,
                        frames = framesBySecond.values.sortedBy { it.snapshot.elapsedSeconds },
                        complete = finished,
                    )
                )
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-POST-025"),
                        message = "Riot historical timeline failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId, "game_number" to gameNumber.toString()),
                    )
                )
            }
        }
    }

    internal fun parseFrame(
        query: PostMatchQuery,
        gameNumber: Int,
        frame: JSONObject,
        metadata: JSONObject,
        providerTeamMap: Map<String, TeamId>,
        elapsedSeconds: Int,
        observedAtEpochMillis: Long,
        sourceTimestampEpochMillis: Long?,
    ): HistoricalTimelineFrame? {
        val blueFrame = frame.optJSONObject("blueTeam") ?: return null
        val redFrame = frame.optJSONObject("redTeam") ?: return null
        val blueMeta = metadata.optJSONObject("blueTeamMetadata") ?: JSONObject()
        val redMeta = metadata.optJSONObject("redTeamMetadata") ?: JSONObject()
        val blueTeamId = providerTeamMap[blueMeta.optString("esportsTeamId")]
            ?: return null
        val redTeamId = providerTeamMap[redMeta.optString("esportsTeamId")]
            ?: return null
        if (blueTeamId == redTeamId) return null

        val gameId = GameIdentity.canonical(query.matchId, gameNumber)
        val provenance = SourceProvenance(
            providerId = providerId,
            sourceClass = SourceClass.POST_MATCH_SOURCE,
            authority = authority,
            freshnessClass = FreshnessClass.STATIC,
            observedAtEpochMillis = observedAtEpochMillis,
            sourceTimestampEpochMillis = sourceTimestampEpochMillis,
            sourceUri = LIVE_BASE,
        )
        val snapshot = LiveGameSnapshot(
            game = GameContext(
                gameId = gameId,
                matchId = query.matchId,
                gameNumber = gameNumber,
                blueTeamId = blueTeamId,
                redTeamId = redTeamId,
            ),
            lifecycle = MatchLifecycleState.IN_GAME,
            elapsedSeconds = elapsedSeconds,
            blue = parseTeamState(blueFrame, blueTeamId),
            red = parseTeamState(redFrame, redTeamId),
            players = parsePlayers(blueMeta, blueFrame, blueTeamId) + parsePlayers(redMeta, redFrame, redTeamId),
        )
        return HistoricalTimelineFrame(snapshot = snapshot, provenance = provenance)
    }

    internal fun providerTeamMap(eventMatch: JSONObject, query: PostMatchQuery): Map<String, TeamId> {
        if (query.teams.size != 2) return emptyMap()
        val teams = eventMatch.optJSONArray("teams") ?: return emptyMap()
        val canonicalByToken = query.teams.associateBy { teamKey(it.code.ifBlank { it.name }) }
        return buildMap {
            for (index in 0 until teams.length()) {
                val team = teams.optJSONObject(index) ?: continue
                val externalId = team.optString("id").trim()
                if (externalId.isBlank()) continue
                val token = teamKey(team.optString("code").ifBlank { team.optString("name") })
                canonicalByToken[token]?.let { put(externalId, it.id) }
            }
        }
    }

    private fun parseTeamState(frame: JSONObject, teamId: TeamId): TeamLiveState = TeamLiveState(
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

    private fun findGame(games: JSONArray?, gameNumber: Int): JSONObject? {
        if (games == null) return null
        for (index in 0 until games.length()) {
            val game = games.optJSONObject(index) ?: continue
            if (game.optInt("number", index + 1) == gameNumber) return game
        }
        return null
    }

    private fun firstTimestamp(frames: JSONArray): Instant? {
        for (index in 0 until frames.length()) {
            parseInstant(frames.optJSONObject(index)?.optString("rfc460Timestamp").orEmpty())?.let { return it }
        }
        return null
    }

    private fun align10(value: Instant): Instant {
        val epoch = value.epochSecond
        return Instant.ofEpochSecond(epoch - Math.floorMod(epoch, WINDOW_SECONDS))
    }

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

    private fun intOrNull(root: JSONObject, key: String): Int? {
        if (!root.has(key) || root.isNull(key)) return null
        return when (val raw = root.opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.toDoubleOrNull()?.toInt()
            else -> null
        }?.takeIf { it >= 0 }
    }

    private fun getPersistedJson(url: String): JSONObject {
        val request = Request.Builder().url(url)
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .header("User-Agent", "Laner/2")
            .build()
        return executeJson(request)
    }

    private fun getLiveJson(url: String): JSONObject {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Laner/2")
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) throw IOException("empty response body")
        JSONObject(body)
    }

    private fun teamKey(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    private fun canonicalToken(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun safeMessage(error: Throwable): String = error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private companion object {
        const val RIOT_PROVIDER_ID = "riot-lolesports"
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val LIVE_BASE = "https://feed.lolesports.com/livestats/v1"
        const val WINDOW_SECONDS = 10L
        const val SAMPLE_SECONDS = 2
        const val MAX_WINDOWS = 1_080
        const val MAX_EMPTY_WINDOWS = 3
    }
}
