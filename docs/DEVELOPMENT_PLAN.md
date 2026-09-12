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

Android LIVE state/timeline 持久化、schema/atomic write、Application truth UI、target-aware query 与 no-provider degradation 已完成自动验证。Cito online 为 `DEFERRED / WAITING EXTERNAL TEST`，不阻塞迁移。

### LNR-015 — Global POST Result / Archive / Historical Timeline / Replay
状态：`WAITING EXTERNAL TEST`

PR #7 已合并 main。强类型 POST facts、Global-first orchestration、canonical GameId/provider identity、fallback-only archive、verified Awards、Riot global Result/Replay、Riot historical Timeline、POST Compose 与按局 backfill 已交付。真实 Riot online 与 Android device POST 仍待验收。

### LNR-016 — Testable Android Platform / Riot Global LIVE / APK Delivery
状态：`WAITING EXTERNAL TEST`

PR #8 已合并 main。已交付第一条 Global Riot LIVE lifecycle baseline、runtime memory-only Riot Key、设备侧 `LNR-SRC-LIVE-002~005` diagnostics 与 installable debug APK。final feature run `34698280239`、PR run `34698393156` 全 PASS；merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`。真实 BLG vs AL Android online evidence 待补。

### LNR-017 — Local AI / OCR / Roster Assist
状态：`TODO`

迁移本地 AI、OCR（中/日/韩）、首发图片识别、赛中 Insight、AI diagnostics。AI 继续保持 `FACT_BACKED / INFERENCE / UNVERIFIED`，永远不能升级为赛事事实权威。

### LNR-018 — Compatibility / Full Regression / Migration Audit
状态：`TODO`

兼容导入、Feature Baseline 全量销账、真实设备/Provider 回归、性能/安全/数据审计、release closure。只有 Migration Audit PASS 后才能宣称完整迁移完成。

### LNR-019 — Global LIVE Snapshot / Timeline / Match HUD
状态：`WAITING EXTERNAL TEST`

已交付：
- Global Riot LiveStats 真帧标准化为 canonical `LiveGameSnapshot`；
- team gold/kills/towers/dragons/barons 与 player level/KDA/CS/gold/champion；
- `LiveSnapshotService` 在 Application 二次校验 canonical Match/Game/team identity 后才写 Timeline；
- lifecycle authority 与 snapshot ingestion 严格分离；
- LIVE 页面全宽真帧卡片与经济差；null 保持“未知”，不伪造成 0；
- region-neutral，同一链覆盖 LPL/LCK/国际赛事。

任务号纠正：该工作最初误用 `LNR-017`，但 `LNR-017/018` 已被本计划预留。旧 branch/record 保留作为历史，正式归档使用 `LNR-019`，不改写失败与提交历史。

自动 Gate 与 PR 已通过并合并；真实赛事 online/device 证据仍需补齐，因此保持 `WAITING EXTERNAL TEST`。

### LNR-020 — RiftScreen / Draft HUD Android Overlay
状态：`WAITING EXTERNAL TEST`

第 1 块平台迁移已完成自动化实现：
- RiftScreen 不复制旧 `MatchSessionStore`，改用 process-level `LanerApplication` Composition Root，共享标准 Application services；
- 链路固定为 `GlobalScheduleService → LiveTargetSelector → LiveMatchStateService + LiveSnapshotService → LiveTimelineService → Presentation → WindowManager`；
- 保留 RiftScreen `MINI / COMPACT / EXPANDED`、拖动、关闭、前台自动隐藏 / 后台显示；
- Verified Draft HUD 仅在 canonical lifecycle=`DRAFT` 时激活，只消费 canonical `DraftChangedEvent`；
- 未有 SideSelection 事实时仅显示左/右侧，未有角色/对位证据时不推断；
- 本地 HUD Preview 与赛事事实彻底隔离，明确标记 `LOCAL PREVIEW · NOT FACT`，不写 Core/Repository/Timeline；
- HUD 支持 Edit / Lock、模块拖动、Scale、Alpha、Visibility、Reset；
- 横屏 / 竖屏使用独立持久化 Profile；
- Lock 后全屏 HUD Window 使用 `FLAG_NOT_TOUCHABLE` 真正触摸穿透，边缘 Dock 保持可操作；
- Architecture/Core/App Unit/Android build/APK 自动 Gate 已通过。

当前仅剩系统悬浮窗权限、后台显示、拖动、横竖屏 Profile 与 Lock 触摸穿透等 Android 真机行为补证，所以平台条目进入 `WAITING EXTERNAL TEST`，而不是伪写 `DONE`。`LIVE-012 BP/Draft 实时状态` 仍为 TODO：本任务交付的是 HUD Presentation，不等价于真实 Draft Provider 已迁移。

## 当前推进顺序
1. LNR-020 Android 真机补证可独立回填，不阻塞后续工程切片；
2. 第 2 块：Tactical HUD + 赛中事件层；
3. 第 3 块：Watch Hub + 播放器；
4. 第 4 块：赛前/赛后剩余功能；
5. 第 5 块：OTA + 设置 + 主题/缓存/诊断；
6. 第 6 块：Local AI / OCR；
7. 第 7 块：全量回归；
8. 第 8 块：Migration Audit / release closure。

Cito online 与已有真实 Provider/设备补证独立回填，不阻塞上述工程顺序。

## 速度原则
允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、把 fixture PASS 冒充在线/实机 PASS。
