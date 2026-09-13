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

Block 1 已完成独立合规整改并冻结；`INC-LNR-020-001 = CLOSED`，Compliance Review PASS 属于工程认证事实，不混入任务状态 token。`LiveMatchContextService`、`OverlayWindowHost`、`RiftScreenWindowController / DraftHudWindowController` 已成为当前边界。系统悬浮窗、拖动、Edit/Lock、横竖屏 profile 和触摸穿透继续等待 Android 真机证据。

### LNR-021 — Tactical HUD / LIVE Event Derivation
状态：`WAITING EXTERNAL TEST`

第 2 块功能工程交付已经合入，但用户规定的独立工程宪法复查发现 `INC-LNR-021-001`；当前必须先完成事故整改、重测和重新认证，Block 2 尚未冻结，Block 3 不得开始。

已交付/当前整改后的业务规则：
- `LiveEventDerivationService` 从 canonical Timeline snapshot 保守派生 aggregate Kill delta、player-backed kill delta、`MultiKillWindowEvent`、`TeamFightWindowEvent`、Objective delta 与 `GoldLeadChangedEvent`；
- 不制造 killer/victim 配对，不从 dragon 总数猜龙种/龙魂/远古龙，不生成现有数据链没有的 HP/CD/位置事实；
- `TeamFightWindowEvent` 只有双方 kill delta 都可比较、窗口 <=20s 且累计 >=3 时生成；任一侧 unknown 不得补 0；
- team kill counter regression 不制造 combat event；player counter regression / player row 缺失时不得制造 PlayerId；
- `LiveTimelineService.reconcileGeneratedEvents` 只重建本地 generator 的派生事件，Provider explicit / Draft / lifecycle 事件不被删除；
- late/out-of-order/stronger same-second snapshot 可以重新计算派生层，避免旧派生事实残留；
- Timeline persistence 当前写 v2、读 v1；首次 v1 → v2 覆盖前必须创建并校验 `.schema-v1.bak` 恢复副本；
- `TacticalHudPresentationMapper / TacticalHudWindowController / TacticalHudOverlayView`；窗口优先级保留 `Draft > Tactical > RiftScreen`；
- Verified Tactical event 采用 25s game-time TTL + 30s wall-clock freshness，避免断流旧卡永久挂屏；
- `TacticalHudPreviewSession` 为 Android-only 视觉夹具，固定 `LOCAL PREVIEW · NOT FACT`，不进入 Core/Repository/Timeline。

自动功能状态：`LIVE-014 / LIVE-015 / LIVE-030 → WAITING EXTERNAL TEST`。真实 Riot online 事件触发、Android overlay 视觉/触摸/窗口优先级/断流行为仍需外部验证，不能升级 DONE。

历史交付锚点继续保留：
- final feature head `4c1f256daf288bbc835d30f01ce1bcf3b6fa85f5`；
- final push run `34712441374` PASS；
- final PR run `34712444119` PASS；
- PR #15 merge `6c72a748a23758c975d78a78e80092b16d225d34`；
- post-merge main run `34712560708` PASS；
- docs closeout PR #16 merge `c73d551925ba273d4caf6259ef621100c105b5de`；main run `34712899722` PASS。

历史失败保留：run `34709821178` 的 Core 已 PASS，但 Android production compile 因 `LiveMatchScreen.eventLabel()` 未穷举新 sealed event 失败；fix `403bba4e874ad37978179618e84dceaafb9f06f8`，Troubleshooting `LNR-UI-LIVE-004`。

## 当前推进顺序
1. **完成 `INC-LNR-021-001` 整改**：事实安全 → 负向/边界永久回归 → migration recovery → 权威文档/状态同步 → 新整改记录；
2. 冻结整改 exact-head，完整 Gate + PR Gate；
3. 合并整改并跑 main Gate；
4. 对整改结果执行独立 Post-change Compliance Review，确认 6 项偏离关闭且没有新债；
5. `INC-LNR-021-001` 通过新记录正式 CLOSED 后冻结 Block 2；
6. 第 3 块：Watch Hub + 播放器；
7. 第 4 块：赛前/赛后剩余功能；
8. 第 5 块：OTA + 设置 + 主题/缓存/诊断；
9. 第 6 块：Local AI / OCR；
10. 第 7 块：全量回归；
11. 第 8 块：Migration Audit / release closure。

LNR-020 Android 真机补证、LNR-019/LNR-021 Riot online 补证继续独立回填，不阻塞事故关闭后的后续工程切片，也不得被 CI 冒充 PASS。

用户已明确后续每个大版本均采用同样节奏：**完成版本 → 复查工程宪法 → 记录偏离 → 先整改 → 再进入下一版本**。历史过错只作为证据和回归输入，不得沿用为新实现惯性；整改本身不得制造新的过错。

Cito online 与已有真实 Provider/设备补证独立回填，不阻塞工程开发，但其状态必须保持真实。

## 速度原则
允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、把 fixture PASS 冒充在线/实机 PASS。
