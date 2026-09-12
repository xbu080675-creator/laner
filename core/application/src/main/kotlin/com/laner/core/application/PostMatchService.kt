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
 * one capability never erases valid facts already returned by another source.
 */
class PostMatchService(
    private val resultSources: List<PostResultSourcePort>,
    private val gameSources: List<CompletedGameSourcePort>,
    private val awardSources: List<PostAwardSourcePort>,
    private val replaySources: List<ReplaySourcePort>,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun load(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): PostMatchSnapshot {
        val failures = mutableListOf<DiagnosticFailure>()
        val conflicts = mutableListOf<PostMatchConflict>()

        val results = resultSources.mapNotNull { source ->
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
        val selectedResult = results.reduceOrNull(::preferResult)
        if (results.map { resultKey(it) }.distinct().size > 1) {
            conflicts += PostMatchConflict(
                factType = "SERIES_RESULT",
                key = query.matchId.value,
                providers = results.map { it.provenance.providerId }.toSet(),
            )
        }

        val gameCandidates = gameSources.flatMap { source ->
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
        val games = gameCandidates
            .groupBy { it.gameNumber }
            .mapNotNull { (gameNumber, candidates) ->
                if (candidates.map { it.gameId }.distinct().size > 1) {
                    conflicts += PostMatchConflict(
                        factType = "GAME_ID",
                        key = "${query.matchId.value}:G$gameNumber",
                        providers = candidates.map { it.provenance.providerId }.toSet(),
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
            .sortedWith(compareBy({ it.gameId?.value ?: "" }, { it.kind.name }))

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
                    "failures" to failures.size.toString(),
                    "conflicts" to conflicts.size.toString(),
                ),
            )
        )

        return PostMatchSnapshot(bundle, status, failures, conflicts)
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

    private fun awardSlot(award: VerifiedPostAward): String =
        "${award.kind.name}|${award.gameId?.value ?: "series"}"

    private fun replayKey(asset: ReplayAsset): String = listOf(
        asset.provider.name,
        asset.gameId?.value ?: "",
        asset.gameNumber?.toString() ?: "",
        asset.locale ?: "",
        asset.externalMediaId ?: "",
        asset.externalPartId ?: "",
        asset.sourceUrl,
    ).joinToString("|")

    private companion object {
        const val POST_FRESHNESS_OVERRIDE_MILLIS = 5L * 60L * 1000L
    }
}
