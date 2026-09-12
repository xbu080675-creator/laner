package com.laner.core.application

import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.MatchId
import com.laner.core.domain.RegionRef
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LiveTargetSelectorTest {
    private val now = 1_800_000_000_000L

    @Test
    fun eventLiveAlwaysBeatsCloserUpcomingSeries() {
        val live = series("live", ScheduleState.EVENT_LIVE, now - 30 * 60_000L)
        val upcoming = series("next", ScheduleState.UPCOMING, now + 60_000L)

        assertEquals(live.matchId, LiveTargetSelector.select(listOf(upcoming, live), now)?.matchId)
    }

    @Test
    fun nearestEventLiveIsSelectedWhenSeveralAreVisible() {
        val older = series("older", ScheduleState.EVENT_LIVE, now - 40 * 60_000L)
        val newer = series("newer", ScheduleState.EVENT_LIVE, now - 5 * 60_000L)

        assertEquals(newer.matchId, LiveTargetSelector.select(listOf(older, newer), now)?.matchId)
    }

    @Test
    fun completedSeriesIsNeverUsedAsFallbackTarget() {
        val completed = series("done", ScheduleState.COMPLETED, now - 30_000L)
        val upcoming = series("upcoming", ScheduleState.UPCOMING, now + 10 * 60_000L)

        assertEquals(upcoming.matchId, LiveTargetSelector.select(listOf(completed, upcoming), now)?.matchId)
    }

    @Test
    fun allCompletedReturnsNoTarget() {
        assertNull(
            LiveTargetSelector.select(
                listOf(
                    series("a", ScheduleState.COMPLETED, now - 60_000L),
                    series("b", ScheduleState.COMPLETED, now - 120_000L),
                ),
                now,
            )
        )
    }

    @Test
    fun negativeClockInputIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            LiveTargetSelector.select(emptyList(), -1L)
        }
    }

    private fun series(id: String, state: ScheduleState, start: Long): ScheduledSeries = ScheduledSeries(
        matchId = MatchId("match:$id"),
        competition = CompetitionRef(
            id = CompetitionId("competition:test"),
            name = "Test League",
            region = RegionRef("GLOBAL", "Global"),
        ),
        competitionSlug = "test-league",
        competitionKind = CompetitionKind.REGIONAL,
        blockName = "Test Block",
        startTimeEpochMillis = start,
        state = state,
        bestOf = 3,
        teams = listOf(
            ScheduledTeam(TeamRef(TeamId("team:$id:a"), "A", "Alpha")),
            ScheduledTeam(TeamRef(TeamId("team:$id:b"), "B", "Beta")),
        ),
        provenance = SourceProvenance(
            providerId = "test",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.OFFICIAL,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = start,
        ),
    )
}
