package com.laner.core.domain

/**
 * Stable user/support facing error identifier.
 * Format: LNR-MODULE-STAGE-NNN, e.g. LNR-SRC-LIVE-001.
 */
@JvmInline
value class ErrorCode(val value: String) {
    init {
        require(PATTERN.matches(value)) {
            "Error code must match LNR-MODULE-STAGE-NNN"
        }
    }

    override fun toString(): String = value

    private companion object {
        val PATTERN = Regex("LNR-[A-Z]{2,5}-[A-Z0-9_]{2,12}-[0-9]{3}")
    }
}

data class DiagnosticFailure(
    val code: ErrorCode,
    val message: String,
    val retryable: Boolean,
    val context: Map<String, String> = emptyMap(),
) {
    init { require(message.isNotBlank()) }
}
