package com.laner.app.data.roster

import android.graphics.BitmapFactory
import com.laner.core.application.ProviderRead
import com.laner.core.application.ProviderStartingRosterAnnouncement
import com.laner.core.application.RosterVisionInspection
import com.laner.core.application.SourceRequestContext
import com.laner.core.application.StartingRosterVisionPort
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.ScheduledSeries
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MlKitStartingRosterVisionSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) : StartingRosterVisionPort {
    override val providerId: String = "device-mlkit-roster-ocr"
    override val authority: DataAuthority = DataAuthority.DERIVED

    private data class Cached(val storedAt: Long, val inspection: RosterVisionInspection)
    private val cache = object : LinkedHashMap<String, Cached>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > 64
    }

    override suspend fun inspect(
        match: ScheduledSeries,
        announcement: ProviderStartingRosterAnnouncement,
        context: SourceRequestContext,
    ): ProviderRead<List<RosterVisionInspection>> = withContext(Dispatchers.IO) {
        if (announcement.imageUrls.isEmpty()) {
            return@withContext ProviderRead.Failure(
                failure("LNR-SRC-PRE-013", "Official roster announcement contains no downloadable image", false, match, announcement)
            )
        }

        val leagueHint = listOf(match.competitionSlug, match.competition.name, announcement.league).joinToString(" ")
        val results = announcement.imageUrls.take(2).map { imageUrl ->
            cached("${leagueHint.uppercase()}|$imageUrl") ?: runInspection(leagueHint, imageUrl, announcement).also {
                store("${leagueHint.uppercase()}|$imageUrl", it)
            }
        }
        if (results.all { it.error != null }) {
            return@withContext ProviderRead.Failure(
                failure(
                    "LNR-SRC-PRE-013",
                    "Official roster image download/OCR failed: ${results.firstNotNullOfOrNull { it.error }?.take(100) ?: "unknown"}",
                    true,
                    match,
                    announcement,
                )
            )
        }
        ProviderRead.Success(results)
    }

    private suspend fun runInspection(
        leagueHint: String,
        imageUrl: String,
        announcement: ProviderStartingRosterAnnouncement,
    ): RosterVisionInspection = runCatching {
        val request = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", "Laner-Roster/4 Android")
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .apply { announcement.sourceUri?.takeIf { it.startsWith("https://") }?.let { header("Referer", it) } }
            .build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("empty image body")
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("bitmap decode failed")
        try {
            val ocr = RosterVisionPipeline.recognize(bitmap, leagueHint)
            val candidates = RosterCandidateExtractor.extract(ocr)
            RosterVisionInspection(
                announcementId = announcement.id,
                imageUrl = imageUrl,
                engines = ocr.engines,
                lineCount = ocr.lineCount,
                roleCandidates = candidates.merged,
                leftRoleCandidates = candidates.left,
                rightRoleCandidates = candidates.right,
                layoutMode = candidates.layoutMode,
                textPreview = ocr.text.replace('\n', ' ').take(500),
            )
        } finally {
            bitmap.recycle()
        }
    }.getOrElse { error ->
        RosterVisionInspection(
            announcementId = announcement.id,
            imageUrl = imageUrl,
            engines = emptyList(),
            lineCount = 0,
            roleCandidates = emptyMap(),
            textPreview = "",
            error = "${error::class.java.simpleName}:${error.message.orEmpty().take(120)}",
        )
    }

    @Synchronized
    private fun cached(key: String): RosterVisionInspection? {
        val row = cache[key] ?: return null
        val ttl = if (row.inspection.error == null) SUCCESS_CACHE_MS else FAILURE_CACHE_MS
        if (System.currentTimeMillis() - row.storedAt > ttl) {
            cache.remove(key)
            return null
        }
        return row.inspection
    }

    @Synchronized
    private fun store(key: String, inspection: RosterVisionInspection) {
        cache[key] = Cached(System.currentTimeMillis(), inspection)
    }

    private fun failure(
        code: String,
        message: String,
        retryable: Boolean,
        match: ScheduledSeries,
        announcement: ProviderStartingRosterAnnouncement,
    ) = DiagnosticFailure(
        code = ErrorCode(code),
        message = message,
        retryable = retryable,
        context = mapOf(
            "provider" to providerId,
            "match" to match.matchId.value,
            "announcement" to announcement.id,
            "team" to announcement.team,
        ),
    )

    private companion object {
        const val SUCCESS_CACHE_MS = 6L * 60L * 60L * 1000L
        const val FAILURE_CACHE_MS = 10L * 60L * 1000L
    }
}
