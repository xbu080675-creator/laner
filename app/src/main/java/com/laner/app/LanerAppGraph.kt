package com.laner.app

import com.laner.app.data.archive.JsonTournamentEditionArchiveRepository
import com.laner.app.data.qualification.Official2026QualificationSource
import com.laner.app.data.riot.RiotCompetitionStructureSource
import com.laner.app.data.riot.RiotGlobalPreMatchSource
import com.laner.app.data.riot.RiotTeamRosterSource
import com.laner.app.data.roster.NormalizedStartingRosterSource
import com.laner.app.data.staff.NormalizedTeamStaffSource
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.PreMatchContextService
import java.io.File

class LanerAppGraph(
    filesDir: File,
) {
    private val diagnostics = AndroidDiagnosticsPort()
    private val riotPreMatchSource = RiotGlobalPreMatchSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )
    private val riotTeamRosterSource = RiotTeamRosterSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )
    private val riotCompetitionStructureSource = RiotCompetitionStructureSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )
    private val official2026QualificationSource = Official2026QualificationSource()
    private val normalizedStartingRosterSource = NormalizedStartingRosterSource()
    private val normalizedTeamStaffSource = NormalizedTeamStaffSource()
    private val editionArchiveRepository = JsonTournamentEditionArchiveRepository(
        directory = File(filesDir, "archive"),
    )

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

    val competitionStructureService = CompetitionStructureService(
        editionSources = listOf(riotCompetitionStructureSource),
        standingsSources = listOf(riotCompetitionStructureSource),
        championshipPointsSources = emptyList(),
        qualificationSources = listOf(official2026QualificationSource),
        archiveRepository = editionArchiveRepository,
        diagnostics = diagnostics,
    )
}
