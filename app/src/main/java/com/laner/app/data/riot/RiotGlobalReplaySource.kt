package com.laner.app.data.riot

import com.laner.core.application.PostMatchQuery
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.application.ProviderRead
import com.laner.core.application.ReplaySourcePort
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ReplayProvider
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
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
 * Global Riot replay metadata source.
 *
 * Works for any competition present in Riot's global schedule (regional or international). It never
 * treats canonical MatchId as a Riot ID. A provider event identity is loaded from the mapping cache
 * or uniquely resolved from Riot schedule using competition + teams + scheduled start, then cached.
 */
class RiotGlobalReplaySource(
    private val apiKey: String,
    private val identityRepository: ProviderMatchIdentityRepository,
    private val client: OkHttpClient = OkHttpClient(),
) : ReplaySourcePort {
    override val providerId: String = "riot-lolesports"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override fun supports(query: PostMatchQuery): Boolean =
        query.teams.size == 2 && query.scheduledStartEpochMillis != null

    override suspend fun readReplays(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<ReplayAsset>> {
        if (apiKey.isBlank()) {
            return ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-POST-020"),
                    message = "LoL Esports API credential is not configured for global replay metadata",
                    retryable = false,
                    context = mapOf("provider" to providerId),
                )
            )
        }
        if (!supports(query)) return ProviderRead.Success(emptyList())

        return withContext(Dispatchers.IO) {
            try {
                val identity = identityRepository.find(providerId, query.matchId)
                    ?: resolveIdentity(query, context.nowEpochMillis)
                    ?: return@withContext ProviderRead.Success(emptyList())
                val eventId = identity.externalEventId
                    ?: return@withContext ProviderRead.Success(emptyList())
                val root = getJson(
                    "$PERSISTED_BASE/getEventDetails?hl=en-US&id=${enc(eventId)}"
                )
                ProviderRead.Success(parseEventDetails(root, query, context.nowEpochMillis, eventId))
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-POST-021"),
                        message = "Riot global replay metadata failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }
    }

    internal fun parseEventDetails(
        root: JSONObject,
        query: PostMatchQuery,
        observedAtEpochMillis: Long,
        eventId: String,
    ): List<ReplayAsset> {
        val games = root.optJSONObject("data")
            ?.optJSONObject("event")
            ?.optJSONObject("match")
            ?.optJSONArray("games") ?: JSONArray()
        val provenance = SourceProvenance(
            providerId = providerId,
            sourceClass = SourceClass.POST_MATCH_SOURCE,
            authority = authority,
            freshnessClass = FreshnessClass.STATIC,
            observedAtEpochMillis = observedAtEpochMillis,
            sourceUri = "$PERSISTED_BASE/getEventDetails?id=$eventId",
        )
        return buildList {
            for (gameIndex in 0 until games.length()) {
                val game = games.optJSONObject(gameIndex) ?: continue
                val gameNumber = game.optInt("number", gameIndex + 1).takeIf { it > 0 } ?: continue
                val vods = game.optJSONArray("vods") ?: JSONArray()
                for (vodIndex in 0 until vods.length()) {
                    val vod = vods.optJSONObject(vodIndex) ?: continue
                    val parameter = vod.optString("parameter").trim()
                    if (parameter.isBlank()) continue
                    val provider = vod.optString("provider").trim().ifBlank { "youtube" }
                    val sourceUrl = mediaUrl(provider, parameter)
                    if (sourceUrl.isBlank()) continue
                    add(
                        ReplayAsset(
                            matchId = query.matchId,
                            gameId = GameIdentity.canonical(query.matchId, gameNumber),
                            gameNumber = gameNumber,
                            provider = replayProvider(provider, parameter),
                            locale = vod.optString("locale").trim().takeIf { it.isNotBlank() },
                            sourceUrl = sourceUrl,
                            externalMediaId = mediaId(provider, parameter),
                            offsetSeconds = vod.optInt("offset", 0).coerceAtLeast(0),
                            title = "G$gameNumber · ${vod.optString("locale").ifBlank { "Riot VOD" }}",
                            provenance = provenance,
                        )
                    )
                }
            }
        }.distinctBy { asset ->
            listOf(asset.gameNumber, asset.provider.name, asset.locale, asset.externalMediaId, asset.sourceUrl).joinToString("|")
        }
    }

    private suspend fun resolveIdentity(query: PostMatchQuery, observedAtEpochMillis: Long): ProviderMatchIdentity? {
        val candidates = mutableListOf<ScheduleCandidate>()
        val center = getJson("$PERSISTED_BASE/getSchedule?hl=en-US")
        candidates += parseScheduleCandidates(center, query)
        var token = pageToken(center, "older")
        var pages = 0
        while (token.isNotBlank() && pages < MAX_OLDER_PAGES && candidates.isEmpty()) {
            val page = getJson("$PERSISTED_BASE/getSchedule?hl=en-US&pageToken=${enc(token)}")
            candidates += parseScheduleCandidates(page, query)
            token = pageToken(page, "older")
            pages += 1
        }
        val unique = candidates.distinctBy { it.eventId }
        if (unique.size != 1) return null
        val winner = unique.single()
        val identity = ProviderMatchIdentity(
            providerId = providerId,
            matchId = query.matchId,
            externalEventId = winner.eventId,
            externalMatchId = winner.matchId.takeIf { it.isNotBlank() },
            observedAtEpochMillis = observedAtEpochMillis,
        )
        identityRepository.upsert(identity)
        return identity
    }

    internal fun parseScheduleCandidates(root: JSONObject, query: PostMatchQuery): List<ScheduleCandidate> {
        val targetStart = query.scheduledStartEpochMillis ?: return emptyList()
        if (query.teams.size != 2) return emptyList()
        val targetTeams = query.teams.map { teamKey(it.code.ifBlank { it.name }) }.toSet()
        val events = root.optJSONObject("data")
            ?.optJSONObject("schedule")
            ?.optJSONArray("events") ?: JSONArray()
        return buildList {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                if (!event.optString("type").equals("match", ignoreCase = true)) continue
                val eventId = event.optString("id").trim()
                if (eventId.isBlank()) continue
                val start = runCatching { Instant.parse(event.optString("startTime")).toEpochMilli() }.getOrNull() ?: continue
                if (abs(start - targetStart) > MATCH_TIME_TOLERANCE_MS) continue
                val leagueSlug = event.optJSONObject("league")?.optString("slug").orEmpty()
                val requestedSlug = query.competitionSlug.orEmpty()
                if (requestedSlug.isNotBlank() && canonicalToken(leagueSlug) != canonicalToken(requestedSlug)) continue
                val match = event.optJSONObject("match") ?: continue
                val teams = match.optJSONArray("teams") ?: continue
                val candidateTeams = buildSet {
                    for (teamIndex in 0 until teams.length()) {
                        val team = teams.optJSONObject(teamIndex) ?: continue
                        val token = teamKey(team.optString("code").ifBlank { team.optString("name") })
                        if (token.isNotBlank()) add(token)
                    }
                }
                if (candidateTeams != targetTeams) continue
                add(ScheduleCandidate(eventId, match.optString("id").trim()))
            }
        }
    }

    private fun pageToken(root: JSONObject, direction: String): String = root.optJSONObject("data")
        ?.optJSONObject("schedule")
        ?.optJSONObject("pages")
        ?.optString(direction)
        .orEmpty()

    private fun replayProvider(provider: String, parameter: String): ReplayProvider = when {
        provider.contains("youtube", true) || parameter.contains("youtu", true) -> ReplayProvider.YOUTUBE
        provider.contains("bilibili", true) || parameter.contains("bilibili", true) -> ReplayProvider.BILIBILI
        else -> ReplayProvider.RIOT
    }

    private fun mediaId(provider: String, parameter: String): String? {
        if (provider.contains("youtube", true) || parameter.contains("youtu", true)) {
            val raw = parameter.trim()
            return when {
                !raw.startsWith("http://") && !raw.startsWith("https://") -> raw.substringBefore('&').substringBefore('?')
                raw.contains("youtu.be/", true) -> raw.substringAfter("youtu.be/").substringBefore('?').substringBefore('&')
                raw.contains("/embed/", true) -> raw.substringAfter("/embed/").substringBefore('?').substringBefore('&')
                raw.contains("v=", true) -> raw.substringAfter("v=").substringBefore('&').substringBefore('#')
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
        return parameter.takeIf { it.isNotBlank() && !it.startsWith("http") }
    }

    private fun mediaUrl(provider: String, parameter: String): String {
        val raw = parameter.trim()
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val id = mediaId(provider, raw) ?: return ""
        return if (provider.contains("youtube", true)) "https://www.youtube.com/watch?v=$id" else raw
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .header("User-Agent", "Laner/2")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("empty response body")
            return JSONObject(body)
        }
    }

    private fun teamKey(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    private fun canonicalToken(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun safeMessage(error: Throwable): String = error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    internal data class ScheduleCandidate(val eventId: String, val matchId: String)

    private companion object {
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val MAX_OLDER_PAGES = 10
        const val MATCH_TIME_TOLERANCE_MS = 90L * 60L * 1000L
    }
}
