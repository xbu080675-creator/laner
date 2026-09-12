package com.laner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.laner.app.ui.LanerRoot
import com.laner.app.ui.LanerTheme

class MainActivity : ComponentActivity() {
    private val appGraph by lazy { LanerAppGraph(filesDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            LanerTheme {
                LanerRoot(
                    scheduleService = appGraph.globalScheduleService,
                    preMatchContextService = appGraph.preMatchContextService,
                    competitionStructureService = appGraph.competitionStructureService,
                    liveMatchStateService = appGraph.liveMatchStateService,
                )
            }
        }
    }
}
