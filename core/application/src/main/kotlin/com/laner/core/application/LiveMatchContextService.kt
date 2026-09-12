package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.ScheduledSeries
import java.util.concurrent.CancellationException

enum class LiveTargetUnavailableReason {
    NO_MATCHES,
    NO_ELIGIBLE_TARGET,
}

sealed interface LiveMatchContextResult {
    data class NoTarget(
        val schedule: GlobalScheduleSnapshot,
        val reason: LiveTargetUnavailableReason,
    ) : LiveMatchContextResult

    data class Ready(
        val match: ScheduledSeries,
        val liveState: LiveStateResolution,
        val snapshot: LiveSnapshotResolution,
        val timeline: GameTimeline?,
    ) : LiveMatchContextResult

    data class Failed(
        val failure: DiagnosticFailure,
    ) : LiveMatchContextResult
}

/**
 * Single Application query for the current LIVE presentation context.
 *
 * Presentation surfaces must not duplicate schedule target selection, lifecycle refresh, gameplay
 * snapshot refresh, game identity selection, or Timeline loading. This service owns that ordering
 * while keeping lifecycle authority in [LiveMatchStateService] and gameplay validation in
 * [LiveSnapshotService].
 */
class LiveMatchContextService(
    private val scheduleService: GlobalScheduleService,
    private val liveMatchStateService: LiveMatchStateService,
    private val liveSnapshotService: LiveSnapshotService,
    private val liveTimelineService: LiveTimelineService,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun load(context: SourceRequestContext): LiveMatchContextResult {
        return try {
            val schedule = scheduleService.load(context)
            val target = LiveTargetSelector.select(
                matches = schedule.matches,
                nowEpochMillis = context.nowEpochMillis,
            ) ?: return LiveMatchContextResult.NoTarget(
                schedule = schedule,
                reason = if (schedule.matches.isEmpty()) {
                    LiveTargetUnavailableReason.NO_MATCHES
                } else {
                    LiveTargetUnavailableReason.NO_ELIGIBLE_TARGET
                },
            )

            val query = LiveMatchSourceQuery.from(target)
            val liveState = liveMatchStateService.refresh(query = query, context = context)
            val snapshot = liveSnapshotService.refresh(query = query, context = context)
            val gameId = snapshot.snapshot?.game?.gameId ?: liveState.state.currentGameId
            val timeline = gameId?.let { liveTimelineService.load(it) }

            LiveMatchContextResult.Ready(
                match = target,
                liveState = liveState,
                snapshot = snapshot,
                timeline = timeline,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = DiagnosticFailure(
                code = ErrorCode("LNR-APP-LIVE-003"),
                message = "Current LIVE context query failed",
                retryable = true,
                context = mapOf(
                    "correlation_id" to context.correlationId,
                    "error_type" to error::class.java.simpleName,
                ),
            )
            diagnostics?.emit(
                DiagnosticEvent(
                    module = "LIVE",
                    level = LogLevel.ERROR,
                    message = failure.message,
                    context = failure.context,
                    failure = failure,
                )
            )
            LiveMatchContextResult.Failed(failure)
        }
    }
}
