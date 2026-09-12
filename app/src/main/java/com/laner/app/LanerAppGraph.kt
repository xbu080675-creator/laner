package com.laner.app

import com.laner.app.data.riot.RiotGlobalPreMatchSource
import com.laner.app.data.riot.RiotTeamRosterSource
import com.laner.app.data.roster.NormalizedStartingRosterSource
import com.laner.app.data.staff.NormalizedTeamStaffSource
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.PreMatchContextService

class LanerAppGraph {
    private val diagnostics = AndroidDiagnosticsPort()
    private val riotPreMatchSource = RiotGlobalPreMatchSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )
    private val riotTeamRosterSource = RiotTeamRosterSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )
    private val normalizedStartingRosterSource = NormalizedStartingRosterSource()
    private val normalizedTeamStaffSource = NormalizedTeamStaffSource()

    val globalScheduleService = GlobalScheduleService(
        sources = listOf(riotPreMatchSource),
        diagnostics = diagnostics,
    )

    val preMatchContextService = PreMatchContextService(
        rosterSources = listOf(riotTeamRosterSource),
        staffSources = listOf(normalizedTeamStaffSource),
        startingRosterSources = listOf(normalizedStartingRosterSource),
        diagnostics = diagnostics,
    )
}
