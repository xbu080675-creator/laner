package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class TimelineSourceClassTest {
    private val matchId = MatchId("lol:series:test")
    private val gameId = GameIdentity.canonical(matchId, 1)
    private val blue = TeamId("lol:team:blue")
    private val red = TeamId("lol:team:red")
    private val snapshot = LiveGameSnapshot(
        game = GameContext(gameId, matchId, 1, blue, red),
        lifecycle = MatchLifecycleState.IN_GAME,
        elapsedSeconds = 100,
        blue = TeamLiveState(blue),
        red = TeamLiveState(red),
    )

    @Test
    fun timelineAcceptsLiveAndPostFactSources() {
        TimelineSnapshotPoint(100, snapshot, provenance(SourceClass.LIVE_MATCH_SOURCE))
        TimelineSnapshotPoint(100, snapshot, provenance(SourceClass.POST_MATCH_SOURCE))
    }

    @Test
    fun timelineRejectsPreAndAiSources() {
        assertFailsWith<IllegalArgumentException> {
            TimelineSnapshotPoint(100, snapshot, provenance(SourceClass.PRE_MATCH_SOURCE))
        }
        assertFailsWith<IllegalArgumentException> {
            TimelineSnapshotPoint(100, snapshot, provenance(SourceClass.GLOBAL_AI_ASSIST))
        }
    }

    private fun provenance(sourceClass: SourceClass) = SourceProvenance(
        providerId = "test",
        sourceClass = sourceClass,
        authority = DataAuthority.OFFICIAL,
        freshnessClass = FreshnessClass.STATIC,
        observedAtEpochMillis = 1_000L,
    )
}
