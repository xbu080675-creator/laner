package com.laner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.laner.app.overlay.RiftScreenController
import com.laner.app.ui.LanerRoot
import com.laner.app.ui.LanerTheme

class MainActivity : ComponentActivity() {
    private val riftScreenController by lazy { RiftScreenController(this) }
    private var overlayPermissionGranted by mutableStateOf(false)
    private var riftScreenRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val lanerApplication = application as LanerApplication
        setContent {
            var runtimeKey by remember { mutableStateOf(lanerApplication.runtimeRiotCredential()) }
            val appGraph = remember(runtimeKey) {
                if (runtimeKey.isBlank()) {
                    lanerApplication.clearRuntimeRiotCredential()
                } else {
                    lanerApplication.setRuntimeRiotCredential(runtimeKey)
                }
                lanerApplication.graph()
            }
            val effectiveCredentialConfigured = runtimeKey.isNotBlank() || BuildConfig.LOL_ESPORTS_API_KEY.isNotBlank()

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
                    riotCredentialConfigured = effectiveCredentialConfigured,
                    runtimeCredentialActive = runtimeKey.isNotBlank(),
                    onSaveRuntimeCredential = { value ->
                        val normalized = value.trim().take(512)
                        lanerApplication.setRuntimeRiotCredential(normalized)
                        runtimeKey = normalized
                    },
                    onClearRuntimeCredential = {
                        lanerApplication.clearRuntimeRiotCredential()
                        runtimeKey = ""
                    },
                    overlayPermissionGranted = overlayPermissionGranted,
                    riftScreenRunning = riftScreenRunning,
                    onRequestOverlayPermission = {
                        riftScreenController.requestOverlayPermission(this)
                    },
                    onStartRiftScreen = {
                        if (riftScreenController.start()) {
                            riftScreenRunning = true
                        }
                    },
                    onStartDraftHudPreview = {
                        if (riftScreenController.startDraftPreview()) {
                            riftScreenRunning = true
                        }
                    },
                    onStopRiftScreen = {
                        riftScreenController.stop()
                        riftScreenRunning = false
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        riftScreenController.setHostForeground(true)
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionGranted = riftScreenController.hasOverlayPermission()
        riftScreenRunning = riftScreenController.isRunning()
    }

    override fun onStop() {
        riftScreenController.setHostForeground(false)
        super.onStop()
    }
}
