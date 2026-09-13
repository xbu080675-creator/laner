# LNR-022 — Block 3 Watch Hub + Player Preflight

- 日期：2026-09-13
- 执行者：OpenAI ChatGPT
- 状态：IN PROGRESS
- 基线：`main@4431a64446991581ca58df068a05e5269f1607d2`
- 分支：`feature/block3-watch-hub-player`

## 1. 目标

1:1 迁移 legacy RiftLab 的观赛入口与赛后播放能力，但不迁回旧 `MatchSessionStore`。新实现必须继续遵守全球统一架构：直播平台是 Android PLATFORM capability；Replay/VOD metadata 是 POST_MATCH_SOURCE；二者不得反向成为赛事事实权威。

## 2. Feature Baseline

本块负责：

- `LIVE-031` Bilibili 观赛入口
- `LIVE-032` 虎牙观赛入口
- `LIVE-033` LoL Esports / YouTube / Twitch / X 观赛入口
- `LIVE-034` 直播入口与赛事数据完全解耦
- `POST-013` Bilibili 官方 VOD 映射
- `POST-016` APP 内 Media3 播放
- `POST-017` APP 内 WebView fallback

并与已有 `POST-014/015/018` Replay Domain 协同，不重写 Riot/YouTube Replay source ownership。

## 3. Legacy 行为基线

### Watch Hub
- Bilibili / Huya / LoL Esports / YouTube / Twitch / X；
- 已安装 APP 优先，失败后网页；只查询已知包，不申请 `QUERY_ALL_PACKAGES`；
- 若 Overlay 权限缺失，保存 pending watch action，授权返回后先启动 RiftScreen，再继续平台跳转；
- `GAME_LIVE` 显示 LIVE，EVENT_LIVE/BETWEEN_GAMES 显示 ON AIR，其余显示直播入口；
- 平台跳转与赛事数据完全解耦。

### VOD / Player
- Bilibili 只保存/缓存 BVID/CID/分P/章节 metadata，不下载整场录像；
- 播放时临时解析当前 CDN descriptor；
- 原生 Media3 为主；无法拿到 progressive playback 时保留 WebView / 官方原稿 fallback；
- 支持 G1/G2/... 分P与 chapter seek；
- 不从录像章节或终局比分伪造 canonical gameplay facts。

## 4. 架构边界

```text
Core/Application canonical match truth
          |                       POST replay metadata
          v                               |
Watch presentation                        v
          |                       Bilibili VOD Adapter
          v                               |
Android Watch Hub                  Media Playback Adapter
(StreamLauncher)                 (Media3 / WebView fallback)
```

禁止：
- UI 直接调用 Riot/LPL Provider；
- StreamLauncher 影响 Source arbitration；
- 按赛区复制 Watch Hub 业务模块；
- Bilibili 搜索结果写成 canonical match facts；
- 下载/持久化完整视频；
- 为打开直播申请全包可见性。

## 5. 计划文件

- `app/.../stream/StreamLauncher.kt`
- `app/.../ui/LiveBroadcastHub.kt`
- `app/.../data/vod/BilibiliVodSource.kt`
- `app/.../data/vod/BilibiliNativePlayback.kt`
- `app/.../ui/MatchVodUi.kt`
- `MainActivity.kt` / `LanerRoot.kt` / `PostMatchScreen.kt`
- `AndroidManifest.xml`
- `app/build.gradle.kts`
- Android unit tests + Feature Baseline / implementation status / immutable development record

## 6. DoD

Automated:
- architecture boundary gate PASS
- Domain/Application tests PASS
- Android adapter unit tests PASS
- debug compile PASS
- debug APK upload PASS
- routing/search/parser deterministic tests PASS where pure logic is available

External:
- app/deep-link fallback, overlay-permission return path, Media3 CDN playback and WebView playback require real Android/network evidence; until proven they remain `WAITING EXTERNAL TEST` and must not be labeled DONE.
