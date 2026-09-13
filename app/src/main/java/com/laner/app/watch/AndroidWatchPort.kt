package com.laner.app.watch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.laner.app.overlay.RiftScreenController
import com.laner.core.application.WatchDestination
import com.laner.core.application.WatchLaunchResult
import com.laner.core.application.WatchLaunchStatus
import com.laner.core.application.WatchPort

/**
 * Android adapter for Watch Port.
 *
 * The catalog is global and launch behavior is identical for every destination. Platform package,
 * deep-link and web fallback details stay here instead of leaking into Core or esports data flow.
 */
class AndroidWatchPort(
    private val context: Context,
    private val riftScreenController: RiftScreenController,
) : WatchPort {
    private data class LaunchSpec(
        val webUrl: String,
        val packages: List<String> = emptyList(),
        val deepLinks: List<String> = emptyList(),
    )

    private val specs = mapOf(
        "bilibili" to LaunchSpec(
            webUrl = "https://live.bilibili.com/6",
            packages = listOf("tv.danmaku.bili"),
            deepLinks = listOf("bilibili://live/6"),
        ),
        "huya" to LaunchSpec(
            webUrl = "https://www.huya.com/lpl",
            packages = listOf("com.duowan.kiwi", "com.huya.kiwi"),
            deepLinks = listOf(
                "https://www.huya.com/660000?source=android&pid=1346609715&hyaction=live&uid=1346609715&platform=7",
            ),
        ),
        "lol_esports" to LaunchSpec(webUrl = "https://lolesports.com/en-US/"),
        "youtube" to LaunchSpec(
            webUrl = "https://www.youtube.com/@lolesports/live",
            packages = listOf("com.google.android.youtube"),
        ),
        "twitch" to LaunchSpec(
            webUrl = "https://www.twitch.tv/riotgames",
            packages = listOf("tv.twitch.android.app"),
        ),
        "x_lolesports" to LaunchSpec(
            webUrl = "https://x.com/lolesports",
            packages = listOf("com.twitter.android"),
        ),
    )

    override fun destinations(): List<WatchDestination> = AndroidWatchCatalog.destinations

    override fun isInstalled(destinationId: String): Boolean =
        specs[destinationId]?.packages.orEmpty().any(::isPackageInstalled)

    override fun launch(destinationId: String): WatchLaunchResult {
        val destination = destinations().firstOrNull { it.id == destinationId }
            ?: return unsupported(destinationId, "未知观赛入口")
        val spec = specs[destinationId]
            ?: return unsupported(destinationId, "观赛入口缺少 Android launch spec")

        if (!Settings.canDrawOverlays(context)) {
            savePending(destination.id)
            if (!requestOverlayPermission()) {
                clearPending()
                return unsupported(destination.id, "系统无法打开悬浮窗授权页面")
            }
            return WatchLaunchResult(
                status = WatchLaunchStatus.PERMISSION_REQUIRED,
                destinationId = destination.id,
                message = "需要先授予悬浮窗权限；授权后会继续打开 ${destination.displayName}",
            )
        }

        clearPending()
        riftScreenController.start()
        return if (open(spec)) {
            WatchLaunchResult(
                status = WatchLaunchStatus.OPENED,
                destinationId = destination.id,
                message = "已打开 ${destination.displayName}；RiftScreen 与直播平台保持解耦",
            )
        } else {
            unsupported(destination.id, "系统中没有可处理该观赛入口的应用或浏览器")
        }
    }

    override fun resumePendingIfReady(): WatchLaunchResult? {
        if (!Settings.canDrawOverlays(context)) return null
        val destinationId = prefs().getString(KEY_PENDING_DESTINATION, null) ?: return null
        val destination = destinations().firstOrNull { it.id == destinationId }
        val spec = specs[destinationId]
        clearPending()
        if (destination == null || spec == null) return unsupported(destinationId, "待恢复观赛入口已失效")

        riftScreenController.start()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (!open(spec)) {
                    Log.w(TAG, "$ERROR_RESUME_OPEN_FAILED destination=${destination.id}")
                }
            },
            RESUME_DELAY_MS,
        )
        return WatchLaunchResult(
            status = WatchLaunchStatus.RESUMING,
            destinationId = destination.id,
            message = "悬浮窗权限已就绪，正在继续打开 ${destination.displayName}",
        )
    }

    private fun open(spec: LaunchSpec): Boolean {
        val installed = spec.packages.filter(::isPackageInstalled)
        installed.forEach { packageName ->
            spec.deepLinks.forEach { deepLink ->
                if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(packageName))) return true
            }
        }
        installed.forEach { packageName ->
            if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(spec.webUrl)).setPackage(packageName))) return true
        }
        return tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(spec.webUrl)))
    }

    private fun tryStart(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Log.d(TAG, "$ERROR_HANDLER_MISSING uri=${intent.data}", error)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "$ERROR_HANDLER_SECURITY uri=${intent.data}", error)
            false
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun requestOverlayPermission(): Boolean = try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, ERROR_PERMISSION_ACTIVITY_MISSING, error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, ERROR_PERMISSION_ACTIVITY_SECURITY, error)
        false
    }

    private fun savePending(destinationId: String) {
        prefs().edit().putString(KEY_PENDING_DESTINATION, destinationId).apply()
    }

    private fun clearPending() {
        prefs().edit().remove(KEY_PENDING_DESTINATION).apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun unsupported(destinationId: String?, message: String) = WatchLaunchResult(
        status = WatchLaunchStatus.UNSUPPORTED,
        destinationId = destinationId,
        message = message,
    )

    private companion object {
        const val TAG = "[Laner:Watch]"
        const val ERROR_HANDLER_MISSING = "LNR-WATCH-LAUNCH-001"
        const val ERROR_HANDLER_SECURITY = "LNR-WATCH-LAUNCH-002"
        const val ERROR_PERMISSION_ACTIVITY_MISSING = "LNR-WATCH-PERMISSION-001"
        const val ERROR_PERMISSION_ACTIVITY_SECURITY = "LNR-WATCH-PERMISSION-002"
        const val ERROR_RESUME_OPEN_FAILED = "LNR-WATCH-RESUME-001"
        const val PREFS_NAME = "laner_watch_port"
        const val KEY_PENDING_DESTINATION = "pending_destination"
        const val RESUME_DELAY_MS = 180L
    }
}
