package com.laner.app.watch

import com.laner.core.application.WatchRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidWatchCatalogTest {
    @Test
    fun catalog_containsLegacyAndGlobalViewingEntrypointsExactlyOnce() {
        val destinations = AndroidWatchCatalog.destinations
        val ids = destinations.map { it.id }

        assertEquals(
            listOf("bilibili", "huya", "lol_esports", "youtube", "twitch", "x_lolesports"),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(2, destinations.count { it.region == WatchRegion.MAINLAND })
        assertEquals(4, destinations.count { it.region == WatchRegion.GLOBAL })
    }

    @Test
    fun catalogPreservesLegacyNativeAppHints() {
        val destinations = AndroidWatchCatalog.destinations.associateBy { it.id }

        assertTrue(destinations.getValue("bilibili").nativeAppSupported)
        assertTrue(destinations.getValue("huya").nativeAppSupported)
        assertFalse(destinations.getValue("lol_esports").nativeAppSupported)
        assertTrue(destinations.getValue("youtube").nativeAppSupported)
        assertTrue(destinations.getValue("twitch").nativeAppSupported)
        assertTrue(destinations.getValue("x_lolesports").nativeAppSupported)
    }

    @Test
    fun catalogLabelsAreUserFacingAndNonBlank() {
        assertTrue(AndroidWatchCatalog.destinations.all { it.displayName.isNotBlank() })
    }
}
