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
- LIVE event derivation：`LiveEventDerivationService`，只从 canonical Timeline snapshot 做保守、可重算的确定性差分事件。
- Timeline：`LiveTimelineRepository / LiveTimelineService`；支持事实 ingest 与指定 generator provider 的派生事件 reconcile。
- POST：Result/Game/Award/Replay Ports、`PostMatchService`、`PostTimelineService`、Archive/Provider identity Ports。
- `DiagnosticsPort`。

## 关键规则
- Provider 只翻译 raw payload，不决定全局事实优先级。
- Application 不写 LPL/LCK/LEC/LCP 业务分支；Global Provider 是 baseline，区域来源只能 supplement。
- **LIVE lifecycle 与 gameplay snapshot 是两条独立证据链**：`LiveMatchStateService` 是 lifecycle 唯一权威；`LiveSnapshotService` 不能推进 lifecycle。
- **Presentation 不得自行组合当前 LIVE Use Case**：`LiveMatchContextService` 唯一负责 `Schedule → Target → Lifecycle → Snapshot → canonical GameId → Timeline` 调用顺序；Compose、RiftScreen、Tactical HUD 只消费结果。
- `LiveSnapshotService` 必须重新验证 canonical MatchId、`GameIdentity.canonical(matchId, gameNumber)`、双方 Team identity 与 `IN_GAME` snapshot 语义后才允许进入 `LiveTimelineService`。
- snapshot identity mismatch 使用 `LNR-APP-LIVE-002`，不得污染 Timeline。
- Current LIVE Context 非业务异常使用 `LNR-APP-LIVE-003`，并保留 correlation id；协程取消不得被兜底吞掉。
- `LiveEventDerivationService` 只处理同 canonical Game、同 LIVE factual source-class、游戏时间单调递增的 snapshot 邻接对；乱序/更强同秒帧通过 Timeline reconcile 后重新派生。
- 派生 provider 固定为 `laner-live-event-derivation`，便于只替换自己生成的事件，不删除 Provider explicit 事件。
- 队伍击杀计数差分只生成 aggregate KillEvent；只有 player kill 计数前后可比较且总和与 team delta 完全一致时，才允许绑定 playerId。
- `MultiKillWindowEvent` 只在同一 player 的 kill delta >=2 且采样窗 <=20s 时生成；不得称官方 Double/Triple/Quadra/Penta。
- `TeamFightWindowEvent` 只有在采样窗 <=20s、**双方 kill delta 都可比较**且累计 >=3 时生成；任一侧 unknown 时不得用 0 替代，也不得生成 TeamFightWindow。
- team kill counter regression 不制造 Kill/TeamFight；player kill counter regression 或 player row 缺失时最多保留 team aggregate Kill，不能制造 PlayerId。
- GoldLeadChange 使用 ±250g deadband，只在领先方从一侧明确切到另一侧时生成，避免持平附近抖动和 Timeline flood。
- Objective delta 只对现有可信计数字段生成；Dragon 总数不能推导龙种/龙魂/远古龙。Baron 可用；Herald/Atakhan 仍未全局标准化。
- `LiveTimelineService.reconcileGeneratedEvents` 只能替换指定 generatorProviderId 的事件；Provider explicit / Draft / lifecycle 等其他来源事件必须保留。
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
- `LNR-APP-LIVE-004`：历史事实完整性故障 ID，指向 TeamFight unknown→0 事故；当前通过永久回归防止复发；
- POST historical identity mismatch：`LNR-APP-POST-005`。

LNR-021 的 event derivation 当前属于确定性纯 Application 逻辑，不为普通“没有足够事实所以不派生”情况输出错误；非法 identity/source/event 继续由既有 Domain/Timeline 边界拒绝。若未来引入外部事件 Provider，必须为 Adapter/Source 另建稳定错误码。

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

LNR-021 LIVE Event 回归：
- team kill delta 生成 aggregate KillEvent，不造 killer/victim；
- player kill delta 完全解释 team delta 时允许 player-bound KillEvent；
- 采样窗多杀生成 `MultiKillWindowEvent`，不冒充官方 multi-kill；
- TeamFightWindow 需要双方 delta 均已知且短窗累计 >=3；一侧 unknown 时不生成；
- team counter regression 不生成 combat event；
- player counter regression / player row 缺失退化为 aggregate unknown-player Kill；
- Dragon/Baron/Tower count delta 生成标准 Objective；Dragon subtype 保持未知；
- Gold lead 只有跨越 deadband 且领先方真正易手时生成；
- 外部 Provider explicit 事件可抑制同区间重复 local-derived 事件；
- 同 generator 的旧派生事件可 reconcile 替换，其他来源事件不被删除；
- late/out-of-order/stronger same-second snapshot 后重新派生保持幂等。

永久事实安全入口：`LiveEventDerivationSafetyRegressionTest`。

POST 回归继续覆盖独立事实、archive fallback-only、wrong identity、LIVE+POST canonical Timeline coexistence 与 Global-first routing。

## 故障定位
- Current LIVE：`LiveMatchContextService → Schedule/Target → LiveMatchStateService + LiveSnapshotService → LiveTimelineService`；
- LIVE lifecycle：`LiveMatchStateService → LiveMatchStateReducer`；
- LIVE 数据：`LiveSnapshotService → LiveSnapshotSourcePort → Adapter → LiveTimelineService`；
- LIVE events：`LiveMatchContextService → canonical GameTimeline → LiveEventDerivationService → LiveTimelineService.reconcileGeneratedEvents`；
- Timeline：`LiveTimelineService → LiveTimelineRepository`；
- POST：`PostMatchService/PostTimelineService → capability/source`；
- Provider identity：`ProviderMatchIdentityRepository`；
- 数据错误不得先在 UI 打补丁。
