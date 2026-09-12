package com.laner.app.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Android-only control surface for the RiftScreen foreground overlay service. */
class RiftScreenController(private val context: Context) {
    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun isRunning(): Boolean = RiftScreenOverlayService.isRunning

    fun requestOverlayPermission(activity: Activity) {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"),
            )
        )
    }

    fun start(): Boolean = startAction(RiftScreenOverlayService.ACTION_SHOW)

    fun startDraftPreview(): Boolean = startAction(RiftScreenOverlayService.ACTION_DRAFT_PREVIEW_AUTO)

    fun stopDraftPreview() {
        if (!isRunning()) return
        context.startService(
            Intent(context, RiftScreenOverlayService::class.java)
                .setAction(RiftScreenOverlayService.ACTION_DRAFT_PREVIEW_STOP),
        )
    }

    fun stop() {
        if (!isRunning()) return
        context.startService(
            Intent(context, RiftScreenOverlayService::class.java)
                .setAction(RiftScreenOverlayService.ACTION_STOP),
        )
    }

    fun setHostForeground(foreground: Boolean) {
        RiftScreenOverlayService.setHostForeground(context, foreground)
    }

    private fun startAction(action: String): Boolean {
        if (!hasOverlayPermission()) return false
        ContextCompat.startForegroundService(
            context,
            Intent(context, RiftScreenOverlayService::class.java).setAction(action),
        )
        return true
    }
}
