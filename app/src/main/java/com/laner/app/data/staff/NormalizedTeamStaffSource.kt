package com.laner.app.data.staff

import com.laner.core.application.ProviderRead
import com.laner.core.application.ProviderStaffEntry
import com.laner.core.application.ProviderStaffSnapshot
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.TeamStaffSourcePort
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
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Transitional adapter for the normalized global team staff mirror.
 * The current mirror is derived from Riot's Global Contract Database; the mirror itself is therefore
 * VERIFIED_PROVIDER rather than being misrepresented as a direct official API response.
 */
class NormalizedTeamStaffSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) : TeamStaffSourcePort {
    override val providerId: String = "global-team-staff-mirror"
    override val authority: DataAuthority = DataAuthority.VERIFIED_PROVIDER

    @Volatile
    private var cache: CachedDirectory? = null

    override suspend fun readTeam(
        team: TeamRef,
        context: SourceRequestContext,
    ): ProviderRead<ProviderStaffSnapshot> = withContext(Dispatchers.IO) {
        try {
            val directory = loadDirectory(context.nowEpochMillis)
            val node = selectTeam(directory.root, team)
                ?: return@withContext ProviderRead.Success(
                    ProviderStaffSnapshot(
                        management = emptyList(),
                        coachingStaff = emptyList(),
                        observedAtEpochMillis = context.nowEpochMillis,
                        sourceTimestampEpochMillis = directory.updatedAtEpochMillis,
                        sourceUri = directory.sourceUri,
                    )
                )
            val fallbackSource = node.optString("source")
                .ifBlank { directory.root.optString("source") }
                .ifBlank { "Global staff mirror" }

            ProviderRead.Success(
                ProviderStaffSnapshot(
                    management = parseRows(node.optJSONArray("management"), fallbackSource),
                    coachingStaff = parseRows(node.optJSONArray("staff"), fallbackSource),
                    observedAtEpochMillis = context.nowEpochMillis,
                    sourceTimestampEpochMillis = directory.updatedAtEpochMillis,
                    sourceUri = directory.sourceUri,
                )
            )
        } catch (error: Throwable) {
            ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-PRE-009"),
                    message = "Global team staff mirror unavailable: ${safeMessage(error)}",
                    retryable = true,
                    context = mapOf("provider" to providerId, "team" to team.code.ifBlank { team.name }),
                )
            )
        }
    }

    private fun loadDirectory(nowEpochMillis: Long): CachedDirectory {
        cache?.takeIf { nowEpochMillis - it.loadedAtEpochMillis <= CACHE_TTL_MILLIS }?.let { return it }
        var lastError: Throwable? = null
        endpoints.forEach { endpoint ->
            try {
                val root = getJson(endpoint)
                if (root.optInt("schemaVersion", 0) <= 0 || root.optJSONObject("teams") == null) {
                    throw IOException("invalid team staff schema")
                }
                val loaded = CachedDirectory(
                    root = root,
                    sourceUri = endpoint,
                    loadedAtEpochMillis = nowEpochMillis,
                    updatedAtEpochMillis = parseEpoch(root.optString("updatedAt")),
                )
                cache = loaded
                return loaded
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IOException("no staff mirror endpoint configured")
    }

    private fun selectTeam(root: JSONObject, team: TeamRef): JSONObject? {
        val teams = root.optJSONObject("teams") ?: return null
        val targetAliases = setOf(
            token(team.id.value.substringAfterLast(':')),
            token(team.code),
            token(team.name),
        ).filter { it.isNotBlank() }.toSet()

        val keys = teams.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val node = teams.optJSONObject(key) ?: continue
            val aliases = buildSet {
                add(token(key))
                add(token(node.optString("name")))
                add(token(node.optString("short")))
                add(token(node.optString("page")))
                val rawAliases = node.optJSONArray("aliases") ?: JSONArray()
                for (index in 0 until rawAliases.length()) add(token(rawAliases.optString(index)))
            }.filter { it.isNotBlank() }.toSet()
            if (targetAliases.intersect(aliases).isNotEmpty()) return node
        }
        return null
    }

    private fun parseRows(rows: JSONArray?, fallbackSource: String): List<ProviderStaffEntry> {
        if (rows == null) return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val name = row.optString("name").trim()
                val role = row.optString("role").trim()
                if (name.isBlank() || role.isBlank()) continue
                add(
                    ProviderStaffEntry(
                        displayName = name,
                        role = role,
                        realName = row.optString("realName").trim().takeIf { it.isNotBlank() },
                        sourceLabel = row.optString("source").ifBlank { fallbackSource },
                    )
                )
            }
        }.distinctBy { "${token(it.displayName)}|${token(it.role)}" }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json,text/plain;q=0.9,*/*;q=0.1")
            .header("User-Agent", "Laner-Staff/2")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("empty response body")
            return JSONObject(body)
        }
    }

    private fun parseEpoch(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        }
    }

    private fun token(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() }

    private fun safeMessage(error: Throwable): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private data class CachedDirectory(
        val root: JSONObject,
        val sourceUri: String,
        val loadedAtEpochMillis: Long,
        val updatedAtEpochMillis: Long?,
    )

    private companion object {
        const val CACHE_TTL_MILLIS = 15L * 60L * 1000L
        val DEFAULT_ENDPOINTS = listOf(
            "https://cdn.jsdelivr.net/gh/xbu080675-creator/Rlftlab@main/data/global/team_staff.json",
            "https://raw.githubusercontent.com/xbu080675-creator/Rlftlab/main/data/global/team_staff.json",
        )
    }
}
