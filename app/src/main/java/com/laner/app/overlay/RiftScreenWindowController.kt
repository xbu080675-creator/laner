package com.laner.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/** Owns only the RiftScreen overlay window, gesture, mode and display-boundary behavior. */
internal class RiftScreenWindowController(
    private val context: Context,
    private val windowHost: OverlayWindowHost,
    private val onClose: () -> Unit,
) {
    private var view: RiftScreenOverlayView? = null
    private var params: WindowManager.LayoutParams? = null

    fun render(presentation: RiftScreenPresentation) {
        if (!ensureCreated()) return
        view?.render(presentation)
        view?.post { clampToDisplay() }
    }

    fun show(): Boolean {
        if (!ensureCreated()) return false
        view?.visibility = View.VISIBLE
        return true
    }

    fun hide() {
        view?.visibility = View.GONE
    }

    fun onConfigurationChanged() {
        view?.post { clampToDisplay() }
    }

    fun destroy() {
        view?.let { windowHost.remove(WINDOW_NAME, it) }
        view = null
        params = null
    }

    private fun ensureCreated(): Boolean {
        if (view != null) return true
        val createdView = RiftScreenOverlayView(context, onClose)
        val createdParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(120)
        }
        attachDragAndMode(createdView, createdParams)
        if (!windowHost.add(WINDOW_NAME, createdView, createdParams)) return false
        view = createdView
        params = createdParams
        createdView.post { clampToDisplay() }
        return true
    }

    private fun attachDragAndMode(
        overlayView: RiftScreenOverlayView,
        layoutParams: WindowManager.LayoutParams,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val threshold = dp(6)
        overlayView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > threshold || abs(dy) > threshold) moved = true
                    if (moved) {
                        layoutParams.x = startX - dx
                        layoutParams.y = startY + dy
                        clampLayoutParams(overlayView, layoutParams)
                        windowHost.update(WINDOW_NAME, overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        overlayView.cycleMode()
                        overlayView.post { clampToDisplay() }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun clampToDisplay() {
        val overlayView = view ?: return
        val layoutParams = params ?: return
        if (overlayView.width <= 0 || overlayView.height <= 0) return
        clampLayoutParams(overlayView, layoutParams)
        windowHost.update(WINDOW_NAME, overlayView, layoutParams)
    }

    private fun clampLayoutParams(view: View, params: WindowManager.LayoutParams) {
        val bounds = windowHost.displayBounds(WINDOW_NAME)
        val maxX = (bounds.width() - view.width).coerceAtLeast(0)
        val maxY = (bounds.height() - view.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val WINDOW_NAME = "riftscreen"
    }
}
