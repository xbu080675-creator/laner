package com.laner.app.data.settings

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Device-local credential override used by debug/test builds.
 *
 * The value never enters Git, logs, diagnostics, screenshots or Provider provenance. Storage lives
 * inside the Android app sandbox. Blank means "use build-time fallback".
 */
class RuntimeCredentialStore(
    filesDir: File,
) {
    private val file = File(filesDir, "config/riot-api-key.txt")

    fun read(): String? = runCatching {
        file.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun write(value: String) {
        val normalized = value.trim()
        require(normalized.length <= 512) { "credential is unexpectedly long" }
        file.parentFile?.mkdirs()
        if (normalized.isBlank()) {
            if (file.exists() && !file.delete()) error("failed to clear credential")
            return
        }
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(normalized, Charsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }
}
