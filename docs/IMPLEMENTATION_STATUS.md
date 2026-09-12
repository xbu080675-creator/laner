# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Working branch: `feature/lnr-013-live-state-timeline`
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
| LNR-014 | LIVE Source Adapters / Local Persistence / Composition Wiring | TODO |

## Current Truth

Laner 已进入 M1 真实功能迁移。当前形成四层连续基础：

1. 全球赛事目录 / 全球赛程；
2. 单场 PRE Roster / Starting Roster Evidence / Staff / Recent Form / H2H；
3. Tournament Edition / Standings / Championship Points boundary / Qualification mechanism；
4. LIVE Core/Application 真相层：权威 lifecycle、Provider Arbitration、标准事件与 provider-neutral Timeline contract。

当前已验证存在：

- `:core:domain` 纯 Kotlin Domain；
- `:core:application` 纯 Kotlin Application/Port；
- `:app` Android/Compose Composition Root；
- PRE/LIVE/POST 三阶段 Android 壳；
- Global Competition / Team / Player / Edition / Match / Game IDs；
- Source Class / Provenance / Authority / Freshness / Revision；
- AI 与事实路径硬隔离；
- PRE `GlobalScheduleService / PreMatchContextService / CompetitionStructureService`；
- Riot PRE Schedule / Roster / Tournament / Standings Adapters；
- normalized Starting Roster / Staff Adapters；
- Tournament Edition device archive；
- PRE Competition Structure Panel；
- `LiveMatchStateReducer`；
- `LiveMatchStateService`；
- LIVE Source/State Repository Ports；
- standardized `MatchStateChanged` + typed `DraftActionType`；
- `GameTimeline / TimelineSnapshotPoint / semanticKey()`；
- `LiveTimelineRepository / LiveTimelineService`；
- duplicate/reconnect semantic dedupe；
- out-of-order Timeline replay；
- same-second snapshot provenance arbitration；
- GitHub Actions Architecture Gate + Core Tests + Android Debug Compile。

尚未完成且不得误报：

- 真实 LIVE Provider Adapter 尚未接入 Laner 新架构；
- Android device-local Live State / Timeline persistence 尚未实现；
- LIVE Service 尚未接入 Composition Root / LIVE UI；
- RiftScreen / HUD 尚未迁移；
- 实时经济/击杀/塔/龙/男爵/Player state Provider normalization 尚未迁移。

## Verification Evidence

### Migration Foundation

- 首轮 CI：FAIL —— CI 错配 Gradle 9.4.0，而 AGP 9.4.0 要求最低 Gradle 9.6.0；失败证据保留。
- 修复后 CI run `34686592578`：PASS。

### LNR-010

GitHub Actions run `34687580424`：PASS。
状态：`WAITING EXTERNAL TEST`，仍需真实 credential online fetch / Android 实机。

### LNR-011

run `34688581238`：Core PASS / Android compile FAIL，根因是 Compose `produceState` 4-key overload；失败保留。
修复后 run `34688715420`：Architecture/Core/Android 全 PASS。
状态：`WAITING EXTERNAL TEST`。

### LNR-012

- run `34689400211`：Core semantics PASS；
- run `34689473333`：Riot structure Adapter PASS；
- run `34690235942`：Architecture/Core PASS，Android compile FAIL；根因是 `Files.move()` 返回 `Path` 导致 Repository `save(): Unit` 类型推断错误；
- branch final run `34690520095`：全 PASS；
- PR #4 run `34690577554`：全 PASS；
- merge commit `07d2b4a3d5094b81a72316bc8f0eba6486d4adb0`。

状态：`WAITING EXTERNAL TEST`；team-level Qualification 仍 `IN PROGRESS`。

### LNR-013

自动化已经证明：

- `EVENT_LIVE_PRE_GAME != IN_GAME`；
- verified gameplay frame 可以确认真实开局；
- `IN_GAME → POST_GAME → BETWEEN_GAMES → next Game` 边界；
- G2 缺 gameId 不继承 G1 gameId；
- old-game delayed signal 不能回滚新局；
- current-game 活跃时 future-game signal 为 Conflict；
- duplicate heartbeat 只刷新 freshness，不制造 lifecycle transition；
- 同一局 observation 时间早于当前权威 freshness 时，即便 lifecycle rank 更高也不能推进状态；
- `SERIES_COMPLETE` 是终态；
- 新鲜 verified frame 可压过陈旧 event-live 文本；
- 同 REALTIME 窗口 evidence strength 优先；
- 较弱 series-end 不能覆盖 stronger verified live frame；
- Provider failure + valid fallback = DEGRADED 且保留有效状态；
- 全源失败 = UNAVAILABLE 且保留 last-known state；
- wrong-match observation 在 arbitration 前拒绝；
- lifecycle transition 输出标准 `MatchStateChanged` evidence；
- Timeline reconnect semantic dedupe / out-of-order replay / same-second provenance arbitration / invalid cross-game rejection。

CI：

- run `34690850479`：Architecture/Core/Android 全 PASS；
- run `34691124746`：Architecture/Core/Android 全 PASS；
- run `34691364933`：Architecture PASS，Domain test FAIL，暴露“新鲜 IN_GAME heartbeat 后旧 POST_GAME 仍可推进”的 stale-order bug；Android 因测试失败跳过；
- fix commit `22668b37d22be5969ec59c99ac687f57c52a1ad3`；
- run `34691458209`：Architecture/Core/Android 全 PASS。

失败已写入 `docs/TROUBLESHOOTING.md`，永久回归：`LiveMatchStateReducerTest.delayedPostGameAfterNewerInGameHeartbeatIsIgnored`。

LNR-013 作为 Core/Application 基础层任务为 `DONE`。对应真实产品能力在 `FEATURE_BASELINE.md` 中仍是 `IN PROGRESS`，直到 LNR-014 接上真实 Provider、持久化与 Composition wiring。

## Next

进入 `LNR-014 — LIVE Source Adapters / Local Persistence / Composition Wiring`。

LNR-014 优先顺序：

1. 审计旧 RiftLab 真实 LIVE Providers 与当前可用性；
2. 选择至少一条可核验真实 LIVE Source 接 `LiveStateSourcePort` / 标准 Snapshot/Event；
3. 实现 Android local `LiveMatchStateRepository / LiveTimelineRepository`；
4. schema version / atomic write / corruption handling；
5. Composition Root wiring；
6. LIVE 页面消费 Application truth；
7. 场间/开局/新 Game/断线重连外部或实机验收。
