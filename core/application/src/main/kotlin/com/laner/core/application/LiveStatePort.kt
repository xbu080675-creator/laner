package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId

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
        matchId: MatchId,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveObservation>
}

interface LiveMatchStateRepository {
    suspend fun read(matchId: MatchId): LiveMatchState?
    suspend fun write(state: LiveMatchState)
}
