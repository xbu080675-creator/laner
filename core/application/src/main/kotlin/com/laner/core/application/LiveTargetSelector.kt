package com.laner.core.application

import com.laner.core.domain.ScheduleState
import com.laner.core.domain.ScheduledSeries
import kotlin.math.abs

/**
 * Single Application-level rule for choosing the series that LIVE surfaces should follow.
 *
 * Presentation and Android platform code must not reimplement this selection policy. An
 * EVENT_LIVE schedule entry is preferred, otherwise the nearest non-completed series is used as a
 * discovery target. This only selects a match target; it never claims that an in-game session has
 * started. [LiveMatchStateService] remains the sole lifecycle authority.
 */
object LiveTargetSelector {
    fun select(
        matches: List<ScheduledSeries>,
        nowEpochMillis: Long,
    ): ScheduledSeries? {
        require(nowEpochMillis >= 0) { "nowEpochMillis must be non-negative" }

        val live = matches
            .asSequence()
            .filter { it.state == ScheduleState.EVENT_LIVE }
            .minByOrNull { distance(nowEpochMillis, it.startTimeEpochMillis) }
        if (live != null) return live

        return matches
            .asSequence()
            .filter { it.state != ScheduleState.COMPLETED }
            .minByOrNull { distance(nowEpochMillis, it.startTimeEpochMillis) }
    }

    private fun distance(now: Long, start: Long): Long {
        val delta = now - start
        return if (delta == Long.MIN_VALUE) Long.MAX_VALUE else abs(delta)
    }
}
