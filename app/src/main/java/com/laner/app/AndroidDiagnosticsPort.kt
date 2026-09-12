package com.laner.app

import android.util.Log
import com.laner.core.application.DiagnosticEvent
import com.laner.core.application.DiagnosticsPort
import com.laner.core.application.LogLevel

class AndroidDiagnosticsPort : DiagnosticsPort {
    override fun emit(event: DiagnosticEvent) {
        val context = if (event.context.isEmpty()) "" else " ${event.context}"
        val failure = event.failure?.let { " ${it.code}: ${it.message}" }.orEmpty()
        val message = "${event.stablePrefix} ${event.message}$context$failure"
        when (event.level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }

    private companion object {
        const val TAG = "Laner"
    }
}
