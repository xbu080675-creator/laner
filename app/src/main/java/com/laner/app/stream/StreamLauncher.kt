package com.laner.app.stream

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.laner.app.overlay.RiftScreenController

enum class StreamRegion { MAINLAND, GLOBAL }

enum class StreamPlatform(
    val id: String,
    val displayName: String,
    val region: StreamRegion,
    val webUrl: String,
    val packages: List<String>,
    val deepLinks: List<String> = emptyList(),
) {
    BILIBILI(
        id = "bilibili",
        displayName = "Bilibili 官方直播",
        region = StreamRegion.MAINLAND,
        webUrl = "https://live.bilibili.com/6",
        packages = listOf("tv.danmaku.bili"),
        deepLinks = listOf("bilibili://live/6"),
    ),
    HUYA(
        id = "huya",
        displayName = "虎牙 LPL",
        region = StreamRegion.MAINLAND,
        webUrl = "https://www.huya.com/lpl",
        packages = listOf("com.duowan.kiwi", "com.huya.kiwi"),
        deepLinks = listOf(
            "https://www.huya.com/660000?source=android&pid=1346609715&hyaction=live&uid=1346609715&platform=7",
        ),
    ),
    LOL_ESPORTS(
        id = "lol_esports",
        displayName = "LoL Esports 官方",
        region = StreamRegion.GLOBAL,
        webUrl = "https://lolesports.com/en-US/",
        packages = emptyList(),
    ),
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube · LoL Esports",
        region = StreamRegion.GLOBAL,
        webUrl = "https://www.youtube.com/@lolesports/live",
        packages = listOf("com.google.android.youtube"),
    ),
    TWITCH(
        id = "twitch",
        displayName = "Twitch · Riot Games",
        region = StreamRegion.GLOBAL,
        webUrl = "https://www.twitch.tv/riotgames",
        packages = listOf("tv.twitch.android.app"),
    ),
    X_LOLESPORTS(
        id = "x_lolesports",
        displayName = "X · @lolesports",
        region = StreamRegion.GLOBAL,
        webUrl = "https://x.com/lolesports",
        packages = listOf("com.twitter.android"),
    ),
}

/** Android PLATFORM adapter. It never reads or mutates esports source arbitration. */
object StreamLauncher {
    private const val PREFS = "laner_stream_launcher"
    private const val KEY_PENDING_PLATFORM = "pending_platform"

    fun watch(
        activity: Activity,
        overlayController: RiftScreenController,
        platform: StreamPlatform,
    ): Boolean {
        if (!overlayController.hasOverlayPermission()) {
            savePending(activity, platform.id)
            overlayController.requestOverlayPermission(activity)
            return false
        }
        clearPending(activity)
        overlayController.start()
        return openPlatform(activity, platform)
    }

    /** Resume a user-requested watch action only after returning from overlay settings. */
    fun resumePendingIfReady(
        activity: Activity,
        overlayController: RiftScreenController,
    ): Boolean {
        if (!overlayController.hasOverlayPermission()) return false
        val pending = prefs(activity).getString(KEY_PENDING_PLATFORM, null) ?: return false
        val platform = StreamPlatform.entries.firstOrNull { it.id == pending }
        clearPending(activity)
        if (platform == null) return false
        overlayController.start()
        Handler(Looper.getMainLooper()).postDelayed({ openPlatform(activity, platform) }, 180L)
        return true
    }

    fun isInstalled(context: Context, platform: StreamPlatform): Boolean =
        platform.packages.any { isPackageInstalled(context, it) }

    internal fun launchCandidates(platform: StreamPlatform, installedPackages: Set<String>): List<Pair<String?, String>> = buildList {
        val installed = platform.packages.filter(installedPackages::contains)
        installed.forEach { packageName ->
            platform.deepLinks.forEach { deepLink -> add(packageName to deepLink) }
        }
        installed.forEach { packageName -> add(packageName to platform.webUrl) }
        add(null to platform.webUrl)
    }

    private fun openPlatform(context: Context, platform: StreamPlatform): Boolean {
        val installed = platform.packages.filterTo(linkedSetOf()) { isPackageInstalled(context, it) }
        return launchCandidates(platform, installed).any { (packageName, uri) ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (packageName != null) setPackage(packageName)
            }
            tryStart(context, intent)
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
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

    private fun savePending(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PENDING_PLATFORM, value).apply()
    }

    private fun clearPending(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_PLATFORM).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
