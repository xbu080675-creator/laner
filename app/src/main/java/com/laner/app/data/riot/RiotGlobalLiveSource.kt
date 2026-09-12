package com.laner.app.data.riot

import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.LiveSnapshotQuery
import com.laner.core.application.LiveSnapshotSourcePort
import com.laner.core.application.ProviderLiveObservation
import com.laner.core.application.ProviderLiveSnapshot
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.TargetAwareLiveStateSourcePort
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
import kotlin.math.abs

/**
 * Global Riot LIVE baseline used for every competition visible in Riot global schedule.
 *
 * Schedule/EventDetails discover provider identity and explicit match/game state. LiveStats is the
 * only signal allowed to assert IN_GAME and is also translated into canonical current snapshots.
 * Provider ids never become Laner MatchId/GameId.
 */
class RiotGlobalLiveSource(
    private val apiKey: String,
    private val identityRepository: ProviderMatchIdentityRepository,
    private val client: OkHttpClient = OkHttpClient(),
) : TargetAwareLiveStateSourcePort, LiveSnapshotSourcePort {
    override val providerId: String = "riot-lolesports-live"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override suspend fun readLiveState(
        query: LiveMatchSourceQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveObservation> {
        if (apiKey.isBlank()) return failure("LNR-SRC-LIVE-001", "LoL Esports API credential is not configured", false)
        if (query.teams.size != 2 || query.scheduledStartEpochMillis == null) {
            return ProviderRead.Success(ProviderLiveObservation(matchId = query.matchId, observedAtEpochMillis = context.nowEpochMillis))
        }
        return withContext(Dispatchers.IO) {
            try {
                val identity = ensureIdentity(query, context.nowEpochMillis)
                    ?: return@withContext ProviderRead.Success(
                        ProviderLiveObservation(matchId = query.matchId, observedAtEpochMillis = context.nowEpochMillis)
                    )
                val details = getPersistedJson("$PERSISTED_BASE/getEventDetails?hl=en-US&id=${enc(identity.externalEventId ?: return@withContext ProviderRead.Success(ProviderLiveObservation(matchId = query.matchId, observedAtEpochMillis = context.nowEpochMillis)))}")
                ProviderRead.Success(parseLiveObservation(details, query, context.nowEpochMillis))
            } catch (error: Throwable) {
                failure("LNR-SRC-LIVE-002", "Riot global live state failed: ${safeMessage(error)}", true)
            }
        }
    }

    override suspend fun readSnapshot(
        query: LiveSnapshotQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveSnapshot?> {
        if (apiKey.isBlank()) return failure("LNR-SRC-LIVE-003", "LoL Esports API credential is not configured for live snapshot", false)
        return withContext(Dispatchers.IO) {
            try {
                val identity = identityRepository.find(RIOT_PROVIDER_ID, query.matchId)
                    ?: return@withContext ProviderRead.Success(null)
                val eventId = identity.externalEventId ?: return@withContext ProviderRead.Success(null)
                val details = getPersistedJson("$PERSISTED_BASE/getEventDetails?hl=en-US&id=${enc(eventId)}")
                val eventMatch = details.optJSONObject("data")?.optJSONObject("event")?.optJSONObject("match")
                    ?: return@withContext ProviderRead.Success(null)
                val providerTeamMap = providerTeamMap(eventMatch, query.teams.map { TeamHint(it.id, it.code, it.name) })
                if (providerTeamMap.size != 2) return@withContext ProviderRead.Success(null)
                val game = findGame(eventMatch.optJSONArray("games"), query.gameNumber)
                    ?: return@withContext ProviderRead.Success(null)
                val providerGameId = game.optString("id").trim()
                if (providerGameId.isBlank()) return@withContext ProviderRead.Success(null)
                val window = latestWindow(providerGameId, context.nowEpochMillis)
                    ?: return@withContext ProviderRead.Success(null)
                val frame = latestFrame(window.optJSONArray("frames"))
                    ?: return@withContext ProviderRead.Success(null)
                val metadata = window.optJSONObject("gameMetadata") ?: JSONObject()
                ProviderRead.Success(parseSnapshot(query, frame, metadata, providerTeamMap, context.nowEpochMillis))
            } catch (error: Throwable) {
                failure("LNR-SRC-LIVE-004", "Riot global live snapshot failed: ${safeMessage(error)}", true)
            }
        }
    }

    internal fun parseLiveObservation(
        root: JSONObject,
        query: LiveMatchSourceQuery,
        observedAtEpochMillis: Long,
    ): ProviderLiveObservation {
        val event = root.optJSONObject("data")?.optJSONObject("event") ?: JSONObject()
        val eventMatch = event.optJSONObject("match") ?: JSONObject()
        val games = eventMatch.optJSONArray("games") ?: JSONArray()
        val eventState = token(event.optString("state").ifBlank { eventMatch.optString("state") })

        var completedCount = 0
        var latestCompletedNumber: Int? = null
        var liveGameNumber: Int? = null
        var liveProviderGameId: String? = null
        var liveTimestamp: Long? = null

        for (index in 0 until games.length()) {
            val game = games.optJSONObject(index) ?: continue
            val number = game.optInt("number", index + 1).takeIf { it > 0 } ?: continue
            val state = token(game.optString("state"))
            if (state in COMPLETE_TOKENS) {
                completedCount += 1
                latestCompletedNumber = maxOf(latestCompletedNumber ?: 0, number)
                continue
            }
            val providerGameId = game.optString("id").trim()
            if (providerGameId.isBlank()) continue
            val live = runCatching { latestWindow(providerGameId, observedAtEpochMillis) }.getOrNull()
            val frame = latestFrame(live?.optJSONArray("frames"))
            if (frame != null && !token(frame.optString("gameState")).contains("finished")) {
                liveGameNumber = number
                liveProviderGameId = providerGameId
                liveTimestamp = parseInstant(frame.optString("rfc460Timestamp"))?.toEpochMilli()
                break
            }
        }

        if (liveGameNumber != null) {
            return ProviderLiveObservation(
                matchId = query.matchId,
                eventStarted = true,
                liveFrameObserved = true,
                gameId = GameIdentity.canonical(query.matchId, liveGameNumber),
                gameNumber = liveGameNumber,
                observedAtEpochMillis = observedAtEpochMillis,
                sourceTimestampEpochMillis = liveTimestamp,
                sourceUri = LIVE_BASE,
            )
        }

        val seriesEnded = eventState in COMPLETE_TOKENS || (games.length() > 0 && completedCount == games.length())
        if (seriesEnded) {
            val last = latestCompletedNumber
            return ProviderLiveObservation(
                matchId = query.matchId,
                eventStarted = true,
                seriesEnded = true,
                gameId = last?.let { GameIdentity.canonical(query.matchId, it) },
                gameNumber = last,
                observedAtEpochMillis = observedAtEpochMillis,
                sourceUri = "$PERSISTED_BASE/getEventDetails",
            )
        }

        val eventStarted = eventState in LIVE_TOKENS || completedCount > 0
        val betweenGames = eventStarted && completedCount > 0
        val last = latestCompletedNumber
        return ProviderLiveObservation(
            matchId = query.matchId,
            eventStarted = eventStarted,
            betweenGames = betweenGames,
            gameId = if (betweenGames && last != null) GameIdentity.canonical(query.matchId, last) else null,
            gameNumber = if (betweenGames) last else null,
            observedAtEpochMillis = observedAtEpochMillis,
            sourceUri = "$PERSISTED_BASE/getEventDetails",
        )
    }

    internal fun parseSnapshot(
        query: LiveSnapshotQuery,
        frame: JSONObject,
        metadata: JSONObject,
        providerTeamMap: Map<String, TeamId>,
        observedAtEpochMillis: Long,
    ): ProviderLiveSnapshot? {
        val blueFrame = frame.optJSONObject("blueTeam") ?: return null
        val redFrame = frame.optJSONObject("redTeam") ?: return null
        val blueMeta = metadata.optJSONObject("blueTeamMetadata") ?: JSONObject()
        val redMeta = metadata.optJSONObject("redTeamMetadata") ?: JSONObject()
        val blueTeamId = providerTeamMap[blueMeta.optString("esportsTeamId")] ?: return null
        val redTeamId = providerTeamMap[redMeta.optString("esportsTeamId")] ?: return null
        if (blueTeamId == redTeamId) return null
        val timestamp = parseInstant(frame.optString("rfc460Timestamp"))
        val gameTime = intOrNull(frame, "gameTime") ?: intOrNull(frame, "gameTimeSeconds")
        val snapshot = LiveGameSnapshot(
            game = GameContext(
                gameId = query.gameId,
                matchId = query.matchId,
                gameNumber = query.gameNumber,
                blueTeamId = blueTeamId,
                redTeamId = redTeamId,
            ),
            lifecycle = MatchLifecycleState.IN_GAME,
            elapsedSeconds = gameTime,
            blue = parseTeamState(blueFrame, blueTeamId),
            red = parseTeamState(redFrame, redTeamId),
            players = parsePlayers(blueMeta, blueFrame, blueTeamId) + parsePlayers(redMeta, redFrame, redTeamId),
        )
        return ProviderLiveSnapshot(
            snapshot = snapshot,
            provenance = SourceProvenance(
                providerId = providerId,
                sourceClass = SourceClass.LIVE_MATCH_SOURCE,
                authority = authority,
                freshnessClass = FreshnessClass.REALTIME,
                observedAtEpochMillis = observedAtEpochMillis,
                sourceTimestampEpochMillis = timestamp?.toEpochMilli(),
                sourceUri = LIVE_BASE,
            ),
        )
    }

    private suspend fun ensureIdentity(query: LiveMatchSourceQuery, observedAt: Long): ProviderMatchIdentity? {
        identityRepository.find(RIOT_PROVIDER_ID, query.matchId)?.let { return it }
        val candidate = findScheduleCandidate(query) ?: return null
        val identity = ProviderMatchIdentity(
            providerId = RIOT_PROVIDER_ID,
            matchId = query.matchId,
            externalEventId = candidate.eventId,
            externalMatchId = candidate.matchId.takeIf { it.isNotBlank() },
            observedAtEpochMillis = observedAt,
        )
        identityRepository.upsert(identity)
        return identity
    }

    private fun findScheduleCandidate(query: LiveMatchSourceQuery): ScheduleCandidate? {
        val targetStart = query.scheduledStartEpochMillis ?: return null
        val targetTeams = query.teams.map { teamKey(it.code.ifBlank { it.name }) }.toSet()
        val root = getPersistedJson("$PERSISTED_BASE/getSchedule?hl=en-US")
        val events = root.optJSONObject("data")?.optJSONObject("schedule")?.optJSONArray("events") ?: JSONArray()
        val matches = buildList {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                if (!event.optString("type").equals("match", true)) continue
                val start = parseInstant(event.optString("startTime"))?.toEpochMilli() ?: continue
                if (abs(start - targetStart) > MATCH_TIME_TOLERANCE_MS) continue
                val match = event.optJSONObject("match") ?: continue
                val teams = match.optJSONArray("teams") ?: continue
                val tokens = buildSet {
                    for (teamIndex in 0 until teams.length()) {
                        val team = teams.optJSONObject(teamIndex) ?: continue
                        val key = teamKey(team.optString("code").ifBlank { team.optString("name") })
                        if (key.isNotBlank()) add(key)
                    }
                }
                if (tokens != targetTeams) continue
                add(ScheduleCandidate(event.optString("id"), match.optString("id")))
            }
        }
        return matches.distinctBy { it.eventId }.singleOrNull()
    }

    private fun latestWindow(providerGameId: String, nowEpochMillis: Long): JSONObject? {
        val now = Instant.ofEpochMilli(nowEpochMillis)
        for (backSeconds in LIVE_BACKOFF_SECONDS) {
            val target = now.minusSeconds(backSeconds)
            val aligned = Instant.ofEpochSecond(target.epochSecond - Math.floorMod(target.epochSecond, WINDOW_SECONDS))
            val root = runCatching {
                getLiveJson("$LIVE_BASE/window/${enc(providerGameId)}?startingTime=${enc(aligned.toString())}")
            }.getOrNull() ?: continue
            if ((root.optJSONArray("frames")?.length() ?: 0) > 0) return root
        }
        return null
    }

    private fun latestFrame(frames: JSONArray?): JSONObject? {
        if (frames == null || frames.length() == 0) return null
        var best: JSONObject? = null
        var bestTime = Long.MIN_VALUE
        for (index in 0 until frames.length()) {
            val frame = frames.optJSONObject(index) ?: continue
            val timestamp = parseInstant(frame.optString("rfc460Timestamp"))?.toEpochMilli() ?: continue
            if (timestamp > bestTime) {
                best = frame
                bestTime = timestamp
            }
        }
        return best
    }

    private fun providerTeamMap(eventMatch: JSONObject, teams: List<TeamHint>): Map<String, TeamId> {
        val canonical = teams.associateBy { teamKey(it.code.ifBlank { it.name }) }
        val providerTeams = eventMatch.optJSONArray("teams") ?: return emptyMap()
        return buildMap {
            for (index in 0 until providerTeams.length()) {
                val row = providerTeams.optJSONObject(index) ?: continue
                val externalId = row.optString("id").trim()
                val key = teamKey(row.optString("code").ifBlank { row.optString("name") })
                val target = canonical[key] ?: continue
                if (externalId.isNotBlank()) put(externalId, target.id)
            }
        }
    }

    private fun findGame(games: JSONArray?, number: Int): JSONObject? {
        if (games == null) return null
        for (index in 0 until games.length()) {
            val game = games.optJSONObject(index) ?: continue
            if (game.optInt("number", index + 1) == number) return game
        }
        return null
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

    private fun <T> failure(code: String, message: String, retryable: Boolean): ProviderRead<T> = ProviderRead.Failure(
        DiagnosticFailure(
            code = ErrorCode(code),
            message = message,
            retryable = retryable,
            context = mapOf("provider" to providerId),
        )
    )

    private fun token(value: String): String = canonicalToken(value)
    private fun teamKey(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    private fun canonicalToken(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun safeMessage(error: Throwable): String = error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private data class ScheduleCandidate(val eventId: String, val matchId: String)
    private data class TeamHint(val id: TeamId, val code: String, val name: String)

    private companion object {
        const val RIOT_PROVIDER_ID = "riot-lolesports"
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val LIVE_BASE = "https://feed.lolesports.com/livestats/v1"
        const val MATCH_TIME_TOLERANCE_MS = 90L * 60L * 1000L
        const val WINDOW_SECONDS = 10L
        val LIVE_BACKOFF_SECONDS = longArrayOf(10L, 20L, 30L, 40L, 60L, 90L, 120L, 180L)
        val COMPLETE_TOKENS = setOf("completed", "complete", "finished")
        val LIVE_TOKENS = setOf("inprogress", "in-progress", "live", "started")
    }
}
