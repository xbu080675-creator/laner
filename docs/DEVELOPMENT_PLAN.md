# Laner Development Plan

## 任务编号
正式任务使用 `LNR-###`。完整迁移状态以 `docs/FEATURE_BASELINE.md` 为功能验收清单；工程状态以本文件、`IMPLEMENTATION_STATUS.md` 与不可变开发记录共同约束。

固定原则：**全局管理、全局接管；Region 只是数据维度，不是业务模块边界。**

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

## M1 — Feature Migration

### LNR-010 — Global Catalogue / Schedule
状态：`WAITING EXTERNAL TEST`

### LNR-011 — PRE Roster / Staff / Form / H2H
状态：`WAITING EXTERNAL TEST`

### LNR-012 — Standings / Qualification / Tournament Edition
状态：`WAITING EXTERNAL TEST`

### LNR-013 — LIVE Core / Arbitration / Event / Timeline
状态：`DONE`

### LNR-014 — LIVE Persistence / Composition / UI
状态：`WAITING EXTERNAL TEST`

Cito online 为 `DEFERRED / WAITING EXTERNAL TEST`，不阻塞迁移。

### LNR-015 — Global POST Result / Archive / Historical Timeline / Replay
状态：`WAITING EXTERNAL TEST`

PR #7 / merge `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca` 已完成；真实 Riot online 与 Android device POST 仍待验收。

### LNR-016 — Testable Android Platform / Riot Global LIVE / APK Delivery
状态：`WAITING EXTERNAL TEST`

已完成并合并：
- Riot Global LIVE baseline；
- canonical identity mapping；
- runtime Riot Key 临时输入；
- `LNR-SRC-LIVE-002~005` 实机可见诊断；
- CI APK artifact；
- `2.0.0-dev.3 / versionCode 3`。

关键证据：feature run `34698280239` PASS，PR #8 run `34698393156` PASS，merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`。真实 BLG vs AL online/device 仍为外部验收。

### LNR-017 — Starting Roster Vision / Staged Diagnostics
状态：`TESTING`

为今晚官方/俱乐部图片型首发发布测试，先完成 AI/OCR 大阶段中最有实时验收价值的切片；不因此跳过质量门禁。

当前范围：
- normalized feed 的 `announcements` 不再被丢弃；
- 官方发布发现与正式首发事实严格分离；
- Application `RosterAssistStage`：`NO_TARGET / NO_ANNOUNCEMENT / ANNOUNCEMENT_DISCOVERED / OCR_FAILED / OCR_PARTIAL / OCR_COMPLETE_UNVERIFIED / NORMALIZED_EVIDENCE_AVAILABLE`；
- bundled ML Kit OCR：Latin + 按赛区添加中/韩/日文模型；
- 双栏海报几何分队与 TOP/JUG/MID/BOT/SUP 候选提取；
- OCR 始终 `DERIVED`，完整五位置也不得自行升级为 Official Starting Roster；
- PRE 页面 60 秒低频重查 + 手动“立即重查”；
- 稳定诊断 `LNR-SRC-PRE-010~013`；
- 测试 build `2.0.0-dev.4 / versionCode 4`。

当前测试证据：
- run `34700136865`：Architecture/Core PASS，App compile FAIL；失败保留；
- 根因 1：ML Kit Latin `TextRecognizerOptions` 包名错误；fix `ad23d8cf493c773b5ff3d6dd6b07b3333a380171`；
- 根因 2：Compose 跨模块 nullable smart-cast；fix `a711dce3947b80405af741d69a8c342b191c121c`；
- final exact-head Gate / APK / PR Gate：进行中。

本切片 DoD：
1. Architecture/Core/App/Android build 全绿；
2. APK artifact 存在；
3. failure history / tests / troubleshooting / module docs 完整留档；
4. PR Gate PASS + merge main；
5. 今晚真实官方发布在 Android 上记录为 `PASS / DEGRADED / FAIL`；fixture 不冒充实机/在线证据。

LNR-017 后续仍包括本地 AI Runtime、赛中 Insight、AI diagnostics；这些不阻塞今晚 roster vision 测试版。

### LNR-018 — Compatibility / Full Regression / Migration Audit
状态：`TODO`

兼容导入、Feature Baseline 全量销账、真实设备/Provider 回归、性能/安全/数据审计、release closure。只有 Migration Audit PASS 后才能宣称完整迁移完成。

## 当前推进顺序
1. LNR-017 roster vision exact-head Gate → APK → PR/merge → 今晚实机首发测试；
2. LNR-016 Android RiftScreen / Watch / Player / OTA；
3. LNR-017 剩余 Local AI / Insight；
4. LNR-018 完整兼容性 / 回归 / Migration Audit。

Cito online 作为 LNR-014 外部补证独立回填，不阻塞上述顺序。

## 速度原则
允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、把 fixture PASS 冒充在线/实机 PASS。
