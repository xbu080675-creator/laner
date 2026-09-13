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

/**
 * Large spectator HUD keeps the established RiftLab product shape.
 *
 * Data still comes exclusively from [RiftScreenPresentation]; this view does not restore the legacy
 * MatchSessionStore/TimelineStore or make any match-truth decisions.
 */
class RiftScreenOverlayView(
    context: Context,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    enum class Mode { MINI, COMPACT, EXPANDED }

    private var mode = Mode.COMPACT
    private val panelBackground = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.TL_BR
        colors = intArrayOf(0xF210141C.toInt(), 0xF2161C26.toInt(), 0xF210141C.toInt())
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), 0xFF2A3340.toInt())
    }

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(12))
    }

    private val title = label("等待数据", 11f, 0xFFA0A8B5.toInt(), true)
    private val timer = label("--:--", 11f, 0xFFA0A8B5.toInt(), false)
    private val modeText = label("COMPACT ›", 10f, 0xFF43D6F1.toInt(), true).apply {
        setPadding(dp(10), 0, 0, 0)
        setOnClickListener { cycleMode() }
    }
    private val close = label("×", 20f, 0xFFA0A8B5.toInt(), true).apply {
        setPadding(dp(12), 0, 0, 0)
        setOnClickListener { onClose() }
    }

    private val blueBadge = teamBadge(38)
    private val redBadge = teamBadge(38)
    private val blue = label("—", 10f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val red = label("—", 10f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val blueTeam = teamIdentity(blueBadge, blue)
    private val redTeam = teamIdentity(redBadge, red)

    private val goldDiff = label("—", 22f, 0xFFF3F5F8.toInt(), true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(84)
        setPadding(dp(10), 0, dp(10), 0)
    }

    private val miniBlue = teamBadge(25)
    private val miniRed = teamBadge(25)
    private val miniCenter = label("VS", 16f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
    private val miniTeams = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(miniBlue, LinearLayout.LayoutParams(dp(25), dp(25)))
        addView(miniCenter, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(miniRed, LinearLayout.LayoutParams(dp(25), dp(25)))
    }

    private val metrics = label("K —:—   T —:—   D —:—", 12f, 0xFFF3F5F8.toInt(), false)
    private val goldLine = label("GOLD —:—   LEAD —", 11f, 0xFFF3F5F8.toInt(), false)
    private val event = label("STATUS · 等待赛事事实", 10f, 0xFFA0A8B5.toInt(), false)
    private val source = label("SOURCE · NO VERIFIED SOURCE", 9f, 0xFF7F8998.toInt(), false)
    private val hint = label("轻点切换尺寸 · 拖动可移动", 10f, 0xFF667386.toInt(), false)
    private val accent = View(context).apply { setBackgroundColor(0xFF43D6F1.toInt()) }
    private val teams = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

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

        teams.addView(blueTeam, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        teams.addView(goldDiff, LinearLayout.LayoutParams(dp(96), LayoutParams.WRAP_CONTENT))
        teams.addView(redTeam, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        root.addView(teams)

        metrics.gravity = Gravity.CENTER_HORIZONTAL
        metrics.setPadding(0, dp(5), 0, 0)
        root.addView(metrics)
        goldLine.setPadding(0, dp(6), 0, 0)
        root.addView(goldLine)
        event.setPadding(0, dp(5), 0, 0)
        root.addView(event)
        source.setPadding(0, dp(3), 0, 0)
        root.addView(source)
        hint.setPadding(0, dp(7), 0, 0)
        root.addView(hint)

        applyMode()
    }

    fun render(presentation: RiftScreenPresentation) {
        title.text = presentation.title
        timer.text = presentation.timer
        blue.text = presentation.leftTeam
        red.text = presentation.rightTeam
        blueBadge.text = badgeText(presentation.leftTeam)
        redBadge.text = badgeText(presentation.rightTeam)
        miniBlue.text = badgeText(presentation.leftTeam)
        miniRed.text = badgeText(presentation.rightTeam)
        goldDiff.text = presentation.center
        miniCenter.text = presentation.center
        metrics.text = presentation.metrics.replace(" · ", "   ")
        goldLine.text = presentation.details.replace(" · ", "   ")
        event.text = "STATUS · ${presentation.status}"
        source.text = "SOURCE · ${presentation.source}"

        val live = presentation.timer != "--:--"
        val leadColor = if (live && presentation.center != "VS" && presentation.center != "EVEN") {
            0xFF43D6F1.toInt()
        } else {
            0xFFF3F5F8.toInt()
        }
        goldDiff.setTextColor(leadColor)
        miniCenter.setTextColor(leadColor)
        title.setTextColor(if (live) 0xFF43D6F1.toInt() else 0xFFA0A8B5.toInt())
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
                goldLine.visibility = View.GONE
                event.visibility = View.GONE
                source.visibility = View.GONE
                hint.visibility = View.GONE
                setWidth(228)
            }
            Mode.COMPACT -> {
                modeText.text = "COMPACT ›"
                timer.visibility = View.VISIBLE
                miniTeams.visibility = View.GONE
                teams.visibility = View.VISIBLE
                metrics.visibility = View.VISIBLE
                goldLine.visibility = View.GONE
                event.visibility = View.GONE
                source.visibility = View.GONE
                hint.visibility = View.GONE
                setWidth(308)
            }
            Mode.EXPANDED -> {
                modeText.text = "EXPANDED ›"
                timer.visibility = View.VISIBLE
                miniTeams.visibility = View.GONE
                teams.visibility = View.VISIBLE
                metrics.visibility = View.VISIBLE
                goldLine.visibility = View.VISIBLE
                event.visibility = View.VISIBLE
                source.visibility = View.VISIBLE
                hint.visibility = View.VISIBLE
                setWidth(348)
            }
        }
        requestLayout()
    }

    private fun setWidth(widthDp: Int) {
        root.layoutParams = LayoutParams(dp(widthDp), LayoutParams.WRAP_CONTENT)
    }

    private fun teamBadge(sizeDp: Int): TextView = label("—", if (sizeDp >= 38) 11f else 8f, 0xFFF3F5F8.toInt(), true).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (sizeDp >= 38) 10 else 7).toFloat()
            colors = intArrayOf(0xFF171D27.toInt(), 0xFF10141C.toInt())
            orientation = GradientDrawable.Orientation.TL_BR
            setStroke(dp(1), 0xFF2A3340.toInt())
        }
    }

    private fun teamIdentity(badge: TextView, label: TextView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))
        label.setPadding(0, dp(3), 0, 0)
        addView(label, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    private fun badgeText(value: String): String = value
        .trim()
        .ifBlank { "—" }
        .take(4)
        .uppercase()

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
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
