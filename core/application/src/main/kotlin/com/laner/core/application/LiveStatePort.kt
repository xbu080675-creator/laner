package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.TeamRef

/**
 * Provider-neutral LIVE lookup context.
 *
 * Canonical Laner identity remains [matchId]. Team/time hints come from the normalized schedule and
 * may be used by an Adapter to discover its own external match id. Provider raw ids never enter
 * this query or become Domain identity.
 */
data class LiveMatchSourceQuery(
    val matchId: MatchId,
    val scheduledStartEpochMillis: Long? = null,
    val teams: List<TeamRef> = emptyList(),
) {
    init {
        require(scheduledStartEpochMillis == null || scheduledStartEpochMillis >= 0)
        require(teams.isEmpty() || teams.size == 2) { "LIVE source lookup must contain zero or two teams" }
        require(teams.map { it.id }.distinct().size == teams.size)
    }

    companion object {
        fun from(series: ScheduledSeries): LiveMatchSourceQuery = LiveMatchSourceQuery(
            matchId = series.matchId,
            scheduledStartEpochMillis = series.startTimeEpochMillis,
            teams = series.teams.map { it.team },
        )
    }
}

/**
 * Provider-neutral live observation.
 *
 * Adapters translate raw provider fields into these observable facts. They do not decide Laner's
 * authoritative lifecycle; Application turns the facts into lifecycle signals and arbitrates them.
 */
data class ProviderLiveObservation(
    val matchId: MatchId,
    val eventStarted: Boolean = false,
    val draftStarted: Boolean = false,
    val loadingObserved: Boolean = false,
    val liveFrameObserved: Boolean = false,
    val gameEnded: Boolean = false,
    val betweenGames: Boolean = false,
    val seriesEnded: Boolean = false,
    val gameId: GameId? = null,
    val gameNumber: Int? = null,
    val observedAtEpochMillis: Long,
    val sourceTimestampEpochMillis: Long? = null,
    val revision: Long = 0L,
    val sourceUri: String? = null,
) {
    init {
        require(gameNumber == null || gameNumber > 0)
        require(observedAtEpochMillis >= 0)
        require(sourceTimestampEpochMillis == null || sourceTimestampEpochMillis >= 0)
        require(revision >= 0)
    }
}

interface LiveStateSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readLiveState(
        query: LiveMatchSourceQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveObservation>
}

interface LiveMatchStateRepository {
    suspend fun read(matchId: MatchId): LiveMatchState?
    suspend fun write(state: LiveMatchState)
}
