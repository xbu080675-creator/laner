package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.ScheduledSeries
import java.util.concurrent.CancellationException

sealed interface WatchHubContextResult {
    data class NoTarget(
        val schedule: GlobalScheduleSnapshot,
        val reason: LiveTargetUnavailableReason,
    ) : WatchHubContextResult

    data class Ready(
        val match: ScheduledSeries,
        val liveState: LiveStateResolution,
    ) : WatchHubContextResult

    data class Failed(
        val failure: DiagnosticFailure,
    ) : WatchHubContextResult
}

/**
 * Lightweight Application query for the global Watch Hub badge.
 *
 * The Watch Hub only needs the current series target and authoritative lifecycle. It deliberately
 * does not load gameplay snapshots, Timeline, Tactical events, replay, or platform launch state.
 * This keeps the legacy always-visible launcher reactive without restoring the legacy global Store
 * or making Presentation call Provider adapters directly.
 */
class WatchHubContextService(
    private val scheduleService: GlobalScheduleService,
    private val liveMatchStateService: LiveMatchStateService,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun load(context: SourceRequestContext): WatchHubContextResult {
        return try {
            val schedule = scheduleService.load(context)
            val target = LiveTargetSelector.select(
                matches = schedule.matches,
                nowEpochMillis = context.nowEpochMillis,
            ) ?: return WatchHubContextResult.NoTarget(
                schedule = schedule,
                reason = if (schedule.matches.isEmpty()) {
                    LiveTargetUnavailableReason.NO_MATCHES
                } else {
                    LiveTargetUnavailableReason.NO_ELIGIBLE_TARGET
                },
            )

            val liveState = liveMatchStateService.refresh(
                query = LiveMatchSourceQuery.from(target),
                context = context,
            )
            WatchHubContextResult.Ready(
                match = target,
                liveState = liveState,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = DiagnosticFailure(
                code = ErrorCode("LNR-APP-WATCH-001"),
                message = "Watch Hub context query failed",
                retryable = true,
                context = mapOf(
                    "correlation_id" to context.correlationId,
                    "error_type" to error::class.java.simpleName,
                ),
            )
            diagnostics?.emit(
                DiagnosticEvent(
                    module = "WATCH",
                    level = LogLevel.ERROR,
                    message = failure.message,
                    context = failure.context,
                    failure = failure,
                )
            )
            WatchHubContextResult.Failed(failure)
        }
    }
}
