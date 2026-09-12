# LNR-009 — 全球赛事统一管理架构

- 日期：2026-09-12
- 状态：DONE
- 类型：Architecture / Domain Boundary

## 1. 需求与目标

用户明确要求：Laner 不再“一个赛区一个”，而是全局管理、全局接管。

本任务将该要求正式定义为架构规则：赛区不得成为独立业务系统边界，Laner 使用统一全球赛事领域模型管理各赛区、联赛与国际赛事。

## 2. Constitution Preflight

结果：PASS

开发前读取并核对：
- `docs/ENGINEERING_CONSTITUTION.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_SCOPE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- 当前 GitHub `main` 状态

本任务仅修改架构与治理文档，无运行时代码、依赖、Schema 或 UI 实现。

## 3. 核心决策

- `Region` 是领域属性/筛选维度，不是业务模块边界；
- 全赛区共享 Competition / Team / Player / Match / Game / Standing / Qualification 模型；
- 赛区专属来源只允许存在于 Provider / Adapter 层；
- 赛区规则差异通过 Ruleset / Capability / Metadata 表达；
- 外部 Provider ID 不得直接作为 Laner 全局主键；
- 建立 Identity Resolution 概念处理跨赛区转会、国际赛事、更名与多 Provider 映射；
- 禁止按赛区复制 Domain/Application 业务逻辑；
- 全球架构可接入不等于当前已验证支持全球所有赛区。

## 4. 文件变更

新增：
- `docs/decisions/ADR-002-global-competition-management.md`
- `docs/development/2026/2026-09-12_LNR-009_global-competition-management.md`

修改：
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_SCOPE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`

删除：无

## 5. 测试与验证

正向：
- Architecture 已包含 Global Competition Layer：PASS
- Project Scope 已明确“全局管理，全局接管”：PASS
- Development Plan 已将全球 Competition / Identity 纳入 LNR-003 验收：PASS
- Implementation Status 已同步 LNR-009 DONE：PASS

负向：
- 无赛区独立业务模块被声明为目标架构：PASS

边界：
- 已明确“全球架构”不等于“全球所有赛区已验证支持”：PASS

回归：
- 赛前 / 赛中 / 赛后三阶段原则未改变：PASS
- 四类 Source Class 原则未改变：PASS
- 功能冻结原则未改变：PASS

脚本验证：N/A —— 本次无脚本变更。
运行时测试：N/A —— 本次无运行时代码。

## 6. 风险与影响

- 架构：统一性增强，但未来必须认真处理 Identity Resolution 与赛制差异；
- 兼容：当前无运行时兼容变化；
- 数据：未来 Schema 必须支持全局内部 ID 与 Provider ID 映射；
- 性能：当前无运行时影响；
- UI：未来赛区只能作为筛选/上下文，不得成为独立业务架构；
- 安全：无。

## 7. 后续

- LNR-001：旧功能基线增加 Region/Competition 依赖识别；
- LNR-002：重点识别旧工程中按赛区复制的逻辑；
- LNR-003：冻结 Competition Domain、Identity Domain、全局 ID 与 Ruleset/Capability Contract。

## 8. 回滚

如需撤销该决策，必须新增 ADR supersede `ADR-002`，并同步 Architecture / Project Scope / Development Plan / Implementation Status。不得直接删除历史记录。

## 9. Post-change Compliance Review

结果：PASS

- 无业务范围蔓延；
- 无运行时代码或依赖变更；
- 架构边界明确；
- 未虚报全球支持状态；
- 状态与开发记录已同步。

## 【任务交付单】

1. 任务：`LNR-009 / 全球赛事统一管理架构 / DONE`
2. Constitution Preflight：PASS
3. 涉及模块：architecture / competition-domain / identity-domain / planning / status / ADR
4. 文件变更：见 §4
5. 测试覆盖：文档一致性与边界审查 PASS；运行时 N/A
6. 脚本验证：N/A
7. 风险：未来 Identity Resolution 与赛区 Ruleset 复杂度
8. 状态同步：PASS
9. Post-change Compliance Review：PASS
10. 最终结论：DONE
