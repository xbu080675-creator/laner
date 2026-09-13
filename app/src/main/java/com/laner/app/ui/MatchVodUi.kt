package com.laner.app.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.laner.app.data.vod.BilibiliNativePlaybackResolver
import com.laner.app.data.vod.BilibiliNativePlaybackSource
import com.laner.app.data.vod.BilibiliVodChapter
import com.laner.core.domain.ReplayAsset
import com.laner.core.domain.ReplayProvider

@Composable
fun MatchVodPanel(replays: List<ReplayAsset>) {
    val playable = replays.filter { it.sourceUrl.startsWith("http://") || it.sourceUrl.startsWith("https://") }
        .sortedWith(compareBy<ReplayAsset> { it.gameNumber ?: Int.MAX_VALUE }.thenBy { it.provider.name })
    if (playable.isEmpty()) return

    var selectedIndex by remember(playable.map { "${it.provider}:${it.gameNumber}:${it.sourceUrl}" }) {
        mutableIntStateOf(playable.indexOfFirst { it.provider == ReplayProvider.BILIBILI }.coerceAtLeast(0))
    }
    if (selectedIndex !in playable.indices) selectedIndex = 0
    val selected = playable[selectedIndex]
    val shape = CutCornerShape(topEnd = 14.dp, bottomStart = 10.dp)

    Column(
        Modifier.fillMaxWidth()
            .background(RiftPanel, shape)
            .border(1.dp, RiftLine, shape)
            .padding(14.dp),
    ) {
        Text("OFFICIAL MATCH VOD", color = RiftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("官方录像 / Laner 播放", color = RiftText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(
            "录像 metadata 来自 POST source；播放器只消费已验证入口。B站原生播放只临时解析 CDN descriptor，不下载、不保存整场录像。",
            color = RiftMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(playable) { index, replay ->
                val active = index == selectedIndex
                Text(
                    replayTabLabel(replay),
                    color = if (active) RiftCyan else RiftMuted,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            if (active) RiftPanelAlt else Color.Transparent,
                            CutCornerShape(topEnd = 7.dp, bottomStart = 5.dp),
                        )
                        .clickable { selectedIndex = index }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ReplaySourceHeader(selected)
        Spacer(Modifier.height(10.dp))
        when (selected.provider) {
            ReplayProvider.BILIBILI -> BilibiliVodPlayer(selected)
            else -> EmbeddedWebReplay(selected)
        }
    }
}

@Composable
private fun ReplaySourceHeader(replay: ReplayAsset) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(replay.title ?: replayTabLabel(replay), color = RiftText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "${replay.provider.name} · ${replay.locale ?: "default"} · ${replay.provenance.providerId}",
                color = RiftMuted,
                fontSize = 10.sp,
            )
        }
        Text(
            "官方原稿 ›",
            color = RiftCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(replay.sourceUrl))) }
            }.padding(8.dp),
        )
    }
}

@Composable
private fun BilibiliVodPlayer(replay: ReplayAsset) {
    var refreshNonce by remember(replay.sourceUrl) { mutableIntStateOf(0) }
    var playback by remember(replay.sourceUrl) { mutableStateOf<BilibiliNativePlaybackSource?>(null) }
    var chapters by remember(replay.sourceUrl) { mutableStateOf<List<BilibiliVodChapter>>(emptyList()) }
    var error by remember(replay.sourceUrl) { mutableStateOf<String?>(null) }
    var loading by remember(replay.sourceUrl) { mutableStateOf(true) }
    var webFallback by remember(replay.sourceUrl) { mutableStateOf(false) }
    var requestedSeekSecond by remember(replay.sourceUrl) { mutableIntStateOf(replay.offsetSeconds) }

    LaunchedEffect(replay.sourceUrl, refreshNonce) {
        loading = true
        error = null
        val playbackResult = runCatching {
            BilibiliNativePlaybackResolver.resolve(replay, forceRefresh = refreshNonce > 0)
        }
        playback = playbackResult.getOrNull()
        error = playbackResult.exceptionOrNull()?.message
        chapters = runCatching { BilibiliNativePlaybackResolver.chapters(replay) }.getOrDefault(emptyList())
        loading = false
    }

    if (webFallback) {
        EmbeddedWebReplay(replay, onBackToNative = { webFallback = false })
        return
    }

    when {
        loading -> PlayerStatus("正在获取 B站当前播放源…")
        playback == null -> {
            PlayerStatus(
                message = "原生播放源暂不可用 · ${error?.take(120) ?: "B站没有返回可用 progressive descriptor"}",
                actions = listOf(
                    "重新获取" to { refreshNonce += 1 },
                    "网页播放" to { webFallback = true },
                ),
            )
        }
        else -> {
            NativeBilibiliPlayer(
                source = playback!!,
                startSecond = requestedSeekSecond,
            )
            Spacer(Modifier.height(10.dp))
            if (chapters.isEmpty()) {
                Text("当前分P没有公开章节锚点；录像仍可播放。", color = RiftMuted, fontSize = 11.sp)
            } else {
                Text("OFFICIAL CHAPTERS / 录像看点", color = RiftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                chapters.forEach { chapter ->
                    Row(
                        Modifier.fillMaxWidth().clickable { requestedSeekSecond = chapter.fromSeconds }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatVodClock((chapter.fromSeconds - replay.offsetSeconds).coerceAtLeast(0)), color = RiftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text(chapter.title, color = RiftText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            if (chapter.teamName.isNotBlank()) Text(chapter.teamName, color = RiftMuted, fontSize = 10.sp)
                        }
                        Text("跳转 ›", color = RiftMuted, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "网页播放 fallback ›",
                color = RiftMuted,
                fontSize = 11.sp,
                modifier = Modifier.clickable { webFallback = true }.padding(vertical = 6.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativeBilibiliPlayer(
    source: BilibiliNativePlaybackSource,
    startSecond: Int,
) {
    val context = LocalContext.current
    val player = remember(source.bvid, source.cid, source.resolvedAtEpochMs) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BilibiliNativePlaybackResolver.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/"))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItems(source.segments.map { MediaItem.fromUri(it.primaryUrl) })
                prepare()
                playWhenReady = false
            }
    }

    LaunchedEffect(player, startSecond) {
        if (startSecond > 0) player.seekTo(startSecond * 1000L)
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
    )
}

@Composable
private fun EmbeddedWebReplay(
    replay: ReplayAsset,
    onBackToNative: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        loadUrl(replay.sourceUrl)
                    }
                },
                update = { view ->
                    if (view.url != replay.sourceUrl) view.loadUrl(replay.sourceUrl)
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "WebView 仅加载已验证官方/主流录像 URL；若站点禁止内嵌，请使用上方“官方原稿”。",
            color = RiftMuted,
            fontSize = 10.sp,
        )
        if (onBackToNative != null) {
            Text(
                "返回原生播放 ›",
                color = RiftCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBackToNative).padding(vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun PlayerStatus(
    message: String,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val shape = CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp)
    Column(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black, shape).border(1.dp, RiftLine, shape).padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = RiftText, fontSize = 11.sp)
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                actions.forEach { (label, action) ->
                    Text(label, color = RiftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = action))
                }
            }
        }
    }
}

private fun replayTabLabel(replay: ReplayAsset): String = buildString {
    append(replay.gameNumber?.let { "G$it" } ?: "SERIES")
    append(" · ")
    append(
        when (replay.provider) {
            ReplayProvider.BILIBILI -> "B站"
            ReplayProvider.YOUTUBE -> "YouTube"
            ReplayProvider.RIOT -> "Riot"
            ReplayProvider.OTHER -> "Other"
        }
    )
}

private fun formatVodClock(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
