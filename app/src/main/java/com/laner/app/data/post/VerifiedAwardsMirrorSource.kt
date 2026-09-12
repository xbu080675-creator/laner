package com.laner.app.data.post

import com.laner.core.application.PostAwardSourcePort
import com.laner.core.application.PostMatchQuery
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.AwardKind
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerRef
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.VerifiedPostAward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Provenance-preserving awards mirror.
 *
 * MVP/POG is never inferred from stats. The mirror may cite official or clearly-labelled verified
 * secondary material; therefore the adapter authority is VERIFIED_PROVIDER rather than OFFICIAL.
 */
class VerifiedAwardsMirrorSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) : PostAwardSourcePort {
    override val providerId: String = "verified-awards-mirror"
    override val authority: DataAuthority = DataAuthority.VERIFIED_PROVIDER

    override suspend fun readAwards(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<VerifiedPostAward>> = withContext(Dispatchers.IO) {
        if (query.teams.size != 2 || query.scheduledStartEpochMillis == null) {
            return@withContext ProviderRead.Success(emptyList())
        }

        val root = endpoints.asSequence()
            .mapNotNull { endpoint -> runCatching { getJson(endpoint) }.getOrNull() }
            .firstOrNull()
            ?: return@withContext ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-POST-006"),
                    message = "Verified awards mirror is unavailable",
                    retryable = true,
                    context = mapOf("provider" to providerId),
                )
            )

        runCatching { parse(root, query, context.nowEpochMillis) }
            .fold(
                onSuccess = { ProviderRead.Success(it) },
                onFailure = { error ->
                    ProviderRead.Failure(
                        DiagnosticFailure(
                            code = ErrorCode("LNR-SRC-POST-007"),
                            message = "Verified awards mirror parse failed: ${error.message ?: error::class.java.simpleName}",
                            retryable = false,
                            context = mapOf("provider" to providerId),
                        )
                    )
                },
            )
    }

    internal fun parse(
        root: JSONObject,
        query: PostMatchQuery,
        observedAtEpochMillis: Long,
    ): List<VerifiedPostAward> {
        require(root.optInt("schemaVersion", 0) == 1) { "Unsupported awards mirror schema" }
        val scheduledDate = Instant.ofEpochMilli(query.scheduledStartEpochMillis!!)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val wantedTeams = query.teams.associateBy { canonicalToken(it.code.ifBlank { it.name }) }
        val rows = root.optJSONArray("awards") ?: JSONArray()
        val selected = (0 until rows.length())
            .mapNotNull(rows::optJSONObject)
            .firstOrNull { row -> matchesRow(row, wantedTeams.keys, scheduledDate) }
            ?: return emptyList()

        return buildList {
            selected.optJSONObject("seriesMvp")?.let { node ->
                parseAwardNode(
                    node = node,
                    kind = AwardKind.SERIES_MVP,
                    gameNumber = null,
                    query = query,
                    wantedTeams = wantedTeams,
                    observedAtEpochMillis = observedAtEpochMillis,
                )?.let(::add)
            }
            val games = selected.optJSONArray("gameMvps")
            if (games != null) {
                for (index in 0 until games.length()) {
                    val node = games.optJSONObject(index) ?: continue
                    val gameNumber = node.optInt("game", 0)
                    if (gameNumber <= 0) continue
                    parseAwardNode(
                        node = node,
                        kind = AwardKind.GAME_MVP,
                        gameNumber = gameNumber,
                        query = query,
                        wantedTeams = wantedTeams,
                        observedAtEpochMillis = observedAtEpochMillis,
                    )?.let(::add)
                }
            }
        }
    }

    private fun matchesRow(
        row: JSONObject,
        wantedTeams: Set<String>,
        scheduledDate: LocalDate,
    ): Boolean {
        val rowTeams = row.optJSONArray("teams") ?: return false
        val tokens = buildSet {
            for (index in 0 until rowTeams.length()) {
                canonicalToken(rowTeams.optString(index)).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        if (tokens.size != 2 || tokens != wantedTeams) return false

        val dateText = row.optString("date").trim()
        if (dateText.isBlank()) return true
        val rowDate = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return false
        return abs(rowDate.toEpochDay() - scheduledDate.toEpochDay()) <= 1L
    }

    private fun parseAwardNode(
        node: JSONObject,
        kind: AwardKind,
        gameNumber: Int?,
        query: PostMatchQuery,
        wantedTeams: Map<String, com.laner.core.domain.TeamRef>,
        observedAtEpochMillis: Long,
    ): VerifiedPostAward? {
        val playerName = node.optString("playerName").trim()
        val teamToken = canonicalToken(node.optString("team"))
        if (playerName.isBlank() || teamToken.isBlank()) return null
        val team = wantedTeams[teamToken] ?: return null
        val source = node.optString("source").trim().ifBlank { "Verified Awards Mirror" }
        val sourceUrl = node.optString("sourceUrl").trim().takeIf { it.isNotBlank() }
        val awardLabel = node.optString("award").trim().ifBlank {
            when (kind) {
                AwardKind.SERIES_MVP -> "Series MVP"
                AwardKind.GAME_MVP -> "Game MVP"
                AwardKind.POG -> "POG"
                AwardKind.OTHER -> "Award"
            }
        }
        return VerifiedPostAward(
            matchId = query.matchId,
            gameId = null,
            gameNumber = gameNumber,
            kind = kind,
            player = PlayerRef(
                id = PlayerId("lol:player:${canonicalToken(playerName)}"),
                handle = playerName,
                teamId = team.id,
                role = parseRole(node.optString("role")),
            ),
            label = awardLabel,
            provenance = SourceProvenance(
                providerId = providerId,
                sourceClass = SourceClass.POST_MATCH_SOURCE,
                authority = authority,
                freshnessClass = FreshnessClass.STATIC,
                observedAtEpochMillis = observedAtEpochMillis,
                sourceUri = sourceUrl ?: source,
            ),
        )
    }

    private fun parseRole(raw: String): PlayerRole = when (canonicalToken(raw)) {
        "top" -> PlayerRole.TOP
        "jungle", "jg" -> PlayerRole.JUNGLE
        "mid", "middle" -> PlayerRole.MID
        "bot", "adc" -> PlayerRole.BOT
        "support", "sup" -> PlayerRole.SUPPORT
        else -> PlayerRole.UNKNOWN
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "Laner-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Empty body")
            return JSONObject(body)
        }
    }

    private fun canonicalToken(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private companion object {
        val DEFAULT_ENDPOINTS = listOf(
            "https://raw.githubusercontent.com/xbu080675-creator/Rlftlab/main/data/global/match_awards.json",
            "https://cdn.jsdelivr.net/gh/xbu080675-creator/Rlftlab@main/data/global/match_awards.json",
        )
    }
}
