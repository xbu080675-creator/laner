package com.laner.core.application

import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EditionId
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.QualificationEvidenceKind
import com.laner.core.domain.QualificationInput
import com.laner.core.domain.QualificationMechanism
import com.laner.core.domain.QualificationNodeState
import com.laner.core.domain.QualificationTeamState
import com.laner.core.domain.RegionRef
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.StandingMetricKind
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import com.laner.core.domain.TournamentEdition
import com.laner.core.domain.TournamentEditionSlot
import com.laner.core.domain.TournamentEditionSlotState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompetitionStructureServiceTest {
    private val context = SourceRequestContext(
        nowEpochMillis = 1_800_000_000_000L,
        correlationId = "competition-structure-test",
    )

    @Test
    fun archiveRetainsOlderEditionMissingFromLatestSourceWindow() = runSuspend {
        val oldEdition = edition(
            id = "lol:edition:lpl:2025:split3",
            year = 2025,
            slug = "split3",
            displayName = "2025 LPL 第三赛段",
            firstSeen = 100L,
            lastSeen = 200L,
        )
        val repository = MemoryEditionRepository(listOf(oldEdition))
        val service = service(
            repository = repository,
            editionSources = listOf(
                FakeEditionSource(
                    listOf(providerEdition(year = 2026, slug = "split3", displayName = "2026 LPL 第三赛段"))
                )
            ),
        )

        val result = service.refreshEditions(context)

        assertEquals(2, result.editions.size)
        assertTrue(result.editions.any { it.id == oldEdition.id })
        assertTrue(result.editions.any { it.seasonYear == 2026 })
    }

    @Test
    fun archiveMergeEnrichesWithoutDeletingKnownParticipantsOrCompleteSlots() {
        val old = edition(
            id = "lol:edition:lpl:2026:split3",
            year = 2026,
            slug = "split3",
            displayName = "2026 LPL 第三赛段",
            firstSeen = 100L,
            lastSeen = 200L,
            participants = setOf(TeamId("lol:team:a")),
            slots = listOf(
                TournamentEditionSlot("rules", "规则", TournamentEditionSlotState.COMPLETE, "已归档", "old")
            ),
        )
        val newer = old.copy(
            participantTeamIds = setOf(TeamId("lol:team:b")),
            scheduleSeriesCount = 12,
            slots = listOf(
                TournamentEditionSlot("rules", "规则", TournamentEditionSlotState.SOURCE_ERROR, "本轮源异常", "new")
            ),
            lastSeenEpochMillis = 300L,
            provenance = provenance(DataAuthority.OFFICIAL, 300L),
        )

        val merged = mergeEditionArchive(listOf(old), listOf(newer)).single()

        assertEquals(setOf(TeamId("lol:team:a"), TeamId("lol:team:b")), merged.participantTeamIds)
        assertEquals(12, merged.scheduleSeriesCount)
        assertEquals(TournamentEditionSlotState.COMPLETE, merged.slots.single().state)
        assertEquals(100L, merged.firstSeenEpochMillis)
        assertEquals(300L, merged.lastSeenEpochMillis)
    }

    @Test
    fun standingsStagePointsNeverBecomeChampionshipPointsOrQualification() = runSuspend {
        val repository = MemoryEditionRepository()
        val service = service(
            repository = repository,
            editionSources = listOf(FakeEditionSource(listOf(providerEdition()))),
            standingsSources = listOf(
                FakeStandingsSource(
                    ProviderStandingsSnapshot(
                        sections = listOf(
                            ProviderStandingsSection(
                                stageName = "Split 3",
                                sectionName = "Overall",
                                rows = listOf(
                                    ProviderStandingRow(
                                        ordinal = 1,
                                        team = team("BLG", "Bilibili Gaming"),
                                        seriesWins = 10,
                                        seriesLosses = 2,
                                        metrics = listOf(
                                            ProviderStandingMetric(
                                                kind = StandingMetricKind.STAGE_POINTS,
                                                value = 90,
                                                label = "Provider standings points",
                                            )
                                        ),
                                    )
                                ),
                            )
                        ),
                        observedAtEpochMillis = context.nowEpochMillis,
                    )
                )
            ),
        )
        val archive = service.refreshEditions(context)
        val result = service.loadEdition(archive.editions.single().id, context)

        assertNotNull(result)
        assertEquals(90, result.standings.single().entries.single().metrics.single().value)
        assertNull(result.championshipPoints)
        assertEquals(QualificationMechanism.UNKNOWN, result.qualification.mechanism)
        assertTrue(result.qualification.routes.isEmpty())
    }

    @Test
    fun firstPlaceInStandingsDoesNotAutoLockQualification() = runSuspend {
        val repository = MemoryEditionRepository()
        val service = service(
            repository = repository,
            editionSources = listOf(FakeEditionSource(listOf(providerEdition()))),
            standingsSources = listOf(
                FakeStandingsSource(
                    ProviderStandingsSnapshot(
                        sections = listOf(
                            ProviderStandingsSection(
                                stageName = "Playoffs",
                                sectionName = "Final",
                                rows = listOf(
                                    ProviderStandingRow(1, team("BLG", "Bilibili Gaming"), 8, 1)
                                ),
                            )
                        ),
                        observedAtEpochMillis = context.nowEpochMillis,
                    )
                )
            ),
        )
        val archive = service.refreshEditions(context)
        val result = service.loadEdition(archive.editions.single().id, context)

        assertNotNull(result)
        assertEquals(1, result.standings.single().entries.single().ordinal)
        assertEquals(QualificationEvidenceKind.PENDING, result.qualification.mechanismEvidence)
        assertEquals(QualificationMechanism.UNKNOWN, result.qualification.mechanism)
    }

    @Test
    fun providerQualificationKeepsEvidenceAndTypedPlacementSeparateFromPoints() = runSuspend {
        val repository = MemoryEditionRepository()
        val service = service(
            repository = repository,
            editionSources = listOf(FakeEditionSource(listOf(providerEdition()))),
            qualificationSources = listOf(
                FakeQualificationSource(
                    ProviderQualificationSnapshot(
                        title = "2026 LPL → Worlds",
                        targetEventName = "Worlds 2026",
                        mechanism = QualificationMechanism.DIRECT_PLACEMENT,
                        mechanismEvidence = QualificationEvidenceKind.PROVIDER,
                        routes = listOf(
                            ProviderQualificationRoute(
                                team = team("BLG", "Bilibili Gaming"),
                                targetEventName = "Worlds 2026",
                                status = QualificationTeamState.CONTENDING,
                                mechanism = QualificationMechanism.DIRECT_PLACEMENT,
                                evidence = QualificationEvidenceKind.PROVIDER,
                                inputs = listOf(ProviderQualificationInput.TournamentPlacement(1, "Split 3")),
                                nodes = listOf(
                                    ProviderQualificationNode(
                                        id = "final-placement",
                                        label = "联赛名次",
                                        detail = "按最终名次确认席位",
                                        state = QualificationNodeState.AVAILABLE,
                                        evidence = QualificationEvidenceKind.PROVIDER,
                                        sourceLabel = "provider",
                                    )
                                ),
                            )
                        ),
                        note = "Provider qualification route",
                        observedAtEpochMillis = context.nowEpochMillis,
                    )
                )
            ),
        )
        val archive = service.refreshEditions(context)
        val result = service.loadEdition(archive.editions.single().id, context)

        assertNotNull(result)
        val route = result.qualification.routes.single()
        assertEquals(QualificationEvidenceKind.PROVIDER, route.evidence)
        assertTrue(route.inputs.single() is QualificationInput.TournamentPlacement)
        assertTrue(route.inputs.none { it is QualificationInput.ChampionshipPoints })
    }

    private fun service(
        repository: MemoryEditionRepository,
        editionSources: List<TournamentEditionSourcePort> = emptyList(),
        standingsSources: List<TournamentStandingsSourcePort> = emptyList(),
        championshipPointsSources: List<ChampionshipPointsSourcePort> = emptyList(),
        qualificationSources: List<QualificationSourcePort> = emptyList(),
    ) = CompetitionStructureService(
        editionSources = editionSources,
        standingsSources = standingsSources,
        championshipPointsSources = championshipPointsSources,
        qualificationSources = qualificationSources,
        archiveRepository = repository,
    )

    private fun providerEdition(
        year: Int = 2026,
        slug: String = "split3",
        displayName: String = "2026 LPL 第三赛段",
    ) = ProviderEditionEntry(
        competitionSlug = "lpl",
        competitionName = "LPL",
        regionCode = "CN",
        regionName = "China",
        tournamentSlug = slug,
        family = "LPL",
        seasonYear = year,
        displayName = displayName,
        stageName = "Split 3",
        startEpochMillis = 1_700_000_000_000L,
        endEpochMillis = 1_900_000_000_000L,
        participantTeams = listOf(team("BLG", "Bilibili Gaming")),
        scheduleSeriesCount = 10,
    )

    private fun team(code: String, name: String) = ProviderTeamIdentity(
        slug = name.lowercase().replace(" ", "-"),
        code = code,
        name = name,
    )

    private fun edition(
        id: String,
        year: Int,
        slug: String,
        displayName: String,
        firstSeen: Long,
        lastSeen: Long,
        participants: Set<TeamId> = emptySet(),
        slots: List<TournamentEditionSlot> = emptyList(),
    ) = TournamentEdition(
        id = EditionId(id),
        competition = CompetitionRef(
            id = CompetitionId("lol:competition:lpl"),
            name = "LPL",
            region = RegionRef("CN", "China"),
        ),
        slug = slug,
        family = "LPL",
        seasonYear = year,
        displayName = displayName,
        stageName = "Split 3",
        startEpochMillis = 1_700_000_000_000L,
        endEpochMillis = 1_900_000_000_000L,
        participantTeamIds = participants,
        scheduleSeriesCount = 5,
        slots = slots,
        firstSeenEpochMillis = firstSeen,
        lastSeenEpochMillis = lastSeen,
        provenance = provenance(DataAuthority.VERIFIED_PROVIDER, lastSeen),
    )

    private fun provenance(authority: DataAuthority, observedAt: Long) = SourceProvenance(
        providerId = "test",
        sourceClass = SourceClass.PRE_MATCH_SOURCE,
        authority = authority,
        freshnessClass = FreshnessClass.DAILY,
        observedAtEpochMillis = observedAt,
    )

    private class MemoryEditionRepository(
        initial: List<TournamentEdition> = emptyList(),
    ) : TournamentEditionArchiveRepository {
        private var data = initial
        override suspend fun load(): List<TournamentEdition> = data
        override suspend fun save(editions: List<TournamentEdition>) { data = editions }
    }

    private class FakeEditionSource(
        private val rows: List<ProviderEditionEntry>,
    ) : TournamentEditionSourcePort {
        override val providerId = "fake-editions"
        override val authority = DataAuthority.VERIFIED_PROVIDER
        override suspend fun readEditions(context: SourceRequestContext): ProviderRead<ProviderEditionSnapshot> =
            ProviderRead.Success(
                ProviderEditionSnapshot(
                    editions = rows,
                    observedAtEpochMillis = context.nowEpochMillis,
                )
            )
    }

    private class FakeStandingsSource(
        private val snapshot: ProviderStandingsSnapshot,
    ) : TournamentStandingsSourcePort {
        override val providerId = "fake-standings"
        override val authority = DataAuthority.OFFICIAL
        override suspend fun readStandings(
            edition: TournamentEdition,
            context: SourceRequestContext,
        ): ProviderRead<ProviderStandingsSnapshot> = ProviderRead.Success(snapshot)
    }

    private class FakeQualificationSource(
        private val snapshot: ProviderQualificationSnapshot,
    ) : QualificationSourcePort {
        override val providerId = "fake-qualification"
        override val authority = DataAuthority.VERIFIED_PROVIDER
        override suspend fun readQualification(
            edition: TournamentEdition,
            context: SourceRequestContext,
        ): ProviderRead<ProviderQualificationSnapshot> = ProviderRead.Success(snapshot)
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result }
    })
    return outcome!!.getOrThrow()
}
