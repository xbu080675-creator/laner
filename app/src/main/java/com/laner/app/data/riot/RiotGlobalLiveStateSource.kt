package com.laner.app.data.riot

import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.application.ProviderLiveObservation
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.TargetAwareLiveStateSourcePort
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.GameIdentity
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
 * Global Riot LIVE lifecycle source.
 *
 * It discovers Riot event identity from the normalized teams + scheduled time, then reads
 * getEventDetails and (when a current provider game id exists) probes LiveStats for a real frame.
 * Provider ids remain inside the Adapter/identity repository; Domain only receives canonical ids.
 */
class RiotGlobalLiveStateSource(
    private val apiKey: String,
    private val identityRepository: ProviderMatchIdentityRepository,
    private val client: OkHttpClient = OkHttpClient(),
) : TargetAwareLiveStateSourcePort {
    override val providerId: String = "riot-livestats-live"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override suspend fun readLiveState(
        query: LiveMatchSourceQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveObservation> {
        if (apiKey.isBlank()) {
            return ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-LIVE-002"),
                    message = "LoL Esports API credential is not configured for Riot LIVE",
                    retryable = false,
                    context = mapOf("provider" to providerId),
                )
            )
        }
        if (query.teams.size != 2 || query.scheduledStartEpochMillis == null) {
            return ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-LIVE-003"),
                    message = "Riot LIVE target requires two teams and scheduled start",
                    retryable = false,
                    context = mapOf("provider" to providerId),
                )
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val identity = identityRepository.find(RIOT_PROVIDER_ID, query.matchId)
                    ?: resolveIdentity(query, context.nowEpochMillis)
                    ?: return@withContext ProviderRead.Failure(
                        DiagnosticFailure(
                            code = ErrorCode("LNR-SRC-LIVE-004"),
                            message = "Riot LIVE target could not be uniquely resolved",
                            retryable = true,
                            context = mapOf("provider" to providerId),
                        )
                    )
                val eventId = identity.externalEventId
                    ?: return@withContext ProviderRead.Failure(
                        DiagnosticFailure(
                            code = ErrorCode("LNR-SRC-LIVE-004"),
                            message = "Riot LIVE event identity is missing",
                            retryable = true,
                            context = mapOf("provider" to providerId),
                        )
                    )
                val root = getPersistedJson("$PERSISTED_BASE/getEventDetails?hl=en-US&id=${enc(eventId)}")
                ProviderRead.Success(parseEventDetails(root, query, context.nowEpochMillis, eventId))
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-LIVE-005"),
                        message = "Riot LIVE state failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }
    }

    internal fun parseEventDetails(
        root: JSONObject,
        query: LiveMatchSourceQuery,
        observedAtEpochMillis: Long,
        eventId: String,
    ): ProviderLiveObservation {
        val event = root.optJSONObject("data")?.optJSONObject("event") ?: JSONObject()
        val match = event.optJSONObject("match") ?: JSONObject()
        val games = match.optJSONArray("games") ?: JSONArray()
        val eventState = token(event.optString("state").ifBlank { match.optString("state") })

        val rows = buildList {
            for (index in 0 until games.length()) {
                val game = games.optJSONObject(index) ?: continue
                val number = game.optInt("number", index + 1).takeIf { it > 0 } ?: index + 1
                add(
                    GameRow(
                        number = number,
                        externalId = game.optString("id").trim(),
                        state = token(game.optString("state")),
                    )
                )
            }
        }.sortedBy { it.number }

        val seriesEnded = isCompleted(eventState)
        val active = rows.firstOrNull { isActive(it.state) }
        val completed = rows.filter { isCompleted(it.state) }
        val next = rows.firstOrNull { !isCompleted(it.state) }
        val probe = active ?: next
        val liveFrame = probe?.externalId?.takeIf { it.isNotBlank() }?.let { providerGameId ->
            runCatching { lastLiveFrame(providerGameId) }.getOrNull()
        }
        val frameTimestamp = liveFrame?.optString("rfc460Timestamp")
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        val frameState = token(liveFrame?.optString("gameState").orEmpty())
        val liveFrameObserved = liveFrame != null && !isCompleted(frameState)

        val currentNumber = when {
            liveFrameObserved -> probe?.number
            active != null -> active.number
            completed.isNotEmpty() && !seriesEnded -> completed.maxOf { it.number }
            else -> null
        }
        val betweenGames = !seriesEnded && !liveFrameObserved && active == null && completed.isNotEmpty()
        val eventStarted = seriesEnded || liveFrameObserved || active != null || completed.isNotEmpty() || isActive(eventState)

        return ProviderLiveObservation(
            matchId = query.matchId,
            eventStarted = eventStarted,
            liveFrameObserved = liveFrameObserved,
            gameEnded = betweenGames || seriesEnded,
            betweenGames = betweenGames,
            seriesEnded = seriesEnded,
            gameId = currentNumber?.let { GameIdentity.canonical(query.matchId, it) },
            gameNumber = currentNumber,
            observedAtEpochMillis = observedAtEpochMillis,
            sourceTimestampEpochMillis = frameTimestamp,
            revision = frameTimestamp ?: observedAtEpochMillis,
            sourceUri = "$PERSISTED_BASE/getEventDetails?id=$eventId",
        )
    }

    private suspend fun resolveIdentity(
        query: LiveMatchSourceQuery,
        observedAtEpochMillis: Long,
    ): ProviderMatchIdentity? {
        val center = getPersistedJson("$PERSISTED_BASE/getSchedule?hl=en-US")
        val candidates = parseScheduleCandidates(center, query).toMutableList()
        if (candidates.isEmpty()) {
            listOf("older", "newer").forEach { direction ->
                var token = pageToken(center, direction)
                var pages = 0
                while (token.isNotBlank() && pages < MAX_PAGES && candidates.isEmpty()) {
                    val page = getPersistedJson("$PERSISTED_BASE/getSchedule?hl=en-US&pageToken=${enc(token)}")
                    candidates += parseScheduleCandidates(page, query)
                    token = pageToken(page, direction)
                    pages += 1
                }
            }
        }
        val unique = candidates.distinctBy { it.eventId }
        if (unique.size != 1) return null
        val candidate = unique.single()
        val identity = ProviderMatchIdentity(
            providerId = RIOT_PROVIDER_ID,
            matchId = query.matchId,
            externalEventId = candidate.eventId,
            externalMatchId = candidate.matchId.takeIf { it.isNotBlank() },
            observedAtEpochMillis = observedAtEpochMillis,
        )
        identityRepository.upsert(identity)
        return identity
    }

    internal fun parseScheduleCandidates(root: JSONObject, query: LiveMatchSourceQuery): List<ScheduleCandidate> {
        val targetStart = query.scheduledStartEpochMillis ?: return emptyList()
        if (query.teams.size != 2) return emptyList()
        val targetTeams = query.teams.map { teamKey(it.code.ifBlank { it.name }) }.toSet()
        val events = root.optJSONObject("data")?.optJSONObject("schedule")?.optJSONArray("events") ?: JSONArray()
        return buildList {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                if (!event.optString("type").equals("match", true)) continue
                val start = runCatching { Instant.parse(event.optString("startTime")).toEpochMilli() }.getOrNull() ?: continue
                if (abs(start - targetStart) > MATCH_TIME_TOLERANCE_MS) continue
                val match = event.optJSONObject("match") ?: continue
                val teams = match.optJSONArray("teams") ?: continue
                val tokens = buildSet {
                    for (teamIndex in 0 until teams.length()) {
                        val team = teams.optJSONObject(teamIndex) ?: continue
                        teamKey(team.optString("code").ifBlank { team.optString("name") })
                            .takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
                if (tokens != targetTeams) continue
                val eventId = event.optString("id").trim()
                if (eventId.isBlank()) continue
                add(ScheduleCandidate(eventId, match.optString("id").trim()))
            }
        }
    }

    private fun lastLiveFrame(providerGameId: String): JSONObject? {
        val root = getLiveJson("$LIVE_BASE/window/${enc(providerGameId)}")
        val frames = root.optJSONArray("frames") ?: return null
        return (frames.length() - 1 downTo 0).firstNotNullOfOrNull { index -> frames.optJSONObject(index) }
    }

    private fun getPersistedJson(url: String): JSONObject = getJson(url, includeApiKey = true)
    private fun getLiveJson(url: String): JSONObject = getJson(url, includeApiKey = false)

    private fun getJson(url: String, includeApiKey: Boolean): JSONObject {
        val builder = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Laner/2")
        if (includeApiKey) builder.header("x-api-key", apiKey)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("empty response body")
            return JSONObject(body)
        }
    }

    private fun pageToken(root: JSONObject, direction: String): String = root.optJSONObject("data")
        ?.optJSONObject("schedule")?.optJSONObject("pages")?.optString(direction).orEmpty()

    private fun token(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), "")
    private fun isCompleted(value: String): Boolean = value.contains("complete") || value.contains("finished")
    private fun isActive(value: String): Boolean = value.contains("inprogress") || value == "live" || value == "started"
    private fun teamKey(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun safeMessage(error: Throwable): String = error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    internal data class ScheduleCandidate(val eventId: String, val matchId: String)
    private data class GameRow(val number: Int, val externalId: String, val state: String)

    private companion object {
        const val RIOT_PROVIDER_ID = "riot-lolesports"
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val LIVE_BASE = "https://feed.lolesports.com/livestats/v1"
        const val MAX_PAGES = 4
        const val MATCH_TIME_TOLERANCE_MS = 90L * 60L * 1000L
    }
}
