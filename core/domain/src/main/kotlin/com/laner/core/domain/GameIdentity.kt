package com.laner.core.domain

/**
 * Canonical Laner identity for one game inside a series.
 *
 * Provider game/bMatch IDs must never become Domain primary keys. Once a source has resolved a
 * provider record to a canonical MatchId and explicit game number, every adapter uses this same
 * deterministic internal GameId.
 */
object GameIdentity {
    fun canonical(matchId: MatchId, gameNumber: Int): GameId {
        require(gameNumber > 0) { "gameNumber must be positive" }
        return GameId("${matchId.value}:game:$gameNumber")
    }
}
