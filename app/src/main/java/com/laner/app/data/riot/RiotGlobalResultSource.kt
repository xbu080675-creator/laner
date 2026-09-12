package com.laner.app.data.riot

import com.laner.core.application.PostMatchQuery
import com.laner.core.application.PostResultSourcePort
import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.SeriesResultState
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

/** Global official SeriesResult source backed by Riot global schedule. */
class RiotGlobalResultSource(
    private val apiKey: String,
    private val identityRepository: ProviderMatchIdentityRepository,
    private val client: OkHttpClient = OkHttpClient(),
) : PostResultSourcePort {
    override val providerId: String = "riot-lolesports"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override fun supports(query: PostMatchQuery): Boolean =
        query.teams.size == 2 && query.scheduledStartEpochMillis != null

    override suspend fun readResult(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<SeriesResult?> {
        if (apiKey.isBlank()) {
            return ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-POST-022"),
                    message = "LoL Esports API credential is not configured for global result",
                    retryable = false,
                    context = mapOf("provider" to providerId),
                )
            )
        }
        if (!supports(query)) return ProviderRead.Success(null)
        return withContext(Dispatchers.IO) {
            try {
                val candidate = findCandidate(query) ?: return@withContext ProviderRead.Success(null)
                identityRepository.upsert(
                    ProviderMatchIdentity(
                        providerId = providerId,
                        matchId = query.matchId,
                        externalEventId = candidate.eventId,
                        externalMatchId = candidate.matchId.takeIf { it.isNotBlank() },
                        observedAtEpochMillis = context.nowEpochMillis,
                    )
                )
                ProviderRead.Success(candidate.toResult(query, context.nowEpochMillis))
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-POST-023"),
                        message = "Riot global result failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }
    }

    private fun findCandidate(query: PostMatchQuery): ResultCandidate? {
        val found = mutableListOf<ResultCandidate>()
        val center = getJson("$PERSISTED_BASE/getSchedule?hl=en-US")
        found += parseCandidates(center, query)
        var token = pageToken(center, "older")
        var pages = 0
        while (token.isNotBlank() && pages < MAX_OLDER_PAGES && found.isEmpty()) {
            val page = getJson("$PERSISTED_BASE/getSchedule?hl=en-US&pageToken=${enc(token)}")
            found += parseCandidates(page, query)
            token = pageToken(page, "older")
            pages += 1
        }
        return found.distinctBy { it.eventId }.singleOrNull()
    }

    internal fun parseCandidates(root: JSONObject, query: PostMatchQuery): List<ResultCandidate> {
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
                val requestedSlug = query.competitionSlug.orEmpty()
                val leagueSlug = event.optJSONObject("league")?.optString("slug").orEmpty()
                if (requestedSlug.isNotBlank() && canonicalToken(requestedSlug) != canonicalToken(leagueSlug)) continue
                val match = event.optJSONObject("match") ?: continue
                val teams = match.optJSONArray("teams") ?: continue
                if (teams.length() != 2) continue
                val parsed = buildList {
                    for (teamIndex in 0 until teams.length()) {
                        val team = teams.optJSONObject(teamIndex) ?: continue
                        val token = teamKey(team.optString("code").ifBlank { team.optString("name") })
                        if (token.isBlank()) continue
                        val result = team.optJSONObject("result")
                        add(
                            TeamResult(
                                token = token,
                                wins = result?.optInt("gameWins", team.optInt("gameWins", 0)) ?: team.optInt("gameWins", 0),
                                outcome = result?.optString("outcome").orEmpty().ifBlank { team.optString("outcome") },
                            )
                        )
                    }
                }
                if (parsed.map { it.token }.toSet() != targetTeams) continue
                val leftToken = teamKey(query.teams[0].code.ifBlank { query.teams[0].name })
                val left = parsed.firstOrNull { it.token == leftToken } ?: continue
                val right = parsed.firstOrNull { it.token != left.token } ?: continue
                val state = canonicalToken(event.optString("state"))
                add(
                    ResultCandidate(
                        eventId = event.optString("id"),
                        matchId = match.optString("id"),
                        leftWins = left.wins,
                        rightWins = right.wins,
                        leftOutcome = left.outcome,
                        rightOutcome = right.outcome,
                        completed = state.contains("complete") || state == "finished",
                    )
                )
            }
        }
    }

    private fun ResultCandidate.toResult(query: PostMatchQuery, observedAtEpochMillis: Long): SeriesResult? {
        val winner = when {
            canonicalToken(leftOutcome) in WIN_TOKENS -> query.teams[0].id
            canonicalToken(rightOutcome) in WIN_TOKENS -> query.teams[1].id
            completed && leftWins > rightWins -> query.teams[0].id
            completed && rightWins > leftWins -> query.teams[1].id
            else -> null
        }
        val final = completed && winner != null && leftWins != rightWins
        val provenance = SourceProvenance(
            providerId = providerId,
            sourceClass = SourceClass.POST_MATCH_SOURCE,
            authority = authority,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = observedAtEpochMillis,
            sourceUri = "$PERSISTED_BASE/getSchedule",
        )
        return if (final) {
            SeriesResult(
                matchId = query.matchId,
                leftTeamId = query.teams[0].id,
                rightTeamId = query.teams[1].id,
                leftWins = leftWins,
                rightWins = rightWins,
                bestOf = query.bestOf,
                state = SeriesResultState.FINAL,
                winnerTeamId = winner,
                provenance = provenance,
            )
        } else {
            SeriesResult(
                matchId = query.matchId,
                leftTeamId = query.teams[0].id,
                rightTeamId = query.teams[1].id,
                leftWins = leftWins,
                rightWins = rightWins,
                bestOf = query.bestOf,
                state = SeriesResultState.PARTIAL,
                winnerTeamId = null,
                provenance = provenance,
            )
        }
    }

    private fun pageToken(root: JSONObject, direction: String): String = root.optJSONObject("data")
        ?.optJSONObject("schedule")?.optJSONObject("pages")?.optString(direction).orEmpty()

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url)
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

    internal data class ResultCandidate(
        val eventId: String,
        val matchId: String,
        val leftWins: Int,
        val rightWins: Int,
        val leftOutcome: String,
        val rightOutcome: String,
        val completed: Boolean,
    )

    private data class TeamResult(val token: String, val wins: Int, val outcome: String)

    private companion object {
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val MAX_OLDER_PAGES = 10
        const val MATCH_TIME_TOLERANCE_MS = 90L * 60L * 1000L
        val WIN_TOKENS = setOf("win", "winner")
    }
}
