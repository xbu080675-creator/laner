# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Default branch: `main`
- Project phase: `M0 / Project Foundation`
- Business implementation: `NOT STARTED`
- Functional migration: `NOT STARTED`

## Task Status

| Task | Title | Status |
|---|---|---|
| LNR-000 | 工程立宪与基线初始化 | DONE |
| LNR-001 | 旧工程功能基线提取 | TODO |
| LNR-002 | 旧工程架构与技术债审计 | TODO |
| LNR-003 | 新架构冻结 | TODO |
| LNR-004 | 工程骨架与 CI Gate | TODO |
| LNR-005 | 三阶段一级架构轴确立 | DONE |
| LNR-006 | 用户角色 × 比赛阶段产品矩阵 | DONE |
| LNR-007 | 极简 × 酷炫体验北极星 | DONE |
| LNR-008 | 四类数据/API 源架构 | DONE |
| LNR-009 | 全球赛事统一管理架构 | DONE |

## Current Truth

当前仓库只包含工程治理、产品模型、UX 原则、数据源分类与架构基线文档，不应被描述为“APP 已开始重写”或“已有功能可测试”。

已确立：

- 所有用户可见业务页面以 `PRE_MATCH / LIVE_MATCH / POST_MATCH` 为一级轴；
- 功能设计遵循 `Persona × Match Phase × User Question`；
- UX 北极星为“极简 + 酷炫”，清晰、快速、不打扰观赛优先；
- 外部来源统一分为 `PRE_MATCH_SOURCE / LIVE_MATCH_SOURCE / POST_MATCH_SOURCE / GLOBAL_AI_ASSIST`；
- 前三类属于赛事事实来源体系，AI 属于辅助解释/推断层，不得覆盖已确认赛事事实；
- Source Orchestration 必须区分速度与权威度，并保存 provenance/revision；
- Laner 采用全球赛事统一管理架构，`Region` 只是领域属性/筛选维度，不是独立业务模块；
- 赛区专属 Provider 可以存在，但必须进入统一 Source/Normalization/Identity/Competition 数据管线；
- Team / Player / Competition / Match 等采用全局内部身份，不按赛区复制业务实体；
- “全局架构”不等于“当前全球全部赛区均已验证支持”，支持状态仍须由真实数据源与测试证据决定。

## Blocking Conditions Before Business Code

- `FEATURE_BASELINE.md` 尚未完成；
- 旧工程真实代码尚未完成本轮审计；
- 技术栈与目标模块尚未冻结。

因此当前任何业务实现都应视为过早。
