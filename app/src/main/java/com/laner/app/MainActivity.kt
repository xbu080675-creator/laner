package com.laner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.laner.app.overlay.RiftScreenController
import com.laner.app.ui.LanerRoot
import com.laner.app.ui.LanerTheme
import com.laner.app.watch.AndroidWatchPort

class MainActivity : ComponentActivity() {
    private val riftScreenController by lazy { RiftScreenController(this) }
    private val watchPort by lazy { AndroidWatchPort(this, riftScreenController) }
    private var overlayPermissionGranted by mutableStateOf(false)
    private var riftScreenRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val appGraph = (application as LanerApplication).graph()
        setContent {
            LanerTheme {
                LanerRoot(
                    scheduleService = appGraph.globalScheduleService,
                    preMatchContextService = appGraph.preMatchContextService,
                    competitionStructureService = appGraph.competitionStructureService,
                    liveMatchContextService = appGraph.liveMatchContextService,
                    postMatchService = appGraph.postMatchService,
                    postTimelineService = appGraph.postTimelineService,
                    watchPort = watchPort,
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
        watchPort.resumePendingIfReady()
        riftScreenRunning = riftScreenController.isRunning()
    }

    override fun onStop() {
        riftScreenController.setHostForeground(false)
        super.onStop()
    }
}
