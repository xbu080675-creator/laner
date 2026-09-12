# ADR-002 — 全球赛事统一管理，不按赛区复制业务系统

- 日期：2026-09-12
- 状态：ACCEPTED

## Context

Laner 需要覆盖多个联赛、赛区与国际赛事。如果继续采用“一个赛区一个业务模块”的方式，Competition、Team、Player、Match、Standing、UI 与状态机将被重复实现，造成跨赛区转会、国际赛事、队伍更名、赛事改制和数据源切换时的大量重复逻辑与身份冲突。

## Decision

Laner 采用 Global Competition Layer：

- `Region` 只是领域属性与筛选维度，不是独立业务架构边界；
- 所有赛区共享 Competition / Season / Stage / Team / Player / Match / Game / Standing / Qualification 领域模型；
- 赛区专属数据源只允许作为 Provider / Adapter 存在；
- 赛区专属规则通过 Ruleset / Capability / Metadata 表达；
- 外部 Provider ID 必须映射到 Laner 全局内部 ID；
- 跨赛区转会、国际赛事、队伍更名必须保持实体身份连续性；
- 禁止复制 `LPLxxxService`、`LCKxxxService` 等仅因赛区不同产生的 Domain/Application 业务实现。

## Consequences

正向：
- 新增赛区主要增加 Adapter/Ruleset，而不是复制业务系统；
- 国际赛事天然复用同一模型；
- 跨赛区比较、历史追踪和转会处理更直接；
- 页面只需统一筛选 Region/Competition。

代价：
- 必须提前做好 Identity Resolution 与 Provider ID 映射；
- 赛事规则差异需要显式建模；
- 数据规范化要求更高。

## Support Boundary

架构目标为全球统一管理，但不得把“架构可接入”描述成“当前已验证支持”。每个赛事/赛区的 `SUPPORTED / DEGRADED / UNSUPPORTED` 状态仍由实际 Provider 能力与测试证据决定。

## Superseding

如未来要恢复赛区独立业务架构，必须新增 ADR supersede 本文，并说明为什么统一领域模型无法继续满足要求。不得静默复制赛区业务实现。
