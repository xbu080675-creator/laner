# `:core:application`

## 职责
纯 Kotlin Application。定义 Ports、全球来源注册/仲裁、Use Case、Query 与明确降级/冲突语义。赛区只能是 Query/Capability 数据，不得成为业务模块分支。

## 输入 / 输出 / 依赖
输入为 Domain 类型和 Adapter 实现的 Port；输出为经过统一业务规则处理的事实、Query 与 `READY / DEGRADED / CONFLICT / UNAVAILABLE` 状态。只依赖 `:core:domain`，禁止 Android、Compose、具体 Provider 或数据库实现。

## Public API
- PRE：`GlobalPreMatchSourcePort / GlobalScheduleService` 及 context/structure services。
- LIVE current context：`LiveMatchContextService / LiveMatchContextResult`，是 Presentation 获取“当前 LIVE 目标 + lifecycle + snapshot + timeline”的唯一组合 Query。
- LIVE lifecycle：`LiveStateSourcePort / LiveMatchStateService`。
- LIVE gameplay：`LiveSnapshotSourcePort / ProviderLiveSnapshot / LiveSnapshotService / LiveSnapshotResolution`。
- Timeline：`LiveTimelineRepository / LiveTimelineService`。
- POST：Result/Game/Award/Replay Ports、`PostMatchService`、`PostTimelineService`、Archive/Provider identity Ports。
- `DiagnosticsPort`。

## 关键规则
- Provider 只翻译 raw payload，不决定全局事实优先级。
- Application 不写 LPL/LCK/LEC/LCP 业务分支；Global Provider 是 baseline，区域来源只能 supplement。
- **LIVE lifecycle 与 gameplay snapshot 是两条独立证据链**：`LiveMatchStateService` 是 lifecycle 唯一权威；`LiveSnapshotService` 不能推进 lifecycle。
- **Presentation 不得自行组合当前 LIVE Use Case**：`LiveMatchContextService` 唯一负责 `Schedule → Target → Lifecycle → Snapshot → canonical GameId → Timeline` 调用顺序；Compose、RiftScreen、未来 Tactical HUD 只消费结果。
- `LiveSnapshotService` 必须重新验证 canonical MatchId、`GameIdentity.canonical(matchId, gameNumber)`、双方 Team identity 与 `IN_GAME` snapshot 语义后才允许进入 `LiveTimelineService`。
- snapshot identity mismatch 使用 `LNR-APP-LIVE-002`，不得污染 Timeline。
- Current LIVE Context 非业务异常使用 `LNR-APP-LIVE-003`，并保留 correlation id；协程取消不得被兜底吞掉。
- nullable metrics 保持未知；Application/UI 不得将 null 补成 0。
- raw Provider IDs 只进入 identity mapping / provenance / Adapter metadata，不成为 canonical Domain identity。
- Timeline 只接受 LIVE/POST factual sources；PRE/AI 禁止写入。
- POST Archive 仅 fallback，不和新鲜 Provider 事实投票。
- UI 不直接读取 Provider 或 Repository。

## 日志 / 失败
具体 Sink 通过 `DiagnosticsPort` 实现。LIVE snapshot diagnostics 至少包含 match/provider/game/elapsed/failures。Source/Conflict/Unavailable 不得静默吞掉。

当前关键错误码：
- `LNR-APP-LIVE-001`：LIVE lifecycle Provider 返回其他 Match；
- `LNR-APP-LIVE-002`：LIVE snapshot canonical Match/Game/team identity 或 lifecycle validation 失败；
- `LNR-APP-LIVE-003`：Current LIVE Context Query 意外失败；
- POST historical identity mismatch：`LNR-APP-POST-005`。

## 测试
`gradle :core:application:test`

LIVE Context 回归：
- Schedule 有 EVENT_LIVE 时统一返回 Ready context；
- 空赛程返回 `NO_MATCHES`，不调用 LIVE source；
- 仅 completed schedule 返回 `NO_ELIGIBLE_TARGET`；
- snapshot GameId 对应的 canonical Timeline 由 Application 统一加载；
- 意外异常返回 `LNR-APP-LIVE-003` 并发出 Diagnostics。

LIVE Snapshot 回归：
- verified canonical snapshot 可写入 Timeline；
- raw/noncanonical GameId 被拒绝且 Repository 不写入；
- wrong-team snapshot 被拒绝；
- lifecycle Service 不被 Snapshot Service 隐式调用/推进。

POST 回归继续覆盖独立事实、archive fallback-only、wrong identity、LIVE+POST canonical Timeline coexistence 与 Global-first routing。

## 故障定位
- Current LIVE：`LiveMatchContextService → Schedule/Target → LiveMatchStateService + LiveSnapshotService → LiveTimelineService`；
- LIVE lifecycle：`LiveMatchStateService → LiveMatchStateReducer`；
- LIVE 数据：`LiveSnapshotService → LiveSnapshotSourcePort → Adapter → LiveTimelineService`；
- Timeline：`LiveTimelineService → LiveTimelineRepository`；
- POST：`PostMatchService/PostTimelineService → capability/source`；
- Provider identity：`ProviderMatchIdentityRepository`；
- 数据错误不得先在 UI 打补丁。
