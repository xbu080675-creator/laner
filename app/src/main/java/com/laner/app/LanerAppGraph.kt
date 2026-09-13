package com.laner.app

import com.laner.app.data.archive.JsonProviderMatchIdentityRepository
import com.laner.app.data.archive.JsonTournamentEditionArchiveRepository
import com.laner.app.data.live.JsonLiveMatchStateRepository
import com.laner.app.data.live.JsonLiveTimelineRepository
import com.laner.app.data.post.JsonPostMatchArchiveRepository
import com.laner.app.data.post.LplHistoricalPostMatchSource
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
import com.laner.core.application.CompletedGameSourcePort
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveEventDerivationService
import com.laner.core.application.LiveMatchContextService
import com.laner.core.application.LiveMatchStateService
import com.laner.core.application.LiveSnapshotService
import com.laner.core.application.LiveTimelineService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostResultSourcePort
import com.laner.core.application.PostTimelineService
import com.laner.core.application.PreMatchContextService
import com.laner.core.application.WatchHubContextService
import java.io.File

/**
 * Android composition root.
 *
 * Providers are capability adapters, never product dependencies. Region remains metadata used by
 * adapters for coverage; Core/Application own one global competition flow.
 */
class LanerAppGraph(
    filesDir: File,
    riotApiKey: String = BuildConfig.LOL_ESPORTS_API_KEY,
    lplTjstatsAuth: String = BuildConfig.LPL_TJSTATS_AUTH,
) {
    val diagnostics = AndroidDiagnosticsPort()
    private val providerIdentityRepository = JsonProviderMatchIdentityRepository(File(filesDir, "identity/provider-match.json"))

    private val riotEnabled = riotApiKey.isNotBlank()
    private val lplHistoryEnabled = lplTjstatsAuth.isNotBlank()

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
    private val lplHistoricalPostMatchSource = LplHistoricalPostMatchSource(tjstatsAuth = lplTjstatsAuth)

    val globalScheduleService = GlobalScheduleService(
        sources = if (riotEnabled) listOf(riotPreMatchSource) else emptyList(),
        diagnostics = diagnostics,
    )

    val preMatchContextService = PreMatchContextService(
        rosterSources = if (riotEnabled) listOf(riotTeamRosterSource) else emptyList(),
        staffSources = listOf(normalizedTeamStaffSource),
        startingRosterSources = listOf(normalizedStartingRosterSource),
        diagnostics = diagnostics,
    )

    val competitionStructureService = CompetitionStructureService(
        editionSources = if (riotEnabled) listOf(riotCompetitionStructureSource) else emptyList(),
        standingsSources = if (riotEnabled) listOf(riotCompetitionStructureSource) else emptyList(),
        championshipPointsSources = emptyList(),
        qualificationSources = listOf(official2026QualificationSource),
        archiveRepository = editionArchiveRepository,
        diagnostics = diagnostics,
    )

    val liveMatchStateService = LiveMatchStateService(
        sources = if (riotEnabled) listOf(riotGlobalLiveStateSource) else emptyList(),
        repository = liveStateRepository,
        diagnostics = diagnostics,
    )

    val watchHubContextService = WatchHubContextService(
        scheduleService = globalScheduleService,
        liveMatchStateService = liveMatchStateService,
        diagnostics = diagnostics,
    )

    val liveTimelineService = LiveTimelineService(liveTimelineRepository)
    val liveEventDerivationService = LiveEventDerivationService(liveTimelineService)

    val liveSnapshotService = LiveSnapshotService(
        sources = if (riotEnabled) listOf(riotGlobalLiveSnapshotSource) else emptyList(),
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

    private val postResultSources: List<PostResultSourcePort> = buildList {
        if (riotEnabled) add(riotGlobalResultSource)
        if (lplHistoryEnabled) add(lplHistoricalPostMatchSource)
    }
    private val completedGameSources: List<CompletedGameSourcePort> = buildList {
        if (lplHistoryEnabled) add(lplHistoricalPostMatchSource)
    }

    val postMatchService = PostMatchService(
        resultSources = postResultSources,
        gameSources = completedGameSources,
        awardSources = listOf(verifiedAwardsMirrorSource),
        replaySources = if (riotEnabled) listOf(riotGlobalReplaySource) else emptyList(),
        archiveRepository = postArchiveRepository,
        diagnostics = diagnostics,
    )

    val postTimelineService = PostTimelineService(
        sources = if (riotEnabled) listOf(riotGlobalHistoricalTimelineSource) else emptyList(),
        timelineService = liveTimelineService,
        diagnostics = diagnostics,
    )
}
