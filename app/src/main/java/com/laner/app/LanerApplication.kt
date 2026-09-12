package com.laner.app

import android.app.Application

/**
 * Process-level Android composition root.
 *
 * Activity and foreground Services share the same Application services instead of recreating a
 * legacy global Store. Runtime Riot credentials remain memory-only and rebuilding the graph never
 * persists the secret.
 */
class LanerApplication : Application() {
    @Volatile
    private var runtimeRiotApiKey: String = ""

    @Volatile
    private var appGraph: LanerAppGraph? = null

    override fun onCreate() {
        super.onCreate()
        rebuildGraph()
    }

    fun graph(): LanerAppGraph = appGraph ?: synchronized(this) {
        appGraph ?: rebuildGraph()
    }

    fun runtimeRiotCredential(): String = runtimeRiotApiKey

    @Synchronized
    fun setRuntimeRiotCredential(value: String) {
        val normalized = value.trim().take(512)
        if (normalized == runtimeRiotApiKey) return
        runtimeRiotApiKey = normalized
        rebuildGraph()
    }

    @Synchronized
    fun clearRuntimeRiotCredential() {
        if (runtimeRiotApiKey.isEmpty()) return
        runtimeRiotApiKey = ""
        rebuildGraph()
    }

    private fun rebuildGraph(): LanerAppGraph {
        val effectiveKey = runtimeRiotApiKey.ifBlank { BuildConfig.LOL_ESPORTS_API_KEY }
        return LanerAppGraph(filesDir = filesDir, riotApiKey = effectiveKey).also { appGraph = it }
    }
}
