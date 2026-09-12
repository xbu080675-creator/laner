package com.laner.app.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.laner.core.application.DiagnosticEvent
import com.laner.core.application.DiagnosticsPort
import com.laner.core.application.LogLevel
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode

internal enum class OverlayWindowOperation(
    val code: ErrorCode,
    val retryable: Boolean,
) {
    ADD(ErrorCode("LNR-OVR-WINDOW-001"), true),
    UPDATE(ErrorCode("LNR-OVR-WINDOW-002"), true),
    REMOVE(ErrorCode("LNR-OVR-WINDOW-003"), false),
    BOUNDS(ErrorCode("LNR-OVR-WINDOW-004"), true),
}

/**
 * Android WindowManager boundary for every Laner overlay window.
 *
 * Platform failures are contained so an overlay cannot crash the process, but no failure is silent:
 * each operation emits a stable diagnostic code and enough non-sensitive context for support.
 */
internal class OverlayWindowHost(
    private val context: Context,
    private val diagnostics: DiagnosticsPort,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun add(windowName: String, view: View, params: WindowManager.LayoutParams): Boolean =
        operate(OverlayWindowOperation.ADD, windowName) {
            windowManager.addView(view, params)
        }

    fun update(windowName: String, view: View, params: WindowManager.LayoutParams): Boolean =
        operate(OverlayWindowOperation.UPDATE, windowName) {
            windowManager.updateViewLayout(view, params)
        }

    fun remove(windowName: String, view: View): Boolean =
        operate(OverlayWindowOperation.REMOVE, windowName) {
            windowManager.removeView(view)
        }

    fun displayBounds(windowName: String): Rect {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect(windowManager.currentWindowMetrics.bounds)
            } else {
                @Suppress("DEPRECATION")
                Rect().also { windowManager.defaultDisplay.getRectSize(it) }
            }
        } catch (error: Exception) {
            report(OverlayWindowOperation.BOUNDS, windowName, error)
            val metrics = context.resources.displayMetrics
            Rect(0, 0, metrics.widthPixels.coerceAtLeast(0), metrics.heightPixels.coerceAtLeast(0))
        }
    }

    private inline fun operate(
        operation: OverlayWindowOperation,
        windowName: String,
        block: () -> Unit,
    ): Boolean = try {
        block()
        true
    } catch (error: Exception) {
        report(operation, windowName, error)
        false
    }

    private fun report(
        operation: OverlayWindowOperation,
        windowName: String,
        error: Exception,
    ) {
        val failure = DiagnosticFailure(
            code = operation.code,
            message = "Overlay window ${operation.name.lowercase()} failed",
            retryable = operation.retryable,
            context = mapOf(
                "window" to windowName,
                "operation" to operation.name.lowercase(),
                "error_type" to error::class.java.simpleName,
            ),
        )
        diagnostics.emit(
            DiagnosticEvent(
                module = "OVERLAY",
                level = if (operation == OverlayWindowOperation.ADD) LogLevel.ERROR else LogLevel.WARN,
                message = failure.message,
                context = failure.context,
                failure = failure,
            )
        )
    }
}
