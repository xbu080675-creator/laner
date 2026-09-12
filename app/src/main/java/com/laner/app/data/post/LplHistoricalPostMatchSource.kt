package com.laner.app.data.post

import com.laner.core.application.CompletedGameSourcePort
import com.laner.core.application.PostMatchQuery
import com.laner.core.application.PostResultSourcePort
import com.laner.core.application.ProviderRead
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.CompletedGameRecord
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerRef
import com.laner.core.domain.PlayerRole
import com.laner.core.domain.PostPlayerStats
import com.laner.core.domain.PostTeamStats
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * LPL historical POST source.
 *
 * Resolution is deliberately independent from the LIVE plane:
 * LPL historical GameList -> BMatch list -> TJStats matchDetail.
 * Provider IDs remain adapter-local; canonical GameId is always produced by GameIdentity.
 *
 * A series result may be published from explicit series score/status. A CompletedGameRecord is only
 * published when the historical payload explicitly identifies that game's winner; gold/kills are
 * never used to guess a winner.
 */
class LplHistoricalPostMatchSource(
    private val tjstatsAuth: String,
    private val client: OkHttpClient = OkHttpClient(),
) : PostResultSourcePort, CompletedGameSourcePort {
    override val providerId: String = "lpl-tjstats-history"
    override val authority: DataAuthority = DataAuthority.VERIFIED_PROVIDER

    private val cache = ConcurrentHashMap<String, Cached>()

    override suspend fun readResult(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<SeriesResult?> = when (val resolved = resolve(query, context)) {
        is ProviderRead.Success -> ProviderRead.Success(resolved.value?.result)
        is ProviderRead.Failure -> resolved
    }

    override suspend fun readGames(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<CompletedGameRecord>> = when (val resolved = resolve(query, context)) {
        is ProviderRead.Success -> ProviderRead.Success(resolved.value?.games.orEmpty())
        is ProviderRead.Failure -> resolved
    }

    private suspend fun resolve(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<ResolvedPost?> {
        if (!isLpl(query.competitionSlug)) return ProviderRead.Success(null)
        if (query.teams.size != 2 || query.scheduledStartEpochMillis == null) {
            return ProviderRead.Success(null)
        }
        if (tjstatsAuth.isBlank()) {
            return ProviderRead.Failure(
                DiagnosticFailure(
                    code = ErrorCode("LNR-SRC-POST-010"),
                    message = "LPL TJStats credential is not configured",
                    retryable = false,
                    context = mapOf("provider" to providerId),
                )
            )
        }

        cache[query.matchId.value]?.takeIf {
            context.nowEpochMillis - it.loadedAtEpochMillis <= CACHE_TTL_MS
        }?.let { return ProviderRead.Success(it.value) }

        return withContext(Dispatchers.IO) {
            try {
                val ref = locateHistoricalMatch(query)
                    ?: return@withContext ProviderRead.Success(null)
                val root = getJson(
                    "$TJ_BASE/compound/matchDetail?matchId=${enc(ref.bmid)}",
                    auth = true,
                )
                if (root.has("success") && !root.optBoolean("success", false)) {
                    throw IOException("TJStats matchDetail success=false")
                }
                val data = root.optJSONObject("data")
                    ?: throw IOException("TJStats matchDetail missing data")
                val parsed = parseMatchDetail(
                    data = data,
                    query = query,
                    ref = ref,
                    observedAtEpochMillis = context.nowEpochMillis,
                )
                cache[query.matchId.value] = Cached(context.nowEpochMillis, parsed)
                ProviderRead.Success(parsed)
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-POST-011"),
                        message = "LPL historical POST source failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }
    }

    internal fun parseMatchDetail(
        data: JSONObject,
        query: PostMatchQuery,
        ref: HistoricalMatchRef,
        observedAtEpochMillis: Long,
    ): ResolvedPost {
        require(query.teams.size == 2)
        val left = query.teams[0]
        val right = query.teams[1]

        val scoreA = intOrNull(data, "teamAScore", "scoreA") ?: ref.scoreA
        val scoreB = intOrNull(data, "teamBScore", "scoreB") ?: ref.scoreB
        val status = intOrNull(data, "matchStatus", "status") ?: ref.matchStatus
        val leftScore = if (ref.leftIsTeamA) scoreA else scoreB
        val rightScore = if (ref.leftIsTeamA) scoreB else scoreA
        val finished = status == 3 || ref.matchStatus == 3 || isSeriesWon(query.bestOf, leftScore, rightScore)

        val provenance = provenance(
            observedAtEpochMillis = observedAtEpochMillis,
            sourceUri = "$TJ_BASE/compound/matchDetail?matchId=${ref.bmid}",
        )

        val result = when {
            finished && leftScore != rightScore -> SeriesResult(
                matchId = query.matchId,
                leftTeamId = left.id,
                rightTeamId = right.id,
                leftWins = leftScore,
                rightWins = rightScore,
                bestOf = query.bestOf,
                state = SeriesResultState.FINAL,
                winnerTeamId = if (leftScore > rightScore) left.id else right.id,
                provenance = provenance,
            )
            leftScore >= 0 && rightScore >= 0 -> SeriesResult(
                matchId = query.matchId,
                leftTeamId = left.id,
                rightTeamId = right.id,
                leftWins = leftScore,
                rightWins = rightScore,
                bestOf = query.bestOf,
                state = SeriesResultState.PARTIAL,
                winnerTeamId = null,
                provenance = provenance,
            )
            else -> null
        }

        val teamAId = intOrNull(data, "teamAId", "teamAID")
        val teamBId = intOrNull(data, "teamBId", "teamBID")
        val providerToCanonical = buildMap<Int, TeamRef> {
            if (teamAId != null && teamAId > 0) put(teamAId, if (ref.leftIsTeamA) left else right)
            if (teamBId != null && teamBId > 0) put(teamBId, if (ref.leftIsTeamA) right else left)
        }

        val gamesArray = firstArray(
            data,
            "matchInfos", "gameInfos", "games", "gameList", "singleGames", "gameInfoList",
        ) ?: JSONArray()
        val games = buildList {
            for (index in 0 until gamesArray.length()) {
                val node = gamesArray.optJSONObject(index) ?: continue
                parseCompletedGame(
                    node = node,
                    fallbackGameNumber = index + 1,
                    query = query,
                    providerToCanonical = providerToCanonical,
                    provenance = provenance,
                )?.let(::add)
            }
        }.distinctBy { it.gameNumber }.sortedBy { it.gameNumber }

        return ResolvedPost(result = result, games = games)
    }

    private fun parseCompletedGame(
        node: JSONObject,
        fallbackGameNumber: Int,
        query: PostMatchQuery,
        providerToCanonical: Map<Int, TeamRef>,
        provenance: SourceProvenance,
    ): CompletedGameRecord? {
        if (providerToCanonical.size < 2) return null
        val gameNumber = positiveInt(node, "bo", "gameNo", "gameNum", "gameNumber", "gameIndex", "round")
            ?: fallbackGameNumber
        if (gameNumber <= 0) return null

        val teamRows = firstArray(
            node,
            "teamInfos", "teams", "teamInfoList", "gameTeamInfos", "teamList", "teamData",
            "battleTeams", "teamStats", "teamDetails",
        ) ?: return null
        val parsedTeams = buildList {
            for (index in 0 until teamRows.length()) {
                val raw = teamRows.optJSONObject(index) ?: continue
                parseTeamState(raw, providerToCanonical)?.let(::add)
            }
        }.distinctBy { it.team.id }
        if (parsedTeams.size < 2) return null

        val blueProviderId = positiveInt(node, "blueTeam", "blueTeamId", "blueId", "blueTeamID", "blueIdNum")
        val blueTeam = blueProviderId?.let(providerToCanonical::get) ?: parsedTeams.first().team
        val redTeam = parsedTeams.firstOrNull { it.team.id != blueTeam.id }?.team ?: return null
        val byCanonical = parsedTeams.associateBy { it.team.id }
        val blueState = byCanonical[blueTeam.id] ?: return null
        val redState = byCanonical[redTeam.id] ?: return null

        val winner = explicitWinner(node, providerToCanonical, blueTeam, redTeam) ?: return null
        val duration = secondsOrNull(node, "gameTime", "time", "elapsedSeconds", "gameDuration", "duration")
        return CompletedGameRecord(
            matchId = query.matchId,
            gameId = GameIdentity.canonical(query.matchId, gameNumber),
            gameNumber = gameNumber,
            blueTeamId = blueTeam.id,
            redTeamId = redTeam.id,
            winnerTeamId = winner.id,
            durationSeconds = duration,
            blueStats = blueState.stats,
            redStats = redState.stats,
            players = blueState.players + redState.players,
            draftBlue = null,
            draftRed = null,
            provenance = provenance,
        )
    }

    private fun parseTeamState(
        raw: JSONObject,
        providerToCanonical: Map<Int, TeamRef>,
    ): ParsedTeam? {
        val providerTeamId = positiveInt(raw, "teamId", "teamID", "team_id", "id") ?: return null
        val team = providerToCanonical[providerTeamId] ?: return null
        val playersNode = firstArray(
            raw,
            "playerInfos", "players", "playerInfoList", "gamePlayerInfos", "playerList",
            "battlePlayers", "playerStats", "playerDetails",
        ) ?: JSONArray()
        val players = parsePlayers(playersNode, team)

        val explicitGold = nestedMetric(raw, "golds", "gold", "totalGold", "teamGold", "goldAmount", "total_gold")
        val explicitKills = nestedMetric(raw, "kills", "kill", "totalKills", "killAmount", "killCount")
        val safeGold = explicitGold ?: players.map { it.gold }.takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }
        val safeKills = explicitKills ?: players.map { it.kills }.takeIf { it.isNotEmpty() && it.all { value -> value != null } }?.sumOf { it!! }

        return ParsedTeam(
            team = team,
            stats = PostTeamStats(
                teamId = team.id,
                gold = safeGold,
                kills = safeKills,
                towers = nestedMetric(raw, "turretAmount", "towers", "tower", "towerAmount", "turretCount", "towerCount"),
                dragons = nestedMetric(raw, "dragonAmount", "dragons", "dragon", "dragonCount"),
                barons = nestedMetric(raw, "baronAmount", "barons", "baron", "baronCount", "nashorCount"),
                heralds = nestedMetric(raw, "heraldAmount", "heralds", "herald", "heraldCount"),
                atakhans = nestedMetric(raw, "atakhanAmount", "atakhans", "atakhan", "atakhanCount"),
            ),
            players = players,
        )
    }

    private fun parsePlayers(array: JSONArray, team: TeamRef): List<PostPlayerStats> = buildList {
        for (index in 0 until array.length()) {
            val node = array.optJSONObject(index) ?: continue
            val battle = node.optJSONObject("battleDetail") ?: node.optJSONObject("stats") ?: JSONObject()
            val other = node.optJSONObject("otherDetail") ?: node.optJSONObject("detail") ?: JSONObject()
            val name = stringAny(node, "playerName", "summonerName", "name").ifBlank { "P${index + 1}" }
            val champion = stringAny(node, "heroId", "championId", "championID")
            if (champion.isBlank()) continue
            add(
                PostPlayerStats(
                    player = PlayerRef(
                        id = PlayerId("lol:player:${canonicalToken(name)}"),
                        handle = name,
                        teamId = team.id,
                        role = parseRole(stringAny(node, "playerLocation", "position", "role")),
                    ),
                    teamId = team.id,
                    championId = champion,
                    kills = intOrNull(node, "kills", "kill", "killCount") ?: intOrNull(battle, "kills", "kill", "killCount"),
                    deaths = intOrNull(node, "deaths", "death", "deathCount") ?: intOrNull(battle, "deaths", "death", "deathCount"),
                    assists = intOrNull(node, "assists", "assist", "assistCount") ?: intOrNull(battle, "assists", "assist", "assistCount"),
                    cs = intOrNull(node, "minionKilled", "creepScore", "cs", "creepsKilled") ?: intOrNull(other, "minionKilled", "creepScore", "cs", "creepsKilled"),
                    gold = intOrNull(node, "golds", "gold", "totalGold") ?: intOrNull(other, "golds", "gold", "totalGold"),
                    damageToChampions = intOrNull(node, "damageToChampions", "heroDamage", "championDamage") ?: intOrNull(battle, "damageToChampions", "heroDamage", "championDamage"),
                    visionScore = intOrNull(node, "visionScore") ?: intOrNull(other, "visionScore"),
                )
            )
        }
    }

    private fun explicitWinner(
        node: JSONObject,
        providerToCanonical: Map<Int, TeamRef>,
        blue: TeamRef,
        red: TeamRef,
    ): TeamRef? {
        positiveInt(node, "winnerTeamId", "winningTeamId", "winTeamId", "winnerId")
            ?.let(providerToCanonical::get)
            ?.let { return it }
        val side = stringAny(node, "winnerSide", "winningSide", "winSide", "winner").lowercase()
        return when (side) {
            "blue", "b", "1" -> blue
            "red", "r", "2" -> red
            else -> null
        }
    }

    private fun locateHistoricalMatch(query: PostMatchQuery): HistoricalMatchRef? {
        val gameList = parseLooseJson(getText(GAME_LIST, auth = false))
            .optJSONObject("msg")
            ?.optJSONObject("sGameList") ?: return null
        val gameIds = buildList {
            val keys = gameList.keys()
            while (keys.hasNext()) {
                val array = gameList.optJSONArray(keys.next()) ?: continue
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.opt("GameId")?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.distinct().sortedByDescending { it.toLongOrNull() ?: Long.MIN_VALUE }

        val targetDate = localDate(query.scheduledStartEpochMillis!!)
        var best: Pair<HistoricalMatchRef, Int>? = null
        for (gameId in gameIds.take(MAX_GAME_LIST_PROBES)) {
            val root = runCatching { parseLooseJson(getText("$BMATCH_PREFIX${enc(gameId)}.js", auth = false)) }.getOrNull()
                ?: continue
            val rows = root.optJSONArray("msg") ?: continue
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val bmid = stringAny(row, "bMatchId", "bMatchID", "BMatchId")
                if (bmid.isBlank()) continue
                val candidate = HistoricalMatchRef(
                    bmid = bmid,
                    matchName = stringAny(row, "bMatchName", "BMatchName", "matchName"),
                    matchDate = stringAny(row, "MatchDate", "matchDate", "startTime"),
                    scoreA = intOrNull(row, "ScoreA", "scoreA") ?: 0,
                    scoreB = intOrNull(row, "ScoreB", "scoreB") ?: 0,
                    matchStatus = intOrNull(row, "MatchStatus", "matchStatus", "status") ?: 0,
                    leftIsTeamA = true,
                )
                val scored = scoreCandidate(candidate, query, targetDate) ?: continue
                if (scored.second > (best?.second ?: Int.MIN_VALUE)) best = scored
            }
            if ((best?.second ?: 0) >= STRONG_MATCH_SCORE) break
        }
        return best?.takeIf { it.second >= MIN_MATCH_SCORE }?.first
    }

    private fun scoreCandidate(
        candidate: HistoricalMatchRef,
        query: PostMatchQuery,
        targetDate: String,
    ): Pair<HistoricalMatchRef, Int>? {
        val parts = candidate.matchName.split(Regex("\\s+vs\\s+", RegexOption.IGNORE_CASE), limit = 2)
        val a = parts.getOrNull(0).orEmpty()
        val b = parts.getOrNull(1).orEmpty()
        val left = query.teams[0]
        val right = query.teams[1]
        val direct = teamMatches(a, left) && teamMatches(b, right)
        val swapped = teamMatches(a, right) && teamMatches(b, left)
        if (!direct && !swapped) return null
        var score = if (direct) 120 else 115
        if (targetDate.isNotBlank() && candidate.matchDate.startsWith(targetDate)) score += 35
        if (candidate.matchStatus == 3) score += 10
        return candidate.copy(leftIsTeamA = direct) to score
    }

    private fun provenance(observedAtEpochMillis: Long, sourceUri: String): SourceProvenance = SourceProvenance(
        providerId = providerId,
        sourceClass = SourceClass.POST_MATCH_SOURCE,
        authority = authority,
        freshnessClass = FreshnessClass.STATIC,
        observedAtEpochMillis = observedAtEpochMillis,
        sourceUri = sourceUri,
    )

    private fun isLpl(slug: String?): Boolean = canonicalToken(slug.orEmpty()).let { it == "lpl" || it.contains("lpl") }

    private fun isSeriesWon(bestOf: Int?, left: Int, right: Int): Boolean {
        val value = bestOf ?: return false
        val need = value / 2 + 1
        return left >= need || right >= need
    }

    private fun teamMatches(upstreamName: String, team: TeamRef): Boolean {
        val upstream = teamKey(upstreamName)
        if (upstream.isBlank()) return false
        val candidates = listOf(team.code, team.name).map(::teamKey).filter { it.isNotBlank() }
        return candidates.any { token ->
            upstream == token ||
                (token.length >= 3 && upstream.contains(token)) ||
                (upstream.length >= 3 && token.contains(upstream))
        }
    }

    private fun nestedMetric(root: JSONObject, vararg keys: String): Int? {
        intOrNull(root, *keys)?.let { return it }
        for (container in listOf("battleDetail", "stats", "teamStats", "detail", "otherDetail", "gameData", "teamData", "battleData")) {
            root.optJSONObject(container)?.let { intOrNull(it, *keys) }?.let { return it }
        }
        return null
    }

    private fun firstArray(root: JSONObject, vararg keys: String): JSONArray? {
        for (key in keys) {
            root.optJSONArray(key)?.takeIf { it.length() > 0 }?.let { return it }
        }
        return null
    }

    private fun positiveInt(root: JSONObject, vararg keys: String): Int? =
        intOrNull(root, *keys)?.takeIf { it > 0 }

    private fun intOrNull(root: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            if (!root.has(key) || root.isNull(key)) continue
            when (val raw = root.opt(key)) {
                is Number -> return raw.toInt()
                is String -> raw.toDoubleOrNull()?.toInt()?.let { return it }
            }
        }
        return null
    }

    private fun secondsOrNull(root: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            if (!root.has(key) || root.isNull(key)) continue
            when (val raw = root.opt(key)) {
                is Number -> return raw.toInt().takeIf { it >= 0 }
                is String -> {
                    raw.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }?.let { return it }
                    val parts = raw.trim().split(":")
                    if (parts.size == 2) {
                        val minutes = parts[0].toIntOrNull()
                        val seconds = parts[1].toIntOrNull()
                        if (minutes != null && seconds != null && minutes >= 0 && seconds in 0..59) {
                            return minutes * 60 + seconds
                        }
                    }
                }
            }
        }
        return null
    }

    private fun stringAny(root: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = root.opt(key)?.toString()?.trim().orEmpty()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun parseRole(raw: String): PlayerRole = when (canonicalToken(raw)) {
        "top", "1" -> PlayerRole.TOP
        "jungle", "jug", "jg", "2" -> PlayerRole.JUNGLE
        "mid", "middle", "3" -> PlayerRole.MID
        "bottom", "bot", "adc", "4" -> PlayerRole.BOT
        "support", "sup", "5" -> PlayerRole.SUPPORT
        else -> PlayerRole.UNKNOWN
    }

    private fun canonicalToken(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun teamKey(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")

    private fun localDate(epochMillis: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.of("Asia/Shanghai"))
        .format(Instant.ofEpochMilli(epochMillis))

    private fun parseLooseJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("invalid JSON/JS payload")
        return JSONObject(text.substring(start, end + 1))
    }

    private fun getJson(url: String, auth: Boolean): JSONObject = parseLooseJson(getText(url, auth))

    private fun getText(url: String, auth: Boolean): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", "Laner-Android")
            .header("Referer", "$LPL_BASE/")
            .header("Origin", LPL_BASE)
            .apply { if (auth) header("Authorization", tjstatsAuth) }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} ${response.request.url.encodedPath}: ${body.take(120)}")
            if (body.isBlank()) throw IOException("empty response ${response.request.url.encodedPath}")
            return body
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun safeMessage(error: Throwable): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    internal data class HistoricalMatchRef(
        val bmid: String,
        val matchName: String,
        val matchDate: String,
        val scoreA: Int,
        val scoreB: Int,
        val matchStatus: Int,
        val leftIsTeamA: Boolean,
    )

    internal data class ResolvedPost(
        val result: SeriesResult?,
        val games: List<CompletedGameRecord>,
    )

    private data class ParsedTeam(
        val team: TeamRef,
        val stats: PostTeamStats,
        val players: List<PostPlayerStats>,
    )

    private data class Cached(
        val loadedAtEpochMillis: Long,
        val value: ResolvedPost?,
    )

    private companion object {
        const val LPL_BASE = "https://lpl.qq.com"
        const val GAME_LIST = "$LPL_BASE/web201612/data/LOL_MATCH2_GAME_LIST_BRIEF.js"
        const val BMATCH_PREFIX = "$LPL_BASE/web201612/data/LOL_MATCH2_MATCH_HOMEPAGE_BMATCH_LIST_"
        const val TJ_BASE = "https://open.tjstats.com/match-auth-app/open/v1"
        const val MAX_GAME_LIST_PROBES = 18
        const val MIN_MATCH_SCORE = 90
        const val STRONG_MATCH_SCORE = 150
        const val CACHE_TTL_MS = 60_000L
    }
}
