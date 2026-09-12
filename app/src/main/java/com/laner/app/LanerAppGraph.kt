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
import com.laner.app.data.riot.RiotGlobalLiveSource
import com.laner.app.data.riot.RiotGlobalPreMatchSource
import com.laner.app.data.riot.RiotGlobalReplaySource
import com.laner.app.data.riot.RiotGlobalResultSource
import com.laner.app.data.riot.RiotTeamRosterSource
import com.laner.app.data.roster.NormalizedStartingRosterSource
import com.laner.app.data.staff.NormalizedTeamStaffSource
import com.laner.core.application.CompetitionStructureService
import com.laner.core.application.GlobalScheduleService
import com.laner.core.application.LiveMatchStateService
import com.laner.core.application.LiveSnapshotService
import com.laner.core.application.LiveTimelineService
import com.laner.core.application.PostMatchService
import com.laner.core.application.PostTimelineService
import com.laner.core.application.PreMatchContextService
import java.io.File
import okhttp3.OkHttpClient

class LanerAppGraph(
    filesDir: File,
) {
    private val diagnostics = AndroidDiagnosticsPort()

    /**
     * Riot LiveStats currently expects the same public LoL Esports web-client token as the
     * persisted gateway. Keep transport authentication in Adapter composition; Core never sees it.
     */
    private val riotLiveHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("x-api-key", BuildConfig.LOL_ESPORTS_API_KEY)
                .build()
            chain.proceed(request)
        }
        .build()

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
    private val providerIdentityRepository = JsonProviderMatchIdentityRepository(File(filesDir, "identity/provider-match.json"))
    private val riotGlobalLiveSource = RiotGlobalLiveSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
        identityRepository = providerIdentityRepository,
        client = riotLiveHttpClient,
    )
    private val riotGlobalResultSource = RiotGlobalResultSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
        identityRepository = providerIdentityRepository,
    )
    private val riotGlobalReplaySource = RiotGlobalReplaySource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
        identityRepository = providerIdentityRepository,
    )
    private val riotGlobalHistoricalTimelineSource = RiotGlobalHistoricalTimelineSource(
        apiKey = BuildConfig.LOL_ESPORTS_API_KEY,
        identityRepository = providerIdentityRepository,
        client = riotLiveHttpClient,
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

    val liveTimelineService = LiveTimelineService(liveTimelineRepository)

    val liveMatchStateService = LiveMatchStateService(
        sources = listOf(riotGlobalLiveSource),
        repository = liveStateRepository,
        diagnostics = diagnostics,
    )

    val liveSnapshotService = LiveSnapshotService(
        sources = listOf(riotGlobalLiveSource),
        timelineService = liveTimelineService,
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

    /** On-demand real historical frames. Never auto-scans every game when POST opens. */
    val postTimelineService = PostTimelineService(
        sources = listOf(riotGlobalHistoricalTimelineSource),
        timelineService = liveTimelineService,
        diagnostics = diagnostics,
    )
}
