# Laner Development Plan

## 任务编号

正式任务使用 `LNR-###`。

## M0 — Project Foundation / Migration Foundation

### LNR-000 — 工程立宪与基线初始化
状态：`DONE`

### LNR-001 — 旧工程功能基线提取
状态：`DONE`

产物：`docs/FEATURE_BASELINE.md`。

验收结果：已以 `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43` 为旧版事实基线，将 PRE/LIVE/POST、共享平台能力、Provider、AI、Overlay、回放、OTA 及已承诺 Sandbox 路线映射为可逐项关闭的迁移清单。

### LNR-002 — 旧工程架构与技术债审计
状态：`DONE`

产物：`docs/LEGACY_ARCHITECTURE_AUDIT.md`。

结论：旧 RiftLab 是可靠行为基线，但 `:app` 内混合 Domain/Application/Provider/UI/Android 的结构不得原样复制。新工程保留能力，不保留大 Store / 分散仲裁 / 赛区特例式耦合。

### LNR-003 — 新架构冻结
状态：`DONE`

产物：`docs/ARCHITECTURE_FREEZE.md`。

冻结内容：
- `PRE_MATCH / LIVE_MATCH / POST_MATCH` 一级产品轴；
- `SPECTATOR / COACH_ANALYST` 共享事实、不同展示密度；
- 四类 Source Class；
- Global Competition Domain；
- Match Lifecycle；
- provenance / authority / freshness / revision；
- Application 唯一 Fact Arbitration；
- `:core:domain ← :core:application ← :app` 依赖 DAG；
- Android/Compose/Provider API 禁止进入 Core。

### LNR-004 — 工程骨架与 CI Gate
状态：`TESTING`

已实现：
- Gradle 多模块工程；
- `:core:domain`；
- `:core:application`；
- Android `:app`；
- 三阶段 Compose 壳；
- Match Lifecycle / Global ID / Source Provenance / Fact Candidate / Standard Event；
- FactArbiter；
- Domain/Application Unit Tests；
- GitHub Actions Core Test + Android Debug Compile Gate；
- 模块 README。

DONE 条件：GitHub CI 实际通过并完成 Compliance Review。

### LNR-005 — 三阶段一级架构轴确立
状态：`DONE`

### LNR-006 — 用户角色 × 比赛阶段产品矩阵
状态：`DONE`

### LNR-007 — 极简 × 酷炫体验北极星
状态：`DONE`

### LNR-008 — 四类数据/API 源架构
状态：`DONE`

### LNR-009 — 全球赛事统一管理架构
状态：`DONE`

## M1 — Feature Migration

完整迁移状态以 `docs/FEATURE_BASELINE.md` 为唯一功能清单。迁移按工程依赖推进，但用户产品结构始终保持赛前 / 赛中 / 赛后。

第一批真实迁移顺序：

1. Global Competition Catalog / Schedule；
2. PRE Roster / Staff / Form / H2H；
3. Standings / Qualification / Tournament Edition；
4. LIVE Match State / Provider Arbitration / Unified Event / Timeline；
5. POST Result / Stats / Replay / Archive；
6. Android RiftScreen / Watch / Player / OTA；
7. Local AI / OCR / Roster Assist；
8. Compatibility Import / Full Regression / Migration Audit。

## 速度原则

允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。

禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、未完成基线条目却宣称“完整迁移”。
