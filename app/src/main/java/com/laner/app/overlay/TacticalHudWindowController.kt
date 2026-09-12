package com.laner.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Owns only the touch-through Tactical HUD window and verified-vs-preview selection. */
internal class TacticalHudWindowController(
    private val context: Context,
    private val windowHost: OverlayWindowHost,
) {
    private var view: TacticalHudOverlayView? = null
    private var verifiedPresentation = TacticalHudPresentation.inactive()
    private var previewState = TacticalHudPreviewSession.state.value

    fun setVerifiedPresentation(presentation: TacticalHudPresentation) {
        verifiedPresentation = presentation
    }

    fun setPreviewState(state: TacticalHudPreviewState) {
        previewState = state
    }

    fun showEffective(): Boolean {
        val presentation = effectivePresentation()
        if (!presentation.isDisplayableAt(System.currentTimeMillis())) {
            hide()
            return false
        }
        if (!ensureCreated()) return false
        view?.apply {
            visibility = View.VISIBLE
            render(presentation)
        }
        return true
    }

    fun hide() {
        view?.visibility = View.GONE
    }

    fun onConfigurationChanged() {
        view?.post {
            val presentation = effectivePresentation()
            if (presentation.isDisplayableAt(System.currentTimeMillis())) {
                view?.render(presentation)
            } else {
                hide()
            }
        }
    }

    fun destroy() {
        view?.let { windowHost.remove(WINDOW_NAME, it) }
        view = null
    }

    private fun effectivePresentation(): TacticalHudPresentation = when {
        verifiedPresentation.isDisplayableAt(System.currentTimeMillis()) -> verifiedPresentation
        previewState.active -> previewState.presentation
        else -> TacticalHudPresentation.inactive()
    }

    private fun ensureCreated(): Boolean {
        if (view != null) return true
        val created = TacticalHudOverlayView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        if (!windowHost.add(WINDOW_NAME, created, params)) return false
        view = created
        return true
    }

    private companion object {
        const val WINDOW_NAME = "tactical_hud"
    }
}
