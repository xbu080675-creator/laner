package com.laner.app

import com.laner.app.data.riot.RiotGlobalPreMatchSource
import com.laner.core.application.GlobalScheduleService

class LanerAppGraph {
    private val diagnostics = AndroidDiagnosticsPort()
    private val riotPreMatchSource = RiotGlobalPreMatchSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )

    val globalScheduleService = GlobalScheduleService(
        sources = listOf(riotPreMatchSource),
        diagnostics = diagnostics,
    )
}
