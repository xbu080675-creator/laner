package com.laner.app.watch

import com.laner.core.application.WatchDestination
import com.laner.core.application.WatchRegion

/** One global Watch catalog. Region is display metadata only, never a routing branch. */
object AndroidWatchCatalog {
    val destinations: List<WatchDestination> = listOf(
        WatchDestination("bilibili", "Bilibili 官方直播", WatchRegion.MAINLAND, nativeAppSupported = true),
        WatchDestination("huya", "虎牙 LPL", WatchRegion.MAINLAND, nativeAppSupported = true),
        WatchDestination("lol_esports", "LoL Esports 官方", WatchRegion.GLOBAL, nativeAppSupported = false),
        WatchDestination("youtube", "YouTube · LoL Esports", WatchRegion.GLOBAL, nativeAppSupported = true),
        WatchDestination("twitch", "Twitch · Riot Games", WatchRegion.GLOBAL, nativeAppSupported = true),
        WatchDestination("x_lolesports", "X · @lolesports", WatchRegion.GLOBAL, nativeAppSupported = true),
    )
}
