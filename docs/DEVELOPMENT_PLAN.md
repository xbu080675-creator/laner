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

PR #7 已合并 main。已交付：
- 强类型 Series Result / Completed Game / Stats / Awards / Replay / Historical Timeline 边界；
- Global-first `PostSourceCapability` orchestration；
- canonical GameId 与 Provider identity mapping；
- fallback-only POST archive；
- verified Awards；
- Riot global Result / Replay；
- Riot historical LiveStats Timeline；
- POST Compose 与按局 backfill。

PR Gate `34696964926` PASS，merge `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca`。真实 Riot online 与 Android device POST 仍待验收。

### LNR-016 — Testable Android Platform / Riot Global LIVE / APK Delivery
状态：`TESTING`

当前优先目标是给实机赛事测试提供可安装、可诊断的数据包，同时开启 Android 平台迁移轮次。

本阶段已实现：
- `RiotGlobalLiveStateSource`：global schedule 唯一定位 → provider identity mapping → EventDetails → LiveStats real-frame probe；
- LIVE Application 不再使用空 Source 列表；
- BLG/AL 与 LCK fixture 共用同一 discovery/parser，不增加赛区业务分支；
- canonical GameId 继续由 Laner 生成，provider raw IDs 不进入 Domain identity；
- Android 顶部提供 Riot Key 临时输入；测试 Key **仅驻留当前进程内存**，退出进程即清除，不落盘、不进日志/Git/provenance；
- 改 Key 后 Composition graph 立即重建；
- LIVE UI 显示 `LNR-SRC-LIVE-002~005` 稳定来源诊断码；
- CI 在 Gate 全绿后上传 `app-debug.apk` artifact；
- test build `2.0.0-dev.3 / versionCode 3`。

历史证据：
- run `34697683655`：Architecture/Core PASS，Android compile FAIL；runtime key Compose 文案 `when` 语法错误，失败留档；
- fix `2cddeb872d7854829b54750db31f8739e37f0d2a`；
- run `34697846793`：Architecture / Core / App unit / Android build PASS，并产出 artifact id `10299088400`；
- 最新 UI diagnostics / docs exact-head 仍需自己 Gate 后才作为正式首选测试包。

本阶段紧急测试 DoD：
1. final exact-head Architecture/Core/App/Android PASS；
2. 对应 APK artifact 存在；
3. PR Gate PASS 并合并 main；
4. BLG vs AL 真实设备结果记为 `PASS / DEGRADED / FAIL`，不得用 fixture 代替；
5. 若失败，必须依据 `LNR-SRC-LIVE-*` 错误码确定断点。

LNR-016 后续 Android 深化仍包括 RiftScreen/HUD、Watch Hub、Player、OTA；这些不得阻塞当前数据链测试包交付。

### LNR-017 — Local AI / OCR / Roster Assist
状态：`TODO`

迁移本地 AI、OCR（中/日/韩）、首发图片识别、赛中 Insight、AI diagnostics。AI 继续保持 `FACT_BACKED / INFERENCE / UNVERIFIED`，永远不能升级为赛事事实权威。

### LNR-018 — Compatibility / Full Regression / Migration Audit
状态：`TODO`

兼容导入、Feature Baseline 全量销账、真实设备/Provider 回归、性能/安全/数据审计、release closure。只有 Migration Audit PASS 后才能宣称完整迁移完成。

## 当前推进顺序
1. LNR-016 当前 Riot Global LIVE Android 测试包交付与 BLG vs AL 实机数据验收；
2. LNR-016 Android RiftScreen / Watch / Player / OTA；
3. LNR-017 AI / OCR；
4. LNR-018 完整兼容性 / 回归 / Migration Audit。

Cito online 作为 LNR-014 外部补证独立回填，不阻塞上述顺序。

## 速度原则
允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、把 fixture PASS 冒充在线/实机 PASS。
