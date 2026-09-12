package com.laner.app

import com.laner.app.data.archive.JsonProviderMatchIdentityRepository
import com.laner.app.data.archive.JsonTournamentEditionArchiveRepository
import com.laner.app.data.live.JsonLiveMatchStateRepository
import com.laner.app.data.live.JsonLiveTimelineRepository
import com.laner.app.data.post.JsonPostMatchArchiveRepository
import com.laner.app.data.post.VerifiedAwardsMirrorSource
import com.laner.app.data.qualification.Official2026QualificationSource
import com.laner.app.data.riot.RiotCompetitionStructureSource
import com.laner.app.data.riot.RiotGlobalHistoricalTimelineSource
import com.laner.app.data.riot.RiotGlobalLiveSnapshotSource
import com.laner.app.data.riot.RiotGlobalLiveStateSource
import com.laner.app.data.riot.RiotGlobalPreMatchSource
import com.laner.app.data.riot.RiotGlobalReplaySource
import com.laner.app.data.riot.RiotGlobalResultSource
import com.laner.app.data.riot.RiotTeamRosterSource
import com.laner.app.data.roster.NormalizedStartingRosterSource
import com.laner.app.data.staff.NormalizedTeamStaffSource
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveEventDerivationService
import com.laner.core.application.LiveMatchContextService
import com.laner.core.application.LiveMatchStateService
import com.laner.core.application.LiveSnapshotService
import com.laner.core.application.LiveTimelineService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostTimelineService
import com.laner.core.application.PreMatchContextService
import java.io.File

class LanerAppGraph(
    filesDir: File,
    riotApiKey: String = BuildConfig.LOL_ESPORTS_API_KEY,
) {
    val diagnostics = AndroidDiagnosticsPort()
    private val providerIdentityRepository = JsonProviderMatchIdentityRepository(File(filesDir, "identity/provider-match.json"))

    private val riotPreMatchSource = RiotGlobalPreMatchSource(apiKey = riotApiKey)
    private val riotTeamRosterSource = RiotTeamRosterSource(apiKey = riotApiKey)
    private val riotCompetitionStructureSource = RiotCompetitionStructureSource(apiKey = riotApiKey)
    private val riotGlobalLiveStateSource = RiotGlobalLiveStateSource(
        apiKey = riotApiKey,
        identityRepository = providerIdentityRepository,
    )
    private val riotGlobalLiveSnapshotSource = RiotGlobalLiveSnapshotSource(
        apiKey = riotApiKey,
        identityRepository = providerIdentityRepository,
    )
    private val official2026QualificationSource = Official2026QualificationSource()
    private val normalizedStartingRosterSource = NormalizedStartingRosterSource()
    private val normalizedTeamStaffSource = NormalizedTeamStaffSource()
    private val verifiedAwardsMirrorSource = VerifiedAwardsMirrorSource()
    private val editionArchiveRepository = JsonTournamentEditionArchiveRepository(File(filesDir, "archive"))
    private val liveStateRepository = JsonLiveMatchStateRepository(File(filesDir, "live/state"))
    private val liveTimelineRepository = JsonLiveTimelineRepository(File(filesDir, "live/timeline"))
    private val postArchiveRepository = JsonPostMatchArchiveRepository(File(filesDir, "post/archive"))
    private val riotGlobalResultSource = RiotGlobalResultSource(
        apiKey = riotApiKey,
        identityRepository = providerIdentityRepository,
    )
    private val riotGlobalReplaySource = RiotGlobalReplaySource(
        apiKey = riotApiKey,
        identityRepository = providerIdentityRepository,
    )
    private val riotGlobalHistoricalTimelineSource = RiotGlobalHistoricalTimelineSource(
        apiKey = riotApiKey,
        identityRepository = providerIdentityRepository,
    )

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

    /** Lifecycle authority remains independent from gameplay snapshot ingestion. */
    val liveMatchStateService = LiveMatchStateService(
        sources = listOf(riotGlobalLiveStateSource),
        repository = liveStateRepository,
        diagnostics = diagnostics,
    )

    val liveTimelineService = LiveTimelineService(liveTimelineRepository)
    val liveEventDerivationService = LiveEventDerivationService(liveTimelineService)

    val liveSnapshotService = LiveSnapshotService(
        sources = listOf(riotGlobalLiveSnapshotSource),
        timelineService = liveTimelineService,
        diagnostics = diagnostics,
    )

    val liveMatchContextService = LiveMatchContextService(
        scheduleService = globalScheduleService,
        liveMatchStateService = liveMatchStateService,
        liveSnapshotService = liveSnapshotService,
        liveTimelineService = liveTimelineService,
        liveEventDerivationService = liveEventDerivationService,
        diagnostics = diagnostics,
    )

    val postMatchService = PostMatchService(
        resultSources = listOf(riotGlobalResultSource),
        gameSources = emptyList(),
        awardSources = listOf(verifiedAwardsMirrorSource),
        replaySources = listOf(riotGlobalReplaySource),
        archiveRepository = postArchiveRepository,
        diagnostics = diagnostics,
    )

    val postTimelineService = PostTimelineService(
        sources = listOf(riotGlobalHistoricalTimelineSource),
        timelineService = liveTimelineService,
        diagnostics = diagnostics,
    )
}
