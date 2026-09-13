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
import kotlin.math.roundToInt

/** Sparse edge HUD matching the legacy product layout while consuming canonical Presentation data. */
internal class TacticalHudOverlayView(context: Context) : FrameLayout(context) {
    private val stage = FrameLayout(context)
    private val eventCard = panel()
    private val evidenceColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val playerColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val phaseText = text(9f, true)
    private val clockText = text(9f, true)
    private val matchText = text(8f)
    private val headlineText = text(13f, true)
    private val explanationText = text(9.5f)
    private val sourceText = text(8f, true)
    private val accent = View(context)
    private val stageWidth = (resources.displayMetrics.widthPixels * 0.92f).roundToInt()
    private val stageHeight = (resources.displayMetrics.heightPixels * 0.52f).roundToInt()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        addView(stage, LayoutParams(stageWidth, stageHeight))

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(phaseText)
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(clockText)
        }
        eventCard.addView(top)
        eventCard.addView(matchText)
        eventCard.addView(accent, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply {
            topMargin = dp(5)
            bottomMargin = dp(6)
        })
        eventCard.addView(headlineText)
        eventCard.addView(explanationText, marginTop(4))
        eventCard.addView(sourceText, marginTop(5))
        stage.addView(eventCard)
        stage.addView(evidenceColumn)
        stage.addView(playerColumn)
        post { placeModules() }
        visibility = View.GONE
    }

    fun render(presentation: TacticalHudPresentation) {
        visibility = if (presentation.active) View.VISIBLE else View.GONE
        if (!presentation.active) return
        val emphasis = presentation.phase == TacticalHudPhase.FIGHT
        val accentColor = if (emphasis) 0xFFFF6470.toInt() else 0xFF65E6FF.toInt()
        phaseText.text = if (emphasis) "团战态势" else "全局态势"
        phaseText.setTextColor(accentColor)
        clockText.text = presentation.clock
        matchText.text = presentation.matchLabel
        headlineText.text = presentation.headline
        explanationText.text = presentation.explanation
        sourceText.text = presentation.sourceLabel
        accent.setBackgroundColor(accentColor)
        eventCard.background = background(0xB5121821.toInt(), accentColor, 7)
        renderEvidence(presentation.evidence, accentColor)
        renderPlayers(presentation.players, accentColor)
        alpha = 0.76f
        animate().cancel()
        animate().alpha(1f).setDuration(130L).start()
    }

    private fun placeModules() {
        eventCard.layoutParams = LayoutParams((stageWidth * 0.35f).roundToInt(), LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(8)
            topMargin = (stageHeight * 0.13f).roundToInt()
        }
        evidenceColumn.layoutParams = LayoutParams((stageWidth * 0.24f).roundToInt(), LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            rightMargin = dp(8)
            topMargin = (stageHeight * 0.15f).roundToInt()
        }
        playerColumn.layoutParams = LayoutParams((stageWidth * 0.31f).roundToInt(), LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(8)
            topMargin = (stageHeight * 0.55f).roundToInt()
        }
    }

    private fun renderEvidence(rows: List<TacticalHudEvidence>, accentColor: Int) {
        evidenceColumn.removeAllViews()
        evidenceColumn.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        rows.take(4).forEachIndexed { index, row ->
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = background(0xA9121820.toInt(), if (index == 0) accentColor else 0x3E667486, 6)
            }
            chip.addView(
                text(8f, true).apply { this.text = row.label },
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
            )
            chip.addView(text(9f, true).apply { text = row.value; gravity = Gravity.END })
            evidenceColumn.addView(chip, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = dp(4)
            })
        }
    }

    private fun renderPlayers(rows: List<TacticalHudPlayer>, accentColor: Int) {
        playerColumn.removeAllViews()
        playerColumn.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        rows.take(3).forEachIndexed { index, row ->
            val card = panel().apply {
                addView(text(8.5f, true).apply { text = listOfNotNull(row.champion, row.label).joinToString(" · ") })
                addView(text(8f).apply { text = row.summary }, marginTop(3))
                addView(
                    View(context).apply { setBackgroundColor(accentColor) },
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(4) },
                )
            }
            playerColumn.addView(card, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = dp(4)
            })
        }
    }

    private fun panel() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(9))
        background = background(0xA9121820.toInt(), 0x3E667486, 7)
    }

    private fun text(size: Float, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        setTextColor(Color.WHITE)
        includeFontPadding = false
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 3
    }

    private fun background(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radius).toFloat()
    }

    private fun marginTop(value: Int) = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(value)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
