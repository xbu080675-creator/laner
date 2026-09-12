package com.laner.app.data.archive

import com.laner.core.application.ProviderMatchIdentity
import com.laner.core.application.ProviderMatchIdentityRepository
import com.laner.core.domain.MatchId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Device-local provider identity map. Provider IDs stay outside Domain identity. */
class JsonProviderMatchIdentityRepository(
    private val file: File,
) : ProviderMatchIdentityRepository {
    override suspend fun upsert(identity: ProviderMatchIdentity): Unit = withContext(Dispatchers.IO) {
        val current = readAll().associateBy { key(it.providerId, it.matchId) }.toMutableMap()
        val mapKey = key(identity.providerId, identity.matchId)
        val previous = current[mapKey]
        current[mapKey] = if (previous == null || identity.observedAtEpochMillis >= previous.observedAtEpochMillis) {
            identity
        } else {
            previous
        }
        writeAll(current.values.sortedWith(compareBy({ it.providerId }, { it.matchId.value })))
    }

    override suspend fun find(providerId: String, matchId: MatchId): ProviderMatchIdentity? =
        withContext(Dispatchers.IO) {
            readAll().firstOrNull { it.providerId == providerId && it.matchId == matchId }
        }

    private fun readAll(): List<ProviderMatchIdentity> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText(StandardCharsets.UTF_8))
        val version = root.optInt("schema_version", -1)
        require(version == SCHEMA_VERSION) { "Unsupported provider identity schema: $version" }
        val rows = root.optJSONArray("mappings") ?: JSONArray()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(
                    ProviderMatchIdentity(
                        providerId = row.getString("provider_id"),
                        matchId = MatchId(row.getString("match_id")),
                        externalEventId = row.optString("external_event_id").takeIf { it.isNotBlank() },
                        externalMatchId = row.optString("external_match_id").takeIf { it.isNotBlank() },
                        observedAtEpochMillis = row.getLong("observed_at_ms"),
                    )
                )
            }
        }
    }

    private fun writeAll(values: List<ProviderMatchIdentity>) {
        val parent = requireNotNull(file.parentFile) {
            "Provider identity file must have a parent directory: ${file.path}"
        }
        check(parent.exists() || parent.mkdirs()) { "Could not create identity directory: ${parent.absolutePath}" }
        val rows = JSONArray()
        values.forEach { value ->
            rows.put(
                JSONObject()
                    .put("provider_id", value.providerId)
                    .put("match_id", value.matchId.value)
                    .put("external_event_id", value.externalEventId)
                    .put("external_match_id", value.externalMatchId)
                    .put("observed_at_ms", value.observedAtEpochMillis)
            )
        }
        val root = JSONObject().put("schema_version", SCHEMA_VERSION).put("mappings", rows)
        val temp = File(parent, "${file.name}.tmp")
        temp.writeText(root.toString(), StandardCharsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun key(providerId: String, matchId: MatchId): String = "$providerId|${matchId.value}"

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
