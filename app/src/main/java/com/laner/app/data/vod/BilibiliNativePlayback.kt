package com.laner.app.data.vod

import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ReplayProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class BilibiliPlaybackSegment(
    val primaryUrl: String,
    val backupUrls: List<String> = emptyList(),
    val durationMs: Long = 0L,
)

data class BilibiliNativePlaybackSource(
    val bvid: String,
    val cid: Long,
    val quality: Int,
    val format: String,
    val segments: List<BilibiliPlaybackSegment>,
    val resolvedAtEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Resolves short-lived public playback descriptors only. No media bytes are downloaded or persisted.
 * Login/risk-control failures are surfaced to UI and never bypassed.
 */
object BilibiliNativePlaybackResolver {
    private const val API_BASE = "https://api.bilibili.com"
    private const val CACHE_TTL_MS = 10L * 60L * 1000L
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

    private val cache = ConcurrentHashMap<String, BilibiliNativePlaybackSource>()
    private val client = OkHttpClient()

    suspend fun resolve(
        replay: ReplayAsset,
        forceRefresh: Boolean = false,
    ): BilibiliNativePlaybackSource = withContext(Dispatchers.IO) {
        require(replay.provider == ReplayProvider.BILIBILI) { "Replay is not Bilibili" }
        val bvid = replay.externalMediaId?.trim().orEmpty()
        val cid = replay.externalPartId?.toLongOrNull()
        if (bvid.isBlank() || cid == null || cid <= 0L) {
            throw IOException("Bilibili replay is missing BVID/CID")
        }
        val key = "$bvid|$cid"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cache[key]?.takeIf { now - it.resolvedAtEpochMs < CACHE_TTL_MS }?.let { return@withContext it }
        }

        val endpoint = "$API_BASE/x/player/playurl" +
            "?bvid=${enc(bvid)}&cid=$cid&qn=80&fnver=0&fnval=0&fourk=0&platform=html5&high_quality=1"
        val root = getJson(endpoint)
        val apiCode = root.optInt("code", -1)
        if (apiCode != 0) throw IOException("Bilibili playurl code=$apiCode · ${root.optString("message")}")
        val data = root.optJSONObject("data") ?: throw IOException("Bilibili playurl missing data")
        val durl = data.optJSONArray("durl") ?: throw IOException("Bilibili did not return a progressive playback source")
        val segments = buildList {
            for (index in 0 until durl.length()) {
                val item = durl.optJSONObject(index) ?: continue
                val primary = normalizeUrl(item.optString("url"))
                if (primary.isBlank()) continue
                val backups = buildList {
                    val array = item.optJSONArray("backup_url")
                    if (array != null) {
                        for (backupIndex in 0 until array.length()) {
                            normalizeUrl(array.optString(backupIndex)).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }.distinct()
                add(
                    BilibiliPlaybackSegment(
                        primaryUrl = primary,
                        backupUrls = backups,
                        durationMs = item.optLong("length", 0L).coerceAtLeast(0L),
                    )
                )
            }
        }
        if (segments.isEmpty()) throw IOException("Bilibili playurl contains no playable segment")

        BilibiliNativePlaybackSource(
            bvid = bvid,
            cid = cid,
            quality = data.optInt("quality", 0),
            format = data.optString("format").ifBlank { "progressive" },
            segments = segments,
            resolvedAtEpochMs = now,
        ).also { cache[key] = it }
    }

    suspend fun chapters(replay: ReplayAsset): List<BilibiliVodChapter> = withContext(Dispatchers.IO) {
        val bvid = replay.externalMediaId?.trim().orEmpty()
        val cid = replay.externalPartId?.toLongOrNull()
        if (bvid.isBlank() || cid == null || cid <= 0L) return@withContext emptyList()
        val root = getJson("$API_BASE/x/player/v2?bvid=${enc(bvid)}&cid=$cid")
        val array = root.optJSONObject("data")?.optJSONArray("view_points") ?: return@withContext emptyList()
        buildList {
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

    private fun normalizeUrl(value: String): String = when {
        value.startsWith("//") -> "https:$value"
        else -> value.trim()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
