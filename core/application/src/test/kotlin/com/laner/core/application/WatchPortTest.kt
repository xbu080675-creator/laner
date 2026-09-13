package com.laner.core.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WatchPortTest {
    @Test
    fun destination_requiresStableIdentity() {
        assertFailsWith<IllegalArgumentException> {
            WatchDestination("", "Bilibili", WatchRegion.MAINLAND)
        }
        assertFailsWith<IllegalArgumentException> {
            WatchDestination("bilibili", "", WatchRegion.MAINLAND)
        }
    }

    @Test
    fun region_isMetadataAndLaunchUsesDestinationIdOnly() {
        val launched = mutableListOf<String>()
        val port = object : WatchPort {
            private val targets = listOf(
                WatchDestination("a", "A", WatchRegion.MAINLAND),
                WatchDestination("b", "B", WatchRegion.GLOBAL),
            )

            override fun destinations(): List<WatchDestination> = targets
            override fun isInstalled(destinationId: String): Boolean = false
            override fun launch(destinationId: String): WatchLaunchResult {
                launched += destinationId
                return WatchLaunchResult(WatchLaunchStatus.OPENED, destinationId, "opened")
            }
        }

        port.destinations().forEach { port.launch(it.id) }

        assertEquals(listOf("a", "b"), launched)
        assertTrue(port.destinations().map { it.region }.containsAll(WatchRegion.entries))
    }
}
