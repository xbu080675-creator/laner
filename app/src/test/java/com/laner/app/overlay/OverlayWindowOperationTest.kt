package com.laner.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowOperationTest {
    @Test
    fun everyWindowOperationHasUniqueStableErrorCode() {
        val values = OverlayWindowOperation.entries
        assertEquals(values.size, values.map { it.code.value }.toSet().size)
        assertEquals("LNR-OVR-WINDOW-001", OverlayWindowOperation.ADD.code.value)
        assertEquals("LNR-OVR-WINDOW-002", OverlayWindowOperation.UPDATE.code.value)
        assertEquals("LNR-OVR-WINDOW-003", OverlayWindowOperation.REMOVE.code.value)
        assertEquals("LNR-OVR-WINDOW-004", OverlayWindowOperation.BOUNDS.code.value)
    }

    @Test
    fun recoveryClassificationDoesNotPretendRemovalCanBeRetriedSafely() {
        assertTrue(OverlayWindowOperation.ADD.retryable)
        assertTrue(OverlayWindowOperation.UPDATE.retryable)
        assertFalse(OverlayWindowOperation.REMOVE.retryable)
        assertTrue(OverlayWindowOperation.BOUNDS.retryable)
    }
}
