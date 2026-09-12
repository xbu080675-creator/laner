package com.laner.app.ui

import com.laner.core.application.LiveTargetSelector
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression coverage for the Application LIVE target policy consumed by LiveMatchScreen. */
class LiveMatchScreenTest {
    @Test
    fun eventLiveAlwaysBeatsCloserUpcomingMatch() {
        val now = 1_000_000L
        val upcoming = series(
            id = "upcoming",
            start = now + 1_000L,
            state = ScheduleState.UPCOMING,
        )
        val live = series(
            id = "live",
            start = now - 30_000L,
            state = ScheduleState.EVENT_LIVE,
        )

        val selected = LiveTargetSelector.select(listOf(upcoming, live), now)

        assertEquals(live.matchId, selected?.matchId)
    }

    @Test
    fun completedMatchIsNeverSelectedAsFallback() {
        val now = 2_000_000L
        val completed = series(
            id = "completed",
            start = now - 100L,
            state = ScheduleState.COMPLETED,
        )
        val upcoming = series(
            id = "upcoming",
            start = now + 50_000L,
            state = ScheduleState.UPCOMING,
        )

        val selected = LiveTargetSelector.select(listOf(completed, upcoming), now)

        assertEquals(upcoming.matchId, selected?.matchId)
    }

    @Test
    fun onlyCompletedMatchesProduceNoLiveTarget() {
        val now = 3_000_000L

        val selected = LiveTargetSelector.select(
            matches = listOf(
                series("done-a", now - 10_000L, ScheduleState.COMPLETED),
                series("done-b", now - 5_000L, ScheduleState.COMPLETED),
            ),
            nowEpochMillis = now,
        )

        assertNull(selected)
    }

    private fun series(
        id: String,
        start: Long,
        state: ScheduleState,
    ): ScheduledSeries = ScheduledSeries(
        matchId = MatchId("lol:series:test:$id"),
        competition = CompetitionRef(
            id = CompetitionId("lol:competition:test"),
            name = "Test League",
            region = null,
        ),
        competitionSlug = "test",
        competitionKind = CompetitionKind.REGIONAL,
        blockName = "Regular",
        startTimeEpochMillis = start,
        state = state,
        bestOf = 3,
        teams = listOf(
            ScheduledTeam(
                team = TeamRef(
                    id = TeamId("lol:team:blue-$id"),
                    code = "BLUE",
                    name = "Blue $id",
                )
            ),
            ScheduledTeam(
                team = TeamRef(
                    id = TeamId("lol:team:red-$id"),
                    code = "RED",
                    name = "Red $id",
                )
            ),
        ),
        provenance = SourceProvenance(
            providerId = "fixture",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.VERIFIED_PROVIDER,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = start,
            sourceTimestampEpochMillis = start,
            revision = 1,
            sourceUri = null,
        ),
    )
}
