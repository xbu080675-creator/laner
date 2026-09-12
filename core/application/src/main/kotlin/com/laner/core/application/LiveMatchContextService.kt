package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import com.laner.core.domain.GameTimeline
import com.laner.core.domain.ScheduledSeries
import java.util.concurrent.CancellationException

enum class LiveTargetUnavailableReason {
    EMPTY_SCHEDULE,
    NO_ELIGIBLE_TARGET,
}

sealed interface LiveMatchContextResult {
    data class Ready(
        val match: ScheduledSeries,
        val schedule: GlobalScheduleSnapshot,
        val stateResolution: LiveStateResolution,
        val snapshotResolution: LiveSnapshotResolution,
        val timeline: GameTimeline?,
    ) : LiveMatchContextResult

    data class NoTarget(
        val schedule: GlobalScheduleSnapshot,
        val reason: LiveTargetUnavailableReason,
    ) : LiveMatchContextResult

    data class Failed(
        val failure: DiagnosticFailure,
    ) : LiveMatchContextResult
}

/**
 * Single Application use case for assembling the current LIVE context consumed by all
 * presentation surfaces (Compose, RiftScreen, future Tactical HUD).
 *
 * Presentation must not reimplement target selection, source refresh ordering, game identity
 * selection, or timeline lookup. Those rules live here and in the lower Application services.
 */
class LiveMatchContextService(
    private val scheduleService: GlobalScheduleService,
    private val liveMatchStateService: LiveMatchStateService,
    private val liveSnapshotService: LiveSnapshotService,
    private val liveTimelineService: LiveTimelineService,
    private val diagnostics: DiagnosticsPort? = null,
) {
    suspend fun refresh(context: SourceRequestContext): LiveMatchContextResult {
        return try {
            val schedule = scheduleService.load(context)
            val target = LiveTargetSelector.select(schedule.matches, context.nowEpochMillis)
                ?: return LiveMatchContextResult.NoTarget(
                    schedule = schedule,
                    reason = if (schedule.matches.isEmpty()) {
                        LiveTargetUnavailableReason.EMPTY_SCHEDULE
                    } else {
                        LiveTargetUnavailableReason.NO_ELIGIBLE_TARGET
                    },
                )

            val query = LiveMatchSourceQuery.from(target)
            val stateResolution = liveMatchStateService.refresh(query = query, context = context)
            val snapshotResolution = liveSnapshotService.refresh(query = query, context = context)
            val gameId = snapshotResolution.snapshot?.game?.gameId ?: stateResolution.state.currentGameId

            LiveMatchContextResult.Ready(
                match = target,
                schedule = schedule,
                stateResolution = stateResolution,
                snapshotResolution = snapshotResolution,
                timeline = gameId?.let { liveTimelineService.load(it) },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = DiagnosticFailure(
                code = ErrorCode("LNR-APP-LIVE-003"),
                message = "LIVE context assembly failed",
                retryable = true,
                context = mapOf(
                    "correlation_id" to context.correlationId,
                    "cause" to (error.message?.take(160)?.takeIf { it.isNotBlank() }
                        ?: error::class.java.simpleName),
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
