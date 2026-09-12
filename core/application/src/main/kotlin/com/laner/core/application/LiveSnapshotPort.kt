package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchId
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamRef

data class LiveSnapshotQuery(
    val matchId: MatchId,
    val gameId: GameId,
    val gameNumber: Int,
    val teams: List<TeamRef>,
) {
    init {
        require(gameNumber > 0)
        require(teams.size == 2)
        require(teams.map { it.id }.distinct().size == 2)
    }
}

data class ProviderLiveSnapshot(
    val snapshot: LiveGameSnapshot,
    val provenance: SourceProvenance,
)

interface LiveSnapshotSourcePort {
    val providerId: String
    val authority: DataAuthority

    suspend fun readSnapshot(
        query: LiveSnapshotQuery,
        context: SourceRequestContext,
    ): ProviderRead<ProviderLiveSnapshot?>
}
