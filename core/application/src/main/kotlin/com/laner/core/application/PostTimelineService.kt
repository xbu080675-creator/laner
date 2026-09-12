package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.GameIdentity
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.SourceClass

enum class PostTimelineLoadStatus {
    READY,
    DEGRADED,
    UNAVAILABLE,
}

data class PostTimelineResolution(
    val timeline: GameTimeline?,
    val status: PostTimelineLoadStatus,
    val failures: List<DiagnosticFailure>,
    val sourcesApplied: Set<String>,
)

/**
 * Application authority for historical process backfill.
 *
 * Sources can be global or regional, but Application only sees capability. Every frame must map to
 * the canonical MatchId/GameId and carry POST_MATCH_SOURCE provenance. Valid real frames are merged
 * into the same canonical GameTimeline used by LIVE; missing history remains a gap.
 */
class PostTimelineService(
    private val sources: List<PostTimelineSourcePort>,
    private val timelineService: LiveTimelineService,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun backfill(
        query: PostMatchQuery,
        gameNumber: Int,
        context: SourceRequestContext,
    ): PostTimelineResolution {
        require(gameNumber > 0)
        val expectedGameId = GameIdentity.canonical(query.matchId, gameNumber)
        val failures = mutableListOf<DiagnosticFailure>()
        val applied = linkedSetOf<String>()
        var acceptedAny = false
        var completeObserved = false

        for (source in sources.filter { it.supports(query) }) {
            when (val read = source.readGameTimeline(query, gameNumber, context)) {
                is ProviderRead.Failure -> failures += read.failure
                is ProviderRead.Success -> {
                    val batch = read.value ?: continue
                    if (batch.gameNumber != gameNumber) {
                        failures += invalidSourceFailure(source.providerId, "game_number", gameNumber.toString(), batch.gameNumber.toString())
                        continue
                    }
                    var sourceApplied = false
                    for (frame in batch.frames) {
                        val snapshot = frame.snapshot
                        val game = snapshot.game
                        if (game.matchId != query.matchId || game.gameId != expectedGameId || game.gameNumber != gameNumber) {
                            failures += invalidSourceFailure(
                                source.providerId,
                                "identity",
                                "${query.matchId.value}|${expectedGameId.value}|G$gameNumber",
                                "${game.matchId.value}|${game.gameId.value}|G${game.gameNumber}",
                            )
                            continue
                        }
                        if (frame.provenance.sourceClass != SourceClass.POST_MATCH_SOURCE) {
                            failures += invalidSourceFailure(source.providerId, "source_class", SourceClass.POST_MATCH_SOURCE.name, frame.provenance.sourceClass.name)
                            continue
                        }
                        timelineService.ingest(snapshot, frame.provenance, frame.events)
                        acceptedAny = true
                        sourceApplied = true
                    }
                    if (sourceApplied) applied += source.providerId
                    completeObserved = completeObserved || (sourceApplied && batch.complete)
                }
            }
        }

        if (completeObserved) timelineService.markCompleted(expectedGameId)
        val timeline = timelineService.load(expectedGameId)
        val status = when {
            timeline == null && !acceptedAny -> PostTimelineLoadStatus.UNAVAILABLE
            failures.isNotEmpty() -> PostTimelineLoadStatus.DEGRADED
            else -> PostTimelineLoadStatus.READY
        }

        diagnostics?.emit(
            DiagnosticEvent(
                module = "POST",
                level = if (status == PostTimelineLoadStatus.READY) LogLevel.INFO else LogLevel.WARN,
                message = "Historical timeline ${status.name.lowercase()}",
                context = mapOf(
                    "match_id" to query.matchId.value,
                    "game_number" to gameNumber.toString(),
                    "frames" to (timeline?.snapshots?.size ?: 0).toString(),
                    "sources" to applied.joinToString(","),
                    "failures" to failures.size.toString(),
                ),
            )
        )

        return PostTimelineResolution(timeline, status, failures, applied)
    }

    private fun invalidSourceFailure(
        providerId: String,
        field: String,
        expected: String,
        actual: String,
    ): DiagnosticFailure = DiagnosticFailure(
        code = ErrorCode("LNR-APP-POST-005"),
        message = "Historical timeline source returned invalid canonical identity",
        retryable = false,
        context = mapOf(
            "provider" to providerId,
            "field" to field,
            "expected" to expected,
            "actual" to actual,
        ),
    )
}
