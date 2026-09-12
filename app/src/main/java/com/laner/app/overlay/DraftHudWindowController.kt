package com.laner.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Owns Draft HUD windows, edit/lock state and preview-vs-verified presentation selection. */
internal class DraftHudWindowController(
    private val context: Context,
    private val windowHost: OverlayWindowHost,
) {
    private var hud: DraftHudOverlayView? = null
    private var hudParams: WindowManager.LayoutParams? = null
    private var dock: DraftHudControlView? = null
    private var dockParams: WindowManager.LayoutParams? = null

    private var editing = false
    private var selectedModule = DraftHudModule.LEFT_PICK
    private var verifiedPresentation = DraftHudPresentation.inactive()
    private var previewState = DraftHudPreviewSession.state.value

    fun setVerifiedPresentation(presentation: DraftHudPresentation) {
        verifiedPresentation = presentation
    }

    fun setPreviewState(state: DraftHudPreviewState) {
        previewState = state
    }

    fun showEffective(): Boolean {
        val presentation = effectivePresentation()
        if (!presentation.active) {
            if (editing) setEditMode(false)
            hide()
            return false
        }
        if (!ensureCreated()) return false
        hud?.apply {
            visibility = View.VISIBLE
            render(presentation)
        }
        dock?.visibility = View.VISIBLE
        refreshDock()
        return true
    }

    fun hide() {
        hud?.visibility = View.GONE
        dock?.visibility = View.GONE
    }

    fun onConfigurationChanged() {
        hud?.post {
            hud?.render(effectivePresentation())
            refreshDock()
        }
    }

    fun destroy() {
        editing = false
        dock?.let { windowHost.remove(DOCK_WINDOW_NAME, it) }
        hud?.let { windowHost.remove(HUD_WINDOW_NAME, it) }
        hud = null
        hudParams = null
        dock = null
        dockParams = null
    }

    private fun effectivePresentation(): DraftHudPresentation = when {
        verifiedPresentation.active -> verifiedPresentation
        previewState.active -> previewState.presentation
        else -> DraftHudPresentation.inactive()
    }

    private fun ensureCreated(): Boolean {
        if (hud != null && dock != null) return true

        val createdHud = DraftHudOverlayView(context) { module ->
            selectedModule = module
            refreshDock()
        }
        val createdHudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            lockedHudFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        if (!windowHost.add(HUD_WINDOW_NAME, createdHud, createdHudParams)) return false

        val createdDock = DraftHudControlView(
            context,
            onToggleAuto = { DraftHudPreviewSession.toggleAuto() },
            onNext = { DraftHudPreviewSession.next() },
            onStopPreview = {
                setEditMode(false)
                DraftHudPreviewSession.stop()
            },
            onToggleEdit = { setEditMode(!editing) },
            onPreviousModule = {
                hud?.cycleSelection(-1)
                refreshDock()
            },
            onNextModule = {
                hud?.cycleSelection(1)
                refreshDock()
            },
            onScaleDown = {
                hud?.adjustSelectedScale(-0.05f)
                refreshDock()
            },
            onScaleUp = {
                hud?.adjustSelectedScale(0.05f)
                refreshDock()
            },
            onAlphaDown = {
                hud?.adjustSelectedAlpha(-0.08f)
                refreshDock()
            },
            onAlphaUp = {
                hud?.adjustSelectedAlpha(0.08f)
                refreshDock()
            },
            onToggleVisibility = {
                hud?.toggleSelectedVisibility()
                refreshDock()
            },
            onResetLayout = {
                hud?.resetCurrentLayout()
                refreshDock()
            },
        )
        val createdDockParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(4)
        }
        if (!windowHost.add(DOCK_WINDOW_NAME, createdDock, createdDockParams)) {
            windowHost.remove(HUD_WINDOW_NAME, createdHud)
            return false
        }

        hud = createdHud
        hudParams = createdHudParams
        dock = createdDock
        dockParams = createdDockParams
        return true
    }

    private fun setEditMode(enabled: Boolean) {
        if (editing == enabled) return
        val currentHud = hud ?: return
        val currentParams = hudParams ?: return
        if (enabled && previewState.autoPlay) DraftHudPreviewSession.toggleAuto()

        val previous = editing
        editing = enabled
        currentHud.setEditMode(enabled)
        currentParams.flags = if (enabled) editableHudFlags() else lockedHudFlags()
        if (!windowHost.update(HUD_WINDOW_NAME, currentHud, currentParams)) {
            editing = previous
            currentHud.setEditMode(previous)
            currentParams.flags = if (previous) editableHudFlags() else lockedHudFlags()
        }
        refreshDock()
    }

    private fun refreshDock() {
        val currentHud = hud ?: return
        val currentDock = dock ?: return
        val presentation = effectivePresentation()
        if (!presentation.active) return
        selectedModule = currentHud.selectedModule()
        currentDock.render(
            presentation = presentation,
            previewState = previewState,
            editing = editing,
            selectedModule = selectedModule,
            placement = currentHud.selectedPlacement(),
        )
        dockParams?.let { params ->
            windowHost.update(DOCK_WINDOW_NAME, currentDock, params)
        }
    }

    private fun lockedHudFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun editableHudFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val HUD_WINDOW_NAME = "draft_hud"
        const val DOCK_WINDOW_NAME = "draft_dock"
    }
}
