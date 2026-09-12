package com.laner.core.application

import com.laner.core.domain.DiagnosticFailure

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class DiagnosticEvent(
    val module: String,
    val level: LogLevel,
    val message: String,
    val context: Map<String, String> = emptyMap(),
    val failure: DiagnosticFailure? = null,
) {
    init {
        require(module.isNotBlank())
        require(message.isNotBlank())
    }

    val stablePrefix: String get() = "[Laner:$module]"
}

/** Platform-neutral diagnostics boundary. Android/logcat implementations live outside Core. */
fun interface DiagnosticsPort {
    fun emit(event: DiagnosticEvent)
}
