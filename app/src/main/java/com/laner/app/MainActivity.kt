package com.laner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.laner.app.ui.LanerRoot
import com.laner.app.ui.LanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            var runtimeKey by remember { mutableStateOf("") }
            val effectiveKey = runtimeKey.ifBlank { BuildConfig.LOL_ESPORTS_API_KEY }
            val appGraph = remember(effectiveKey) { LanerAppGraph(filesDir, riotApiKey = effectiveKey) }

            LanerTheme {
                LanerRoot(
                    scheduleService = appGraph.globalScheduleService,
                    preMatchContextService = appGraph.preMatchContextService,
                    competitionStructureService = appGraph.competitionStructureService,
                    liveMatchStateService = appGraph.liveMatchStateService,
                    liveSnapshotService = appGraph.liveSnapshotService,
                    liveTimelineService = appGraph.liveTimelineService,
                    postMatchService = appGraph.postMatchService,
                    postTimelineService = appGraph.postTimelineService,
                    riotCredentialConfigured = effectiveKey.isNotBlank(),
                    runtimeCredentialActive = runtimeKey.isNotBlank(),
                    onSaveRuntimeCredential = { value -> runtimeKey = value.trim().take(512) },
                    onClearRuntimeCredential = { runtimeKey = "" },
                )
            }
        }
    }
}
