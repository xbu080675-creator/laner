package com.laner.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameIdentityTest {
    @Test
    fun sameMatchAndGameNumberAlwaysProduceSameCanonicalId() {
        val matchId = MatchId("lol:series:lpl:2026:blg-al")

        assertEquals(
            GameId("lol:series:lpl:2026:blg-al:game:2"),
            GameIdentity.canonical(matchId, 2),
        )
    }

    @Test
    fun providerIdsCannotInfluenceCanonicalGameId() {
        val matchId = MatchId("lol:series:worlds:2026:t1-blg")

        val first = GameIdentity.canonical(matchId, 1)
        val second = GameIdentity.canonical(matchId, 1)

        assertEquals(first, second)
    }

    @Test
    fun nonPositiveGameNumberIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            GameIdentity.canonical(MatchId("lol:series:test"), 0)
        }
    }
}
