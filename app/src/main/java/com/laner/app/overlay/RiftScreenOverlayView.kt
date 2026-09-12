package com.laner.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class RiftScreenOverlayView(
    context: Context,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    enum class Mode { MINI, COMPACT, EXPANDED }

    private var mode = Mode.COMPACT
    private val panelBackground = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.TL_BR
        colors = intArrayOf(0xF21B1F2A.toInt(), 0xF2141822.toInt())
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), 0x886CEBFF.toInt())
    }

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(12))
    }
    private val title = label("RIFTSCREEN · 等待比赛", 11f, 0xFF6CEBFF.toInt(), true)
    private val timer = label("--:--", 11f, 0xFF9AA6B8.toInt(), true)
    private val modeText = label("COMPACT ›", 10f, 0xFF6CEBFF.toInt(), true).apply {
        setPadding(dp(10), 0, 0, 0)
        setOnClickListener { cycleMode() }
    }
    private val close = label("×", 20f, 0xFF8C98AA.toInt(), true).apply {
        setPadding(dp(12), 0, 0, 0)
        setOnClickListener { onClose() }
    }
    private val leftTeam = label("—", 14f, Color.WHITE, true).apply { gravity = Gravity.START }
    private val center = label("VS", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val rightTeam = label("—", 14f, Color.WHITE, true).apply { gravity = Gravity.END }
    private val metrics = label("K —:— · T —:— · D —:—", 12f, 0xFFD9DFE8.toInt(), false).apply { gravity = Gravity.CENTER }
    private val details = label("GOLD —:— · BARON —:—", 11f, 0xFFD9DFE8.toInt(), false)
    private val source = label("NO VERIFIED SOURCE", 10f, 0xFF8D98AA.toInt(), false)
    private val status = label("等待 Application LIVE truth", 10f, 0xFF718097.toInt(), false)
    private val hint = label("轻点尺寸标签切换 · 拖动空白区移动", 9f, 0xFF617087.toInt(), false)
    private val accent = View(context).apply { setBackgroundColor(0xFF6CEBFF.toInt()) }

    init {
        elevation = dp(16).toFloat()
        background = panelBackground
        addView(root, LayoutParams(dp(310), LayoutParams.WRAP_CONTENT))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(timer)
        header.addView(modeText)
        header.addView(close)
        root.addView(header)

        root.addView(accent, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
            topMargin = dp(7)
            bottomMargin = dp(10)
        })

        val teams = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        teams.addView(leftTeam, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        teams.addView(center, LinearLayout.LayoutParams(dp(112), LayoutParams.WRAP_CONTENT))
        teams.addView(rightTeam, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        root.addView(teams)

        metrics.setPadding(0, dp(8), 0, 0)
        root.addView(metrics)
        details.setPadding(0, dp(7), 0, 0)
        root.addView(details)
        source.setPadding(0, dp(7), 0, 0)
        root.addView(source)
        status.setPadding(0, dp(3), 0, 0)
        root.addView(status)
        hint.setPadding(0, dp(7), 0, 0)
        root.addView(hint)
        applyMode()
    }

    fun render(presentation: RiftScreenPresentation) {
        title.text = presentation.title
        timer.text = presentation.timer
        leftTeam.text = presentation.leftTeam
        rightTeam.text = presentation.rightTeam
        center.text = presentation.center
        metrics.text = presentation.metrics
        details.text = presentation.details
        source.text = "SOURCE · ${presentation.source}"
        status.text = presentation.status
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
                metrics.visibility = View.GONE
                details.visibility = View.GONE
                source.visibility = View.GONE
                status.visibility = View.GONE
                hint.visibility = View.GONE
                setWidth(232)
            }
            Mode.COMPACT -> {
                modeText.text = "COMPACT ›"
                metrics.visibility = View.VISIBLE
                details.visibility = View.GONE
                source.visibility = View.GONE
                status.visibility = View.GONE
                hint.visibility = View.GONE
                setWidth(310)
            }
            Mode.EXPANDED -> {
                modeText.text = "EXPANDED ›"
                metrics.visibility = View.VISIBLE
                details.visibility = View.VISIBLE
                source.visibility = View.VISIBLE
                status.visibility = View.VISIBLE
                hint.visibility = View.VISIBLE
                setWidth(360)
            }
        }
        requestLayout()
    }

    private fun setWidth(widthDp: Int) {
        root.layoutParams = LayoutParams(dp(widthDp), LayoutParams.WRAP_CONTENT)
    }

    private fun label(value: String, sp: Float, color: Int, bold: Boolean): TextView = TextView(context).apply {
        text = value
        textSize = sp
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
