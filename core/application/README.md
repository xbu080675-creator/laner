# `:core:application`

## 职责
纯 Kotlin应用层。定义 Ports、全球来源注册/仲裁、Use Case、Query 与明确的降级/冲突语义。赛区只能出现在 Query/Capability 数据中，不得成为业务模块分支。

## 输入
Domain 类型和由 Adapter 实现的 Port。

## 输出
经过统一业务规则处理的 Domain Fact、Application Query 与明确的 `READY / DEGRADED / CONFLICT / UNAVAILABLE` 状态。

## 依赖
只依赖 `:core:domain`。禁止依赖 Android、Compose、具体 Provider、数据库实现。

## Public API
- PRE: `GlobalPreMatchSourcePort` / `GlobalScheduleService` / context/structure services
- LIVE: `LiveStateSourcePort` / `LiveMatchStateService` / `LiveTimelineRepository` / `LiveTimelineService`
- POST: `PostMatchQuery`
- `PostSourceCapability`
- `PostResultSourcePort`
- `CompletedGameSourcePort`
- `PostAwardSourcePort`
- `ReplaySourcePort`
- `PostMatchArchiveRepository`
- `ProviderMatchIdentityRepository`
- `PostMatchService`
- `PostTimelineSourcePort` / `HistoricalTimelineFrame`
- `PostTimelineService`
- `DiagnosticsPort`

## 关键规则
- Provider 只翻译 raw payload，不决定全局事实优先级。
- Application 不写 `if LPL / if LCK / if LEC / if LCP`；来源通过 `PostSourceCapability.supports(query)` 声明覆盖能力。
- Global Provider 提供跨赛区 baseline；区域 Provider 只能作为 supplement，与全球来源平级参与事实仲裁。
- Series Result / Completed Game / Awards / Replay 独立读取、独立失败；单个来源失败不得清空其他已验证事实。
- Archive 只填补外部来源缺失事实，不与新鲜 Provider 事实投票；Conflict 不覆盖 last-known-good archive。
- Provider raw event/match/game ID 只进入 `ProviderMatchIdentityRepository` / provenance / Adapter metadata，不泄漏为 canonical Domain identity。
- Replay 多 Provider 可以共存；同一事实 slot 的矛盾 Result/Award 必须显式 Conflict。
- `PostTimelineService` 再次校验 canonical MatchId/GameId/gameNumber 后，才允许历史真实帧进入统一 `LiveTimelineService`；错误身份返回 `LNR-APP-POST-005`。
- Historical Timeline 只保存 Provider 真帧，不插值、不根据终局数值反推过程。
- LIVE lifecycle 与 Timeline 的既有 freshness/semantic dedupe 规则继续生效。
- UI 不直接读取 Provider 或 Repository。

## 日志
具体 Sink 通过 `DiagnosticsPort` 实现。PRE/LIVE/POST failure、Conflict、Unavailable 均不得吞异常。POST 诊断至少应可定位 `match_id / competition / provider / capability / game / failures / conflicts`。

## 失败
- PRE：`LNR-SRC-PRE-*`
- LIVE：`LNR-APP-LIVE-001` 等
- POST：来源错误使用 `LNR-SRC-POST-*`；历史 Timeline canonical identity mismatch 使用 `LNR-APP-POST-005`。

## 测试
`gradle :core:application:test`

POST 回归重点：
- Award failure 不擦除有效 Result；
- conflicting Award/Result 可见；
- wrong-match fact 在仲裁前拒绝；
- fresh final 可压过 stale partial；
- 多 Replay Provider 共存；
- unsupported regional source 不被调用；
- archive fallback-only；Conflict 不覆盖 last-good；
- POST 历史帧可进入 canonical Timeline；错误 GameId/MatchId 不得污染 Timeline；
- Global-first routing 不依赖赛区业务分支。

## 故障定位
- Schedule/PRE：`GlobalScheduleService` → Port → Adapter；
- LIVE lifecycle：`LiveMatchStateService` → Reducer；
- Timeline：`PostTimelineService` / `LiveTimelineService` → Repository；
- POST aggregate：`PostMatchService` → capability routing → 对应 Result/Game/Award/Replay Port；
- Provider identity：`ProviderMatchIdentityRepository` → Adapter 定位；
- 数据错误不得先在 UI 内补丁修正。
