package com.laner.app.data.archive

import com.laner.core.application.TournamentEditionArchiveRepository
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EditionId
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.RegionRef
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TournamentEdition
import com.laner.core.domain.TournamentEditionSlot
import com.laner.core.domain.TournamentEditionSlotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Device-local Tournament Edition archive.
 *
 * The file is a cache/archive implementation, never a domain identity source. Writes use a sibling
 * temporary file plus an atomic replace; if the filesystem cannot provide that guarantee we fail
 * the write instead of deleting the last known-good archive first.
 */
class JsonTournamentEditionArchiveRepository(
    private val directory: File,
) : TournamentEditionArchiveRepository {
    private val archiveFile = File(directory, FILE_NAME)

    override suspend fun load(): List<TournamentEdition> = withContext(Dispatchers.IO) {
        if (!archiveFile.exists()) return@withContext emptyList()
        val root = JSONObject(archiveFile.readText())
        val version = root.optInt("schema_version", -1)
        require(version == SCHEMA_VERSION) { "Unsupported Tournament Edition archive schema: $version" }
        val array = root.optJSONArray("editions") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                val node = array.optJSONObject(index) ?: continue
                decodeEdition(node)?.let(::add)
            }
        }
    }

    override suspend fun save(editions: List<TournamentEdition>) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val root = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("editions", JSONArray().apply { editions.forEach { put(encodeEdition(it)) } })
        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(root.toString())
        try {
            Files.move(
                temp.toPath(),
                archiveFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
        Unit
    }

    private fun encodeEdition(edition: TournamentEdition): JSONObject = JSONObject()
        .put("id", edition.id.value)
        .put("competition_id", edition.competition.id.value)
        .put("competition_name", edition.competition.name)
        .put("region_code", edition.competition.region?.code)
        .put("region_name", edition.competition.region?.displayName)
        .put("slug", edition.slug)
        .put("family", edition.family)
        .put("season_year", edition.seasonYear)
        .put("display_name", edition.displayName)
        .put("stage_name", edition.stageName)
        .put("start_ms", edition.startEpochMillis)
        .put("end_ms", edition.endEpochMillis)
        .put("participant_team_ids", JSONArray().apply { edition.participantTeamIds.forEach { put(it.value) } })
        .put("schedule_series_count", edition.scheduleSeriesCount)
        .put("slots", JSONArray().apply {
            edition.slots.forEach { slot ->
                put(
                    JSONObject()
                        .put("key", slot.key)
                        .put("label", slot.label)
                        .put("state", slot.state.name)
                        .put("detail", slot.detail)
                        .put("source_label", slot.sourceLabel)
                )
            }
        })
        .put("first_seen_ms", edition.firstSeenEpochMillis)
        .put("last_seen_ms", edition.lastSeenEpochMillis)
        .put("provenance", encodeProvenance(edition.provenance))

    private fun decodeEdition(node: JSONObject): TournamentEdition? = runCatching {
        val regionCode = node.optString("region_code").takeIf { it.isNotBlank() }
        val regionName = node.optString("region_name").takeIf { it.isNotBlank() }
        val region = if (regionCode != null && regionName != null) RegionRef(regionCode, regionName) else null
        val teams = buildSet {
            val array = node.optJSONArray("participant_team_ids") ?: JSONArray()
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let { add(TeamId(it)) }
            }
        }
        val slots = buildList {
            val array = node.optJSONArray("slots") ?: JSONArray()
            for (index in 0 until array.length()) {
                val slot = array.optJSONObject(index) ?: continue
                val sourceLabel = slot.optString("source_label").takeIf { it.isNotBlank() }
                add(
                    TournamentEditionSlot(
                        key = slot.getString("key"),
                        label = slot.getString("label"),
                        state = TournamentEditionSlotState.valueOf(slot.getString("state")),
                        detail = slot.getString("detail"),
                        sourceLabel = sourceLabel,
                    )
                )
            }
        }
        TournamentEdition(
            id = EditionId(node.getString("id")),
            competition = CompetitionRef(
                id = CompetitionId(node.getString("competition_id")),
                name = node.getString("competition_name"),
                region = region,
            ),
            slug = node.getString("slug"),
            family = node.getString("family"),
            seasonYear = if (node.isNull("season_year")) null else node.getInt("season_year"),
            displayName = node.getString("display_name"),
            stageName = node.optString("stage_name"),
            startEpochMillis = node.getLong("start_ms"),
            endEpochMillis = node.getLong("end_ms"),
            participantTeamIds = teams,
            scheduleSeriesCount = node.optInt("schedule_series_count", 0),
            slots = slots,
            firstSeenEpochMillis = node.getLong("first_seen_ms"),
            lastSeenEpochMillis = node.getLong("last_seen_ms"),
            provenance = decodeProvenance(node.getJSONObject("provenance")),
        )
    }.getOrNull()

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

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "tournament_editions_v1.json"
    }
}
