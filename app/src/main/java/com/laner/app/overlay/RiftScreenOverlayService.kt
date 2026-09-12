package com.laner.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.laner.app.LanerApplication
import com.laner.core.application.DiagnosticEvent
import com.laner.core.application.LiveMatchContextResult
import com.laner.core.application.LiveTargetUnavailableReason
import com.laner.core.application.LogLevel
import com.laner.core.application.SourceRequestContext
import com.laner.core.domain.DiagnosticFailure
import com.laner.core.domain.ErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground-service lifecycle/composition coordinator for RiftScreen and Draft HUD.
 *
 * Window operations live in dedicated controllers. LIVE business orchestration lives in
 * LiveMatchContextService. This class only schedules refreshes, maps Application results to
 * presentation, and connects Android service lifecycle to the platform windows.
 */
class RiftScreenOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowHost: OverlayWindowHost
    private lateinit var riftWindow: RiftScreenWindowController
    private lateinit var draftWindow: DraftHudWindowController

    private var pollingJob: Job? = null
    private var previewJob: Job? = null
    private var latestRiftPresentation = RiftScreenPresentationMapper.waiting("等待 Application LIVE truth")

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val graph = (application as LanerApplication).graph()
        windowHost = OverlayWindowHost(this, graph.diagnostics)
        riftWindow = RiftScreenWindowController(this, windowHost) { stopSelf() }
        draftWindow = DraftHudWindowController(this, windowHost)
        createNotificationChannel()
        startAsForeground()
        ensurePreviewCollection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE -> hideOverlay()
            ACTION_DRAFT_PREVIEW_AUTO -> {
                DraftHudPreviewSession.startAuto()
                syncVisibility()
            }
            ACTION_DRAFT_PREVIEW_NEXT -> {
                DraftHudPreviewSession.next()
                syncVisibility()
            }
            ACTION_DRAFT_PREVIEW_STOP -> {
                DraftHudPreviewSession.stop()
                syncVisibility()
            }
            ACTION_SHOW,
            ACTION_SYNC -> syncVisibility()
            else -> syncVisibility()
        }
        ensurePolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        riftWindow.onConfigurationChanged()
        draftWindow.onConfigurationChanged()
    }

    private fun syncVisibility() {
        if (hostInForeground || !Settings.canDrawOverlays(this)) {
            hideOverlay()
        } else {
            refreshOverlayMode()
        }
    }

    private fun hideOverlay() {
        if (this::riftWindow.isInitialized) riftWindow.hide()
        if (this::draftWindow.isInitialized) draftWindow.hide()
    }

    private fun ensurePolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                if (!hostInForeground && Settings.canDrawOverlays(this@RiftScreenOverlayService)) {
                    refreshTruth()
                }
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private fun ensurePreviewCollection() {
        if (previewJob?.isActive == true) return
        previewJob = scope.launch {
            DraftHudPreviewSession.state.collect { state ->
                mainHandler.post {
                    if (this@RiftScreenOverlayService::draftWindow.isInitialized) {
                        draftWindow.setPreviewState(state)
                        refreshOverlayMode()
                    }
                }
            }
        }
    }

    private data class OverlayTruth(
        val rift: RiftScreenPresentation,
        val draft: DraftHudPresentation,
    )

    private suspend fun refreshTruth() {
        val now = System.currentTimeMillis()
        val graph = (application as LanerApplication).graph()
        val context = SourceRequestContext(
            nowEpochMillis = now,
            correlationId = "riftscreen-$now",
        )
        val result = graph.liveMatchContextService.load(context)
        val truth = try {
            when (result) {
                is LiveMatchContextResult.NoTarget -> OverlayTruth(
                    rift = RiftScreenPresentationMapper.waiting(
                        when (result.reason) {
                            LiveTargetUnavailableReason.NO_MATCHES ->
                                "没有可用赛事目录；RiftScreen 不猜比赛目标"
                            LiveTargetUnavailableReason.NO_ELIGIBLE_TARGET ->
                                "没有可识别的 LIVE 目标"
                        }
                    ),
                    draft = DraftHudPresentation.inactive(),
                )
                is LiveMatchContextResult.Ready -> OverlayTruth(
                    rift = RiftScreenPresentationMapper.from(
                        result.match,
                        result.liveState,
                        result.snapshot,
                        result.timeline,
                    ),
                    draft = DraftHudPresentationMapper.from(
                        result.match,
                        result.liveState,
                        result.timeline,
                    ),
                )
                is LiveMatchContextResult.Failed -> OverlayTruth(
                    rift = RiftScreenPresentationMapper.waiting(
                        "读取失败 · ${result.failure.code.value}"
                    ),
                    draft = DraftHudPresentation.inactive(),
                )
            }
        } catch (error: Exception) {
            val failure = DiagnosticFailure(
                code = ErrorCode("LNR-OVR-REFRESH-001"),
                message = "Overlay presentation mapping failed",
                retryable = true,
                context = mapOf(
                    "correlation_id" to context.correlationId,
                    "error_type" to error::class.java.simpleName,
                ),
            )
            graph.diagnostics.emit(
                DiagnosticEvent(
                    module = "OVERLAY",
                    level = LogLevel.ERROR,
                    message = failure.message,
                    context = failure.context,
                    failure = failure,
                )
            )
            OverlayTruth(
                rift = RiftScreenPresentationMapper.waiting("读取失败 · ${failure.code.value}"),
                draft = DraftHudPresentation.inactive(),
            )
        }

        mainHandler.post {
            if (!this@RiftScreenOverlayService::riftWindow.isInitialized ||
                !this@RiftScreenOverlayService::draftWindow.isInitialized
            ) return@post
            latestRiftPresentation = truth.rift
            draftWindow.setVerifiedPresentation(truth.draft)
            riftWindow.render(truth.rift)
            refreshOverlayMode()
        }
    }

    private fun refreshOverlayMode() {
        if (!this::riftWindow.isInitialized || !this::draftWindow.isInitialized) return
        if (hostInForeground || !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }

        val draftActive = draftWindow.showEffective()
        if (draftActive) {
            riftWindow.hide()
        } else {
            draftWindow.hide()
            riftWindow.render(latestRiftPresentation)
            riftWindow.show()
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, RiftScreenOverlayService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Laner RiftScreen 正在运行")
            .setContentText("赛事副屏 / Draft HUD · 本地预览不会写入赛事事实")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, "HUD预览", actionIntent(ACTION_DRAFT_PREVIEW_AUTO, 2001))
            .addAction(android.R.drawable.ic_media_next, "下一步", actionIntent(ACTION_DRAFT_PREVIEW_NEXT, 2002))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止预览", actionIntent(ACTION_DRAFT_PREVIEW_STOP, 2003))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RiftScreen 赛事副屏",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        pollingJob?.cancel()
        previewJob?.cancel()
        DraftHudPreviewSession.stop()
        if (this::draftWindow.isInitialized) draftWindow.destroy()
        if (this::riftWindow.isInitialized) riftWindow.destroy()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "laner_riftscreen"
        private const val NOTIFICATION_ID = 42
        private const val REFRESH_INTERVAL_MILLIS = 5_000L

        const val ACTION_SHOW = "com.laner.app.overlay.RIFTSCREEN_SHOW"
        const val ACTION_HIDE = "com.laner.app.overlay.RIFTSCREEN_HIDE"
        const val ACTION_STOP = "com.laner.app.overlay.RIFTSCREEN_STOP"
        const val ACTION_DRAFT_PREVIEW_AUTO = "com.laner.app.overlay.DRAFT_PREVIEW_AUTO"
        const val ACTION_DRAFT_PREVIEW_NEXT = "com.laner.app.overlay.DRAFT_PREVIEW_NEXT"
        const val ACTION_DRAFT_PREVIEW_STOP = "com.laner.app.overlay.DRAFT_PREVIEW_STOP"
        private const val ACTION_SYNC = "com.laner.app.overlay.RIFTSCREEN_SYNC"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var hostInForeground: Boolean = false

        fun setHostForeground(context: Context, foreground: Boolean) {
            hostInForeground = foreground
            if (isRunning) {
                context.startService(
                    Intent(context, RiftScreenOverlayService::class.java).setAction(ACTION_SYNC)
                )
            }
        }
    }
}
