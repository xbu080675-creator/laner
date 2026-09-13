package com.laner.app.overlay

import com.laner.core.application.LiveSnapshotLoadStatus
import com.laner.core.application.LiveSnapshotResolution
import com.laner.core.application.LiveStateLoadStatus
import com.laner.core.application.LiveStateResolution
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import com.laner.core.domain.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiftScreenPresentationMapperTest {
    private val matchId = MatchId("lol:series:test:riftscreen")
    private val gameId = GameId("lol:game:test:riftscreen:1")
    private val leftId = TeamId("lol:team:left")
    private val rightId = TeamId("lol:team:right")

    @Test
    fun waitingKeepsLegacyHudPlaceholders() {
        val result = RiftScreenPresentationMapper.waiting("等待实时赛事")

        assertEquals("等待比赛", result.title)
        assertEquals("--:--", result.timer)
        assertEquals("VS", result.center)
        assertEquals("K —:— · T —:— · D —:—", result.metrics)
        assertEquals("GOLD — : — · LEAD —", result.details)
    }

    @Test
    fun blueLeadUsesSignedLegacyCenterInsteadOfTeamPrefixedRedesign() {
        val result = RiftScreenPresentationMapper.from(
            match = series(),
            stateResolution = state(),
            snapshotResolution = snapshotResolution(blueGold = 20_000, redGold = 19_000),
            timeline = null,
        )

        assertEquals("+1.0K", result.center)
        assertEquals("GOLD 20.0K : 19.0K · LEAD +1.0K", result.details)
        assertFalse(result.center.contains("LEFT"))
        assertTrue(result.metrics.contains("K 4:3"))
    }

    @Test
    fun redLeadKeepsBluePerspectiveNegativeDiff() {
        val result = RiftScreenPresentationMapper.from(
            match = series(),
            stateResolution = state(),
            snapshotResolution = snapshotResolution(blueGold = 18_500, redGold = 20_000),
            timeline = null,
        )

        assertEquals("-1.5K", result.center)
        assertEquals("GOLD 18.5K : 20.0K · LEAD -1.5K", result.details)
    }

    private fun state(): LiveStateResolution = LiveStateResolution(
        state = LiveMatchState(
            matchId = matchId,
            lifecycle = MatchLifecycleState.IN_GAME,
            currentGameId = gameId,
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 1_000L,
            provenance = provenance(),
        ),
        status = LiveStateLoadStatus.READY,
        selectedProviderId = "riot-live",
        failures = emptyList(),
        conflicts = emptyList(),
        appliedTransitions = emptyList(),
        stateEvents = emptyList(),
    )

    private fun snapshotResolution(blueGold: Int, redGold: Int): LiveSnapshotResolution = LiveSnapshotResolution(
        snapshot = LiveGameSnapshot(
            game = GameContext(gameId, matchId, 1, leftId, rightId),
            lifecycle = MatchLifecycleState.IN_GAME,
            elapsedSeconds = 605,
            blue = TeamLiveState(leftId, gold = blueGold, kills = 4, towers = 2, dragons = 1, barons = 0),
            red = TeamLiveState(rightId, gold = redGold, kills = 3, towers = 1, dragons = 0, barons = 0),
        ),
        provenance = provenance(),
        timelineResult = null,
        status = LiveSnapshotLoadStatus.READY,
        selectedProviderId = "riot-live",
        failures = emptyList(),
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
        startTimeEpochMillis = 1_000L,
        state = ScheduleState.EVENT_LIVE,
        bestOf = 5,
        teams = listOf(
            ScheduledTeam(TeamRef(leftId, "LEFT", "Left Team")),
            ScheduledTeam(TeamRef(rightId, "RIGHT", "Right Team")),
        ),
        provenance = provenance(SourceClass.PRE_MATCH_SOURCE),
    )

    private fun provenance(sourceClass: SourceClass = SourceClass.LIVE_MATCH_SOURCE): SourceProvenance = SourceProvenance(
        providerId = if (sourceClass == SourceClass.LIVE_MATCH_SOURCE) "riot-live" else "schedule",
        sourceClass = sourceClass,
        authority = DataAuthority.OFFICIAL,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = 1_000L,
    )
}
