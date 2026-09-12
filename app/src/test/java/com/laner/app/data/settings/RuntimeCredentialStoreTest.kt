package com.laner.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class RuntimeCredentialStoreTest {
    @Test
    fun roundTripAndClearStayDeviceLocal() {
        val dir = Files.createTempDirectory("laner-credential-test").toFile()
        val store = RuntimeCredentialStore(dir)

        assertNull(store.read())
        store.write("  test-key-123  ")
        assertEquals("test-key-123", store.read())
        store.write("")
        assertNull(store.read())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedlyLongCredential() {
        val dir = Files.createTempDirectory("laner-credential-long").toFile()
        RuntimeCredentialStore(dir).write("x".repeat(513))
    }
}
