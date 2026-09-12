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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Android platform adapter for RiftScreen and Draft HUD.
 *
 * Provider payloads never enter these windows. Verified presentation comes only from Application
 * truth/canonical Timeline; the local HUD preview is a separate Android-only fixture and never
 * writes back into Core or persistence.
 */
class RiftScreenOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var windowManager: WindowManager

    private var overlay: RiftScreenOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var draftHud: DraftHudOverlayView? = null
    private var draftHudParams: WindowManager.LayoutParams? = null
    private var draftDock: DraftHudControlView? = null
    private var draftDockParams: WindowManager.LayoutParams? = null

    private var pollingJob: Job? = null
    private var previewJob: Job? = null
    private var draftEditing = false
    private var selectedDraftModule = DraftHudModule.LEFT_PICK

    private var latestRiftPresentation = RiftScreenPresentationMapper.waiting("等待 Application LIVE truth")
    private var latestVerifiedDraft = DraftHudPresentation.inactive()
    private var latestPreviewState = DraftHudPreviewSession.state.value

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
                setDraftEditMode(false)
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
        overlay?.post { clampOverlayToDisplay() }
        draftHud?.post {
            draftHud?.render(effectiveDraftPresentation())
            refreshDraftDock()
        }
    }

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
        refreshOverlayMode()
    }

    private fun hideOverlay() {
        overlay?.visibility = View.GONE
        draftHud?.visibility = View.GONE
        draftDock?.visibility = View.GONE
    }

    private fun createOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val view = RiftScreenOverlayView(this) { stopSelf() }
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
        view.render(latestRiftPresentation)
        view.post { clampOverlayToDisplay() }
    }

    private fun createDraftWindows() {
        if (draftHud != null && draftDock != null) return

        val hud = DraftHudOverlayView(this) { module ->
            selectedDraftModule = module
            refreshDraftDock()
        }
        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            lockedHudFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(hud, hudParams)
        draftHud = hud
        draftHudParams = hudParams

        val dock = DraftHudControlView(
            this,
            onToggleAuto = { DraftHudPreviewSession.toggleAuto() },
            onNext = { DraftHudPreviewSession.next() },
            onStopPreview = {
                setDraftEditMode(false)
                DraftHudPreviewSession.stop()
            },
            onToggleEdit = { setDraftEditMode(!draftEditing) },
            onPreviousModule = {
                draftHud?.cycleSelection(-1)
                refreshDraftDock()
            },
            onNextModule = {
                draftHud?.cycleSelection(1)
                refreshDraftDock()
            },
            onScaleDown = {
                draftHud?.adjustSelectedScale(-0.05f)
                refreshDraftDock()
            },
            onScaleUp = {
                draftHud?.adjustSelectedScale(0.05f)
                refreshDraftDock()
            },
            onAlphaDown = {
                draftHud?.adjustSelectedAlpha(-0.08f)
                refreshDraftDock()
            },
            onAlphaUp = {
                draftHud?.adjustSelectedAlpha(0.08f)
                refreshDraftDock()
            },
            onToggleVisibility = {
                draftHud?.toggleSelectedVisibility()
                refreshDraftDock()
            },
            onResetLayout = {
                draftHud?.resetCurrentLayout()
                refreshDraftDock()
            },
        )
        val dockParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(4)
        }
        windowManager.addView(dock, dockParams)
        draftDock = dock
        draftDockParams = dockParams
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
                latestPreviewState = state
                overlay?.post { refreshOverlayMode() }
            }
        }
    }

    private data class OverlayTruth(
        val rift: RiftScreenPresentation,
        val draft: DraftHudPresentation,
    )

    private suspend fun refreshTruth() {
        val now = System.currentTimeMillis()
        val truth = runCatching {
            val graph = (application as LanerApplication).graph()
            val context = SourceRequestContext(
                nowEpochMillis = now,
                correlationId = "riftscreen-$now",
            )
            val schedule = graph.globalScheduleService.load(context)
            val target = LiveTargetSelector.select(schedule.matches, now)
                ?: return@runCatching OverlayTruth(
                    rift = RiftScreenPresentationMapper.waiting(
                        if (schedule.matches.isEmpty()) {
                            "没有可用赛事目录；RiftScreen 不猜比赛目标"
                        } else {
                            "没有可识别的 LIVE 目标"
                        }
                    ),
                    draft = DraftHudPresentation.inactive(),
                )
            val query = LiveMatchSourceQuery.from(target)
            val liveState = graph.liveMatchStateService.refresh(query, context)
            val snapshot = graph.liveSnapshotService.refresh(query, context)
            val gameId = snapshot.snapshot?.game?.gameId ?: liveState.state.currentGameId
            val timeline = gameId?.let { graph.liveTimelineService.load(it) }
            OverlayTruth(
                rift = RiftScreenPresentationMapper.from(target, liveState, snapshot, timeline),
                draft = DraftHudPresentationMapper.from(target, liveState, timeline),
            )
        }.getOrElse { error ->
            OverlayTruth(
                rift = RiftScreenPresentationMapper.waiting(
                    "读取失败 · ${error.message?.take(96)?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName}"
                ),
                draft = DraftHudPresentation.inactive(),
            )
        }

        latestRiftPresentation = truth.rift
        latestVerifiedDraft = truth.draft
        overlay?.post {
            overlay?.render(truth.rift)
            refreshOverlayMode()
        }
    }

    private fun effectiveDraftPresentation(): DraftHudPresentation = when {
        latestVerifiedDraft.active -> latestVerifiedDraft
        latestPreviewState.active -> latestPreviewState.presentation
        else -> DraftHudPresentation.inactive()
    }

    private fun refreshOverlayMode() {
        if (hostInForeground || !Settings.canDrawOverlays(this)) {
            hideOverlay()
            return
        }

        val draft = effectiveDraftPresentation()
        if (draft.active) {
            createDraftWindows()
            overlay?.visibility = View.GONE
            draftHud?.apply {
                visibility = View.VISIBLE
                render(draft)
            }
            draftDock?.visibility = View.VISIBLE
            refreshDraftDock()
        } else {
            if (draftEditing) setDraftEditMode(false)
            draftHud?.visibility = View.GONE
            draftDock?.visibility = View.GONE
            overlay?.apply {
                visibility = View.VISIBLE
                render(latestRiftPresentation)
                post { clampOverlayToDisplay() }
            }
        }
    }

    private fun setDraftEditMode(enabled: Boolean) {
        if (draftEditing == enabled) return
        if (enabled && latestPreviewState.autoPlay) DraftHudPreviewSession.toggleAuto()
        draftEditing = enabled
        draftHud?.setEditMode(enabled)
        draftHudParams?.let { params ->
            params.flags = if (enabled) editableHudFlags() else lockedHudFlags()
            draftHud?.let { hud -> runCatching { windowManager.updateViewLayout(hud, params) } }
        }
        refreshDraftDock()
    }

    private fun refreshDraftDock() {
        val hud = draftHud ?: return
        val dock = draftDock ?: return
        val presentation = effectiveDraftPresentation()
        if (!presentation.active) return
        selectedDraftModule = hud.selectedModule()
        dock.render(
            presentation = presentation,
            previewState = latestPreviewState,
            editing = draftEditing,
            selectedModule = selectedDraftModule,
            placement = hud.selectedPlacement(),
        )
        draftDockParams?.let { params ->
            runCatching { windowManager.updateViewLayout(dock, params) }
        }
    }

    private fun lockedHudFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun editableHudFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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
        if (this::windowManager.isInitialized) {
            overlay?.let { runCatching { windowManager.removeView(it) } }
            draftHud?.let { runCatching { windowManager.removeView(it) } }
            draftDock?.let { runCatching { windowManager.removeView(it) } }
        }
        overlay = null
        overlayParams = null
        draftHud = null
        draftHudParams = null
        draftDock = null
        draftDockParams = null
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
