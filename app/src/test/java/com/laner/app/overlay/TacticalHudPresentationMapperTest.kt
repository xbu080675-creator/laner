package com.laner.app.overlay

import com.laner.core.application.LiveSnapshotLoadStatus
import com.laner.core.application.LiveSnapshotResolution
import com.laner.core.application.LiveStateLoadStatus
import com.laner.core.application.LiveStateResolution
import com.laner.core.domain.CompetitionId
import com.laner.core.domain.CompetitionKind
import com.laner.core.domain.CompetitionRef
import com.laner.core.domain.DataAuthority
import com.laner.core.domain.EventEvidence
import com.laner.core.domain.FreshnessClass
import com.laner.core.domain.GameContext
import com.laner.core.domain.GameId
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.KillEvent
import com.laner.core.domain.LiveGameSnapshot
import com.laner.core.domain.LiveMatchState
import com.laner.core.domain.MatchId
import com.laner.core.domain.MatchLifecycleState
import com.laner.core.domain.MultiKillWindowEvent
import com.laner.core.domain.ObjectiveTakenEvent
import com.laner.core.domain.ObjectiveType
import com.laner.core.domain.PlayerId
import com.laner.core.domain.PlayerLiveState
import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import com.laner.core.domain.ScheduledTeam
import com.laner.core.domain.SourceClass
import com.laner.core.domain.SourceProvenance
import com.laner.core.domain.TeamFightWindowEvent
import com.laner.core.domain.TeamId
import com.laner.core.domain.TeamLiveState
import com.laner.core.domain.TeamRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalHudPresentationMapperTest {
    private val matchId = MatchId("lol:series:test:tactical")
    private val gameId = GameId("lol:game:test:tactical:1")
    private val leftId = TeamId("lol:team:left")
    private val rightId = TeamId("lol:team:right")
    private val playerId = PlayerId("lol:player:carry")

    @Test
    fun outsideInGameNeverActivatesTacticalHud() {
        val result = TacticalHudPresentationMapper.from(
            match = series(),
            stateResolution = state(MatchLifecycleState.BETWEEN_GAMES),
            snapshotResolution = snapshotResolution(snapshot(500)),
            timeline = timeline(
                TeamFightWindowEvent(
                    matchId, gameId, 1, 500, derivedProvenance(),
                    blueKillDelta = 2, redKillDelta = 1, windowSeconds = 10,
                )
            ),
        )

        assertFalse(result.active)
    }

    @Test
    fun expiredEventDoesNotBecomePermanentScoreboardOverlay() {
        val result = TacticalHudPresentationMapper.from(
            match = series(),
            stateResolution = state(MatchLifecycleState.IN_GAME),
            snapshotResolution = snapshotResolution(snapshot(700)),
            timeline = timeline(
                KillEvent(
                    matchId = matchId,
                    gameId = gameId,
                    sequence = 1,
                    gameTimeSeconds = 650,
                    provenance = derivedProvenance(),
                    evidence = EventEvidence.VERIFIED_DELTA,
                    killerId = null,
                    victimId = null,
                    teamId = leftId,
                    count = 1,
                    observedWindowSeconds = 10,
                )
            ),
        )

        assertFalse(result.active)
    }

    @Test
    fun verifiedPresentationExpiresByWallClockEvenWhenGameClockStops() {
        val result = TacticalHudPresentationMapper.from(
            match = series(),
            stateResolution = state(MatchLifecycleState.IN_GAME),
            snapshotResolution = snapshotResolution(snapshot(1_005)),
            timeline = timeline(
                KillEvent(
                    matchId = matchId,
                    gameId = gameId,
                    sequence = 1,
                    gameTimeSeconds = 1_000,
                    provenance = derivedProvenance(),
                    evidence = EventEvidence.VERIFIED_DELTA,
                    killerId = null,
                    victimId = null,
                    teamId = leftId,
                    count = 1,
                    observedWindowSeconds = 10,
                )
            ),
        )

        assertTrue(result.active)
        assertEquals(31_000L, result.validUntilEpochMillis)
        assertTrue(result.isDisplayableAt(31_000L))
        assertFalse(result.isDisplayableAt(31_001L))
    }

    @Test
    fun localPreviewDoesNotPretendToUseProviderFreshness() {
        val preview = TacticalHudPresentation(
            active = true,
            sourceMode = TacticalHudSourceMode.PREVIEW,
            phase = TacticalHudPhase.GLOBAL,
            clock = "00:00",
            matchLabel = "PREVIEW",
            headline = "preview",
            explanation = "not fact",
            evidence = emptyList(),
            players = emptyList(),
            sourceLabel = "LOCAL PREVIEW · NOT FACT",
        )

        assertTrue(preview.isDisplayableAt(Long.MAX_VALUE))
    }

    @Test
    fun teamFightWinsSameSecondPriorityOverAggregateKill() {
        val events = listOf(
            KillEvent(
                matchId = matchId,
                gameId = gameId,
                sequence = 1,
                gameTimeSeconds = 800,
                provenance = derivedProvenance(),
                evidence = EventEvidence.VERIFIED_DELTA,
                killerId = null,
                victimId = null,
                teamId = leftId,
                count = 3,
                observedWindowSeconds = 12,
            ),
            TeamFightWindowEvent(
                matchId = matchId,
                gameId = gameId,
                sequence = 2,
                gameTimeSeconds = 800,
                provenance = derivedProvenance(),
                blueKillDelta = 3,
                redKillDelta = 1,
                windowSeconds = 12,
            ),
        )
        val result = TacticalHudPresentationMapper.from(
            match = series(),
            stateResolution = state(MatchLifecycleState.IN_GAME),
            snapshotResolution = snapshotResolution(snapshot(805)),
            timeline = timeline(*events.toTypedArray()),
        )

        assertTrue(result.active)
        assertEquals(TacticalHudPhase.FIGHT, result.phase)
        assertTrue(result.headline.contains("团战窗口"))
        assertTrue(result.explanation.contains("不声称官方团战分类"))
    }

    @Test
    fun multiKillWindowNeverClaimsOfficialDoubleTripleClassification() {
        val result = TacticalHudPresentationMapper.from(
            match = series(),
            stateResolution = state(MatchLifecycleState.IN_GAME),
            snapshotResolution = snapshotResolution(snapshot(905, playerKills = 3)),
            timeline = timeline(
                MultiKillWindowEvent(
                    matchId = matchId,
                    gameId = gameId,
                    sequence = 1,
                    gameTimeSeconds = 900,
                    provenance = derivedProvenance(),
                    playerId = playerId,
                    teamId = leftId,
                    killCount = 3,
                    windowSeconds = 15,
                )
            ),
        )

        assertTrue(result.active)
        assertTrue(result.headline.contains("采样窗口多杀"))
        assertTrue(result.explanation.contains("不等同官方"))
        assertFalse(result.headline.contains("Triple Kill"))
        assertEquals("Syndra", result.players.single().champion)
    }

    @Test
    fun dragonDeltaExplicitlyKeepsSubtypeUnknown() {
        val result = TacticalHudPresentationMapper.from(
            match = series(),
            stateResolution = state(MatchLifecycleState.IN_GAME),
            snapshotResolution = snapshotResolution(snapshot(1_005)),
            timeline = timeline(
                ObjectiveTakenEvent(
                    matchId = matchId,
                    gameId = gameId,
                    sequence = 1,
                    gameTimeSeconds = 1_000,
                    provenance = derivedProvenance(),
                    evidence = EventEvidence.VERIFIED_DELTA,
                    teamId = rightId,
                    objective = ObjectiveType.DRAGON,
                    detail = "龙种未知；仅由总数差分确认",
                    count = 1,
                    observedWindowSeconds = 10,
                )
            ),
        )

        assertTrue(result.active)
        assertTrue(result.explanation.contains("不推断龙种、龙魂或远古龙"))
    }

    private fun timeline(vararg events: com.laner.core.domain.MatchEvent): GameTimeline = GameTimeline(
        matchId = matchId,
        gameId = gameId,
        gameNumber = 1,
        events = events.toList(),
    )

    private fun state(lifecycle: MatchLifecycleState): LiveStateResolution = LiveStateResolution(
        state = LiveMatchState(
            matchId = matchId,
            lifecycle = lifecycle,
            currentGameId = gameId,
            currentGameNumber = 1,
            lastObservedAtEpochMillis = 1_000L,
            provenance = liveProvenance(),
        ),
        status = LiveStateLoadStatus.READY,
        selectedProviderId = "riot-live",
        failures = emptyList(),
        conflicts = emptyList(),
        appliedTransitions = emptyList(),
        stateEvents = emptyList(),
    )

    private fun snapshotResolution(value: LiveGameSnapshot): LiveSnapshotResolution = LiveSnapshotResolution(
        snapshot = value,
        provenance = liveProvenance(),
        timelineResult = null,
        status = LiveSnapshotLoadStatus.READY,
        selectedProviderId = "riot-live",
        failures = emptyList(),
    )

    private fun snapshot(seconds: Int, playerKills: Int? = null): LiveGameSnapshot = LiveGameSnapshot(
        game = GameContext(gameId, matchId, 1, leftId, rightId),
        lifecycle = MatchLifecycleState.IN_GAME,
        elapsedSeconds = seconds,
        blue = TeamLiveState(leftId, gold = 20_000, kills = 4),
        red = TeamLiveState(rightId, gold = 19_000, kills = 3),
        players = if (playerKills == null) emptyList() else listOf(
            PlayerLiveState(
                playerId = playerId,
                teamId = leftId,
                level = 11,
                kills = playerKills,
                deaths = 1,
                assists = 3,
                creepScore = 176,
                championId = "Syndra",
            )
        ),
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
        provenance = SourceProvenance(
            providerId = "schedule",
            sourceClass = SourceClass.PRE_MATCH_SOURCE,
            authority = DataAuthority.OFFICIAL,
            freshnessClass = FreshnessClass.MINUTES,
            observedAtEpochMillis = 1_000L,
        ),
    )

    private fun liveProvenance(): SourceProvenance = SourceProvenance(
        providerId = "riot-live",
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = DataAuthority.OFFICIAL,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = 1_000L,
    )

    private fun derivedProvenance(): SourceProvenance = SourceProvenance(
        providerId = "laner-live-event-derivation",
        sourceClass = SourceClass.LIVE_MATCH_SOURCE,
        authority = DataAuthority.DERIVED,
        freshnessClass = FreshnessClass.REALTIME,
        observedAtEpochMillis = 1_000L,
    )
}
