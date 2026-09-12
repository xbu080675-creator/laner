package com.laner.core.domain

@JvmInline value class CompetitionId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class EditionId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class StageId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class TeamId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class PlayerId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class MatchId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class GameId(val value: String) { init { require(value.isNotBlank()) } }

/**
 * Region is metadata/filtering context, never a business-module boundary.
 */
data class RegionRef(
    val code: String,
    val displayName: String,
) {
    init {
        require(code.isNotBlank())
        require(displayName.isNotBlank())
    }
}

data class CompetitionRef(
    val id: CompetitionId,
    val name: String,
    val region: RegionRef?,
) {
    init { require(name.isNotBlank()) }
}

data class TeamRef(
    val id: TeamId,
    val code: String,
    val name: String,
) {
    init { require(code.isNotBlank() || name.isNotBlank()) }
}

data class PlayerRef(
    val id: PlayerId,
    val handle: String,
    val teamId: TeamId?,
    val role: PlayerRole?,
) {
    init { require(handle.isNotBlank()) }
}

enum class PlayerRole {
    TOP,
    JUNGLE,
    MID,
    BOT,
    SUPPORT,
    SUBSTITUTE,
    UNKNOWN,
}
