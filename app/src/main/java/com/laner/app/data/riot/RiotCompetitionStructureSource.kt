package com.laner.app.data.riot

import com.laner.core.application.ProviderEditionEntry
import com.laner.core.application.ProviderEditionSnapshot
import com.laner.core.application.ProviderRead
import com.laner.core.application.ProviderStandingMetric
import com.laner.core.application.ProviderStandingRow
import com.laner.core.application.ProviderStandingsSection
import com.laner.core.application.ProviderStandingsSnapshot
import com.laner.core.application.ProviderTeamIdentity
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.TournamentEditionSourcePort
import com.laner.core.application.TournamentStandingsSourcePort
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.StandingMetricKind
import com.laner.core.domain.TournamentEdition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class RiotCompetitionStructureSource(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient(),
) : TournamentEditionSourcePort, TournamentStandingsSourcePort {
    override val providerId: String = "riot-lolesports-structure"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    private data class RiotLeague(
        val id: String,
        val slug: String,
        val name: String,
        val regionCode: String?,
        val regionName: String?,
    )

    private data class RiotTournament(
        val id: String,
        val slug: String,
        val startDate: String,
        val endDate: String,
        val league: RiotLeague,
    )

    private val tournamentIdByEditionId = ConcurrentHashMap<String, String>()

    override suspend fun readEditions(context: SourceRequestContext): ProviderRead<ProviderEditionSnapshot> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext credentialFailure()
            try {
                val leagues = fetchLeagues()
                val semaphore = Semaphore(4)
                val tournaments = coroutineScope {
                    leagues.map { league ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                runCatching { fetchTournamentsForLeague(league) }.getOrDefault(emptyList())
                            }
                        }
                    }.awaitAll().flatten()
                }.distinctBy { it.id }

                val editions = tournaments.mapNotNull { tournament ->
                    toEditionEntry(tournament)?.also { row ->
                        tournamentIdByEditionId[editionIdentity(row)] = tournament.id
                    }
                }
                if (editions.isEmpty()) {
                    return@withContext ProviderRead.Failure(
                        DiagnosticFailure(
                            code = ErrorCode("LNR-SRC-PRE-011"),
                            message = "Riot tournament catalogue returned no usable Tournament Editions",
                            retryable = true,
                            context = mapOf("provider" to providerId),
                        )
                    )
                }
                ProviderRead.Success(
                    ProviderEditionSnapshot(
                        editions = editions,
                        observedAtEpochMillis = context.nowEpochMillis,
                        sourceUri = PERSISTED_BASE,
                    )
                )
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-PRE-011"),
                        message = "Riot Tournament Edition catalogue unavailable: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }

    override suspend fun readStandings(
        edition: TournamentEdition,
        context: SourceRequestContext,
    ): ProviderRead<ProviderStandingsSnapshot> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext credentialFailure()
        try {
            val tournamentId = tournamentIdByEditionId[edition.id.value]
                ?: resolveTournamentId(edition)
                ?: return@withContext ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-PRE-012"),
                        message = "Riot tournament id could not be resolved for ${edition.displayName}",
                        retryable = true,
                        context = mapOf("provider" to providerId, "edition" to edition.id.value),
                    )
                )
            tournamentIdByEditionId[edition.id.value] = tournamentId
            val root = getJson(
                "$PERSISTED_BASE/getStandings?hl=en-US&tournamentId=${enc(tournamentId)}"
            )
            val sections = parseStandings(root)
            ProviderRead.Success(
                ProviderStandingsSnapshot(
                    sections = sections,
                    observedAtEpochMillis = context.nowEpochMillis,
                    sourceUri = "$PERSISTED_BASE/getStandings",
                )
            )
        } catch (error: Throwable) {
            ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-PRE-012"),
                    message = "Riot standings unavailable: ${safeMessage(error)}",
                    retryable = true,
                    context = mapOf("provider" to providerId, "edition" to edition.id.value),
                )
            )
        }
    }

    private fun credentialFailure(): ProviderRead.Failure = ProviderRead.Failure(
        DiagnosticFailure(
            code = ErrorCode("LNR-SRC-PRE-010"),
            message = "LoL Esports API credential is not configured for Tournament/Standings",
            retryable = false,
            context = mapOf("provider" to providerId),
        )
    )

    private fun fetchLeagues(): List<RiotLeague> {
        val root = getJson("$PERSISTED_BASE/getLeagues?hl=en-US")
        val array = root.optJSONObject("data")?.optJSONArray("leagues") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val node = array.optJSONObject(index) ?: continue
                val id = node.optString("id")
                val slug = node.optString("slug")
                val name = node.optString("name")
                if (id.isBlank() || (slug.isBlank() && name.isBlank())) continue
                if (isExcludedProduct(slug, name)) continue
                val regionValue = node.opt("region")
                val regionObject = regionValue as? JSONObject
                add(
                    RiotLeague(
                        id = id,
                        slug = slug,
                        name = name.ifBlank { slug.uppercase() },
                        regionCode = regionObject?.optString("code")?.takeIf { it.isNotBlank() },
                        regionName = when (regionValue) {
                            is String -> regionValue.takeIf { it.isNotBlank() }
                            is JSONObject -> regionValue.optString("name").takeIf { it.isNotBlank() }
                            else -> null
                        },
                    )
                )
            }
        }
    }

    private fun fetchTournamentsForLeague(league: RiotLeague): List<RiotTournament> {
        val root = getJson(
            "$PERSISTED_BASE/getTournamentsForLeague?hl=en-US&leagueId=${enc(league.id)}"
        )
        val leagues = root.optJSONObject("data")?.optJSONArray("leagues") ?: JSONArray()
        return buildList {
            for (leagueIndex in 0 until leagues.length()) {
                val leagueNode = leagues.optJSONObject(leagueIndex) ?: continue
                val tournaments = leagueNode.optJSONArray("tournaments") ?: JSONArray()
                for (index in 0 until tournaments.length()) {
                    val node = tournaments.optJSONObject(index) ?: continue
                    val id = node.optString("id")
                    val slug = node.optString("slug")
                    val startDate = node.optString("startDate")
                    val endDate = node.optString("endDate")
                    if (id.isBlank() || slug.isBlank() || startDate.isBlank() || endDate.isBlank()) continue
                    add(RiotTournament(id, slug, startDate, endDate, league))
                }
            }
        }
    }

    private fun toEditionEntry(tournament: RiotTournament): ProviderEditionEntry? {
        val start = parseDate(tournament.startDate) ?: return null
        val end = parseDate(tournament.endDate) ?: return null
        val year = LocalDate.ofInstant(start, ZoneOffset.UTC).year
        val stage = stageLabel(tournament.slug)
        return ProviderEditionEntry(
            competitionSlug = tournament.league.slug,
            competitionName = tournament.league.name,
            regionCode = tournament.league.regionCode,
            regionName = tournament.league.regionName,
            tournamentSlug = tournament.slug,
            family = competitionFamily(tournament.league.slug, tournament.league.name),
            seasonYear = year,
            displayName = editionDisplayName(tournament.league.name, tournament.slug, year),
            stageName = stage,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
        )
    }

    private fun parseStandings(root: JSONObject): List<ProviderStandingsSection> {
        val standings = root.optJSONObject("data")?.optJSONArray("standings") ?: JSONArray()
        return buildList {
            for (blockIndex in 0 until standings.length()) {
                val block = standings.optJSONObject(blockIndex) ?: continue
                val stages = block.optJSONArray("stages") ?: JSONArray()
                for (stageIndex in 0 until stages.length()) {
                    val stage = stages.optJSONObject(stageIndex) ?: continue
                    val stageKey = stage.optString("id").ifBlank { stage.optString("slug") }
                    val stageName = stage.optString("name")
                        .ifBlank { stage.optString("slug") }
                        .ifBlank { "Stage ${stageIndex + 1}" }
                    val sections = stage.optJSONArray("sections") ?: JSONArray()
                    for (sectionIndex in 0 until sections.length()) {
                        val section = sections.optJSONObject(sectionIndex) ?: continue
                        val rows = parseRankingRows(section.optJSONArray("rankings"))
                        if (rows.isEmpty()) continue
                        add(
                            ProviderStandingsSection(
                                stageKey = stageKey,
                                stageName = stageName,
                                sectionName = section.optString("name").ifBlank { "Section ${sectionIndex + 1}" },
                                rows = rows,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun parseRankingRows(rankings: JSONArray?): List<ProviderStandingRow> {
        if (rankings == null) return emptyList()
        return buildList {
            for (rankingIndex in 0 until rankings.length()) {
                val ranking = rankings.optJSONObject(rankingIndex) ?: continue
                val ordinal = ranking.optInt("ordinal", rankingIndex + 1).coerceAtLeast(1)
                val teams = ranking.optJSONArray("teams") ?: JSONArray()
                for (teamIndex in 0 until teams.length()) {
                    val node = teams.optJSONObject(teamIndex) ?: continue
                    val identity = ProviderTeamIdentity(
                        slug = node.optString("slug"),
                        code = node.optString("code"),
                        name = node.optString("name"),
                    )
                    val record = node.optJSONObject("record") ?: JSONObject()
                    val metrics = buildList {
                        val explicitPoints = when {
                            node.has("points") && !node.isNull("points") -> node.optInt("points", -1)
                            record.has("points") && !record.isNull("points") -> record.optInt("points", -1)
                            else -> -1
                        }
                        if (explicitPoints >= 0) {
                            add(
                                ProviderStandingMetric(
                                    kind = StandingMetricKind.STAGE_POINTS,
                                    value = explicitPoints,
                                    label = "Riot standings points",
                                )
                            )
                        }
                    }
                    add(
                        ProviderStandingRow(
                            ordinal = ordinal,
                            team = identity,
                            seriesWins = record.optInt("wins", 0).coerceAtLeast(0),
                            seriesLosses = record.optInt("losses", 0).coerceAtLeast(0),
                            metrics = metrics,
                        )
                    )
                }
            }
        }
    }

    private fun resolveTournamentId(edition: TournamentEdition): String? {
        val league = fetchLeagues().firstOrNull { league ->
            token(league.slug) == token(edition.competition.id.value.substringAfterLast(':')) ||
                token(league.name) == token(edition.competition.name)
        } ?: return null
        val candidates = fetchTournamentsForLeague(league)
        return candidates.firstOrNull { tournament ->
            val row = toEditionEntry(tournament) ?: return@firstOrNull false
            editionIdentity(row) == edition.id.value
        }?.id
    }

    private fun editionIdentity(row: ProviderEditionEntry): String {
        val competition = token(row.competitionSlug.ifBlank { row.competitionName })
        val edition = token(row.tournamentSlug.ifBlank { row.displayName })
        val year = row.seasonYear?.toString() ?: "unknown"
        return "lol:edition:$competition:$year:$edition"
    }

    private fun competitionFamily(slug: String, name: String): String {
        val value = "${slug.lowercase()} ${name.lowercase()}"
        return when {
            value.contains("world") -> "WORLDS"
            value.contains("msi") || value.contains("mid-season") -> "MSI"
            value.contains("first stand") || value.contains("first-stand") -> "FIRST_STAND"
            else -> slug.ifBlank { name }.uppercase()
        }
    }

    private fun editionDisplayName(leagueName: String, slug: String, year: Int): String {
        val stage = stageLabel(slug)
        return listOf(year.toString(), leagueName, stage)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
    }

    private fun stageLabel(slug: String): String {
        val value = slug.lowercase()
        return when {
            value.contains("split-1") || value.contains("split1") -> "Split 1"
            value.contains("split-2") || value.contains("split2") -> "Split 2"
            value.contains("split-3") || value.contains("split3") -> "Split 3"
            value.contains("spring") -> "Spring"
            value.contains("summer") -> "Summer"
            value.contains("playoff") -> "Playoffs"
            value.contains("world") -> "World Championship"
            value.contains("msi") -> "MSI"
            value.contains("first-stand") || value.contains("firststand") -> "First Stand"
            else -> slug.replace('-', ' ').replace('_', ' ').trim()
        }
    }

    private fun parseDate(value: String): Instant? = runCatching {
        LocalDate.parse(value.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC)
    }.getOrNull()

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

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun token(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun isExcludedProduct(slug: String, name: String): Boolean {
        val normalizedSlug = slug.trim().lowercase().replace('_', '-')
        val normalizedName = name.trim().lowercase()
        return normalizedSlug == "tft-esports" || normalizedName == "tft esports"
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private companion object {
        const val PERSISTED_BASE = "https://esports-api.lolesports.com/persisted/gw"
    }
}
