# Laner Development Plan

## 任务编号
正式任务使用 `LNR-###`。完整迁移状态以 `docs/FEATURE_BASELINE.md` 为功能验收清单；工程状态以本文件、`IMPLEMENTATION_STATUS.md` 与不可变开发记录共同约束。

## M0 — Project / Product Foundation

| Task | Scope | Status |
|---|---|---|
| LNR-000 | 工程立宪与基线初始化 | DONE |
| LNR-001 | 旧工程功能基线提取 | DONE |
| LNR-002 | 旧架构与技术债审计 | DONE |
| LNR-003 | 新架构冻结 | DONE |
| LNR-004 | 工程骨架与 CI Gate | DONE |
| LNR-005 | PRE/LIVE/POST 一级架构轴 | DONE |
| LNR-006 | Persona × Phase × User Question | DONE |
| LNR-007 | 极简 × 酷炫 UX 北极星 | DONE |
| LNR-008 | 四类 Source 架构 | DONE |
| LNR-009 | 全球赛事统一管理 | DONE |

固定原则：**全局管理、全局接管；Region 只是数据维度，不是业务模块边界。**

## M1 — Feature Migration

### LNR-010 — Global Catalogue / Schedule
状态：`WAITING EXTERNAL TEST`

Global Competition Catalog / Schedule、Riot PRE Adapter、真实赛程 UI、来源状态与安全 credential 注入已自动验证；真实 Provider/实机继续补证。

### LNR-011 — PRE Roster / Staff / Form / H2H
状态：`WAITING EXTERNAL TEST`

Roster Pool、Official Starting Roster Evidence、Staff、Recent Form、H2H 与 PRE 页面已完成自动链路；真实配送/实机待验收。

### LNR-012 — Standings / Qualification / Tournament Edition
状态：`WAITING EXTERNAL TEST`

Standings、Championship Points boundary、Qualification evidence、Tournament Edition archive/UI 已自动验证；在线和 team-level qualification 证据待补。

### LNR-013 — LIVE Core / Arbitration / Event / Timeline
状态：`DONE`

权威 lifecycle、`赛事开始 != 游戏进入`、场间/新局、stale/future-game/terminal、Provider Arbitration、标准事件和 canonical Timeline 已完成并合并。

### LNR-014 — LIVE Persistence / Composition / UI
状态：`WAITING EXTERNAL TEST`

Android LIVE state/timeline 持久化、schema/atomic write、Application truth UI、target-aware query 与 no-provider degradation 已完成自动验证。Cito online 尚未完成外部验证，不阻塞当前迁移。

### LNR-015 — Global POST Result / Archive / Historical Timeline / Replay
状态：`WAITING EXTERNAL TEST`

PR #7 已合并 main。强类型 POST facts、Global-first orchestration、canonical GameId/provider identity、fallback-only archive、verified Awards、Riot global Result/Replay、Riot historical Timeline、POST Compose 与按局 backfill 已交付。真实 Riot online 与 Android device POST 仍待验收。

### LNR-016 — Testable Android Platform / Riot Global LIVE / APK Delivery
状态：`WAITING EXTERNAL TEST`

PR #8 已合并 main。已交付第一条 Global Riot LIVE lifecycle baseline、runtime memory-only Riot Key、设备侧 `LNR-SRC-LIVE-002~005` diagnostics 与 installable debug APK。final feature run `34698280239`、PR run `34698393156` 全 PASS；merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`。真实 Android online evidence 待补。

### LNR-017 — Local AI / OCR / Roster Assist
状态：`TODO`

迁移本地 AI、OCR（中/日/韩）、首发图片识别、赛中 Insight、AI diagnostics。AI 继续保持 `FACT_BACKED / INFERENCE / UNVERIFIED`，永远不能升级为赛事事实权威。

### LNR-018 — Compatibility / Full Regression / Migration Audit
状态：`TODO`

兼容导入、Feature Baseline 全量销账、真实设备/Provider 回归、性能/安全/数据审计、release closure。只有 Migration Audit PASS 后才能宣称完整迁移完成。

### LNR-019 — Global LIVE Snapshot / Timeline / Match HUD
状态：`WAITING EXTERNAL TEST`

已交付 Global Riot LiveStats → canonical `LiveGameSnapshot` → Application validation → Timeline → LIVE UI 的真实 baseline。team gold/kills/towers/dragons/barons 与 player level/KDA/CS/gold/champion 均仅在上游明确提供时展示；真实赛事 online/device 仍待补证。

### LNR-020 — RiftScreen / Draft HUD Android Overlay
状态：`WAITING EXTERNAL TEST`

Block 1 已完成独立合规整改并工程冻结；`INC-LNR-020-001 = CLOSED`。`LiveMatchContextService`、`OverlayWindowHost`、`RiftScreenWindowController / DraftHudWindowController` 是当前边界。系统悬浮窗、拖动、Edit/Lock、横竖屏 profile 和触摸穿透继续等待 Android 真机证据。

### LNR-021 — Tactical HUD / LIVE Event Derivation
状态：`WAITING EXTERNAL TEST`

Block 2 已完成工程交付、独立宪法复查、六项整改与 docs-only closeout；`INC-LNR-021-001 = CLOSED`，Block 2 engineering scope 已冻结。冻结不等于 LNR-021 产品任务 DONE：真实 Riot online 与 Android device 项继续 `WAITING EXTERNAL TEST`。

当前业务规则：
- `LiveEventDerivationService` 从 canonical Timeline snapshot 保守派生 aggregate Kill delta、player-backed kill delta、`MultiKillWindowEvent`、`TeamFightWindowEvent`、Objective delta 与 `GoldLeadChangedEvent`；
- 不制造 killer/victim 配对，不从 dragon 总数猜龙种/龙魂/远古龙，不生成现有数据链没有的 HP/CD/位置事实；
- `TeamFightWindowEvent` 只有双方 kill delta 都可比较、窗口 <=20s 且累计 >=3 时生成；任一侧 unknown 不得补 0；
- team kill counter regression 不制造 combat event；player counter regression / player row 缺失不得制造 PlayerId；
- `LiveTimelineService.reconcileGeneratedEvents` 只重建本地 generator 的派生事件，Provider explicit / Draft / lifecycle 事件不被删除；
- late/out-of-order/stronger same-second snapshot 可以重新计算派生层；
- Timeline persistence 写 v2、读 v1/v2；首次覆盖旧 v1 前必须完整 decode、校验 canonical GameId、建立并验证 `.schema-v1.bak` 恢复点；错身份、损坏或 mismatched recovery 均阻断覆盖；
- Tactical HUD 保留 `Draft > Tactical > RiftScreen` 优先级，Verified event 使用 25s game-time TTL + 30s wall-clock freshness；
- `TacticalHudPreviewSession` 固定 `LOCAL PREVIEW · NOT FACT`，不进入 Core/Repository/Timeline。

自动功能状态：`LIVE-014 / LIVE-015 / LIVE-030 → WAITING EXTERNAL TEST`。真实 Riot online 事件触发、Android overlay 视觉/触摸/窗口优先级/断流行为仍需外部验证，不能升级 DONE。

合规关闭锚点：
- remediation PR #17 exact head `a62df876bf21f6009ea4a7fc1c9227fd48e219b1`；run `34732025919` 全 Gate PASS；merge `2d7ff40034a8e834f20c02bef41f8c088e98f479`；main run `34732179957` 全 Gate PASS；
- closeout PR #18 exact head `df58f5651fd241dc95137e0d40aa05e2e29850ee`；run `34732993654` 全 Gate PASS；merge `38161578e04a523c9247c64c762f4dd983561a9d`；main run `34733073857` 全 Gate PASS；
- final evidence: `docs/development/2026/2026-09-13_LNR-021_constitution-closeout-final.md`；
- 不创建 freeze Tag：LNR-021 的外部/实机 DoD 尚未完成。

历史 Failure A 继续保留：run `34709821178` 的 Core 已 PASS，但 Android production compile 因 `LiveMatchScreen.eventLabel()` 未穷举新增 sealed event 失败；fix `403bba4e874ad37978179618e84dceaafb9f06f8`，Troubleshooting `LNR-UI-LIVE-004`。

### LNR-022 — Global Watch Hub / Android Viewing Handoff
状态：`WAITING EXTERNAL TEST`

Block 3 从 `main@2b9cf6ba39d3306ec89557451207aa30d29a7cc9` 完成 fresh Constitution Preflight。实现一个平台中立 `WatchPort`、单一全球 Watch Catalog，以及 Android `AndroidWatchPort` Adapter；Bilibili、虎牙、LoL Esports、YouTube、Twitch、X 共用同一条观赛流程。`MAINLAND/GLOBAL` 仅为展示元数据，不进入赛事业务路由。观赛入口可触发 RiftScreen，悬浮窗权限往返保留 pending destination；直播平台跳转永远不改变赛事 Provider 选择与 LIVE authority。

自动化覆盖 stable destination contract 与六平台 catalog。Android 真机外部 App/浏览器 handoff、权限设置返回、具体客户端 deep-link 行为必须外部验证，因此状态保持 `WAITING EXTERNAL TEST`。

## 当前推进顺序
1. **Block 4：赛前/赛后剩余功能** — 必须在 LNR-022 implementation Gate + 独立 Constitution Review 通过后，从届时最新 main fresh Preflight 开始。
2. 第 5 块：OTA + 设置 + 主题/缓存/诊断。
3. 第 6 块：Local AI / OCR。
4. 第 7 块：全量回归。
5. 第 8 块：Migration Audit / release closure。

LNR-020 Android 真机补证、LNR-019/LNR-021 Riot online 补证、LNR-022 Android Watch handoff 补证继续独立回填，不阻塞下一工程切片，也不得被 CI 冒充 PASS。

用户已明确后续每个大版本均采用同样节奏：**完成版本 → 复查工程宪法 → 记录偏离 → 先整改 → 再进入下一版本**。历史过错只作为证据和回归输入，不得沿用为新实现惯性；整改本身不得制造新的过错。

## 速度原则
允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、把 fixture PASS 冒充在线/实机 PASS。
