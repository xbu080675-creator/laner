package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchLifecycleTest {
    @Test
    fun `pre event maps to pre match`() {
        assertEquals(MatchPhase.PRE_MATCH, MatchLifecycleState.PRE_EVENT.phaseOrNull())
    }

    @Test
    fun `event live before game is still live match`() {
        assertEquals(MatchPhase.LIVE_MATCH, MatchLifecycleState.EVENT_LIVE_PRE_GAME.phaseOrNull())
    }

    @Test
    fun `draft loading game post game and between games remain live match`() {
        val states = listOf(
            MatchLifecycleState.DRAFT,
            MatchLifecycleState.LOADING,
            MatchLifecycleState.IN_GAME,
            MatchLifecycleState.POST_GAME,
            MatchLifecycleState.BETWEEN_GAMES,
        )

        states.forEach { assertEquals(MatchPhase.LIVE_MATCH, it.phaseOrNull()) }
    }

    @Test
    fun `series complete maps to post match`() {
        assertEquals(MatchPhase.POST_MATCH, MatchLifecycleState.SERIES_COMPLETE.phaseOrNull())
    }

    @Test
    fun `unknown state is not guessed by core`() {
        assertNull(MatchLifecycleState.UNKNOWN.phaseOrNull())
    }
}
