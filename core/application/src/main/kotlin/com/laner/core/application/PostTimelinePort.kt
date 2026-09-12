package com.laner.core.application

import com.laner.core.domain.DataAuthority
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.MatchEvent
import com.laner.core.domain.SourceProvenance

data class HistoricalTimelineFrame(
    val snapshot: LiveGameSnapshot,
    val provenance: SourceProvenance,
    val events: List<MatchEvent> = emptyList(),
) {
    init {
        require(provenance.sourceClass == com.laner.core.domain.SourceClass.POST_MATCH_SOURCE)
        require(events.all { it.provenance.sourceClass == com.laner.core.domain.SourceClass.POST_MATCH_SOURCE })
    }
}

data class HistoricalGameTimelineBatch(
    val gameNumber: Int,
    val frames: List<HistoricalTimelineFrame>,
    val complete: Boolean,
) {
    init {
        require(gameNumber > 0)
        require(frames.all { it.snapshot.game.gameNumber == gameNumber })
    }
}

/** Global/region-neutral historical process source. Adapters return only real provider frames. */
interface PostTimelineSourcePort : PostSourceCapability {
    val providerId: String
    val authority: DataAuthority

    suspend fun readGameTimeline(
        query: PostMatchQuery,
        gameNumber: Int,
        context: SourceRequestContext,
    ): ProviderRead<HistoricalGameTimelineBatch?>
}
