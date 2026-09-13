package com.riftlab.app.data

data class CompletedSeriesSnapshot(
    val matchKey: String,
    val teamA: String,
    val teamB: String,
    val scoreA: Int,
    val scoreB: Int,
    val games: List<LiveSnapshot>,
    val seriesFinished: Boolean,
    val source: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val winner: String
        get() = when {
            scoreA > scoreB -> teamA
            scoreB > scoreA -> teamB
            else -> "—"
        }
}
