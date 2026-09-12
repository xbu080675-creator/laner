package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StandingsQualificationTest {
    private val competition = CompetitionRef(
        id = CompetitionId("lol:competition:lpl"),
        name = "LPL",
        region = RegionRef("CN", "China"),
    )
    private val teamA = TeamRef(TeamId("lol:team:a"), "A", "Team A")
    private val teamB = TeamRef(TeamId("lol:team:b"), "B", "Team B")
    private val provenance = SourceProvenance(
        providerId = "test",
        sourceClass = SourceClass.PRE_MATCH_SOURCE,
        authority = DataAuthority.VERIFIED_PROVIDER,
        freshnessClass = FreshnessClass.DAILY,
        observedAtEpochMillis = 1000L,
    )

    @Test
    fun tiedStandingsOrdinalsAreValid() {
        val snapshot = TournamentStandingsSnapshot(
            editionId = EditionId("lol:edition:lpl:2026:split3"),
            competition = competition,
            stageId = null,
            stageName = "Split 3",
            sectionName = "Group",
            entries = listOf(
                StandingEntry(1, teamA, 8, 1),
                StandingEntry(1, teamB, 8, 1),
            ),
            provenance = provenance,
        )
        assertEquals(listOf(1, 1), snapshot.entries.map { it.ordinal })
    }

    @Test
    fun unknownQualificationMechanismCannotPublishChampionshipPointInput() {
        assertFailsWith<IllegalArgumentException> {
            TeamQualificationRoute(
                team = teamA,
                targetEventName = "Worlds 2026",
                status = QualificationTeamState.PENDING,
                mechanism = QualificationMechanism.UNKNOWN,
                evidence = QualificationEvidenceKind.PENDING,
                inputs = listOf(QualificationInput.ChampionshipPoints(90, "Split 2")),
            )
        }
    }

    @Test
    fun participantOriginCannotInventChampionshipPoints() {
        assertFailsWith<IllegalArgumentException> {
            TeamQualificationRoute(
                team = teamA,
                targetEventName = "MSI 2026",
                status = QualificationTeamState.LOCKED,
                mechanism = QualificationMechanism.PARTICIPANT_ORIGIN,
                evidence = QualificationEvidenceKind.PROVIDER,
                inputs = listOf(QualificationInput.ChampionshipPoints(50, "Spring")),
                provenance = provenance,
            )
        }
    }

    @Test
    fun nonPendingQualificationRequiresProvenance() {
        assertFailsWith<IllegalArgumentException> {
            TeamQualificationRoute(
                team = teamA,
                targetEventName = "Worlds 2026",
                status = QualificationTeamState.CONTENDING,
                mechanism = QualificationMechanism.DIRECT_PLACEMENT,
                evidence = QualificationEvidenceKind.DERIVED,
            )
        }
    }
}
