package com.laner.app.data.riot

import com.laner.core.application.GlobalPreMatchSourcePort
import com.laner.core.application.ProviderCompetitionEntry
import com.laner.core.application.ProviderPreMatchSnapshot
import com.laner.core.application.ProviderRead
import com.laner.core.application.ProviderScheduleEntry
import com.laner.core.application.ProviderTeamEntry
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant

class RiotGlobalPreMatchSource(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : GlobalPreMatchSourcePort {
    override val providerId: String = "riot-lolesports"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override suspend fun readGlobal(context: SourceRequestContext): ProviderRead<ProviderPreMatchSnapshot> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-PRE-001"),
                        message = "LoL Esports API credential is not configured",
                        retryable = false,
                        context = mapOf("provider" to providerId),
                    )
                )
            }

            val warnings = mutableListOf<DiagnosticFailure>()
            try {
                val competitions = runCatching { fetchCompetitions() }
                    .getOrElse { error ->
                        warnings += DiagnosticFailure(
                            code = ErrorCode("LNR-SRC-PRE-003"),
                            message = "Global competition catalogue unavailable: ${safeMessage(error)}",
                            retryable = true,
                            context = mapOf("provider" to providerId, "operation" to "getLeagues"),
                        )
                        emptyList()
                    }
                val competitionById = competitions.associateBy { it.externalId }
                val matches = fetchGlobalSchedule(competitionById, warnings)

                ProviderRead.Success(
                    ProviderPreMatchSnapshot(
                        competitions = competitions,
                        matches = matches,
                        observedAtEpochMillis = context.nowEpochMillis,
                        sourceUri = PERSISTED_BASE,
                        warnings = warnings,
                    )
                )
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-PRE-002"),
                        message = "LoL Esports global schedule unavailable: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId, "operation" to "getSchedule"),
                    )
                )
            }
        }

    private fun fetchCompetitions(): List<ProviderCompetitionEntry> {
        val root = getJson("$PERSISTED_BASE/getLeagues?hl=en-US")
        val leagues = root.optJSONObject("data")?.optJSONArray("leagues") ?: JSONArray()
        return buildList {
            for (index in 0 until leagues.length()) {
                val league = leagues.optJSONObject(index) ?: continue
                val externalId = league.optString("id")
                val slug = league.optString("slug")
                val name = league.optString("name")
                if (externalId.isBlank() || (slug.isBlank() && name.isBlank())) continue
                if (isExcludedProduct(slug, name)) continue

                val regionValue = league.opt("region")
                val regionObject = regionValue as? JSONObject
                val regionName = when (regionValue) {
                    is String -> regionValue
                    is JSONObject -> regionValue.optString("name")
                    else -> ""
                }
                val regionCode = regionObject?.optString("code").orEmpty()

                add(
                    ProviderCompetitionEntry(
                        externalId = externalId,
                        slug = slug,
                        name = name.ifBlank { slug.uppercase() },
                        regionCode = regionCode.takeIf { it.isNotBlank() },
                        regionName = regionName.takeIf { it.isNotBlank() },
                    )
                )
            }
        }.distinctBy { it.externalId }
    }

    private fun fetchGlobalSchedule(
        competitionById: Map<String, ProviderCompetitionEntry>,
        warnings: MutableList<DiagnosticFailure>,
    ): List<ProviderScheduleEntry> {
        val pages = mutableListOf<JSONObject>()
        val center = fetchSchedulePage(pageToken = null)
        pages += center

        val visited = mutableSetOf<String>()
        for (direction in listOf("older", "newer")) {
            var token = schedulePageToken(center, direction)
            var pageCount = 0
            while (token.isNotBlank() && pageCount < MAX_PAGES_PER_DIRECTION) {
                val visitKey = "$direction:$token"
                if (!visited.add(visitKey)) break
                val page = try {
                    fetchSchedulePage(token)
                } catch (error: Throwable) {
                    warnings += DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-PRE-004"),
                        message = "Schedule pagination degraded: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf(
                            "provider" to providerId,
                            "direction" to direction,
                            "page" to pageCount.toString(),
                        ),
                    )
                    break
                }
                pages += page
                token = schedulePageToken(page, direction)
                pageCount += 1
            }
        }

        return pages
            .flatMap { parseSchedulePage(it, competitionById) }
            .distinctBy { row -> row.externalEventId.ifBlank { row.externalMatchId } }
            .sortedBy { it.startTimeEpochMillis }
    }

    private fun fetchSchedulePage(pageToken: String?): JSONObject {
        val url = buildString {
            append("$PERSISTED_BASE/getSchedule?hl=en-US")
            if (!pageToken.isNullOrBlank()) {
                append("&pageToken=")
                append(URLEncoder.encode(pageToken, Charsets.UTF_8.name()))
            }
        }
        return getJson(url)
    }

    private fun schedulePageToken(root: JSONObject, direction: String): String =
        root.optJSONObject("data")
            ?.optJSONObject("schedule")
            ?.optJSONObject("pages")
            ?.optString(direction)
            .orEmpty()

    private fun parseSchedulePage(
        root: JSONObject,
        competitionById: Map<String, ProviderCompetitionEntry>,
    ): List<ProviderScheduleEntry> {
        val events = root.optJSONObject("data")
            ?.optJSONObject("schedule")
            ?.optJSONArray("events") ?: JSONArray()

        return buildList {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                if (!event.optString("type").equals("match", ignoreCase = true)) continue

                val match = event.optJSONObject("match") ?: continue
                val teams = parseTeams(match.optJSONArray("teams"))
                if (teams.size != 2) continue

                val league = event.optJSONObject("league")
                val competitionExternalId = league?.optString("id").orEmpty()
                val knownCompetition = competitionById[competitionExternalId]
                val competitionSlug = league?.optString("slug").orEmpty()
                    .ifBlank { knownCompetition?.slug.orEmpty() }
                val competitionName = league?.optString("name").orEmpty()
                    .ifBlank { knownCompetition?.name.orEmpty() }
                    .ifBlank { competitionSlug.uppercase() }
                if (competitionSlug.isBlank() && competitionName.isBlank()) continue
                if (isExcludedProduct(competitionSlug, competitionName)) continue

                val start = parseInstant(event.optString("startTime")) ?: continue
                val count = match.optJSONObject("strategy")?.optInt("count", 0) ?: 0
                add(
                    ProviderScheduleEntry(
                        externalEventId = event.optString("id"),
                        externalMatchId = match.optString("id"),
                        competitionExternalId = competitionExternalId,
                        competitionSlug = competitionSlug,
                        competitionName = competitionName,
                        regionCode = knownCompetition?.regionCode,
                        regionName = knownCompetition?.regionName,
                        blockName = event.optString("blockName"),
                        startTimeEpochMillis = start.toEpochMilli(),
                        rawState = event.optString("state"),
                        bestOf = count.takeIf { it > 0 },
                        teams = teams,
                    )
                )
            }
        }
    }

    private fun parseTeams(array: JSONArray?): List<ProviderTeamEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val team = array.optJSONObject(index) ?: continue
                val result = team.optJSONObject("result")
                val code = team.optString("code")
                val name = team.optString("name")
                val slug = team.optString("slug")
                if (code.isBlank() && name.isBlank() && slug.isBlank()) continue
                add(
                    ProviderTeamEntry(
                        externalId = team.optString("id"),
                        slug = slug,
                        code = code,
                        name = name,
                        gameWins = result?.optInt("gameWins", team.optInt("gameWins", 0))
                            ?: team.optInt("gameWins", 0),
                        outcome = result?.optString("outcome").orEmpty().ifBlank { team.optString("outcome") },
                    )
                )
            }
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .header("User-Agent", "Laner/2")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("empty response body")
            return JSONObject(body)
        }
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun isExcludedProduct(slug: String, name: String): Boolean {
        val normalizedSlug = slug.trim().lowercase().replace('_', '-')
        val normalizedName = name.trim().lowercase()
        return normalizedSlug == "tft-esports" || normalizedName == "tft esports"
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private companion object {
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
        const val MAX_PAGES_PER_DIRECTION = 10
    }
}
