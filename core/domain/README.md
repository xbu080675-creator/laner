# `:core:domain`

## 职责
纯 Kotlin 赛事领域核心。定义全局赛事身份、比赛生命周期、三阶段、来源证据、标准事件与业务不变量。

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
- `SourceProvenance` / `FactCandidate`
- `MatchState` / `LiveGameSnapshot`
- `MatchEvent`

## 日志
N/A：Domain 无平台日志实现。诊断由 Application/Adapter 在边界记录。

## 失败
非法状态通过构造不变量/异常拒绝，不静默修正赛事事实。

## 测试
`gradle :core:domain:test`

## 故障定位
领域状态或数据不变量异常，先查本模块对应模型和 Unit Test，再查进入 Domain 前的 Adapter 标准化。
