# LNR-008 — 四类数据/API 源架构

- 日期：2026-09-12
- 状态：DONE
- 类型：Architecture / Source Orchestration

## 1. 需求与目标

用户明确要求 Laner API/数据源分为四大类：赛前源、赛中源、赛后源、全局 AI 辅助源。

本任务将该要求固化为 Source Architecture，并定义事实源与 AI 辅助源的权威边界。

## 2. Constitution Preflight

结果：PASS

已读取并核对：
- `docs/ENGINEERING_CONSTITUTION.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- 当前 GitHub `main` 真实状态

本任务无业务代码、运行时依赖、Schema 或 UI 实现变化。

## 3. 架构决策

统一 Source Class：

- `PRE_MATCH_SOURCE`
- `LIVE_MATCH_SOURCE`
- `POST_MATCH_SOURCE`
- `GLOBAL_AI_ASSIST`

前三类负责提供或验证赛事事实；`GLOBAL_AI_ASSIST` 不属于事实权威层。

AI 输出至少区分：
- `FACT_BACKED`
- `INFERENCE`
- `UNVERIFIED`

同时确立：
- 来源速度与权威度分离；
- 首个可信来源可 provisional 发布；
- 高权威来源可后续确认/修正；
- 所有修订保留 provenance/revision；
- UI 不直连 Provider；
- Provider 必须通过 Source Adapter / Source Orchestration。

## 4. 文件变更

新增：
- `docs/SOURCE_ARCHITECTURE.md`
- `docs/development/2026/2026-09-12_LNR-008_source-architecture.md`

修改：
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`

删除：无

## 5. 测试与验证

正向：文档已定义四类 Source Class：PASS

负向：AI 不得覆盖已确认事实的边界已明确：PASS

边界：来源类别与页面三阶段不是一一绑定，跨阶段复用规则已明确：PASS

回归：三阶段产品轴、Persona 模型、极简+酷炫原则未被改变：PASS

运行时 / Integration / E2E：N/A —— 当前无业务实现。

脚本验证：N/A —— 本次无脚本变更。

## 6. 风险与影响

- 架构：新增 Source Class 和 Source Orchestration 强约束。
- 兼容：无运行时影响。
- 数据：未来所有标准化数据必须保存来源/provenance 元数据。
- 性能：无当前运行时影响；未来赛中源需重点评估低延迟和断流恢复。
- 安全：AI/外部源仍按零信任处理。
- UI：无直接变化。

## 7. 后续

- LNR-001 提取旧功能时记录每项功能所消费的 Source Class。
- LNR-002 审计旧 Provider、轮询、降级与冲突处理。
- LNR-003 冻结 Source Adapter / Source Registry / Source Orchestration Contracts。

## 8. Post-change Compliance Review

结果：PASS

无业务范围蔓延；无代码、依赖、Schema 或 UI 变化；状态与 Changelog 已同步；开发记录完整。

## 【任务交付单】

1. 任务：`LNR-008 / 四类数据/API 源架构 / DONE`
2. Constitution Preflight：PASS
3. 涉及模块：source-architecture / architecture / planning / status
4. 文件变更：见 §4
5. 测试覆盖：文档一致性与边界检查 PASS；运行时 N/A
6. 脚本验证：N/A
7. 风险：无当前运行时风险；未来事实源与 AI 权威边界受强约束
8. 状态同步：PASS
9. Post-change Compliance Review：PASS
10. 最终结论：DONE
