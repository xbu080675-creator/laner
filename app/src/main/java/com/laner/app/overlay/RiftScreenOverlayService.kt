package com.laner.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.laner.app.LanerApplication
import com.laner.core.application.LiveMatchSourceQuery
import com.laner.core.application.LiveTargetSelector
import com.laner.core.application.SourceRequestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Android platform adapter for the RiftScreen system overlay.
 *
 * The service never talks to a Provider directly. It resolves the process-level Application graph,
 * asks Core/Application services for canonical LIVE truth, and renders only normalized results.
 */
class RiftScreenOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var windowManager: WindowManager
    private var overlay: RiftScreenOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var pollingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE -> hideOverlay()
            ACTION_SYNC -> syncVisibility()
            else -> syncVisibility()
        }
        ensurePolling()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun syncVisibility() {
        if (hostInForeground || !Settings.canDrawOverlays(this)) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlay == null) createOverlay()
        overlay?.visibility = View.VISIBLE
        overlay?.post { clampOverlayToDisplay() }
    }

    private fun hideOverlay() {
        overlay?.visibility = View.GONE
    }

    private fun createOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val view = RiftScreenOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(120)
        }
        overlay = view
        overlayParams = params
        attachDragAndMode(view, params)
        windowManager.addView(view, params)
        view.render(RiftScreenPresentationMapper.waiting("等待 Application LIVE truth"))
        view.post { clampOverlayToDisplay() }
    }

    private fun ensurePolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                if (!hostInForeground && Settings.canDrawOverlays(this@RiftScreenOverlayService)) {
                    refreshPresentation()
                }
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun refreshPresentation() {
        val now = System.currentTimeMillis()
        val presentation = runCatching {
            val graph = (application as LanerApplication).graph()
            val context = SourceRequestContext(
                nowEpochMillis = now,
                correlationId = "riftscreen-$now",
            )
            val schedule = graph.globalScheduleService.load(context)
            val target = LiveTargetSelector.select(schedule.matches, now)
                ?: return@runCatching RiftScreenPresentationMapper.waiting(
                    if (schedule.matches.isEmpty()) {
                        "没有可用赛事目录；RiftScreen 不猜比赛目标"
                    } else {
                        "没有可识别的 LIVE 目标"
                    }
                )
            val query = LiveMatchSourceQuery.from(target)
            val liveState = graph.liveMatchStateService.refresh(query, context)
            val snapshot = graph.liveSnapshotService.refresh(query, context)
            val gameId = snapshot.snapshot?.game?.gameId ?: liveState.state.currentGameId
            val timeline = gameId?.let { graph.liveTimelineService.load(it) }
            RiftScreenPresentationMapper.from(target, liveState, snapshot, timeline)
        }.getOrElse { error ->
            RiftScreenPresentationMapper.waiting(
                "读取失败 · ${error.message?.take(96)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}"
            )
        }
        overlay?.post {
            overlay?.render(presentation)
            clampOverlayToDisplay()
        }
    }

    private fun attachDragAndMode(view: RiftScreenOverlayView, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val threshold = dp(6)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > threshold || abs(dy) > threshold) moved = true
                    if (moved) {
                        params.x = startX - dx
                        params.y = startY + dy
                        clampLayoutParams(view, params)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        view.cycleMode()
                        view.post { clampOverlayToDisplay() }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun clampOverlayToDisplay() {
        val view = overlay ?: return
        val params = overlayParams ?: return
        if (view.width <= 0 || view.height <= 0) return
        clampLayoutParams(view, params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun clampLayoutParams(view: View, params: WindowManager.LayoutParams) {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Rect().also { windowManager.defaultDisplay.getRectSize(it) }
        }
        val maxX = (bounds.width() - view.width).coerceAtLeast(0)
        val maxY = (bounds.height() - view.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
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

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            2001,
            Intent(this, RiftScreenOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Laner RiftScreen 正在运行")
            .setContentText("退到后台后显示赛事副屏；数据只来自 Application LIVE truth")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
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
        if (this::windowManager.isInitialized) {
            overlay?.let { runCatching { windowManager.removeView(it) } }
        }
        overlay = null
        overlayParams = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "laner_riftscreen"
        private const val NOTIFICATION_ID = 42
        private const val REFRESH_INTERVAL_MILLIS = 5_000L

        const val ACTION_SHOW = "com.laner.app.overlay.RIFTSCREEN_SHOW"
        const val ACTION_HIDE = "com.laner.app.overlay.RIFTSCREEN_HIDE"
        const val ACTION_STOP = "com.laner.app.overlay.RIFTSCREEN_STOP"
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
