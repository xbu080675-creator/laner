# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Working branch: `feature/lnr-014-live-adapters-persistence`
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

## Current Truth

Laner 已形成连续的 PRE → LIVE 基础链：

1. 全球赛事目录 / 全球赛程；
2. 单场 PRE Roster / Starting Roster Evidence / Staff / Recent Form / H2H；
3. Tournament Edition / Standings / Championship Points boundary / Qualification mechanism；
4. LIVE Core/Application 权威状态、来源仲裁、统一事件与 Timeline；
5. LIVE Android 本地 State/Timeline 持久化、Composition Root、赛中 Application-truth 页面与 Timeline 只读展示。

当前已验证/已实现存在：

- `:core:domain` 纯 Kotlin Domain；
- `:core:application` 纯 Kotlin Application/Port；
- `:app` Android/Compose Composition Root；
- PRE/LIVE/POST 三阶段 Android 壳；
- `LiveMatchStateReducer / LiveMatchStateService`；
- `GameTimeline / LiveTimelineService`，UI 通过 Application `load(gameId)` 读取 Timeline；
- Android `JsonLiveMatchStateRepository`；
- Android `JsonLiveTimelineRepository`；
- LIVE local persistence `schema_version=1`；
- SHA-256 稳定文件名、temp file + atomic replace；
- 损坏 JSON / unsupported schema 显式失败，不静默返回空状态；
- Snapshot/Event 显式 type/schema 序列化，不持久化 Provider raw payload；
- LIVE target-aware query 携带 canonical MatchId、TeamRef、计划时间，Provider raw ID 不泄漏进 Domain；
- `LanerAppGraph` 已接入本地 LIVE State/Timeline repositories 与 Application services；
- 无实时 Source 时 `sources = emptyList()` 是合法显式降级态：`UNAVAILABLE / last-known`，不伪造赛事事实；
- LIVE 页面通过 `GlobalScheduleService` 选目标：`EVENT_LIVE` 优先，否则只从未完成比赛选最近目标；COMPLETED 不得冒充当前 LIVE；
- LIVE 页面展示权威 lifecycle、来源状态、本地 Timeline 快照/事件数量及最近标准事件；
- “赛事已开始 · 游戏未开始”仍作为独立 lifecycle 展示；
- CI 永久包含 `:app:testDebugUnitTest` Android Adapter/Persistence Gate。

尚未完成且不得误报：

- Cito credentialed REST/WSS 在线链未验收；按用户要求状态为 `WAITING EXTERNAL TEST / DEFERRED`；
- WSS entitlement 不作假定，后续只作为 REST 基线之上的可选增强；
- 真实实时经济/击杀/塔/龙/男爵/Player state Provider normalization 尚未迁移；
- 真实 Provider 驱动的持续 Timeline capture 尚未外部验证；
- RiftScreen / HUD 尚未迁移；
- 真实赛事“场间 → 新局 → 断线重连”新架构实机验收尚待外部条件。

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
- run `34692588440`：Composition + LIVE Application-truth UI 全 PASS；
- run `34692688208`：LIVE target selection 回归 PASS；
- final code head `4d64749c07ce6f91bebad91de91f4fb70ed0bd04` / run `34692936906`：Architecture / Domain+Application / Android Adapter tests / Android debug compile 全 PASS。

LNR-014 自动可认证部分已完成；由于 Cito 在线链及真实赛事驱动尚未具备外部验收条件，任务状态为 `WAITING EXTERNAL TEST`，不阻塞后续 POST 迁移。

## Next

进入 `LNR-015 — POST Result / Game Archive / Stats / Timeline Archive / Replay Domain`。

优先顺序：
1. 从旧 RiftLab 提取 Result / Completed Game / Match Detail / Historical Resolver 行为基线；
2. 冻结 POST 强类型 Domain，避免 Series Result、Game Archive、Player Stats、Awards、Replay 互相冒充；
3. 建立 POST Source Ports 与 Application aggregation；
4. 复用标准 `GameTimeline` 作为赛后只读归档输入，不复制旧 MatchTimelineStore；
5. Replay 先做 provider-neutral Domain/Port，Media3/WebView 留在 Android Adapter；
6. 接 POST 页面真实 Application state；
7. Provider/播放器外部验收仍按证据单独标记。
