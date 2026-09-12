# `:core:application`

## 职责
纯 Kotlin 应用层。定义 Ports、全球来源注册/仲裁、Use Case、Query 与明确的降级/冲突/辅助状态。赛区只能出现在 Query/Capability 数据中，不得成为业务模块分支。

## 输入 / 输出
输入 Domain 类型和 Adapter 实现的 Port；输出经过统一规则处理的 Domain Fact、Application Query 以及 `READY / DEGRADED / CONFLICT / UNAVAILABLE` 或明确辅助阶段。

## 依赖
只依赖 `:core:domain`。禁止依赖 Android、Compose、OkHttp、ML Kit、具体 Provider、数据库实现。

## Public API
### PRE
- `GlobalPreMatchSourcePort / GlobalScheduleService`
- `TeamRosterSourcePort / StartingRosterSourcePort / TeamStaffSourcePort`
- `PreMatchContextService`
- `ProviderStartingRosterAnnouncement`
- `StartingRosterVisionPort`
- `RosterVisionInspection / RosterAssistStage / StartingRosterAssistSnapshot`
- `StartingRosterAssistService`

### LIVE
- `LiveStateSourcePort / LiveMatchStateService`
- `LiveTimelineRepository / LiveTimelineService`

### POST
- `PostMatchQuery / PostSourceCapability`
- Result / CompletedGame / Award / Replay Ports
- `PostMatchArchiveRepository / ProviderMatchIdentityRepository`
- `PostMatchService`
- `PostTimelineSourcePort / HistoricalTimelineFrame / PostTimelineService`

### Shared
- `DiagnosticsPort`

## PRE Starting Roster Assist 规则
- `ProviderStartingRosterAnnouncement` 只是官方发布“被发现”的 transport-neutral metadata，不是 Starting Roster fact。
- OCR/vision Adapter 必须通过 `StartingRosterVisionPort`；Core 不知道 ML Kit/Bitmap/HTTP。
- `StartingRosterAssistService` 只负责 target correlation、assist orchestration、诊断阶段与 truth boundary。
- `candidateScore` 不具有赛事事实权威；低分官方图片允许继续 OCR 以提高召回，但不能自动确认。
- `RosterVisionInspection.complete == true` 也只能产生 `OCR_COMPLETE_UNVERIFIED`；不得直接构造 `OfficialStartingRoster`。
- 正式首发仍由 `PreMatchContextService` 对 normalized evidence 重新验证日期、对阵、赛事、五位置、冲突与 authority。
- 过旧/无关队伍公告必须拒绝关联；当前 assist correlation window 仅用于发现，不替代正式 evidence validation。

## Global / LIVE / POST 关键规则
- Provider 只翻译 raw payload，不决定全局事实优先级。
- Application 不写赛区业务 `if/else`；Global Provider 提供 baseline，区域 Provider 仅 supplement。
- raw provider identity 不泄漏为 canonical Domain identity。
- LIVE lifecycle/Timeline freshness、semantic dedupe 继续由 Application/Domain 掌权。
- POST Result/Game/Award/Replay 独立成功/失败；archive fallback-only；Historical Timeline 不插值。
- UI 不直接读取 Provider 或 Repository。

## 日志 / 失败
具体 Sink 通过 `DiagnosticsPort` 实现，failure/conflict/unavailable 不得吞掉。

Roster Assist stable diagnostics：
- `LNR-SRC-PRE-010` announcement discovered / OCR adapter unavailable；
- `LNR-SRC-PRE-011` no image or partial/ambiguous OCR；
- `LNR-SRC-PRE-012` complete OCR candidate but unverified；
- `LNR-SRC-PRE-013` image download/OCR failure。

POST historical identity mismatch：`LNR-APP-POST-005`。

## 测试
`gradle :core:application:test`

LNR-017 回归重点：
- candidateScore=35 的官方图片仍可被 assist 检查，但不会自动确认；
- complete OCR 仍为 unverified；
- normalized evidence 优先走正式校验并短路 OCR assist；
- unrelated/old announcement 不绑定当前比赛。

LNR-015 POST 回归继续覆盖 Result/Award conflict、archive fallback、global routing、LIVE+POST Timeline、wrong identity rejection。

## 故障定位
- PRE schedule：`GlobalScheduleService → Port → Adapter`；
- 正式首发：`PreMatchContextService → StartingRosterSourcePort`；
- 首发辅助：`StartingRosterAssistService → StartingRosterVisionPort`；
- LIVE：`LiveMatchStateService / LiveTimelineService`；
- POST：`PostMatchService / PostTimelineService`；
- 数据错误不得先在 UI 内补丁修正。
