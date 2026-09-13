package com.laner.app.ui

import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
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

class WatchHubPresentationMapperTest {
    @Test
    fun inGameShowsLiveAndMatchup() {
        val presentation = WatchHubPresentationMapper.from(
            match = series(ScheduleState.EVENT_LIVE),
            lifecycle = MatchLifecycleState.IN_GAME,
        )

        assertTrue(presentation.gameLive)
        assertTrue(presentation.eventActive)
        assertEquals("BLG VS AL", presentation.matchup)
    }

    @Test
    fun betweenGamesShowsOnAirButNeverFakeLive() {
        val presentation = WatchHubPresentationMapper.from(
            match = series(ScheduleState.EVENT_LIVE),
            lifecycle = MatchLifecycleState.BETWEEN_GAMES,
        )

        assertFalse(presentation.gameLive)
        assertTrue(presentation.eventActive)
    }

    @Test
    fun scheduleEventLiveIsOnlyEventEvidenceWhenLifecycleUnknown() {
        val presentation = WatchHubPresentationMapper.from(
            match = series(ScheduleState.EVENT_LIVE),
            lifecycle = MatchLifecycleState.UNKNOWN,
        )

        assertFalse(presentation.gameLive)
        assertTrue(presentation.eventActive)
    }

    @Test
    fun upcomingMatchDoesNotFabricateOnAirState() {
        val presentation = WatchHubPresentationMapper.from(
            match = series(ScheduleState.UPCOMING),
            lifecycle = MatchLifecycleState.PRE_EVENT,
        )

        assertFalse(presentation.gameLive)
        assertFalse(presentation.eventActive)
    }

    @Test
    fun missingMatchIsNeutral() {
        assertEquals(
            WatchHubPresentation.Idle,
            WatchHubPresentationMapper.from(null, MatchLifecycleState.IN_GAME),
        )
    }

    private fun series(state: ScheduleState): ScheduledSeries = ScheduledSeries(
        matchId = MatchId("lol:series:test:watch"),
        competition = CompetitionRef(
            id = CompetitionId("lol:competition:test"),
            name = "Test League",
            region = null,
        ),
        competitionSlug = "test",
        competitionKind = CompetitionKind.REGIONAL,
        blockName = "Playoffs",
        startTimeEpochMillis = 1_000L,
        state = state,
        bestOf = 5,
        teams = listOf(
            ScheduledTeam(TeamRef(TeamId("lol:team:blg"), "BLG", "Bilibili Gaming")),
            ScheduledTeam(TeamRef(TeamId("lol:team:al"), "AL", "Anyone's Legend")),
        ),
        provenance = SourceProvenance(
            providerId = "fixture",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.VERIFIED_PROVIDER,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = 1_000L,
            sourceTimestampEpochMillis = 1_000L,
            revision = 1,
        ),
    )
}
