package com.laner.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen Draft HUD. Locked mode becomes touch-through at the WindowManager layer. */
class DraftHudOverlayView(
    context: Context,
    private val onModuleSelected: (DraftHudModule) -> Unit,
) : FrameLayout(context) {
    private val topStatus = pill("Laner · BP / Draft", 10f, 0xFF8CEBFF.toInt())
    private val progress = pill("0 events", 10f, Color.WHITE)
    private val leftCard = sideCard()
    private val rightCard = sideCard()
    private val matchupCard = matchupCard()
    private val watermark = text("NO VERIFIED DRAFT SOURCE", 10f, 0xAAFFFFFF.toInt(), true)
    private val safeZoneGuide = text("HUD 编辑模式 · 拖动模块调整位置", 10f, 0x99FFFFFF.toInt(), true).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(10), 0, 0)
        setBackgroundColor(0x2218A7C4)
        visibility = View.GONE
    }

    private val placements = mutableMapOf<DraftHudModule, DraftHudPlacement>()
    private var current = DraftHudPresentation.inactive()
    private var selectedModule = DraftHudModule.LEFT_PICK
    private var editing = false
    private var lastLandscape: Boolean? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(safeZoneGuide, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply { gravity = Gravity.BOTTOM })
        addModule(DraftHudModule.STATUS, topStatus, LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)))
        addModule(DraftHudModule.PROGRESS, progress, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28)))
        addModule(DraftHudModule.LEFT_PICK, leftCard.root, LayoutParams(dp(252), LayoutParams.WRAP_CONTENT))
        addModule(DraftHudModule.RIGHT_PICK, rightCard.root, LayoutParams(dp(252), LayoutParams.WRAP_CONTENT))
        addModule(DraftHudModule.MATCHUP, matchupCard.root, LayoutParams(dp(340), LayoutParams.WRAP_CONTENT))
        addModule(DraftHudModule.WATERMARK, watermark, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        DraftHudModule.entries.forEach { module -> attachModuleDrag(module, viewFor(module)) }
        post { ensureProfile(force = true) }
    }

    fun render(presentation: DraftHudPresentation) {
        current = presentation
        visibility = if (presentation.active) View.VISIBLE else View.GONE
        if (!presentation.active) return

        topStatus.text = when (presentation.sourceMode) {
            DraftHudSourceMode.VERIFIED -> "Laner · VERIFIED DRAFT"
            DraftHudSourceMode.PREVIEW -> "Laner · HUD PREVIEW"
        }
        progress.text = presentation.totalSteps?.let { "${presentation.step} / $it" } ?: "${presentation.step} events"
        leftCard.bind(presentation.leftTeam, presentation.leftPicks, presentation.leftBans)
        rightCard.bind(presentation.rightTeam, presentation.rightPicks, presentation.rightBans)
        matchupCard.bind(presentation.matchup)
        watermark.text = presentation.sourceLabel

        val landscape = isLandscape()
        (leftCard.root.layoutParams as LayoutParams).width = dp(if (landscape) 252 else 178)
        (rightCard.root.layoutParams as LayoutParams).width = dp(if (landscape) 252 else 178)
        (matchupCard.root.layoutParams as LayoutParams).width = dp(if (landscape) 340 else 260)
        ensureProfile()
        post {
            applyAllPlacements()
            applyRuntimeVisibility()
        }
    }

    fun setEditMode(enabled: Boolean) {
        editing = enabled
        safeZoneGuide.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            ensureProfile()
            selectedModule = selectedModule.takeIf { placements[it]?.visible == true } ?: DraftHudModule.LEFT_PICK
            onModuleSelected(selectedModule)
        }
        applyRuntimeVisibility()
    }

    fun selectedModule(): DraftHudModule = selectedModule

    fun selectedPlacement(): DraftHudPlacement =
        placements[selectedModule] ?: DraftHudLayoutStore.load(context, selectedModule, isLandscape())

    fun cycleSelection(direction: Int) {
        val entries = DraftHudModule.entries
        val currentIndex = entries.indexOf(selectedModule).coerceAtLeast(0)
        selectedModule = entries[(currentIndex + direction + entries.size) % entries.size]
        onModuleSelected(selectedModule)
    }

    fun adjustSelectedScale(delta: Float) = mutateSelected {
        it.copy(scale = (it.scale + delta).coerceIn(0.55f, 1.45f))
    }

    fun adjustSelectedAlpha(delta: Float) = mutateSelected {
        it.copy(alpha = (it.alpha + delta).coerceIn(0.30f, 1f))
    }

    fun toggleSelectedVisibility() {
        mutateSelected { it.copy(visible = !it.visible) }
        applyRuntimeVisibility()
    }

    fun resetCurrentLayout() {
        DraftHudLayoutStore.reset(context, isLandscape())
        placements.clear()
        ensureProfile(force = true)
        applyRuntimeVisibility()
        onModuleSelected(selectedModule)
    }

    private fun mutateSelected(block: (DraftHudPlacement) -> DraftHudPlacement) {
        val value = block(selectedPlacement())
        placements[selectedModule] = value
        DraftHudLayoutStore.save(context, selectedModule, isLandscape(), value)
        applyPlacement(selectedModule, value)
        onModuleSelected(selectedModule)
    }

    private fun addModule(module: DraftHudModule, view: View, lp: LayoutParams) {
        lp.gravity = Gravity.TOP or Gravity.START
        view.tag = module
        addView(view, lp)
    }

    private fun attachModuleDrag(module: DraftHudModule, view: View) {
        var downX = 0f
        var downY = 0f
        var startTx = 0f
        var startTy = 0f
        view.setOnTouchListener { target, event ->
            if (!editing) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    selectedModule = module
                    onModuleSelected(module)
                    downX = event.rawX
                    downY = event.rawY
                    startTx = target.translationX
                    startTy = target.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val point = clampTranslation(
                        target,
                        startTx + event.rawX - downX,
                        startTy + event.rawY - downY,
                    )
                    target.translationX = point.first
                    target.translationY = point.second
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    savePosition(module, target)
                    true
                }
                else -> false
            }
        }
    }

    private fun savePosition(module: DraftHudModule, view: View) {
        if (width <= 0 || height <= 0) return
        val old = placements[module] ?: DraftHudLayoutStore.load(context, module, isLandscape())
        val value = old.copy(
            x = ((view.translationX + view.width / 2f) / width).coerceIn(0f, 1f),
            y = ((view.translationY + view.height / 2f) / height).coerceIn(0f, 1f),
        )
        placements[module] = value
        DraftHudLayoutStore.save(context, module, isLandscape(), value)
        onModuleSelected(module)
    }

    private fun ensureProfile(force: Boolean = false) {
        val landscape = isLandscape()
        if (!force && lastLandscape == landscape && placements.size == DraftHudModule.entries.size) return
        lastLandscape = landscape
        placements.clear()
        DraftHudModule.entries.forEach { placements[it] = DraftHudLayoutStore.load(context, it, landscape) }
        safeZoneGuide.layoutParams = (safeZoneGuide.layoutParams as LayoutParams).apply {
            height = if (landscape) (resources.displayMetrics.heightPixels * 0.40f).toInt()
            else (resources.displayMetrics.heightPixels * 0.28f).toInt()
            gravity = Gravity.BOTTOM
        }
        post { applyAllPlacements() }
    }

    private fun applyAllPlacements() {
        if (width <= 0 || height <= 0) return
        DraftHudModule.entries.forEach { module ->
            applyPlacement(module, placements[module] ?: return@forEach)
        }
    }

    private fun applyPlacement(module: DraftHudModule, value: DraftHudPlacement) {
        val view = viewFor(module)
        if (view.width <= 0 || view.height <= 0 || width <= 0 || height <= 0) {
            view.post { applyPlacement(module, placements[module] ?: value) }
            return
        }
        view.scaleX = value.scale
        view.scaleY = value.scale
        view.alpha = value.alpha
        val point = clampTranslation(
            view,
            value.x * width - view.width / 2f,
            value.y * height - view.height / 2f,
        )
        view.translationX = point.first
        view.translationY = point.second
    }

    private fun clampTranslation(view: View, desiredX: Float, desiredY: Float): Pair<Float, Float> {
        val scale = view.scaleX.coerceAtLeast(0.01f)
        val halfW = view.width * scale / 2f
        val halfH = view.height * scale / 2f
        val centerX = (desiredX + view.width / 2f).coerceIn(halfW, (width - halfW).coerceAtLeast(halfW))
        val centerY = (desiredY + view.height / 2f).coerceIn(halfH, (height - halfH).coerceAtLeast(halfH))
        return (centerX - view.width / 2f) to (centerY - view.height / 2f)
    }

    private fun applyRuntimeVisibility() {
        if (!current.active) return
        DraftHudModule.entries.forEach { module ->
            val userVisible = placements[module]?.visible ?: true
            val runtimeVisible = when (module) {
                DraftHudModule.MATCHUP -> current.matchup != null || editing
                DraftHudModule.WATERMARK -> isLandscape() || editing || current.sourceMode == DraftHudSourceMode.PREVIEW
                else -> true
            }
            viewFor(module).visibility = if (userVisible && runtimeVisible) View.VISIBLE else View.GONE
        }
        safeZoneGuide.visibility = if (editing) View.VISIBLE else View.GONE
    }

    private fun viewFor(module: DraftHudModule): View = when (module) {
        DraftHudModule.STATUS -> topStatus
        DraftHudModule.PROGRESS -> progress
        DraftHudModule.LEFT_PICK -> leftCard.root
        DraftHudModule.RIGHT_PICK -> rightCard.root
        DraftHudModule.MATCHUP -> matchupCard.root
        DraftHudModule.WATERMARK -> watermark
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun sideCard(): SideCard {
        val title = text("—", 10f, 0xFF8CEBFF.toInt(), true)
        val champion = text("等待锁定", 20f, Color.WHITE, true)
        val picks = text("PICKS · —", 10f, 0xFFE6EBF2.toInt())
        val bans = text("BANS · —", 10f, 0xFFB9C3D3.toInt())
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(10))
            background = panelBackground(0xD911151C.toInt(), 0xAA8CEBFF.toInt())
            addView(title)
            addView(champion)
            addView(picks)
            addView(bans)
        }
        return SideCard(root, title, champion, picks, bans)
    }

    private fun matchupCard(): MatchupCard {
        val summary = text("对位证据等待", 11f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        val detail = text("缺少角色/位置证据时不推断", 10f, 0xFFB9C3D3.toInt()).apply { gravity = Gravity.CENTER }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(6))
            background = panelBackground(0xD911151C.toInt(), 0xAA8CEBFF.toInt())
            addView(summary)
            addView(detail)
            visibility = View.GONE
        }
        return MatchupCard(root, summary, detail)
    }

    private class SideCard(
        val root: LinearLayout,
        private val title: TextView,
        private val champion: TextView,
        private val picks: TextView,
        private val bans: TextView,
    ) {
        fun bind(label: String, pickValues: List<String>, banValues: List<String>) {
            title.text = label
            champion.text = pickValues.lastOrNull() ?: "等待锁定"
            picks.text = "PICKS · ${pickValues.takeLast(5).joinToString(" / ").ifBlank { "—" }}"
            bans.text = "BANS · ${banValues.takeLast(5).joinToString(" / ").ifBlank { "—" }}"
        }
    }

    private class MatchupCard(
        val root: LinearLayout,
        private val summary: TextView,
        private val detail: TextView,
    ) {
        fun bind(matchup: String?) {
            root.visibility = if (matchup == null) View.GONE else View.VISIBLE
            if (matchup != null) {
                summary.text = matchup
                detail.text = "仅显示已有结构化证据 / 本地预览"
            }
        }
    }

    private fun pill(value: String, sp: Float, color: Int) = text(value, sp, color, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(10), 0)
        background = panelBackground(0xD911151C.toInt(), 0x667B8799)
    }

    private fun panelBackground(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(8).toFloat()
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = sp
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** Edge control dock. The full-screen HUD itself is touch-through whenever editing is locked. */
class DraftHudControlView(
    context: Context,
    private val onToggleAuto: () -> Unit,
    private val onNext: () -> Unit,
    private val onStopPreview: () -> Unit,
    private val onToggleEdit: () -> Unit,
    private val onPreviousModule: () -> Unit,
    private val onNextModule: () -> Unit,
    private val onScaleDown: () -> Unit,
    private val onScaleUp: () -> Unit,
    private val onAlphaDown: () -> Unit,
    private val onAlphaUp: () -> Unit,
    private val onToggleVisibility: () -> Unit,
    private val onResetLayout: () -> Unit,
) : LinearLayout(context) {
    private var collapsed = true
    private val header = button("RIFT") {
        collapsed = !collapsed
        applyCollapsedState()
    }
    private val stateText = text("LIVE", 10f, 0xFF8CEBFF.toInt(), true)
    private val normalPanel = LinearLayout(context).apply { orientation = VERTICAL }
    private val editPanel = LinearLayout(context).apply { orientation = VERTICAL; visibility = View.GONE }
    private val play = button("▶") { onToggleAuto() }
    private val next = button("下一步") { onNext() }
    private val stopPreview = button("停止预览") { onStopPreview() }
    private val edit = button("编辑") { onToggleEdit() }
    private val selected = text("左侧选择", 10f, Color.WHITE, true)
    private val visibilityButton = button("隐藏") { onToggleVisibility() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(5), dp(5), dp(6))
        background = GradientDrawable().apply {
            setColor(0xE611151C.toInt())
            setStroke(dp(1), 0xAA8CEBFF.toInt())
            cornerRadius = dp(10).toFloat()
        }
        addView(header, LayoutParams(dp(48), dp(30)))
        addView(stateText, LayoutParams(dp(64), dp(18)))
        normalPanel.addView(play, LayoutParams(LayoutParams.MATCH_PARENT, dp(34)))
        normalPanel.addView(next, LayoutParams(LayoutParams.MATCH_PARENT, dp(30)))
        normalPanel.addView(edit, LayoutParams(LayoutParams.MATCH_PARENT, dp(30)))
        normalPanel.addView(stopPreview, LayoutParams(LayoutParams.MATCH_PARENT, dp(32)))
        addView(normalPanel, LayoutParams(dp(84), LayoutParams.WRAP_CONTENT))
        selected.gravity = Gravity.CENTER
        editPanel.addView(selected, LayoutParams(dp(100), dp(24)))
        editPanel.addView(twoButtons("◀", { onPreviousModule() }, "▶", { onNextModule() }))
        editPanel.addView(twoButtons("缩−", { onScaleDown() }, "缩+", { onScaleUp() }))
        editPanel.addView(twoButtons("透−", { onAlphaDown() }, "透+", { onAlphaUp() }))
        editPanel.addView(visibilityButton, LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))
        editPanel.addView(button("重置") { onResetLayout() }, LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))
        editPanel.addView(button("锁定") { onToggleEdit() }, LayoutParams(LayoutParams.MATCH_PARENT, dp(30)))
        addView(editPanel, LayoutParams(dp(104), LayoutParams.WRAP_CONTENT))
        applyCollapsedState()
    }

    fun render(
        presentation: DraftHudPresentation,
        previewState: DraftHudPreviewState,
        editing: Boolean,
        selectedModule: DraftHudModule,
        placement: DraftHudPlacement,
    ) {
        val preview = presentation.sourceMode == DraftHudSourceMode.PREVIEW
        stateText.text = when {
            editing -> "编辑"
            preview -> "${previewState.step}/${previewState.totalSteps}"
            else -> "LIVE"
        }
        play.text = if (previewState.autoPlay) "Ⅱ" else "▶"
        play.visibility = if (preview) View.VISIBLE else View.GONE
        next.visibility = if (preview) View.VISIBLE else View.GONE
        stopPreview.visibility = if (preview) View.VISIBLE else View.GONE
        normalPanel.visibility = if (!collapsed && !editing) View.VISIBLE else View.GONE
        editPanel.visibility = if (!collapsed && editing) View.VISIBLE else View.GONE
        selected.text = selectedModule.label
        visibilityButton.text = if (placement.visible) "隐藏" else "显示"
    }

    private fun applyCollapsedState() {
        normalPanel.visibility = if (collapsed) View.GONE else View.VISIBLE
        editPanel.visibility = View.GONE
        stateText.visibility = if (collapsed) View.GONE else View.VISIBLE
        header.text = if (collapsed) "RIFT" else "RIFT ‹"
        requestLayout()
    }

    private fun twoButtons(
        left: String,
        leftAction: () -> Unit,
        right: String,
        rightAction: () -> Unit,
    ) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(button(left, leftAction), LayoutParams(0, dp(28), 1f))
        addView(button(right, rightAction), LayoutParams(0, dp(28), 1f))
    }

    private fun button(value: String, action: () -> Unit) = TextView(context).apply {
        text = value
        textSize = if (value.length > 2) 10f else 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        includeFontPadding = false
        setOnClickListener { action() }
    }

    private fun text(value: String, sp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = sp
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
