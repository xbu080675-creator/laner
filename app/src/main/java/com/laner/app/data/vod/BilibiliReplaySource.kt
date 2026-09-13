package com.laner.app.data.vod

import com.laner.core.application.PostMatchQuery
import com.laner.core.application.ProviderRead
import com.laner.core.application.ReplaySourcePort
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ReplayProvider
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * POST_MATCH_SOURCE adapter for the official Bilibili League of Legends account.
 * Stores metadata only. Video bytes remain on Bilibili and are resolved on demand by the media adapter.
 */
class BilibiliReplaySource(
    private val client: OkHttpClient = OkHttpClient(),
) : ReplaySourcePort {
    override val providerId: String = "bilibili-lol-official"
    override val authority: DataAuthority = DataAuthority.OFFICIAL

    override fun supports(query: PostMatchQuery): Boolean =
        query.teams.size == 2 && query.scheduledStartEpochMillis != null

    override suspend fun readReplays(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<ReplayAsset>> {
        if (!supports(query)) return ProviderRead.Success(emptyList())
        return withContext(Dispatchers.IO) {
            try {
                ProviderRead.Success(resolve(query, context.nowEpochMillis))
            } catch (error: Throwable) {
                ProviderRead.Failure(
                    DiagnosticFailure(
                        code = ErrorCode("LNR-SRC-POST-030"),
                        message = "Bilibili official VOD lookup failed: ${safeMessage(error)}",
                        retryable = true,
                        context = mapOf("provider" to providerId),
                    )
                )
            }
        }
    }

    internal suspend fun resolve(query: PostMatchQuery, observedAtEpochMillis: Long): List<ReplayAsset> {
        val left = query.teams.getOrNull(0)?.let { it.code.ifBlank { it.name } }.orEmpty()
        val right = query.teams.getOrNull(1)?.let { it.code.ifBlank { it.name } }.orEmpty()
        if (left.isBlank() || right.isBlank()) return emptyList()

        val date = query.scheduledStartEpochMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val year = date?.year ?: 0
        val monthDay = date?.let { "${it.monthValue}月${it.dayOfMonth}日" }.orEmpty()
        val leagueToken = competitionToken(query.competitionSlug.orEmpty())
        val orderedPairs = listOf("$left vs $right", "$right vs $left")
        val queries = buildList {
            if (year > 0 && monthDay.isNotBlank()) {
                orderedPairs.forEach { pair -> add("$year$leagueToken $monthDay $pair") }
            }
            if (monthDay.isNotBlank()) orderedPairs.forEach { pair -> add("$monthDay $pair") }
            orderedPairs.forEach(::add)
            add("$left $right")
        }.map(String::trim).filter(String::isNotBlank).distinct()

        var best: SearchCandidate? = null
        for (search in queries) {
            searchVideos(search).forEach { candidate ->
                val score = candidateScore(candidate, query, left, right, monthDay, year)
                if (score > (best?.score ?: Int.MIN_VALUE)) best = candidate.copy(score = score)
            }
            if ((best?.score ?: 0) >= 230) break
            delay(REQUEST_GAP_MS)
        }
        val winner = best?.takeIf { it.score >= 150 } ?: return emptyList()
        delay(REQUEST_GAP_MS)
        return parseVideoDetails(winner, query, observedAtEpochMillis)
    }

    internal fun parseVideoDetails(
        candidate: SearchCandidate,
        query: PostMatchQuery,
        observedAtEpochMillis: Long,
    ): List<ReplayAsset> {
        val root = getJson("$API_BASE/x/web-interface/view?bvid=${enc(candidate.bvid)}")
        val data = root.optJSONObject("data") ?: return emptyList()
        val owner = data.optJSONObject("owner") ?: JSONObject()
        if (!isOfficialOwner(owner.optString("name"))) return emptyList()
        val pages = parsePages(data.optJSONArray("pages") ?: JSONArray())
        val normalized = normalizeGamePages(pages, query.bestOf)
        val provenance = SourceProvenance(
            providerId = providerId,
            sourceClass = SourceClass.POST_MATCH_SOURCE,
            authority = authority,
            freshnessClass = FreshnessClass.STATIC,
            observedAtEpochMillis = observedAtEpochMillis,
            sourceUri = "$API_BASE/x/web-interface/view?bvid=${candidate.bvid}",
        )
        return normalized.mapNotNull { page ->
            if (page.cid <= 0L || page.game <= 0) return@mapNotNull null
            val chapters = runCatching { fetchChapters(candidate.bvid, page.cid) }.getOrDefault(emptyList())
            val startOffset = chapters.firstOrNull { isGameStartChapter(it.title) }?.fromSeconds ?: 0
            ReplayAsset(
                matchId = query.matchId,
                gameId = GameIdentity.canonical(query.matchId, page.game),
                gameNumber = page.game,
                provider = ReplayProvider.BILIBILI,
                locale = "zh-CN",
                sourceUrl = "https://www.bilibili.com/video/${candidate.bvid}?p=${page.page}",
                externalMediaId = candidate.bvid,
                externalPartId = page.cid.toString(),
                offsetSeconds = startOffset,
                title = page.title.ifBlank { "G${page.game} · Bilibili 官方录像" },
                provenance = provenance,
            )
        }.distinctBy { it.gameNumber }
    }

    internal fun searchVideos(query: String): List<SearchCandidate> {
        val root = getJson(
            "$API_BASE/x/web-interface/search/type?search_type=video&order=pubdate&page=1&keyword=${enc(query)}"
        )
        val code = root.optInt("code", -1)
        if (code != 0) throw IOException("Bilibili search code=$code · ${root.optString("message")}")
        val result = root.optJSONObject("data")?.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val bvid = item.optString("bvid").trim()
                if (bvid.isBlank()) continue
                add(
                    SearchCandidate(
                        bvid = bvid,
                        title = stripHtml(item.optString("title")),
                        author = stripHtml(item.optString("author")),
                        pubdate = item.optLong("pubdate", 0L),
                    )
                )
            }
        }
    }

    internal fun candidateScore(
        candidate: SearchCandidate,
        query: PostMatchQuery,
        left: String,
        right: String,
        monthDay: String,
        year: Int,
    ): Int {
        if (!isOfficialOwner(candidate.author)) return Int.MIN_VALUE / 4
        val titleToken = token(candidate.title)
        val leftToken = token(left)
        val rightToken = token(right)
        if (leftToken.isBlank() || rightToken.isBlank()) return 0
        if (!titleToken.contains(leftToken) || !titleToken.contains(rightToken)) return 0

        var score = 165
        if (monthDay.isNotBlank() && candidate.title.contains(monthDay)) score += 45
        if (year > 0 && candidate.title.contains(year.toString())) score += 15
        query.competitionSlug?.takeIf(String::isNotBlank)?.let { slug ->
            if (candidate.title.contains(slug, ignoreCase = true)) score += 15
        }
        if (candidate.title.contains("速看") || candidate.title.contains("TOP5", true) || candidate.title.contains("集锦")) score -= 80
        if (candidate.title.contains("vs", true)) score += 10

        val target = query.scheduledStartEpochMillis?.div(1000L)
        if (target != null && candidate.pubdate > 0) {
            when (abs(candidate.pubdate - target) / 3600L) {
                in 0..24 -> score += 35
                in 25..48 -> score += 20
                in 49..96 -> score += 5
                else -> score -= 25
            }
        }
        return score
    }

    private fun parsePages(array: JSONArray): List<PageCandidate> = buildList {
        for (index in 0 until array.length()) {
            val page = array.optJSONObject(index) ?: continue
            val number = page.optInt("page", index + 1).coerceAtLeast(1)
            val title = page.optString("part").ifBlank { "P$number" }
            add(
                PageCandidate(
                    game = parseGameNumber(title),
                    page = number,
                    cid = page.optLong("cid", 0L),
                    title = title,
                )
            )
        }
    }

    private fun normalizeGamePages(pages: List<PageCandidate>, bestOf: Int?): List<PageCandidate> {
        val explicit = pages.filter { it.game > 0 }.distinctBy { it.game }.sortedBy { it.game }
        if (explicit.isNotEmpty()) return explicit
        val likelyGames = pages.filter {
            it.title.contains("局") && !it.title.contains("赛前") && !it.title.contains("赛后") && !it.title.contains("采访")
        }
        val source = if (likelyGames.isNotEmpty()) likelyGames else pages
        val maxGames = bestOf?.takeIf { it in 1..7 } ?: source.size
        return source.take(maxGames).mapIndexed { index, page -> page.copy(game = index + 1) }
    }

    private fun fetchChapters(bvid: String, cid: Long): List<BilibiliVodChapter> {
        val root = getJson("$API_BASE/x/player/v2?bvid=${enc(bvid)}&cid=$cid")
        val array = root.optJSONObject("data")?.optJSONArray("view_points") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("content").trim()
                if (title.isBlank()) continue
                val from = item.optDouble("from", 0.0).toInt().coerceAtLeast(0)
                val to = item.optDouble("to", from.toDouble()).toInt().coerceAtLeast(from)
                add(BilibiliVodChapter(title, from, to, item.optString("team_name")))
            }
        }.distinctBy { "${it.fromSeconds}|${it.title}" }.sortedBy { it.fromSeconds }
    }

    private fun parseGameNumber(title: String): Int {
        Regex("(?:GAME|G)\\s*([1-7])", RegexOption.IGNORE_CASE).find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        val chinese = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7)
        Regex("第([一二三四五六七1-7])局").find(title)?.groupValues?.getOrNull(1)?.firstOrNull()?.let { raw ->
            return raw.digitToIntOrNull() ?: chinese[raw] ?: 0
        }
        return 0
    }

    private fun isGameStartChapter(title: String): Boolean {
        val compact = title.replace(" ", "").uppercase()
        return compact == "开始" || compact == "GAMESTART" || compact.contains("比赛开始") || compact.contains("游戏开始")
    }

    private fun competitionToken(slug: String): String = when {
        slug.contains("lpl", true) -> "LPL"
        slug.contains("msi", true) -> "MSI"
        slug.contains("world", true) -> "全球总决赛"
        else -> slug.take(18)
    }

    private fun isOfficialOwner(name: String): Boolean =
        name.trim() == OFFICIAL_OWNER || name.contains(OFFICIAL_OWNER)

    private fun token(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]+"), "")
    private fun stripHtml(value: String): String = value
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://www.bilibili.com/")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Bilibili HTTP ${response.code} · ${body.take(140)}")
            if (body.isBlank()) throw IOException("Bilibili empty response")
            return JSONObject(body)
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun safeMessage(error: Throwable): String = error.message?.take(160)?.takeIf(String::isNotBlank) ?: error::class.java.simpleName

    internal data class SearchCandidate(
        val bvid: String,
        val title: String,
        val author: String,
        val pubdate: Long,
        val score: Int = 0,
    )

    private data class PageCandidate(
        val game: Int,
        val page: Int,
        val cid: Long,
        val title: String,
    )

    private companion object {
        const val API_BASE = "https://api.bilibili.com"
        const val OFFICIAL_OWNER = "哔哩哔哩英雄联盟赛事"
        const val REQUEST_GAP_MS = 260L
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    }
}

data class BilibiliVodChapter(
    val title: String,
    val fromSeconds: Int,
    val toSeconds: Int,
    val teamName: String = "",
)
