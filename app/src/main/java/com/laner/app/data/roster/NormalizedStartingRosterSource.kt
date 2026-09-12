package com.laner.app.data.roster

import com.laner.core.application.ProviderRead
import com.laner.core.application.ProviderStartingPlayer
import com.laner.core.application.ProviderStartingRosterEvidence
import com.laner.core.application.ProviderStartingRosterSnapshot
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.StartingRosterSourcePort
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
import java.time.Instant

/**
 * Transitional transport adapter for the normalized official-roster feed.
 * The feed contains evidence derived from official team/league accounts and official sites.
 * Laner still re-validates date, matchup and 5-role completeness in Application.
 */
class NormalizedStartingRosterSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) : StartingRosterSourcePort {
    override val providerId: String = "normalized-official-roster"
    override val authority: DataAuthority = DataAuthority.VERIFIED_PROVIDER

    override suspend fun read(
        context: SourceRequestContext,
    ): ProviderRead<ProviderStartingRosterSnapshot> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        endpoints.forEach { endpoint ->
            try {
                val root = getJson(endpoint)
                val schema = root.optInt("schemaVersion", -1)
                if (schema !in 1..MAX_SCHEMA_VERSION) throw IOException("unsupported schema $schema")
                val rows = root.optJSONArray("evidence") ?: throw IOException("missing evidence array")
                val evidence = parseEvidence(rows)
                return@withContext ProviderRead.Success(
                    ProviderStartingRosterSnapshot(
                        evidence = evidence,
                        observedAtEpochMillis = context.nowEpochMillis,
                        sourceUri = endpoint,
                    )
                )
            } catch (error: Throwable) {
                lastError = error
            }
        }

        ProviderRead.Failure(
            DiagnosticFailure(
                code = ErrorCode("LNR-SRC-PRE-008"),
                message = "Normalized starting-roster feed unavailable: ${safeMessage(lastError)}",
                retryable = true,
                context = mapOf("provider" to providerId, "endpoints" to endpoints.size.toString()),
            )
        )
    }

    private fun parseEvidence(rows: JSONArray): List<ProviderStartingRosterEvidence> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val matchDate = row.optString("matchDateLocal").ifBlank { row.optString("matchDateChina") }
            val timezone = row.optString("timezone").ifBlank { "Asia/Shanghai" }
            val team = row.optString("team").trim()
            val opponent = row.optString("opponent").trim()
            val platform = row.optString("platform").ifBlank { "OFFICIAL" }
            val account = row.optString("account").trim()
            val publishedAt = parseInstant(row.optString("publishedAt")) ?: continue
            val observedAt = parseInstant(row.optString("observedAt")) ?: publishedAt
            val startersJson = row.optJSONArray("starters") ?: continue
            val starters = buildList {
                for (starterIndex in 0 until startersJson.length()) {
                    val player = startersJson.optJSONObject(starterIndex) ?: continue
                    val handle = player.optString("id").trim()
                    val role = player.optString("role").trim()
                    if (handle.isBlank() || role.isBlank()) continue
                    add(
                        ProviderStartingPlayer(
                            externalId = player.optString("externalId").ifBlank { handle },
                            handle = handle,
                            role = role,
                        )
                    )
                }
            }
            if (matchDate.isBlank() || team.isBlank() || opponent.isBlank() || account.isBlank()) continue

            add(
                ProviderStartingRosterEvidence(
                    matchDateLocal = matchDate,
                    timezoneId = timezone,
                    league = row.optString("league"),
                    team = team,
                    opponent = opponent,
                    starters = starters,
                    sourceCategory = row.optString("source"),
                    evidenceType = row.optString("evidenceType").ifBlank { "TEXT" },
                    platform = platform,
                    account = account,
                    publishedAtEpochMillis = publishedAt,
                    observedAtEpochMillis = observedAt,
                    confidence = row.optDouble("confidence", 1.0).toFloat().coerceIn(0f, 1f),
                    sourceUri = row.optString("sourceUrl").takeIf { it.isNotBlank() },
                )
            )
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/json,text/plain;q=0.9,*/*;q=0.1")
            .header("User-Agent", "Laner-Roster/2")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("empty response body")
            return JSONObject(body)
        }
    }

    private fun parseInstant(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun safeMessage(error: Throwable?): String =
        error?.message?.take(160)?.takeIf { it.isNotBlank() }
            ?: error?.javaClass?.simpleName
            ?: "unknown error"

    private companion object {
        const val MAX_SCHEMA_VERSION = 3
        val DEFAULT_ENDPOINTS = listOf(
            "https://gitee.com/xiaobaiaaa1/Rlftlab/raw/main/data/global/starting_rosters.json",
            "https://cdn.jsdelivr.net/gh/xbu080675-creator/Rlftlab@main/data/global/starting_rosters.json",
            "https://raw.githubusercontent.com/xbu080675-creator/Rlftlab/main/data/global/starting_rosters.json",
        )
    }
}
