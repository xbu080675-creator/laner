package com.laner.app.watch

import com.laner.core.application.WatchDestination
import com.laner.core.application.WatchRegion

/** One global Watch catalog. Region is display metadata only, never a routing branch. */
object AndroidWatchCatalog {
    val destinations: List<WatchDestination> = listOf(
        WatchDestination("bilibili", "Bilibili 官方直播", WatchRegion.MAINLAND),
        WatchDestination("huya", "虎牙 LPL", WatchRegion.MAINLAND),
        WatchDestination("lol_esports", "LoL Esports 官方", WatchRegion.GLOBAL),
        WatchDestination("youtube", "YouTube · LoL Esports", WatchRegion.GLOBAL),
        WatchDestination("twitch", "Twitch · Riot Games", WatchRegion.GLOBAL),
        WatchDestination("x_lolesports", "X · @lolesports", WatchRegion.GLOBAL),
    )
}
