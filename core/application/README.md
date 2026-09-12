# `:core:application`

## 职责
纯 Kotlin 应用层。定义 Ports、来源注册/仲裁、Use Case、Query 与观众/教练两种展示契约。

## 输入
Domain 类型和由 Adapter 实现的 Port。

## 输出
经过统一业务规则处理的 Domain Fact、Application Query 与明确的冲突/失败/降级状态。

## 依赖
只依赖 `:core:domain`。禁止依赖 Android、Compose、具体 Provider、数据库实现。

## Public API
- `FactSourcePort`
- `FactRepository`
- `FactArbiter`
- `GlobalPreMatchSourcePort`
- `GlobalScheduleService`
- `GlobalScheduleSnapshot`
- `ScheduleLoadStatus`
- `TeamRosterSourcePort`
- `StartingRosterSourcePort`
- `TeamStaffSourcePort`
- `PreMatchContextService`
- `MatchPreContextSnapshot`
- `PreMatchContextStatus`
- `PhaseQuery`
- `PhaseViewState`
- `DiagnosticsPort`

## 关键规则
- Provider 只翻译 raw payload，不决定全局事实优先级。
- Global Schedule 的 ID、赛事分类、完成状态校验、跨源去重与降级均由 Application 统一处理。
- Schedule `EVENT_LIVE` 不能升级为 Match `IN_GAME`；该权限属于 LIVE Match State Engine。
- 子来源失败允许显式 `DEGRADED`，已经拿到的真实事实不得被无故丢弃。
- `PreMatchContextService` 是单场赛前上下文的统一入口；UI 不自行拼 Roster/Staff/Starting evidence。
- Roster Pool 即使恰好五人也不得成为 Starting Roster。
- Starting evidence 必须重新验证日期、对阵、赛事、五位置；冲突同级 evidence 返回 `Conflict`。
- Recent Form/H2H 只从已验证 `COMPLETED` Series 派生，且结果必须带 perspective。

## 日志
本模块定义诊断语义；具体日志 Sink 通过 `DiagnosticsPort` 实现。PRE 使用 `[Laner:SRC]` / `[Laner:PRE]` 语义字段。

## 失败
来源失败必须显式返回稳定错误码；同级事实冲突必须返回 `Conflict`，禁止静默覆盖。

当前 PRE 错误码：
- `LNR-SRC-PRE-001~004`：Global Schedule；
- `LNR-SRC-PRE-006~007`：Riot Team Roster；
- `LNR-SRC-PRE-008`：normalized Starting Roster；
- `LNR-SRC-PRE-009`：normalized Staff。

## 测试
`gradle :core:application:test`

LNR-010 覆盖：提前 completed 防误判、真实完成、EVENT_LIVE/IN_GAME 隔离、高 Authority 去重、目录 fallback、全源失败不造数据。

LNR-011 覆盖：名单池/首发隔离、官方首发证据校验、重复角色拒绝、交叉确认、冲突保留、completed-only Form/H2H、H2H perspective。

## 故障定位
- Schedule/目录：先查 `GlobalScheduleService` → Source Port → Adapter；
- 单场赛前上下文：先查 `PreMatchContextService` → 对应 Port → Adapter；
- 数据错误不得先在 UI 内补丁修正。
