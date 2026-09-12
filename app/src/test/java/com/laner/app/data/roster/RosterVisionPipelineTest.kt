package com.laner.app.data.roster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RosterVisionPipelineTest {
    @Test
    fun twoColumnPosterSeparatesBothTeamsByGeometry() {
        val tokens = mutableListOf<RosterOcrToken>()
        val roles = listOf("TOP", "JUG", "MID", "BOT", "SUP")
        val left = listOf("Bin", "Beichuan", "knight", "Viper", "ON")
        val right = listOf("Flandre", "Tarzan", "Shanks", "Hope", "Kael")
        roles.forEachIndexed { index, role ->
            val y = 100 + index * 100
            tokens += token(role, "chinese", 450, y, 550, y + 30)
            tokens += token(left[index], "latin", 140, y, 260, y + 30)
            tokens += token(right[index], "latin", 740, y, 860, y + 30)
        }
        val result = RosterOcrResult(
            text = "",
            engines = listOf("latin", "chinese"),
            lineCount = 15,
            tokens = tokens,
            imageWidth = 1000,
            imageHeight = 800,
        )

        val extracted = RosterCandidateExtractor.extract(result)

        assertEquals("TWO_COLUMN", extracted.layoutMode)
        assertEquals(listOf("Bin"), extracted.left["TOP"])
        assertEquals(listOf("ON"), extracted.left["SUP"])
        assertEquals(listOf("Flandre"), extracted.right["TOP"])
        assertEquals(listOf("Kael"), extracted.right["SUP"])
        assertTrue(extracted.left.values.all { it.size == 1 })
        assertTrue(extracted.right.values.all { it.size == 1 })
    }

    @Test
    fun roleWordsAndLeagueNoiseAreNeverPlayers() {
        val result = RosterOcrResult(
            text = "TOP TOP LPL\nJUG JUNGLE\nMID knight\nBOT ADC\nSUP SUPPORT",
            engines = listOf("latin"),
            lineCount = 5,
            tokens = emptyList(),
            imageWidth = 1000,
            imageHeight = 600,
        )

        val extracted = RosterCandidateExtractor.extract(result)

        assertEquals(listOf("knight"), extracted.merged["MID"])
        assertTrue(extracted.merged["TOP"].orEmpty().isEmpty())
        assertTrue(extracted.merged["JUG"].orEmpty().isEmpty())
        assertTrue(extracted.merged["BOT"].orEmpty().isEmpty())
        assertTrue(extracted.merged["SUP"].orEmpty().isEmpty())
    }

    private fun token(text: String, engine: String, left: Int, top: Int, right: Int, bottom: Int) =
        RosterOcrToken(text, engine, left, top, right, bottom, 1000, 800)
}
