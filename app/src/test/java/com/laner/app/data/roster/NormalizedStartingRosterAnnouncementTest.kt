package com.laner.app.data.roster

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizedStartingRosterAnnouncementTest {
    @Test
    fun unparsedOfficialImageAnnouncementIsPreservedForDeviceAssist() {
        val rows = JSONArray().put(
            JSONObject()
                .put("id", "blg-post")
                .put("league", "LPL")
                .put("team", "BLG")
                .put("platform", "OFFICIAL")
                .put("account", "BLG电子竞技俱乐部")
                .put("source", "TEAM_SOCIAL")
                .put("observedAt", "2026-09-12T14:40:00Z")
                .put("sourceUrl", "https://weibo.com/example")
                .put("imageUrls", JSONArray().put("https://img.example/1.jpg").put("javascript:bad"))
                .put("parseStatus", "UNPARSED")
                .put("candidateBasis", "TEAM_IMAGE_ONLY_FALLBACK")
                .put("candidateTeams", JSONArray().put("BLG"))
                .put("candidateScore", 35)
        )

        val parsed = NormalizedStartingRosterSource(endpoints = emptyList()).parseAnnouncements(rows)

        assertEquals(1, parsed.size)
        assertEquals("UNPARSED", parsed.single().parseStatus)
        assertEquals(35, parsed.single().candidateScore)
        assertEquals(listOf("BLG"), parsed.single().candidateTeams)
        assertEquals(listOf("https://img.example/1.jpg"), parsed.single().imageUrls)
        assertTrue(parsed.single().sourceUri!!.startsWith("https://"))
    }
}
