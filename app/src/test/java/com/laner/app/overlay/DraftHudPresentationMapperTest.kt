package com.laner.app.overlay

import com.laner.core.application.LiveStateLoadStatus
import com.laner.core.application.LiveStateResolution
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.DraftActionType
import com.laner.core.domain.DraftChangedEvent
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftHudPresentationMapperTest {
    private val matchId = MatchId("lol:series:test:draft")
    private val gameId = GameId("lol:game:test:draft:1")
    private val leftId = TeamId("lol:team:left")
    private val rightId = TeamId("lol:team:right")

    @Test
    fun nonDraftLifecycleDoesNotActivateHud() {
        val result = DraftHudPresentationMapper.from(
            match = series(),
            stateResolution = resolution(MatchLifecycleState.IN_GAME),
            timeline = null,
        )

        assertFalse(result.active)
    }

    @Test
    fun verifiedDraftEventsProjectWithoutInventingRoleMatchups() {
        val events = listOf(
            event(1, DraftActionType.PICK, leftId, "Ahri"),
            event(2, DraftActionType.LOCK, leftId, "Ahri"),
            event(3, DraftActionType.BAN, rightId, "Renekton"),
            event(4, DraftActionType.PICK, rightId, "Orianna"),
        )
        val result = DraftHudPresentationMapper.from(
            match = series(),
            stateResolution = resolution(MatchLifecycleState.DRAFT),
            timeline = GameTimeline(matchId, gameId, 1, events = events),
        )

        assertTrue(result.active)
        assertEquals(DraftHudSourceMode.VERIFIED, result.sourceMode)
        assertEquals(listOf("Ahri"), result.leftPicks)
        assertEquals(listOf("Orianna"), result.rightPicks)
        assertEquals(listOf("Renekton"), result.rightBans)
        assertEquals(null, result.matchup)
        assertEquals("riot-live", result.sourceLabel)
    }

    @Test
    fun undoRemovesOnlyExplicitChampionAndUnknownTeamDoesNotPolluteSides() {
        val unknown = TeamId("lol:team:unknown")
        val events = listOf(
            event(1, DraftActionType.PICK, leftId, "Ahri"),
            event(2, DraftActionType.PICK, unknown, "Garen"),
            event(3, DraftActionType.UNDO, leftId, "Ahri"),
        )
        val result = DraftHudPresentationMapper.from(
            match = series(),
            stateResolution = resolution(MatchLifecycleState.DRAFT),
            timeline = GameTimeline(matchId, gameId, 1, events = events),
        )

        assertTrue(result.leftPicks.isEmpty())
        assertTrue(result.rightPicks.isEmpty())
        assertFalse(result.leftPicks.contains("Garen"))
        assertFalse(result.rightPicks.contains("Garen"))
    }

    private fun resolution(lifecycle: MatchLifecycleState): LiveStateResolution = LiveStateResolution(
        state = LiveMatchState(
            matchId = matchId,
            lifecycle = lifecycle,
            currentGameId = gameId,
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 1000L,
            provenance = liveProvenance(),
        ),
        status = LiveStateLoadStatus.READY,
        selectedProviderId = "riot-live",
        failures = emptyList(),
        conflicts = emptyList(),
        appliedTransitions = emptyList(),
        stateEvents = emptyList(),
    )

    private fun event(sequence: Long, action: DraftActionType, teamId: TeamId, champion: String) = DraftChangedEvent(
        matchId = matchId,
        gameId = gameId,
        sequence = sequence,
        gameTimeSeconds = null,
        provenance = liveProvenance(),
        evidence = EventEvidence.PROVIDER_EXPLICIT,
        action = action,
        teamId = teamId,
        championId = champion,
    )

    private fun series(): ScheduledSeries = ScheduledSeries(
        matchId = matchId,
        competition = CompetitionRef(
            id = CompetitionId("lol:competition:test"),
            name = "Test League",
            region = null,
        ),
        competitionSlug = "test",
        competitionKind = CompetitionKind.REGIONAL,
        blockName = "Playoffs",
        startTimeEpochMillis = 1000L,
        state = ScheduleState.EVENT_LIVE,
        bestOf = 5,
        teams = listOf(
            ScheduledTeam(TeamRef(leftId, "LEFT", "Left Team")),
            ScheduledTeam(TeamRef(rightId, "RIGHT", "Right Team")),
        ),
        provenance = SourceProvenance(
            providerId = "schedule",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.OFFICIAL,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = 1000L,
        ),
    )

    private fun liveProvenance() = SourceProvenance(
        providerId = "riot-live",
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = DataAuthority.OFFICIAL,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = 1000L,
    )
}
