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
| LNR-014 | LIVE Source Adapters / Local Persistence / Composition Wiring | IN PROGRESS |

## Current Truth

Laner 已进入 M1 真实功能迁移。当前形成：

1. 全球赛事目录 / 全球赛程；
2. 单场 PRE Roster / Starting Roster Evidence / Staff / Recent Form / H2H；
3. Tournament Edition / Standings / Championship Points boundary / Qualification mechanism；
4. LIVE Core/Application 真相层；
5. LIVE Android 本地持久化与 Composition wiring 已开始落地。

当前已验证/已实现存在：

- `:core:domain` 纯 Kotlin Domain；
- `:core:application` 纯 Kotlin Application/Port；
- `:app` Android/Compose Composition Root；
- PRE/LIVE/POST 三阶段 Android 壳；
- `LiveMatchStateReducer / LiveMatchStateService`；
- `GameTimeline / LiveTimelineService`；
- Android `JsonLiveMatchStateRepository`；
- Android `JsonLiveTimelineRepository`；
- LIVE local persistence `schema_version=1`；
- 持久化使用 SHA-256 稳定文件名、temp file + atomic replace；
- 损坏 JSON / unsupported schema 显式失败，不静默返回空状态；
- Timeline 对标准 Snapshot/Event 做显式 type/schema 序列化，不持久化 Provider raw payload；
- LIVE target-aware query 可向 Adapter 提供 canonical MatchId、TeamRef 与计划时间，不让 Provider raw ID 泄漏进 Domain；
- `LanerAppGraph` 已接入真实本地 LIVE State/Timeline repositories 与 Application services；
- 未配置真实 LIVE Source 时 `LiveMatchStateService(sources = emptyList())` 是合法显式降级态，不伪造赛事事实；
- CI 新增 `:app:testDebugUnitTest` Android Adapter/Persistence Gate。

尚未完成且不得误报：

- Cito 在线 LIVE Adapter 尚未完成外部验证；
- Cito 当前按用户要求暂缓在线测试，状态为 `WAITING EXTERNAL TEST / DEFERRED`，不作为本阶段阻塞项；
- WSS entitlement 不作假定，后续只作为 REST 基线之上的可选增强；
- LIVE 页面尚未完成 Application truth 的正式展示/目标选择接线；
- 实时经济/击杀/塔/龙/男爵/Player state Provider normalization 尚未迁移；
- RiftScreen / HUD 尚未迁移。

## Verification Evidence

### Migration Foundation

- 首轮 CI：FAIL —— CI 错配 Gradle 9.4.0，而 AGP 9.4.0 要求最低 Gradle 9.6.0；失败证据保留。
- 修复后 CI run `34686592578`：PASS。

### LNR-010

GitHub Actions run `34687580424`：PASS。
状态：`WAITING EXTERNAL TEST`。

### LNR-011

run `34688581238`：Core PASS / Android compile FAIL，失败保留。
修复后 run `34688715420`：Architecture/Core/Android 全 PASS。
状态：`WAITING EXTERNAL TEST`。

### LNR-012

- run `34690235942`：Architecture/Core PASS，Android compile FAIL；失败保留；
- branch final run `34690520095`：全 PASS；
- PR #4 run `34690577554`：全 PASS；
- merge commit `07d2b4a3d5094b81a72316bc8f0eba6486d4adb0`。

状态：`WAITING EXTERNAL TEST`。

### LNR-013

- run `34691364933` 暴露 stale-order bug，失败保留；
- fix commit `22668b37d22be5969ec59c99ac687f57c52a1ad3`；
- run `34691458209`：Architecture/Core/Android 全 PASS；
- final exact-head run `34691766133`：全 PASS；
- PR #5 run `34691843981`：全 PASS；
- merge commit `996cd1275729c324aca7a8d8c6b145c2f9206fc8`。

### LNR-014

- 新增 Android Adapter/Persistence unit-test Gate；
- run `34692037250`：Core PASS / Android unit test FAIL，暴露 JUnit4 expression-body 测试签名初始化错误；失败保留；
- fix commit `86ca106cf6372a4f23f4f83faaa997eef3f5bad5` 改为标准 block-body `void` 测试签名；
- run `34692350405`（head `afa4bf7f...`）：Architecture/Core/App unit tests/Android build 全 PASS；
- latest Composition wiring commit `d46b1b5876a3dd7d989226641748a7081098b91c` 正在 CI 验证。

## Next

继续 `LNR-014`，但 Cito 在线验证暂缓：

1. 完成本地 LIVE State/Timeline persistence 自动回归；
2. 完成 Composition Root 与 LIVE 页面 Application-truth 接线；
3. 明确无 Provider 时的 `UNAVAILABLE / last-known` 降级 UI；
4. Cito Adapter 只保留契约/fixture/可接结构，不在没有在线条件时宣称真实支持；
5. Cito REST/WSS 在线、场间、开局、新 Game、断线重连实测进入 `WAITING EXTERNAL TEST`，条件具备后补验收；
6. 不让 Cito 暂缓阻塞其它 LIVE 迁移工作。
