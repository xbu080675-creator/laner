package com.laner.app.data.riot

import com.laner.core.application.ProviderRead
import com.laner.core.application.ProviderRosterPlayer
import com.laner.core.application.ProviderRosterPoolSnapshot
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.TeamRosterSourcePort
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.TeamRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class RiotTeamRosterSource(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : TeamRosterSourcePort {
    override val providerId: String = "riot-lolesports-team"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override suspend fun readTeam(
        team: TeamRef,
        context: SourceRequestContext,
    ): ProviderRead<ProviderRosterPoolSnapshot> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-PRE-006"),
                    message = "LoL Esports team credential is not configured",
                    retryable = false,
                    context = mapOf("provider" to providerId, "team" to team.code.ifBlank { team.name }),
                )
            )
        }

        val lookup = team.id.value.substringAfter("lol:team:", "").ifBlank {
            slugify(team.name.ifBlank { team.code })
        }
        if (lookup.isBlank()) {
            return@withContext ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-PRE-007"),
                    message = "Team identity cannot be mapped to Riot getTeams",
                    retryable = false,
                    context = mapOf("provider" to providerId, "team_id" to team.id.value),
                )
            )
        }

        try {
            val encoded = URLEncoder.encode(lookup, Charsets.UTF_8.name())
            val root = getJson("$PERSISTED_BASE/getTeams?hl=en-US&id=$encoded")
            val teams = root.optJSONObject("data")?.optJSONArray("teams") ?: JSONArray()
            val selected = selectTeam(teams, team, lookup)
                ?: return@withContext ProviderRead.Success(
                    ProviderRosterPoolSnapshot(
                        players = emptyList(),
                        observedAtEpochMillis = context.nowEpochMillis,
                        sourceUri = "$PERSISTED_BASE/getTeams",
                    )
                )
            val players = selected.optJSONArray("players") ?: JSONArray()
            val normalized = buildList {
                for (index in 0 until players.length()) {
                    val player = players.optJSONObject(index) ?: continue
                    val handle = player.optString("summonerName").trim()
                    if (handle.isBlank()) continue
                    add(
                        ProviderRosterPlayer(
                            externalId = player.optString("id").trim(),
                            handle = handle,
                            role = player.optString("role").trim(),
                        )
                    )
                }
            }.distinctBy { it.externalId.ifBlank { it.handle.lowercase() } }

            ProviderRead.Success(
                ProviderRosterPoolSnapshot(
                    players = normalized,
                    observedAtEpochMillis = context.nowEpochMillis,
                    sourceUri = "$PERSISTED_BASE/getTeams",
                )
            )
        } catch (error: Throwable) {
            ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-PRE-007"),
                    message = "Riot team roster unavailable: ${safeMessage(error)}",
                    retryable = true,
                    context = mapOf("provider" to providerId, "team" to team.code.ifBlank { team.name }),
                )
            )
        }
    }

    private fun selectTeam(teams: JSONArray, target: TeamRef, lookup: String): JSONObject? {
        val targetAliases = setOf(
            token(lookup),
            token(target.id.value.substringAfterLast(':')),
            token(target.code),
            token(target.name),
        ).filter { it.isNotBlank() }.toSet()

        var first: JSONObject? = null
        for (index in 0 until teams.length()) {
            val row = teams.optJSONObject(index) ?: continue
            if (first == null) first = row
            val aliases = setOf(
                token(row.optString("id")),
                token(row.optString("slug")),
                token(row.optString("code")),
                token(row.optString("name")),
            ).filter { it.isNotBlank() }.toSet()
            if (targetAliases.intersect(aliases).isNotEmpty()) return row
        }
        return if (teams.length() == 1) first else null
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

    private fun slugify(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun token(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9\\p{L}\\p{N}]+"), "")

    private fun safeMessage(error: Throwable): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private companion object {
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
    }
}
