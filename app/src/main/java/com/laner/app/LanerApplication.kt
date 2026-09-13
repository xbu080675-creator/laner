package com.laner.app

import android.app.Application

/**
 * Process-level Android composition root.
 *
 * Activity and foreground Services share one graph. Provider availability is resolved inside the
 * composition root from deployment configuration; the product UI never owns provider credentials.
 */
class LanerApplication : Application() {
    @Volatile
    private var appGraph: LanerAppGraph? = null

    override fun onCreate() {
        super.onCreate()
        appGraph = buildGraph()
    }

    fun graph(): LanerAppGraph = appGraph ?: synchronized(this) {
        appGraph ?: buildGraph().also { appGraph = it }
    }

    private fun buildGraph(): LanerAppGraph = LanerAppGraph(filesDir = filesDir)
}
