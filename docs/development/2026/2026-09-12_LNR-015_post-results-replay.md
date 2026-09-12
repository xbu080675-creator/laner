# LNR-015 — Global POST Result / Game Archive / Stats / Historical Timeline / Replay

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@a530a8a88d9cc1ee649f0d95732b73e42675510e`
- Working branch: `feature/lnr-015-post-results-replay`
- Current status: `TESTING` — final exact-head / PR Gate pending

## Request / Goal
完整迁移旧 RiftLab 的赛后能力基础，但不复制旧 `MatchDetailRepository` / `MatchTimelineStore` God Store。全局原则：**全局管理、全局接管；赛区只是数据维度，不是架构边界。**

## Constitution Preflight
`PASS`。读取工程宪法、开发计划、main/branch baseline、旧 POST 相关源码与历史行为证据。

## Scope
In scope：Global Series Result、POST 强类型 Domain、Awards、Replay metadata、provider identity mapping、device-local archive、Global historical process backfill、Application orchestration、POST Compose、regional supplement boundary。

Non-goals：Media3/WebView、HUD、AI 总结、Bilibili supplement、无证据 per-game winner 推断、按赛区复制 Domain/Application。

## Legacy Evidence
- `CompletedGameArchive`: 赛后可独立恢复，APP 当时未在线也可补；缺失不合成。
- `MatchDetailRepository`: 旧 God Store 不复制。
- `RiotLiveStatsHistoryResolver`: 全球真实历史 window，不插值；finished 真帧保留。
- `GlobalVerifiedAwardsProvider`: Awards 保留 provenance，不从 KDA 推 MVP。
- `RiotVodRepository`: 全球 VOD metadata 与播放器分离。
- `BilibiliVodRepository`: metadata only，不下载/托管。
- `LplHistoricalPostMatchResolver`: 仅作为区域 supplement 行为证据；旧 auth 不迁移。

## Locked Fact Boundaries
`Series Result != Completed Game != Player Stats != Awards != Replay != Historical Timeline`。

- FINAL Series winner 与比分必须一致；PARTIAL 不得提前 winner。
- stat missing = null，不是 0。
- DERIVED 禁止发布 MVP/POG。
- Historical Timeline 只收真实帧，不插值。
- raw Provider ID 不成为 canonical Match/Game ID。
- 无 explicit per-game winner 不得用 gold/kills/objectives 猜胜方。

## Architecture
`PostMatchQuery → PostSourceCapability → Global/Regional/Archive → PostMatchService → canonical POST facts → UI`。

历史过程：`PostTimelineSourcePort → PostTimelineService canonical validation → LiveTimelineService → canonical GameTimeline`。

UI 不直接访问 Provider / Repository。

## Implemented
### Domain
`SeriesResult`, `CompletedGameRecord`, nullable `PostPlayerStats`, `PostTeamStats`, `PostDraftSide`, `VerifiedPostAward`, `ReplayAsset`, `PostMatchBundle`, `GameIdentity.canonical()`；ADR-003；Timeline 接受 LIVE/POST 事实源、拒绝 PRE/AI。

### Application
独立 Result/Game/Award/Replay Ports；`PostSourceCapability` Global-first routing；`PostMatchService`；fallback-only `PostMatchArchiveRepository`；`ProviderMatchIdentityRepository`；`PostTimelineSourcePort/PostTimelineService`；错误历史 identity → `LNR-APP-POST-005`；多 Replay 共存；显式 READY/DEGRADED/CONFLICT/UNAVAILABLE。

### Android / UI
- `JsonPostMatchArchiveRepository` 与 `JsonProviderMatchIdentityRepository`：schema version、SHA-256 filename、atomic move、corruption/unsupported schema 显式失败。
- POST 只选择 COMPLETED 目标；不借 PRE score 冒充正式 POST Result。
- `VerifiedAwardsMirrorSource`。
- `RiotGlobalResultSource`：Riot global schedule 覆盖赛事统一 Series baseline。
- `RiotGlobalReplaySource`：global EventDetails replay metadata。
- `RiotGlobalHistoricalTimelineSource`：global LiveStats 真帧；不插值；finished 强制保留。
- `PostTimelineCard`：用户选择具体 Gx 才 backfill，不自动扫描整场。
- Replay/Awards/Timeline 均展示 provenance/状态。

### Regional supplement
`LplHistoricalPostMatchSource` 仅为 supplement，不是主架构；TJStats credential 只允许 `LPL_TJSTATS_AUTH / lplTjstatsAuth` 外部注入；无 explicit winner 不发布 CompletedGame。

## Design Corrections
1. Awards mirror 只有 gameNumber 时不造 provider-derived GameId；ADR-003 后统一 canonical GameId。
2. 开发中发现 LPL-first drift，立即改为 Global-first capability routing；区域源只做 supplement。
3. run `34695777894` 暴露 Domain Timeline 只允许 LIVE，修为 LIVE/POST factual sources，PRE/AI 仍拒绝。
4. Historical sampling 从机械 `elapsed % 2` 修正为“距上次已存帧 ≥2s”，finished 真帧始终保留，不插值。

## Tests / Evidence
- run `34693494001`: Architecture PASS / Core FAIL；fixture 使用非法 `LNR-SRC-POST-TEST`。
- fix `ca805e77f89bdb65311c24e5c12e3ed056d7e83a`; run `34693630753` 全 Gate PASS。
- Global capability routing commit `e3de01052602d7633368acd003c1ab0fd0f14401`; run `34694930111` PASS。
- Global identity/result/replay commit `ea9b6e576fe22f4459c0c55c89f805a3d556285e`; run `34695412163` PASS。
- run `34695777894`: Architecture PASS / Core FAIL；POST Timeline source-class contract mismatch。
- fix `f60f87be12e676e3623bac73d586a144f84b3f17`; run `34695924994` 全 Gate PASS。
- code/UI exact-head `0681c3b20f2e6cad4cd6fb52bf90a4912e299608`; run `34696081645` 全 Gate PASS。
- governance/test-evidence head `420f21a5ddab70cde7449ebe7fb50abec40416b0`; run `34696404045` 全 Gate PASS。
- final documentation exact-head: **pending**。
- PR Gate: **pending**。
- Online Riot integration: `WAITING EXTERNAL TEST`。
- Android real-device POST verification: `WAITING EXTERNAL TEST`。

Automated regressions cover final/partial Series invariants, nullable stats, Derived Award rejection, canonical GameId, capability routing, archive fallback-only semantics, LCK+Worlds same global Result/Replay/Timeline parsers, provider identity persistence, LIVE+POST Timeline coexistence, PRE/AI rejection, wrong historical identity rejection, real-frame parsing and POST target selection。

## Security / Secrets
旧 TJStats auth 不迁移；Riot/TJStats credentials 仅外部注入；raw provider IDs 只在 mapping/provenance/Adapter；不下载/托管 VOD bytes；UI 不接触 credential/client。

## Performance
POST 打开不自动扫历史 Timeline；仅用户选 Gx 后串行读取窗口；连续空窗口/3h hard cap 防无限扫描；本地 Timeline 可复用。

## Known Issues / Waiting External Test
- Riot credentialed Result/EventDetails/LiveStats 在线证据待补。
- Android 实机 POST UI 待验收。
- Global per-game CompletedGame winner 统一可靠映射仍 `IN PROGRESS`。
- Player 跨更名 identity 后续需 Global Player Mapping。
- Bilibili supplement、BP、items、event filtering、TeamFight aggregation 属后续明确条目。

## Rollback
任务在独立 feature branch；POST archive/identity 使用独立存储；Timeline source-class 仅扩展到 POST factual source 且 PRE/AI 仍拒绝。可按任务 commits 回滚，无需修改 PRE/LIVE schema。

## Status Sync
`DEVELOPMENT_PLAN / IMPLEMENTATION_STATUS / FEATURE_BASELINE / TESTING / TROUBLESHOOTING / CHANGELOG / core module READMEs` 已同步。功能状态严格区分自动 PASS 与外部验收。

## Compliance Conclusion
Core 无 Android/Provider API；Application Global-first、不复制赛区业务；Adapter 封装 raw IDs/transport；UI 只消费 Application；缺失事实保持未知；失败历史与永久回归已归档。

**当前结论：自动代码与治理材料已完成，等待 final documentation exact-head Gate + PR Gate。通过后任务状态进入 `WAITING EXTERNAL TEST`，不得写成 DONE。**
