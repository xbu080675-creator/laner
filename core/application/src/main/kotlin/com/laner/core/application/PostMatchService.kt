package com.laner.core.application

import com.laner.core.domain.CompletedGameRecord
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.MatchId
import com.laner.core.domain.PostMatchBundle
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.SeriesResultState
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.VerifiedPostAward
import kotlin.math.abs

enum class PostMatchLoadStatus {
    READY,
    DEGRADED,
    CONFLICT,
    UNAVAILABLE,
}

data class PostMatchConflict(
    val factType: String,
    val key: String,
    val providers: Set<String>,
)

data class PostMatchSnapshot(
    val bundle: PostMatchBundle,
    val status: PostMatchLoadStatus,
    val failures: List<DiagnosticFailure>,
    val conflicts: List<PostMatchConflict>,
)

/**
 * Application authority for POST facts.
 *
 * Result, completed games, awards and replay metadata are independent capabilities. A failure in
 * one capability never erases valid facts already returned by another source. The device archive
 * is fallback-only: fresh external candidates replace the cached fact for the same capability/game
 * while missing facts may still be recovered from the last verified archive.
 */
class PostMatchService(
    private val resultSources: List<PostResultSourcePort>,
    private val gameSources: List<CompletedGameSourcePort>,
    private val awardSources: List<PostAwardSourcePort>,
    private val replaySources: List<ReplaySourcePort>,
    private val archiveRepository: PostMatchArchiveRepository? = null,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun load(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): PostMatchSnapshot {
        val failures = mutableListOf<DiagnosticFailure>()
        val conflicts = mutableListOf<PostMatchConflict>()
        val archived = loadArchive(query.matchId, failures)

        val externalResults = resultSources.mapNotNull { source ->
            when (val read = source.readResult(query, context)) {
                is ProviderRead.Success -> read.value?.takeIf {
                    validateMatch(it.matchId, query.matchId, source.providerId, "result", failures)
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    null
                }
            }
        }
        val resultCandidates = if (externalResults.isNotEmpty()) {
            externalResults
        } else {
            listOfNotNull(archived?.result)
        }
        val selectedResult = resultCandidates.reduceOrNull(::preferResult)
        if (externalResults.map { resultKey(it) }.distinct().size > 1) {
            conflicts += PostMatchConflict(
                factType = "SERIES_RESULT",
                key = query.matchId.value,
                providers = externalResults.map { it.provenance.providerId }.toSet(),
            )
        }

        val externalGameCandidates = gameSources.flatMap { source ->
            when (val read = source.readGames(query, context)) {
                is ProviderRead.Success -> read.value.filter {
                    validateMatch(it.matchId, query.matchId, source.providerId, "game", failures)
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emptyList()
                }
            }
        }
        val externalGameNumbers = externalGameCandidates.map { it.gameNumber }.toSet()
        val fallbackGames = archived?.games.orEmpty().filter { it.gameNumber !in externalGameNumbers }
        val gameCandidates = externalGameCandidates + fallbackGames
        val games = gameCandidates
            .groupBy { it.gameNumber }
            .mapNotNull { (gameNumber, candidates) ->
                val externalForGame = externalGameCandidates.filter { it.gameNumber == gameNumber }
                if (externalForGame.map { it.gameId }.distinct().size > 1) {
                    conflicts += PostMatchConflict(
                        factType = "GAME_ID",
                        key = "${query.matchId.value}:G$gameNumber",
                        providers = externalForGame.map { it.provenance.providerId }.toSet(),
                    )
                }
                candidates.reduceOrNull { best, next -> if (prefer(next.provenance, best.provenance)) next else best }
            }
            .sortedBy { it.gameNumber }

        val awardCandidates = awardSources.flatMap { source ->
            when (val read = source.readAwards(query, context)) {
                is ProviderRead.Success -> read.value.filter {
                    validateMatch(it.matchId, query.matchId, source.providerId, "award", failures)
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emptyList()
                }
            }
        }
        val awards = awardCandidates
            .groupBy(::awardSlot)
            .mapNotNull { (slot, candidates) ->
                if (candidates.map { it.player.id }.distinct().size > 1) {
                    conflicts += PostMatchConflict(
                        factType = "AWARD",
                        key = slot,
                        providers = candidates.map { it.provenance.providerId }.toSet(),
                    )
                }
                candidates.reduceOrNull { best, next -> if (prefer(next.provenance, best.provenance)) next else best }
            }
            .sortedWith(compareBy({ it.gameNumber ?: Int.MIN_VALUE }, { it.gameId?.value ?: "" }, { it.kind.name }))

        val replays = replaySources.flatMap { source ->
            when (val read = source.readReplays(query, context)) {
                is ProviderRead.Success -> read.value.filter {
                    validateMatch(it.matchId, query.matchId, source.providerId, "replay", failures)
                }
                is ProviderRead.Failure -> {
                    failures += read.failure
                    emptyList()
                }
            }
        }.groupBy(::replayKey)
            .mapNotNull { (_, candidates) ->
                candidates.reduceOrNull { best, next -> if (prefer(next.provenance, best.provenance)) next else best }
            }
            .sortedWith(compareBy({ it.gameNumber ?: Int.MAX_VALUE }, { it.provider.name }, { it.locale ?: "" }))

        val bundle = PostMatchBundle(
            matchId = query.matchId,
            result = selectedResult,
            games = games,
            awards = awards,
            replays = replays,
        )

        val archiveFactConflict = conflicts.any { it.factType == "SERIES_RESULT" || it.factType == "GAME_ID" }
        if (!archiveFactConflict && (selectedResult != null || games.isNotEmpty())) {
            saveArchive(
                PostArchiveSnapshot(
                    matchId = query.matchId,
                    result = selectedResult,
                    games = games,
                    storedAtEpochMillis = context.nowEpochMillis,
                ),
                failures,
            )
        }

        val hasAnyFact = selectedResult != null || games.isNotEmpty() || awards.isNotEmpty() || replays.isNotEmpty()
        val status = when {
            conflicts.isNotEmpty() -> PostMatchLoadStatus.CONFLICT
            !hasAnyFact -> PostMatchLoadStatus.UNAVAILABLE
            failures.isNotEmpty() -> PostMatchLoadStatus.DEGRADED
            else -> PostMatchLoadStatus.READY
        }

        diagnostics?.emit(
            DiagnosticEvent(
                module = "POST",
                level = when (status) {
                    PostMatchLoadStatus.READY -> LogLevel.INFO
                    PostMatchLoadStatus.DEGRADED -> LogLevel.WARN
                    PostMatchLoadStatus.CONFLICT -> LogLevel.WARN
                    PostMatchLoadStatus.UNAVAILABLE -> LogLevel.WARN
                },
                message = "Post match ${status.name.lowercase()}",
                context = mapOf(
                    "match_id" to query.matchId.value,
                    "result" to (selectedResult != null).toString(),
                    "games" to games.size.toString(),
                    "awards" to awards.size.toString(),
                    "replays" to replays.size.toString(),
                    "archive" to (archived != null).toString(),
                    "failures" to failures.size.toString(),
                    "conflicts" to conflicts.size.toString(),
                ),
            )
        )

        return PostMatchSnapshot(bundle, status, failures, conflicts)
    }

    private suspend fun loadArchive(
        matchId: MatchId,
        failures: MutableList<DiagnosticFailure>,
    ): PostArchiveSnapshot? {
        val repository = archiveRepository ?: return null
        return try {
            repository.load(matchId)?.takeIf { snapshot ->
                if (snapshot.matchId == matchId) {
                    true
                } else {
                    failures += DiagnosticFailure(
                        code = ErrorCode("LNR-APP-POST-002"),
                        message = "POST archive returned another match",
                        retryable = false,
                        context = mapOf(
                            "requested_match" to matchId.value,
                            "archived_match" to snapshot.matchId.value,
                        ),
                    )
                    false
                }
            }
        } catch (error: Throwable) {
            failures += DiagnosticFailure(
                code = ErrorCode("LNR-APP-POST-003"),
                message = "POST archive read failed: ${safeMessage(error)}",
                retryable = true,
                context = mapOf("match_id" to matchId.value),
            )
            null
        }
    }

    private suspend fun saveArchive(
        snapshot: PostArchiveSnapshot,
        failures: MutableList<DiagnosticFailure>,
    ) {
        val repository = archiveRepository ?: return
        try {
            repository.save(snapshot)
        } catch (error: Throwable) {
            failures += DiagnosticFailure(
                code = ErrorCode("LNR-APP-POST-004"),
                message = "POST archive write failed: ${safeMessage(error)}",
                retryable = true,
                context = mapOf("match_id" to snapshot.matchId.value),
            )
        }
    }

    private fun validateMatch(
        actual: MatchId,
        requested: MatchId,
        providerId: String,
        factType: String,
        failures: MutableList<DiagnosticFailure>,
    ): Boolean {
        if (actual == requested) return true
        failures += DiagnosticFailure(
            code = ErrorCode("LNR-APP-POST-001"),
            message = "POST provider returned a fact for another match",
            retryable = false,
            context = mapOf(
                "provider" to providerId,
                "fact_type" to factType,
                "requested_match" to requested.value,
                "returned_match" to actual.value,
            ),
        )
        return false
    }

    private fun preferResult(a: SeriesResult, b: SeriesResult): SeriesResult {
        val at = factTime(a.provenance)
        val bt = factTime(b.provenance)
        if (abs(at - bt) > POST_FRESHNESS_OVERRIDE_MILLIS) return if (at > bt) a else b
        if (a.state != b.state) return if (a.state == SeriesResultState.FINAL) a else b
        return if (prefer(a.provenance, b.provenance)) a else b
    }

    private fun prefer(a: SourceProvenance, b: SourceProvenance): Boolean {
        val authority = a.authority.weight.compareTo(b.authority.weight)
        if (authority != 0) return authority > 0
        val time = factTime(a).compareTo(factTime(b))
        if (time != 0) return time > 0
        return a.revision > b.revision
    }

    private fun factTime(provenance: SourceProvenance): Long =
        provenance.sourceTimestampEpochMillis ?: provenance.observedAtEpochMillis

    private fun resultKey(result: SeriesResult): String = listOf(
        result.leftTeamId.value,
        result.rightTeamId.value,
        result.leftWins,
        result.rightWins,
        result.state.name,
        result.winnerTeamId?.value ?: "",
    ).joinToString("|")

    private fun awardSlot(award: VerifiedPostAward): String = listOf(
        award.kind.name,
        award.gameId?.value ?: "",
        award.gameNumber?.toString() ?: "series",
    ).joinToString("|")

    private fun replayKey(asset: ReplayAsset): String = listOf(
        asset.provider.name,
        asset.gameId?.value ?: "",
        asset.gameNumber?.toString() ?: "",
        asset.locale ?: "",
        asset.externalMediaId ?: "",
        asset.externalPartId ?: "",
        asset.sourceUrl,
    ).joinToString("|")

    private fun safeMessage(error: Throwable): String =
        error.message?.take(160)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName

    private companion object {
        const val POST_FRESHNESS_OVERRIDE_MILLIS = 5L * 60L * 1000L
    }
}
