# Laner Development Plan

## 任务编号

正式任务使用 `LNR-###`。

## M0 — Project Foundation

### LNR-000 — 工程立宪与基线初始化
状态：`DONE`

### LNR-001 — 旧工程功能基线提取
状态：`TODO`

目标：读取现有电竞观赛助手仓库，形成 `FEATURE_BASELINE.md`。

必须逐项记录：功能名称、Phase、Persona、User Question、Decision Value、用户入口、数据来源、Source Class、UI/交互、成功表现、已知问题、迁移验收方式。

任何用户可见业务功能如果无法归属三阶段之一，或无法说明真实用户问题，不得直接迁移。

### LNR-002 — 旧工程架构与技术债审计
状态：`TODO`

目标：只分析，不复制。输出旧数据流、模块耦合、状态管理、网络、缓存、后台任务和故障模式，并识别旧来源应归属的 Source Class。

### LNR-003 — 新架构冻结
状态：`TODO`

目标：在 LNR-001/002 证据基础上冻结 Laner vNext 的模块图、依赖 DAG、核心 Contracts、数据模型边界和首批错误码。

验收必须包含：
- 三阶段一级产品轴；
- Match State → 三阶段稳定映射；
- 共享能力不成为第四业务阶段；
- UI 不自行判断阶段；
- 观众/教练共享领域事实；
- UX 满足极简 + 酷炫约束；
- 所有外部 Provider 声明 `PRE_MATCH_SOURCE / LIVE_MATCH_SOURCE / POST_MATCH_SOURCE / GLOBAL_AI_ASSIST`；
- AI 与事实权威层严格分离；
- Source Orchestration 具备 provenance、authority、freshness、revision、fallback 设计。

### LNR-004 — 工程骨架与 CI Gate
状态：`TODO`

目标：建立真实代码目录、构建、测试、静态检查、架构 Gate、日志基础设施。

### LNR-005 — 三阶段一级架构轴确立
状态：`DONE`

### LNR-006 — 用户角色 × 比赛阶段产品矩阵
状态：`DONE`

### LNR-007 — 极简 × 酷炫体验北极星
状态：`DONE`

### LNR-008 — 四类数据/API 源架构
状态：`DONE`

目标：将 Laner 外部来源固定分为赛前源、赛中源、赛后源、全局 AI 辅助源，并明确事实源与 AI 辅助源的权威边界。

产物：
- `docs/SOURCE_ARCHITECTURE.md`
- `docs/ARCHITECTURE.md` Source Orchestration 更新
- 本任务开发留档

## M1 — Core Migration

任务在旧工程功能基线提取后拆分。不得在不知道完整功能基线前为了“快”提前编造迁移顺序。

## 速度原则

允许并行读取与分析、一次完成同一责任域内的一组改动、使用自动化减少重复，并在需求已确定时直接实现。

禁止为赶进度跨模块乱改、跳过测试/留档、先写临时代码以后再重构、把未执行测试写成 PASS、在功能基线未知时删除旧能力。
