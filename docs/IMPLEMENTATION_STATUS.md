# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Working branch: `feature/lnr-015-post-results-replay`
- Project phase: `M1 / Feature Migration`
- Business implementation: `STARTED`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy source version: `1.0.0-dev.94 / versionCode 94`

## Task Status

| Task | Title | Status |
|---|---|---|
| LNR-000 | 工程立宪与基线初始化 | DONE |
| LNR-001 | 旧工程功能基线提取 | DONE |
| LNR-002 | 旧工程架构与技术债审计 | DONE |
| LNR-003 | 新架构冻结 | DONE |
| LNR-004 | 工程骨架与 CI Gate | DONE |
| LNR-005 | 三阶段一级架构轴确立 | DONE |
| LNR-006 | 用户角色 × 比赛阶段产品矩阵 | DONE |
| LNR-007 | 极简 × 酷炫体验北极星 | DONE |
| LNR-008 | 四类数据/API 源架构 | DONE |
| LNR-009 | 全球赛事统一管理架构 | DONE |
| LNR-010 | 全球赛事目录与赛程中心 | WAITING EXTERNAL TEST |
| LNR-011 | PRE Roster / Staff / Form / H2H | WAITING EXTERNAL TEST |
| LNR-012 | Standings / Qualification / Tournament Edition | WAITING EXTERNAL TEST |
| LNR-013 | LIVE Match State / Provider Arbitration / Unified Event / Timeline | DONE |
| LNR-014 | LIVE Source Adapters / Local Persistence / Composition Wiring | WAITING EXTERNAL TEST |
| LNR-015 | Global POST Result / Archive / Historical Timeline / Replay | TESTING |

## Current Truth

Laner 已形成连续的 PRE → LIVE → POST 基础链：

1. 全球赛事目录 / 全球赛程；
2. 单场 PRE Roster / Starting Roster Evidence / Staff / Recent Form / H2H；
3. Tournament Edition / Standings / Championship Points boundary / Qualification mechanism；
4. LIVE Core/Application 权威状态、来源仲裁、统一事件与 Timeline；
5. LIVE Android 本地 State/Timeline 持久化、Composition Root、赛中 Application-truth 页面；
6. Global POST Result / Replay / Historical Timeline / device-local archive 基础链。

当前已验证/已实现存在：

### Core / Global Identity
- `:core:domain` 纯 Kotlin Domain；
- `:core:application` 纯 Kotlin Application/Port；
- `:app` Android/Compose Composition Root；
- PRE/LIVE/POST 三阶段 Android 页面；
- Provider raw ID 不成为 MatchId/GameId；
- `GameIdentity.canonical(matchId, gameNumber)` 为 canonical GameId 唯一生成规则；
- `ProviderMatchIdentityRepository` 保存 canonical MatchId ↔ provider external event/match identity。

### LIVE
- `LiveMatchStateReducer / LiveMatchStateService`；
- `GameTimeline / LiveTimelineService`；
- Android `JsonLiveMatchStateRepository / JsonLiveTimelineRepository`；
- `schema_version=1`、SHA-256 文件名、temp + atomic replace；
- LIVE 页面只消费 Application truth；
- 无实时 Source 时明确 `UNAVAILABLE / last-known`；
- Cito 在线链按用户要求 `WAITING EXTERNAL TEST / DEFERRED`。

### Global POST
- 强类型 `SeriesResult / CompletedGameRecord / PostTeamStats / PostPlayerStats / VerifiedPostAward / ReplayAsset`；
- `PostMatchService` 按 `PostSourceCapability` 编排来源，Application 不包含 LPL/LCK/LEC/LCP 赛区业务分支；
- `JsonPostMatchArchiveRepository` 保存已验证 Result/Game，外部事实缺失时才 fallback；
- archive 不参与和新 Provider 的冲突投票，不把 LOCAL cache 冒充官方事实；
- `VerifiedAwardsMirrorSource` 提供 provenance-preserving Awards；
- `RiotGlobalResultSource` 从 Riot global schedule 提供跨区域/国际赛事 Series Result baseline；
- `RiotGlobalReplaySource` 从 Riot getEventDetails 提供跨区域/国际赛事 Replay metadata；
- `RiotGlobalHistoricalTimelineSource` 从 Riot LiveStats window 按需恢复真实历史过程帧；
- 历史 Timeline 不插值；上游不再保留历史窗口时保持缺口；
- LIVE 与 POST 真实帧可合并到同一个 canonical GameTimeline；PRE/AI 仍被 Domain 拒绝；
- `PostTimelineService` 二次校验 canonical MatchId/GameId/gameNumber/sourceClass，错误来源不得污染 Timeline；
- POST 页面只有用户选择 G1/G2/... 后才触发 historical backfill，不在页面打开时暴力扫描全部小局；
- POST 页面展示 verified Result / Replay metadata / Awards / historical frame coverage；
- LPL TJStats 历史链只保留为区域补充 Adapter，且 credential 外部注入；它不构成主架构。

## 尚未完成且不得误报

- Riot `LOL_ESPORTS_API_KEY` credentialed online POST Result/EventDetails/LiveStats 链尚未形成 LNR-015 真实在线验收证据；
- Global per-game `CompletedGameRecord` 仍要求明确 per-game winner evidence；未确认赢家时不得通过 gold/kills 推断；
- 每局 BP / 终局装备 / 完整 objective result 尚未全球迁移；
- Timeline 事件筛选、TeamFightWindow 聚合、VOD↔Timeline 对齐尚未完成；
- Bilibili Replay supplement 尚未迁移；
- Media3 / WebView 播放器属于后续 Android Adapter；
- 真实 Android 设备上的 POST 页面与历史恢复链尚待外部验收；
- Cito 在线链仍 DEFERRED，不影响 POST 主线。

## Verification Evidence

### LNR-010
- run `34687580424`：PASS；状态 `WAITING EXTERNAL TEST`。

### LNR-011
- run `34688581238`：Core PASS / Android compile FAIL，失败保留；
- run `34688715420`：Architecture/Core/Android 全 PASS；状态 `WAITING EXTERNAL TEST`。

### LNR-012
- run `34690235942`：Architecture/Core PASS，Android compile FAIL，失败保留；
- final branch run `34690520095`：全 PASS；
- PR #4 run `34690577554`：全 PASS；
- merge `07d2b4a3d5094b81a72316bc8f0eba6486d4adb0`。

### LNR-013
- run `34691364933` 暴露 stale-order bug，失败保留；
- fix `22668b37d22be5969ec59c99ac687f57c52a1ad3`；
- run `34691458209`：PASS；
- final run `34691766133`：PASS；
- PR #5 run `34691843981`：PASS；
- merge `996cd1275729c324aca7a8d8c6b145c2f9206fc8`。

### LNR-014
- run `34692037250`：Core PASS / Android unit tests FAIL，暴露 JUnit4 expression-body 非 void 测试签名；失败保留；
- fix `86ca106cf6372a4f23f4f83faaa997eef3f5bad5`；
- run `34692350405`：Architecture/Core/App unit/Android build 全 PASS；
- final code run `34692936906`：Architecture / Domain+Application / Android Adapter tests / Android debug compile 全 PASS；
- Cito online：`WAITING EXTERNAL TEST / DEFERRED`。

### LNR-015
- run `34693494001`：Domain/Application tests FAIL；根因是测试 fixture 使用非法错误码格式，失败保留；
- fix `ca805e77f89bdb65311c24e5c12e3ed056d7e83a`；run `34693630753` 全 PASS；
- global POST capability routing commit `e3de01052602d7633368acd003c1ab0fd0f14401` / run `34694930111` 全 PASS；
- global Riot identity/result/replay baseline commit `ea9b6e576fe22f4459c0c55c89f805a3d556285e` / run `34695412163` 全 PASS；
- run `34695777894`：Domain/Application tests FAIL，暴露 Domain `TimelineSnapshotPoint` 仍只允许 LIVE source 的真实边界遗漏；
- root fix `f60f87be12e676e3623bac73d586a144f84b3f17`：Timeline 允许 LIVE/POST 真实事实源，仍拒绝 PRE/AI；
- regression head `7451c302f106fa1f4d636a8ac77f1580b8dd672c` / run `34695924994` 全 PASS；
- current code/UI head `0681c3b20f2e6cad4cd6fb52bf90a4912e299608` / run `34696081645`：Architecture / Domain+Application / Android Adapter unit tests / Android debug compile 全 PASS。

## Current Delivery Boundary

LNR-015 当前属于 `TESTING`：全球 POST 自动化/编译证据已经成立，但真实 credentialed Riot online fetch 与 Android real-device 仍未验收。因此收口时若代码/文档 exact-head 与 PR Gate 继续全绿，任务应进入 `WAITING EXTERNAL TEST`，而不是 `DONE`。

## Next

1. 同步 `FEATURE_BASELINE / TESTING / TROUBLESHOOTING / CHANGELOG / module README`；
2. 记录 historical Timeline source-class 失败与永久回归；
3. final exact-head CI；
4. PR Gate；
5. 合并后回填 PR/merge/final CI；
6. 进入 LNR-016 Android RiftScreen / Watch / Player / OTA；
7. LNR-015 online Provider/real-device evidence 后续独立补证。
