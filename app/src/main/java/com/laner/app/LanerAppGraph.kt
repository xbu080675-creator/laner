package com.laner.app

import com.laner.app.data.archive.JsonTournamentEditionArchiveRepository
import com.laner.app.data.live.JsonLiveMatchStateRepository
import com.laner.app.data.live.JsonLiveTimelineRepository
import com.laner.app.data.post.JsonPostMatchArchiveRepository
import com.laner.app.data.post.VerifiedAwardsMirrorSource
import com.laner.app.data.qualification.Official2026QualificationSource
import com.laner.app.data.riot.RiotCompetitionStructureSource
import com.laner.app.data.riot.RiotGlobalPreMatchSource
import com.laner.app.data.riot.RiotTeamRosterSource
import com.laner.app.data.roster.NormalizedStartingRosterSource
import com.laner.app.data.staff.NormalizedTeamStaffSource
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveMatchStateService
import com.laner.core.application.LiveTimelineService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PreMatchContextService
import java.io.File

class LanerAppGraph(
    filesDir: File,
) {
    private val diagnostics = AndroidDiagnosticsPort()
    private val riotPreMatchSource = RiotGlobalPreMatchSource(apiKey = BuildConfig.LOL_ESPORTS_API_KEY)
    private val riotTeamRosterSource = RiotTeamRosterSource(apiKey = BuildConfig.LOL_ESPORTS_API_KEY)
    private val riotCompetitionStructureSource = RiotCompetitionStructureSource(apiKey = BuildConfig.LOL_ESPORTS_API_KEY)
    private val official2026QualificationSource = Official2026QualificationSource()
    private val normalizedStartingRosterSource = NormalizedStartingRosterSource()
    private val normalizedTeamStaffSource = NormalizedTeamStaffSource()
    private val verifiedAwardsMirrorSource = VerifiedAwardsMirrorSource()
    private val editionArchiveRepository = JsonTournamentEditionArchiveRepository(File(filesDir, "archive"))
    private val liveStateRepository = JsonLiveMatchStateRepository(File(filesDir, "live/state"))
    private val liveTimelineRepository = JsonLiveTimelineRepository(File(filesDir, "live/timeline"))
    private val postArchiveRepository = JsonPostMatchArchiveRepository(File(filesDir, "post/archive"))

    val globalScheduleService = GlobalScheduleService(listOf(riotPreMatchSource), diagnostics)

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

    val liveMatchStateService = LiveMatchStateService(
        sources = emptyList(),
        repository = liveStateRepository,
        diagnostics = diagnostics,
    )

    val liveTimelineService = LiveTimelineService(liveTimelineRepository)

    /**
     * Result/game/replay capabilities remain explicit until verified adapters are added. Awards can
     * already use the provenance-preserving mirror because they do not require provider raw IDs or
     * private credentials. Verified Result/Game facts are cached locally after arbitration so POST
     * can recover them later without pretending the cache is a new source authority.
     */
    val postMatchService = PostMatchService(
        resultSources = emptyList(),
        gameSources = emptyList(),
        awardSources = listOf(verifiedAwardsMirrorSource),
        replaySources = emptyList(),
        archiveRepository = postArchiveRepository,
        diagnostics = diagnostics,
    )
}
