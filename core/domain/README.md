# `:core:domain`

## 职责
纯 Kotlin 赛事领域核心。定义全局赛事身份、比赛生命周期、三阶段、来源证据、赛程事实、赛前上下文、LIVE 权威状态、Timeline、标准事件与业务不变量。

## 输入
仅接受领域值对象与标准化事实；不得接收 Android、Compose、HTTP、数据库或 Provider SDK 类型。

## 输出
稳定领域模型、状态、事件与验证失败。

## 依赖
无项目内部上游依赖；禁止依赖 `:core:application`、`:app` 或平台库。

## Public API
- `MatchPhase`
- `MatchLifecycleState`
- 全局 ID Value Objects
- `CompetitionRef` / `TeamRef` / `PlayerRef`
- `SourceProvenance` / `FactCandidate`
- `CompetitionKind` / `ScheduleState`
- `CompetitionCatalogEntry` / `ScheduledSeries` / `ScheduledTeam`
- `TeamRosterPool`
- `OfficialStartingRoster`
- `StartingRosterResolution`
- `StaffMember` / `TeamStaffSnapshot`
- `RecentSeries` / `TeamPreMatchContext`
- `MatchState` / `LiveGameSnapshot`
- `LiveMatchState` / `LiveStateSignal`
- `LiveMatchStateReducer`
- `LiveStateTransitionResult`
- `GameTimeline` / `TimelineSnapshotPoint`
- `MatchEvent` / `MatchStateChanged`
- `KillEvent` / `ObjectiveTakenEvent` / `GoldLeadChangedEvent` / `DraftChangedEvent`
- `EventEvidence` / `DraftActionType`

## 关键不变量
- Region 只是赛事维度，不是业务模块边界。
- Schedule `EVENT_LIVE` 不等于 Match `IN_GAME`。
- 场间 `BETWEEN_GAMES` 不得被误判为新局 `IN_GAME`。
- 新 Game 开始后，旧 Game 的延迟信号不得回滚权威状态。
- `SERIES_COMPLETE` 是终态，后续 LIVE 信号不能把 Series 拉回进行中。
- 同一 lifecycle 的新鲜心跳必须刷新权威状态 freshness，但不得制造伪状态变化事件。
- 新一局若 Provider 尚未给出 gameId，不得继承上一局 gameId。
- Timeline event identity 不依赖 transport sequence / Provider 描述文本；重复重连事件必须可幂等去重。
- Timeline 允许乱序到达后按事实时间重排；同秒 snapshot 由 provenance 仲裁，不使用 last-write-wins。
- Draft action 使用标准 `DraftActionType`，Provider 自由文本不能直接成为领域动作类型。
- `ScheduledSeries` 必须恰好包含两个不同 Team。
- Schedule facts 必须来自 `PRE_MATCH_SOURCE`。
- LIVE lifecycle / Timeline provenance 必须来自 `LIVE_MATCH_SOURCE`。
- Domain 不接受 Provider raw ID 作为跨源业务语义；稳定身份由内部 ID 表达。
- Roster Pool 与 Official Starting Roster 是不同事实类型，名单池不得自动升级为首发。
- Official Starting Roster 必须恰好五名不同选手，并完整覆盖 TOP/JUNGLE/MID/BOT/SUPPORT。
- 同级冲突官方首发必须保留冲突状态，不允许静默覆盖。
- Recent Series 的 W/L 必须带明确 perspective。

## 日志
N/A：Domain 无平台日志实现。诊断由 Application/Adapter 在边界记录。

## 失败
非法状态通过构造不变量/`LiveStateTransitionResult.Conflict` 拒绝，不静默修正赛事事实。

典型冲突：
- Match identity 不一致；
- 当前 Game 仍进行中却收到未来 Game 信号；
- 同一 Game Number 出现冲突 gameId；
- 非法生命周期回退/跳转。

## 测试
`gradle :core:domain:test`

LNR-013 覆盖：
- event live != game live；
- 跳过缺失 Draft/Loading 后由 verified frame 进入 IN_GAME；
- POST_GAME / BETWEEN_GAMES / 新 Game 边界；
- G2 缺 gameId 不继承 G1；
- delayed old-game 防回滚；
- future-game conflict；
- duplicate heartbeat freshness；
- delayed POST_GAME after newer heartbeat；
- Series terminal state。

## 故障定位
- 生命周期错误：先查 `LiveMatchStateReducer` 与 `LiveMatchStateReducerTest`；
- Timeline 去重/乱序异常：查 `GameTimeline` / `semanticKey()`，再查 Application Timeline ingestion；
- 领域状态或数据不变量异常：先查本模块对应模型和 Unit Test，再查进入 Domain 前的 Application 标准化与 Adapter 翻译。
