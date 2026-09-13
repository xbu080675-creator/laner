package com.laner.app

import android.app.Application

/**
 * Process-level Android composition root.
 *
 * Activity and foreground Services share the same Application services instead of recreating a
 * legacy global Store. Provider credentials are deployment/configuration concerns and are never
 * promoted into ordinary product UI state.
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

    private fun buildGraph(): LanerAppGraph = LanerAppGraph(
        filesDir = filesDir,
        riotApiKey = BuildConfig.LOL_ESPORTS_API_KEY,
    )
}
