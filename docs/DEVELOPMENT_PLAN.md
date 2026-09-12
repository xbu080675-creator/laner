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
状态：`WAITING EXTERNAL TEST (functional) / COMPLIANCE REMEDIATION IN PROGRESS`

原第 1 块平台迁移已通过 PR #10 合入 `main@967e112d…`，post-merge run `34706047380` 自动 Gate PASS。功能状态不作废，但 `INC-LNR-020-001` 后续确认了 7 项工程合规偏离，因此工程认证暂停，正在 PR #12 中整改。

功能基线保持：
- RiftScreen `MINI / COMPACT / EXPANDED`、拖动、关闭、前台自动隐藏 / 后台显示；
- Verified Draft HUD 仅在 canonical lifecycle=`DRAFT` 时激活，只消费 canonical `DraftChangedEvent`；
- 未有 SideSelection 事实时仅显示左/右侧，未有角色/对位证据时不推断；
- 本地 HUD Preview 明确 `LOCAL PREVIEW · NOT FACT`，不写 Core/Repository/Timeline；
- HUD Edit / Lock、模块拖动、Scale、Alpha、Visibility、Reset；
- 横/竖屏独立 Profile；
- Lock 使用 `FLAG_NOT_TOUCHABLE`，边缘 Dock 继续可操作；
- `LIVE-024~029` 保持 `WAITING EXTERNAL TEST`；`LIVE-012` 真实 Draft Provider 仍为 TODO。

合规整改目标固定为：
- `LiveMatchContextService` 唯一编排 `Schedule → Target → Lifecycle → Snapshot → GameId → Timeline`，Presentation 不再复制 Use Case；
- `OverlayWindowHost` 统一 WindowManager add/update/remove/bounds 与 `[Laner:OVERLAY]` diagnostics；
- `RiftScreenWindowController / DraftHudWindowController` 拆出平台窗口职责，收薄 Foreground Service；
- LNR-020 Failure A/B 进入 `TROUBLESHOOTING.md`；
- 独立整改记录填写 §15 固定 17 项交付单；
- 状态文档同步真实 merge/CI 事实；
- Post-change Compliance Review 与 exact-head Gate 完成后才能关闭事故。

整改中 PR #11 已把分支截至 `6720f660…` 的前半段合入 `main@d4e4f70…`；changed-files 已复核均属于本事故/整改范围。剩余闭环由 Draft PR #12 承载。该中途主线变化已写入整改记录，不掩盖、不反向改写历史。

## 当前推进顺序
1. **先完成 LNR-020 / INC-LNR-020-001 合规整改**：PR #12 exact-head Gate → 文档/状态落账 → Post-change Compliance Review → merge → main Gate；
2. LNR-020 Android 真机补证继续独立回填，但不得被 CI 冒充 PASS；
3. 第 2 块：Tactical HUD + 赛中事件层；
4. 第 3 块：Watch Hub + 播放器；
5. 第 4 块：赛前/赛后剩余功能；
6. 第 5 块：OTA + 设置 + 主题/缓存/诊断；
7. 第 6 块：Local AI / OCR；
8. 第 7 块：全量回归；
9. 第 8 块：Migration Audit / release closure。

用户已明确后续每个大版本均采用同样节奏：**完成版本 → 复查工程宪法 → 记录偏离 → 先整改 → 再进入下一版本**。历史过错只作为证据和回归输入，不得沿用为新实现惯性；整改本身不得制造新的过错。

Cito online 与已有真实 Provider/设备补证独立回填，不阻塞工程开发，但其状态必须保持真实。

## 速度原则
允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、把 fixture PASS 冒充在线/实机 PASS。
