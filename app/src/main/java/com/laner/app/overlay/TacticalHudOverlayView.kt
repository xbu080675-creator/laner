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

/**
 * Sparse full-screen tactical X-ray layer. Important state lives on the edges and deliberately avoids
 * the center/bottom broadcast scoreboard area.
 */
internal class TacticalHudOverlayView(context: Context) : FrameLayout(context) {
    private val phaseText = textView(11, bold = true)
    private val clockText = textView(13, bold = true)
    private val matchText = textView(11)
    private val headlineText = textView(18, bold = true)
    private val explanationText = textView(12)
    private val sourceText = textView(10, bold = true)
    private val evidenceColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val playerColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false

        val eventCard = panel().apply {
            orientation = LinearLayout.VERTICAL
            addView(phaseText)
            addView(clockText, topMargin(dp(4)))
            addView(matchText, topMargin(dp(2)))
            addView(headlineText, topMargin(dp(10)))
            addView(explanationText, topMargin(dp(7)))
            addView(sourceText, topMargin(dp(9)))
        }
        addView(
            eventCard,
            LayoutParams(dp(340), LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(18)
                topMargin = dp(72)
            },
        )

        val evidenceCard = panel().apply {
            orientation = LinearLayout.VERTICAL
            addView(textView(10, bold = true).apply { text = "EVIDENCE / 证据" })
            addView(evidenceColumn, topMargin(dp(6)))
        }
        addView(
            evidenceCard,
            LayoutParams(dp(230), LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dp(18)
                topMargin = dp(72)
            },
        )

        val playerCard = panel().apply {
            orientation = LinearLayout.VERTICAL
            addView(textView(10, bold = true).apply { text = "RELATED PLAYER / 关联选手" })
            addView(playerColumn, topMargin(dp(6)))
        }
        addView(
            playerCard,
            LayoutParams(dp(300), LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = dp(18)
                topMargin = dp(285)
            },
        )
        playerCard.tag = PLAYER_CARD_TAG
    }

    fun render(presentation: TacticalHudPresentation) {
        phaseText.text = when (presentation.phase) {
            TacticalHudPhase.FIGHT -> "TACTICAL / FIGHT WINDOW"
            TacticalHudPhase.GLOBAL -> "TACTICAL / GLOBAL SHIFT"
        }
        clockText.text = presentation.clock
        matchText.text = presentation.matchLabel
        headlineText.text = presentation.headline
        explanationText.text = presentation.explanation
        sourceText.text = presentation.sourceLabel

        evidenceColumn.removeAllViews()
        presentation.evidence.take(4).forEach { evidence ->
            evidenceColumn.addView(textView(11).apply {
                text = "${evidence.label}  ${evidence.value}"
            }, topMargin(dp(3)))
        }

        playerColumn.removeAllViews()
        presentation.players.take(3).forEach { player ->
            playerColumn.addView(textView(12, bold = true).apply {
                text = listOfNotNull(player.champion, player.label).joinToString(" · ")
            }, topMargin(dp(4)))
            playerColumn.addView(textView(11).apply { text = player.summary })
        }
        findViewWithTag<View>(PLAYER_CARD_TAG)?.visibility =
            if (presentation.players.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun panel(): LinearLayout = LinearLayout(context).apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            setColor(Color.argb(210, 14, 16, 22))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.argb(140, 255, 255, 255))
        }
    }

    private fun textView(sizeSp: Int, bold: Boolean = false): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = sizeSp.toFloat()
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxLines = 5
    }

    private fun topMargin(value: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = value }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PLAYER_CARD_TAG = "tactical-player-card"
    }
}
