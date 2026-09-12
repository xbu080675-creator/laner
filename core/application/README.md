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
- `CompetitionStructureService`
- `LiveStateSourcePort`
- `LiveMatchStateRepository`
- `LiveMatchStateService`
- `LiveStateResolution`
- `LiveTimelineRepository`
- `LiveTimelineService`
- `TimelineIngestResult`
- `PhaseQuery`
- `PhaseViewState`
- `DiagnosticsPort`

## 关键规则
- Provider 只翻译 raw payload，不决定全局事实优先级。
- Global Schedule 的 ID、赛事分类、完成状态校验、跨源去重与降级均由 Application 统一处理。
- Schedule `EVENT_LIVE` 不能升级为 Match `IN_GAME`；该权限属于 LIVE Match State Engine。
- LIVE Provider Arbitration 由 `LiveMatchStateService` 统一执行，Adapter 不得自行决定权威 lifecycle。
- REALTIME 窗口内，verified gameplay frame 可以压过仅文本/事件状态；明显更晚的可信观察可覆盖陈旧高 Authority 状态。
- 低质量/冲突状态不得直接结束正在由更强 verified frame 证明仍在进行的比赛。
- `BETWEEN_GAMES` / `SERIES_COMPLETE` 可证明上一局已结束时，Application 可以补 `POST_GAME`，但补出的状态事件必须标记为 derived evidence。
- 所有实际 lifecycle 变化必须输出标准 `MatchStateChanged`；普通同状态心跳只刷新 freshness，不制造伪 transition event。
- Timeline ingestion 只接受标准 Domain Snapshot/Event；Provider raw payload/free text 不进入 Timeline Repository。
- Timeline 通过 semantic identity 去重重连事件，允许乱序事件回填；同秒快照按 provenance/evidence 仲裁。
- 子来源失败允许显式 `DEGRADED`，已经拿到的真实事实不得被无故丢弃。
- `PreMatchContextService` 是单场赛前上下文的统一入口；UI 不自行拼 Roster/Staff/Starting evidence。
- Roster Pool 即使恰好五人也不得成为 Starting Roster。
- Starting evidence 必须重新验证日期、对阵、赛事、五位置；冲突同级 evidence 返回 `Conflict`。
- Recent Form/H2H 只从已验证 `COMPLETED` Series 派生，且结果必须带 perspective。

## 日志
本模块定义诊断语义；具体日志 Sink 通过 `DiagnosticsPort` 实现。

- PRE 使用 `[Laner:SRC]` / `[Laner:PRE]` 语义字段；
- LIVE 使用 module=`LIVE` 的结构化诊断字段，至少包含 `match_id/provider/lifecycle/game/failures/conflicts`；
- Source failure、Conflict、Unavailable 不得吞异常或静默降级。

## 失败
来源失败必须显式返回稳定错误码；同级事实冲突必须返回 `Conflict`，禁止静默覆盖。

当前 PRE 错误码：
- `LNR-SRC-PRE-001~004`：Global Schedule；
- `LNR-SRC-PRE-006~007`：Riot Team Roster；
- `LNR-SRC-PRE-008`：normalized Starting Roster；
- `LNR-SRC-PRE-009`：normalized Staff。

当前 LIVE Application 错误码：
- `LNR-APP-LIVE-001`：Provider 返回错误 Match identity；
- 具体 LIVE Adapter 错误码在真实 Provider 接入任务中继续分配，Core 不伪造 Provider 失败分类。

## 测试
`gradle :core:application:test`

LNR-010 覆盖：提前 completed 防误判、真实完成、EVENT_LIVE/IN_GAME 隔离、高 Authority 去重、目录 fallback、全源失败不造数据。

LNR-011 覆盖：名单池/首发隔离、官方首发证据校验、重复角色拒绝、交叉确认、冲突保留、completed-only Form/H2H、H2H perspective。

LNR-013 覆盖：
- 新鲜 verified frame 压过陈旧 event-live；
- REALTIME 窗口内 evidence strength 优先；
- 低质量 series-end 不得覆盖强 verified live frame；
- provider failure + valid fallback = DEGRADED 但保留有效状态；
- 全源失败保持 last-known state；
- wrong-match observation 在仲裁前拒绝；
- lifecycle transition 输出标准 `MatchStateChanged` evidence；
- Timeline reconnect semantic dedupe；
- out-of-order event replay；
- same-second snapshot provenance arbitration；
- invalid cross-game event rejection。

## 故障定位
- Schedule/目录：先查 `GlobalScheduleService` → Source Port → Adapter；
- 单场赛前上下文：先查 `PreMatchContextService` → 对应 Port → Adapter；
- LIVE lifecycle：先查 `LiveMatchStateService` 的 selected provider / failure / conflict，再查 `LiveMatchStateReducer`；
- Timeline：先查 `LiveTimelineService` 的 ingest result（duplicate/invalid/replaced），再查 Repository Adapter；
- 数据错误不得先在 UI 内补丁修正。
