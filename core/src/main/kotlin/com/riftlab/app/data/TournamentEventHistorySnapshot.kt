package com.riftlab.app.data

data class TournamentEventHistorySnapshot(
    val completedSeries: List<ScheduledEsportsMatch> = emptyList(),
    val patchVersions: List<String> = emptyList(),
    val verifiedAwards: List<OfficialMvpRecord> = emptyList(),
    val completedEventsSource: String = "",
    val patchSource: String = "",
    val awardsSource: String = "",
    val diagnostics: List<String> = emptyList()
)
