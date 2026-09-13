package com.laner.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Legacy-compatible RiftScreen surface backed only by the new Presentation model.
 * Visual behavior stays stable; provider arbitration remains in Core/Application.
 */
class RiftScreenOverlayView(
    context: Context,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    enum class Mode { MINI, COMPACT, EXPANDED }

    private var mode = Mode.COMPACT
    private val panelBackground = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.TL_BR
        colors = intArrayOf(0xF211151D.toInt(), 0xF2171D27.toInt(), 0xF211151D.toInt())
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), 0x662A3545)
    }
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(12))
    }
    private val title = label("等待数据", 11f, 0xFF94A0B2.toInt(), true)
    private val timer = label("--:--", 11f, 0xFF94A0B2.toInt(), false)
    private val modeText = label("COMPACT ›", 10f, 0xFF6CEBFF.toInt(), true).apply {
        setPadding(dp(10), 0, 0, 0)
        setOnClickListener { cycleMode() }
    }
    private val close = label("×", 20f, 0xFF8C98AA.toInt(), true).apply {
        setPadding(dp(12), 0, 0, 0)
        setOnClickListener { onClose() }
    }
    private val blue = label("—", 10f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val red = label("—", 10f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val center = label("VS", 22f, 0xFF94A0B2.toInt(), true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(84)
    }
    private val miniLeft = label("—", 11f, Color.WHITE, true).apply { gravity = Gravity.START }
    private val miniCenter = label("VS", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val miniRight = label("—", 11f, Color.WHITE, true).apply { gravity = Gravity.END }
    private val miniTeams = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(miniLeft, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(miniCenter, LinearLayout.LayoutParams(dp(92), LayoutParams.WRAP_CONTENT))
        addView(miniRight, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
    private val teams = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(blue, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(center, LinearLayout.LayoutParams(dp(96), LayoutParams.WRAP_CONTENT))
        addView(red, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
    private val metrics = label("K —:—   T —:—   D —:—", 12f, 0xFFD1D7E2.toInt(), false).apply {
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val details = label("GOLD —:—   BARON —:—", 11f, 0xFFD1D7E2.toInt(), false)
    private val source = label("SOURCE · NO VERIFIED SOURCE", 10f, 0xFF8C98AA.toInt(), false)
    private val status = label("等待实时数据", 10f, 0xFF8C98AA.toInt(), false)
    private val hint = label("轻点切换尺寸 · 拖动可移动", 10f, 0xFF667386.toInt(), false)
    private val accent = View(context).apply { setBackgroundColor(0xFF6CEBFF.toInt()) }

    init {
        elevation = dp(14).toFloat()
        background = panelBackground
        addView(root, LayoutParams(dp(308), LayoutParams.WRAP_CONTENT))

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        top.addView(timer)
        top.addView(modeText)
        top.addView(close)
        root.addView(top)

        root.addView(accent, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
            topMargin = dp(6)
            bottomMargin = dp(9)
        })
        root.addView(miniTeams)
        root.addView(teams)
        metrics.setPadding(0, dp(5), 0, 0)
        root.addView(metrics)
        details.setPadding(0, dp(6), 0, 0)
        root.addView(details)
        source.setPadding(0, dp(5), 0, 0)
        root.addView(source)
        status.setPadding(0, dp(3), 0, 0)
        root.addView(status)
        hint.setPadding(0, dp(7), 0, 0)
        root.addView(hint)
        applyMode()
    }

    fun render(presentation: RiftScreenPresentation) {
        title.text = presentation.title.removePrefix("RIFTSCREEN · ")
        timer.text = presentation.timer
        blue.text = presentation.leftTeam
        red.text = presentation.rightTeam
        center.text = presentation.center
        miniLeft.text = presentation.leftTeam
        miniRight.text = presentation.rightTeam
        miniCenter.text = presentation.center
        metrics.text = presentation.metrics.replace(" · ", "   ")
        details.text = presentation.details.replace(" · ", "   ")
        source.text = "SOURCE · ${presentation.source}"
        status.text = presentation.status
        flashAccent()
    }

    fun cycleMode() {
        mode = when (mode) {
            Mode.MINI -> Mode.COMPACT
            Mode.COMPACT -> Mode.EXPANDED
            Mode.EXPANDED -> Mode.MINI
        }
        applyMode()
    }

    private fun applyMode() {
        when (mode) {
            Mode.MINI -> {
                modeText.text = "MINI ›"
                timer.visibility = View.GONE
                miniTeams.visibility = View.VISIBLE
                teams.visibility = View.GONE
                metrics.visibility = View.GONE
                details.visibility = View.GONE
                source.visibility = View.GONE
                status.visibility = View.GONE
                hint.visibility = View.GONE
                setWidth(228)
            }
            Mode.COMPACT -> {
                modeText.text = "COMPACT ›"
                timer.visibility = View.VISIBLE
                miniTeams.visibility = View.GONE
                teams.visibility = View.VISIBLE
                metrics.visibility = View.VISIBLE
                details.visibility = View.GONE
                source.visibility = View.GONE
                status.visibility = View.GONE
                hint.visibility = View.GONE
                setWidth(308)
            }
            Mode.EXPANDED -> {
                modeText.text = "EXPANDED ›"
                timer.visibility = View.VISIBLE
                miniTeams.visibility = View.GONE
                teams.visibility = View.VISIBLE
                metrics.visibility = View.VISIBLE
                details.visibility = View.VISIBLE
                source.visibility = View.VISIBLE
                status.visibility = View.VISIBLE
                hint.visibility = View.VISIBLE
                setWidth(348)
            }
        }
        requestLayout()
    }

    private fun setWidth(widthDp: Int) {
        root.layoutParams = LayoutParams(dp(widthDp), LayoutParams.WRAP_CONTENT)
    }

    private fun flashAccent() {
        ValueAnimator.ofFloat(1f, 0.35f, 1f).apply {
            duration = 320
            addUpdateListener { accent.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun label(value: String, sp: Float, color: Int, bold: Boolean): TextView = TextView(context).apply {
        text = value
        textSize = sp
        setTextColor(color)
        includeFontPadding = false
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
