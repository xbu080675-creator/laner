package com.laner.core.application

import com.laner.core.domain.CompletedGameRecord
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.MatchId
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.SeriesResult
import com.laner.core.domain.TeamRef
import com.laner.core.domain.VerifiedPostAward

data class PostMatchQuery(
    val matchId: MatchId,
    val competitionSlug: String? = null,
    val scheduledStartEpochMillis: Long? = null,
    val bestOf: Int? = null,
    val teams: List<TeamRef> = emptyList(),
) {
    init {
        require(competitionSlug == null || competitionSlug.isNotBlank())
        require(scheduledStartEpochMillis == null || scheduledStartEpochMillis >= 0)
        require(bestOf == null || bestOf > 0)
        require(teams.isEmpty() || teams.size == 2)
        require(teams.map { it.id }.distinct().size == teams.size)
    }

    companion object {
        fun from(series: ScheduledSeries): PostMatchQuery = PostMatchQuery(
            matchId = series.matchId,
            competitionSlug = series.competitionSlug,
            scheduledStartEpochMillis = series.startTimeEpochMillis,
            bestOf = series.bestOf,
            teams = series.teams.map { it.team },
        )
    }
}

data class PostArchiveSnapshot(
    val matchId: MatchId,
    val result: SeriesResult? = null,
    val games: List<CompletedGameRecord> = emptyList(),
    val storedAtEpochMillis: Long,
) {
    init {
        require(result == null || result.matchId == matchId)
        require(games.all { it.matchId == matchId })
        require(games.map { it.gameNumber }.distinct().size == games.size)
        require(games.map { it.gameId }.distinct().size == games.size)
        require(storedAtEpochMillis >= 0)
    }
}

/**
 * Device-local cache for already verified POST facts.
 *
 * The archive is not a new fact authority. It preserves the original provenance embedded in each
 * fact so Application can recover completed series even when the external historical provider is
 * temporarily unavailable. Adapters must version/corruption-protect their storage format.
 */
interface PostMatchArchiveRepository {
    suspend fun load(matchId: MatchId): PostArchiveSnapshot?
    suspend fun save(snapshot: PostArchiveSnapshot)
}

/**
 * Global POST capability contract.
 *
 * Region/league differences are adapter capabilities, never Application branches. A source must
 * explicitly say whether it can serve the current canonical match. Global providers keep the
 * default `true`; regional providers override this method. Unsupported providers are not called.
 */
interface PostSourceCapability {
    fun supports(query: PostMatchQuery): Boolean = true
}

interface PostResultSourcePort : PostSourceCapability {
    val providerId: String
    val authority: DataAuthority

    suspend fun readResult(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<SeriesResult?>
}

interface CompletedGameSourcePort : PostSourceCapability {
    val providerId: String
    val authority: DataAuthority

    suspend fun readGames(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<CompletedGameRecord>>
}

interface PostAwardSourcePort : PostSourceCapability {
    val providerId: String
    val authority: DataAuthority

    suspend fun readAwards(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<VerifiedPostAward>>
}

interface ReplaySourcePort : PostSourceCapability {
    val providerId: String
    val authority: DataAuthority

    suspend fun readReplays(
        query: PostMatchQuery,
        context: SourceRequestContext,
    ): ProviderRead<List<ReplayAsset>>
}
