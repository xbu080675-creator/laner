package com.laner.app.ui

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

class PostMatchScreenTest {
    @Test
    fun closestCompletedSeriesIsSelected() {
        val now = 1_000_000L
        val older = series("older", now - 100_000L, ScheduleState.COMPLETED)
        val recent = series("recent", now - 10_000L, ScheduleState.COMPLETED)
        val upcoming = series("upcoming", now + 1_000L, ScheduleState.UPCOMING)

        val selected = selectPostTarget(listOf(older, upcoming, recent), now)

        assertEquals(recent.matchId, selected?.matchId)
    }

    @Test
    fun liveAndUpcomingSeriesCanNeverBecomePostTarget() {
        val now = 2_000_000L

        val selected = selectPostTarget(
            listOf(
                series("live", now - 10_000L, ScheduleState.EVENT_LIVE),
                series("upcoming", now + 10_000L, ScheduleState.UPCOMING),
            ),
            now,
        )

        assertNull(selected)
    }

    @Test
    fun completedSeriesMayBeInFutureOnlyByBadScheduleButStillUsesCompletedFact() {
        val now = 3_000_000L
        val completed = series("completed", now + 1_000L, ScheduleState.COMPLETED)

        assertEquals(completed.matchId, selectPostTarget(listOf(completed), now)?.matchId)
    }

    private fun series(id: String, start: Long, state: ScheduleState): ScheduledSeries = ScheduledSeries(
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
            ScheduledTeam(TeamRef(TeamId("lol:team:blue-$id"), "BLUE", "Blue $id")),
            ScheduledTeam(TeamRef(TeamId("lol:team:red-$id"), "RED", "Red $id")),
        ),
        provenance = SourceProvenance(
            providerId = "fixture",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.VERIFIED_PROVIDER,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = start,
            sourceTimestampEpochMillis = start,
            revision = 1,
        ),
    )
}
