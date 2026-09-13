package com.riftlab.app.data

import java.io.IOException
import java.time.Instant

/**
 * Riot LiveStats publishes frames on a 10-second time grid. Keep the provider cursor on that grid
 * and adapt the look-back distance instead of invalidating the current event/game binding after a
 * temporary empty window.
 *
 * This is an independent implementation of the observable Riot LiveStats protocol. No source code
 * from the GPL-3.0 reference application is copied into Laner.
 */
internal class RiotLiveStatsCursor {
    private var preferredLagSeconds = DEFAULT_LAG_SECONDS
    private var consecutiveMisses = 0
    private var successesSinceAdjustment = 0

    fun reset() {
        preferredLagSeconds = DEFAULT_LAG_SECONDS
        consecutiveMisses = 0
        successesSinceAdjustment = 0
    }

    fun candidates(now: Instant): List<String> {
        val lags = linkedSetOf(
            preferredLagSeconds,
            30L,
            60L,
            90L,
            120L,
            180L,
            300L,
            600L
        )
        return lags
            .filter { it >= MIN_LAG_SECONDS }
            .map { lag -> alignToTenSeconds(now.minusSeconds(lag)).toString() }
    }

    fun onSuccess() {
        consecutiveMisses = 0
        successesSinceAdjustment += 1
        if (successesSinceAdjustment >= SUCCESS_WINDOW_BEFORE_TIGHTENING) {
            preferredLagSeconds = (preferredLagSeconds - STEP_SECONDS).coerceAtLeast(MIN_LAG_SECONDS)
            successesSinceAdjustment = 0
        }
    }

    fun onMiss() {
        successesSinceAdjustment = 0
        consecutiveMisses += 1
        if (consecutiveMisses >= MISSES_BEFORE_BACKOFF) {
            preferredLagSeconds = (preferredLagSeconds + STEP_SECONDS).coerceAtMost(MAX_LAG_SECONDS)
            consecutiveMisses = 0
        }
    }

    internal fun preferredLagForDiagnostics(): Long = preferredLagSeconds

    companion object {
        private const val DEFAULT_LAG_SECONDS = 60L
        private const val MIN_LAG_SECONDS = 20L
        private const val MAX_LAG_SECONDS = 180L
        private const val STEP_SECONDS = 10L
        private const val SUCCESS_WINDOW_BEFORE_TIGHTENING = 10
        private const val MISSES_BEFORE_BACKOFF = 2

        internal fun alignToTenSeconds(value: Instant): Instant {
            val epoch = value.epochSecond
            val aligned = epoch - Math.floorMod(epoch, 10L)
            return Instant.ofEpochSecond(aligned)
        }
    }
}

internal class RiotLiveWindowNotReadyException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
