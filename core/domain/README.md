# `:core:domain`

## 职责
纯 Kotlin 赛事领域核心。定义全局赛事身份、比赛生命周期、三阶段、来源证据、赛程事实、赛前上下文、标准事件与业务不变量。

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
- `MatchEvent`

## 关键不变量
- Region 只是赛事维度，不是业务模块边界。
- Schedule `EVENT_LIVE` 不等于 Match `IN_GAME`。
- `ScheduledSeries` 必须恰好包含两个不同 Team。
- Schedule facts 必须来自 `PRE_MATCH_SOURCE`。
- Domain 不接受 Provider raw ID 作为跨源业务语义；稳定身份由内部 ID 表达。
- Roster Pool 与 Official Starting Roster 是不同事实类型，名单池不得自动升级为首发。
- Official Starting Roster 必须恰好五名不同选手，并完整覆盖 TOP/JUNGLE/MID/BOT/SUPPORT。
- 同级冲突官方首发必须保留冲突状态，不允许静默覆盖。
- Recent Series 的 W/L 必须带明确 perspective。

## 日志
N/A：Domain 无平台日志实现。诊断由 Application/Adapter 在边界记录。

## 失败
非法状态通过构造不变量/异常拒绝，不静默修正赛事事实。

## 测试
`gradle :core:domain:test`

## 故障定位
领域状态或数据不变量异常，先查本模块对应模型和 Unit Test，再查进入 Domain 前的 Application 标准化与 Adapter 翻译。
