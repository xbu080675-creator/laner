package com.laner.app.data.live

import com.laner.core.application.LiveMatchStateRepository
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Device-local last-known authoritative LIVE state.
 *
 * One file per MatchId avoids unrelated matches rewriting one shared document. The file name is a
 * SHA-256 of the canonical MatchId so provider/user text never becomes a filesystem path.
 * Corrupt/unsupported files fail loudly; callers decide whether that failure is degradable.
 */
class JsonLiveMatchStateRepository(
    private val directory: File,
) : LiveMatchStateRepository {
    override suspend fun read(matchId: MatchId): LiveMatchState? = withContext(Dispatchers.IO) {
        val file = fileFor(matchId)
        if (!file.exists()) return@withContext null
        decode(JSONObject(file.readText(StandardCharsets.UTF_8))).also { decoded ->
            require(decoded.matchId == matchId) {
                "Live state identity mismatch: requested=${matchId.value}, stored=${decoded.matchId.value}"
            }
        }
    }

    override suspend fun write(state: LiveMatchState): Unit = withContext(Dispatchers.IO) {
        check(directory.exists() || directory.mkdirs()) {
            "Could not create LIVE state directory: ${directory.absolutePath}"
        }
        atomicWrite(fileFor(state.matchId), encode(state).toString())
    }

    private fun fileFor(matchId: MatchId): File =
        File(directory, "${sha256(matchId.value)}.json")

    private fun encode(state: LiveMatchState): JSONObject = JSONObject()
        .put("schema_version", SCHEMA_VERSION)
        .put("match_id", state.matchId.value)
        .put("lifecycle", state.lifecycle.name)
        .put("current_game_id", state.currentGameId?.value)
        .put("current_game_number", state.currentGameNumber)
        .put("last_observed_at_ms", state.lastObservedAtEpochMillis)
        .put("provenance", state.provenance?.let(::encodeProvenance))

    private fun decode(root: JSONObject): LiveMatchState {
        val version = root.optInt("schema_version", -1)
        require(version == SCHEMA_VERSION) { "Unsupported LIVE state schema: $version" }
        val matchId = root.getString("match_id").takeIf { it.isNotBlank() }
            ?: error("LIVE state match_id is blank")
        val lifecycle = runCatching {
            MatchLifecycleState.valueOf(root.getString("lifecycle"))
        }.getOrElse { throw IllegalArgumentException("Invalid LIVE lifecycle", it) }
        val gameId = root.optString("current_game_id").takeIf { it.isNotBlank() }
        val gameNumber = if (root.isNull("current_game_number")) null else root.getInt("current_game_number")
        val provenance = root.optJSONObject("provenance")?.let(::decodeProvenance)
        return LiveMatchState(
            matchId = MatchId(matchId),
            lifecycle = lifecycle,
            currentGameId = gameId?.let(::GameId),
            currentGameNumber = gameNumber,
            lastObservedAtEpochMillis = root.getLong("last_observed_at_ms"),
            provenance = provenance,
        )
    }

    private fun encodeProvenance(value: SourceProvenance): JSONObject = JSONObject()
        .put("provider_id", value.providerId)
        .put("source_class", value.sourceClass.name)
        .put("authority", value.authority.name)
        .put("freshness", value.freshnessClass.name)
        .put("observed_ms", value.observedAtEpochMillis)
        .put("source_timestamp_ms", value.sourceTimestampEpochMillis)
        .put("revision", value.revision)
        .put("source_uri", value.sourceUri)

    private fun decodeProvenance(node: JSONObject): SourceProvenance = SourceProvenance(
        providerId = node.getString("provider_id"),
        sourceClass = SourceClass.valueOf(node.getString("source_class")),
        authority = DataAuthority.valueOf(node.getString("authority")),
        freshnessClass = FreshnessClass.valueOf(node.getString("freshness")),
        observedAtEpochMillis = node.getLong("observed_ms"),
        sourceTimestampEpochMillis = if (node.isNull("source_timestamp_ms")) null else node.getLong("source_timestamp_ms"),
        revision = node.optLong("revision", 0L),
        sourceUri = node.optString("source_uri").takeIf { it.isNotBlank() },
    )

    private fun atomicWrite(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content, StandardCharsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}
