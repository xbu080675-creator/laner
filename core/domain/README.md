# `:core:domain`

## 职责
纯 Kotlin 赛事领域核心。定义全局赛事身份、比赛生命周期、三阶段、来源证据、赛程事实、赛前上下文、LIVE 权威状态、统一 Timeline、POST 赛后事实与业务不变量。

## 输入
仅接受领域值对象与标准化事实；不得接收 Android、Compose、HTTP、数据库或 Provider SDK 类型。

## 输出
稳定领域模型、状态、事件与验证失败。

## 依赖
无项目内部上游依赖；禁止依赖 `:core:application`、`:app` 或平台库。

## Public API
- `MatchPhase` / `MatchLifecycleState`
- 全局 ID Value Objects；`CompetitionRef` / `TeamRef` / `PlayerRef`
- `SourceProvenance` / `FactCandidate`
- `CompetitionCatalogEntry` / `ScheduledSeries` / PRE context contracts
- `LiveMatchState` / `LiveStateSignal` / `LiveMatchStateReducer`
- `GameTimeline` / `TimelineSnapshotPoint`
- `MatchEvent` / `MatchStateChanged`
- `KillEvent`：可表达明确击杀，也可表达“采样窗口内队伍/选手击杀计数 +N”；缺少明确配对证据时 killer/victim 必须保持 null
- `MultiKillWindowEvent`：只表达可信采样窗内同一选手击杀计数增加，不等于官方 Double/Triple/Quadra/Penta Kill
- `TeamFightWindowEvent`：只表达短采样窗内双方累计击杀变化达到聚合阈值，不等于官方团战分类
- `ObjectiveTakenEvent`：支持 count / observed window；Dragon 总数差分不能反推龙种/龙魂/远古龙
- `GoldLeadChangedEvent`：表达领先方确认易手，不做插值
- `DraftChangedEvent`
- `SeriesResult / SeriesResultState`
- `CompletedGameRecord / PostTeamStats / PostPlayerStats / PostDraftSide`
- `VerifiedPostAward`
- `ReplayAsset / ReplayProvider`
- `PostMatchBundle`
- `GameIdentity.canonical(matchId, gameNumber)`

## 关键不变量
- Region 只是赛事维度，不是业务模块边界。
- Provider raw ID 不得成为 canonical Match/Game identity；canonical GameId 仅由 `(canonical MatchId, gameNumber)` 生成。
- Schedule `EVENT_LIVE` 不等于 Match `IN_GAME`；场间不得误判为新局。
- 新 Game 开始后旧 Game 延迟信号不得回滚状态；`SERIES_COMPLETE` 是终态。
- Timeline event identity 不依赖 transport sequence / Provider 描述文本；重复重连必须幂等。
- canonical Timeline 可接收 `LIVE_MATCH_SOURCE` 实时事实与 `POST_MATCH_SOURCE` 历史真实帧；PRE/AI 不得写入赛事 Timeline。
- 派生 LIVE 事件必须保留 `DERIVED / VERIFIED_DELTA / DERIVED_WINDOW` 等证据语义；不能把计数差分冒充 Provider explicit event。
- team kill total 的增加只证明“发生了若干击杀”，不能凭空生成 killer/victim 配对。
- player kill delta 只有在前后可信帧存在同一 canonical PlayerId 且计数可比较时才允许绑定 playerId；victim 仍保持未知。
- MultiKillWindow 只能称“采样窗口多杀”，不能升级成官方多杀播报。
- TeamFightWindow 只能称“团战窗口/战斗窗口”，不能宣称官方团战分类。
- dragon 总数差分只允许标准化为 `DRAGON`；没有专门证据不得生成 SOUL / ELDER_DRAGON 或具体龙种。
- POST Historical Timeline 不插值；缺失保持缺失。
- FINAL Series 必须有 winner 且与比分一致；PARTIAL 不得提前声明 winner。
- Series Result、Completed Game、Player Stats、Awards、Replay、Historical Timeline 是不同事实类型，不得互相反推。
- player stat 缺失使用 `null`，不得用 `0` 冒充未知。
- `DataAuthority.DERIVED` 禁止发布 MVP/POG；Awards 必须有可追溯证据。
- Replay 只表达录像元数据，不表达 Android 播放实现。
- Completed Game 必须有明确赢家证据；不得从经济、击杀、塔数猜 winner。
- Roster Pool 与 Official Starting Roster 是不同事实类型，名单池不得自动升级为首发。

## 日志
N/A：Domain 无平台日志实现。诊断由 Application/Adapter 在边界记录。

## 失败
非法事实通过构造不变量或显式 Conflict 拒绝，不静默修正赛事事实。典型冲突包括 Match/Game identity 不一致、非法 lifecycle 回退、FINAL Series winner/score 不一致、错误 source class 写入 Timeline、Derived Award、负数事件 count/window、跨 Game 事件污染。

## 测试
`gradle :core:domain:test`

重点覆盖：LIVE 生命周期/乱序/终态；Timeline 去重与 LIVE/POST source-class 边界；新增 Tactical event 构造不变量与 semantic identity；POST final winner/score consistency；partial winner rejection；nullable stats；Derived Award rejection；Replay provider-neutral；canonical GameId 稳定性与跨 Match 隔离。

## 故障定位
- 生命周期：`LiveMatchStateReducer` / `LiveMatchStateReducerTest`；
- Timeline：`GameTimeline` / `TimelineSnapshotPoint` / `semanticKey()`；
- Tactical event 模型：`MatchEvent.kt` / `Timeline.kt`；
- POST 事实：`PostMatch.kt` 与对应 Domain tests；
- 身份：`GameIdentity` / ADR-003；
- 不得先在 UI 内补丁修正 Domain 数据错误。
