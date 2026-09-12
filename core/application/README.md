# `:core:application`

## 职责
纯 Kotlin 应用层。定义 Ports、来源注册/仲裁、Use Case、Query 与观众/教练两种展示契约。

## 输入
Domain 类型和由 Adapter 实现的 Port。

## 输出
经过统一业务规则处理的 Domain Fact、Application Query 与明确的冲突/失败状态。

## 依赖
只依赖 `:core:domain`。禁止依赖 Android、Compose、具体 Provider、数据库实现。

## Public API
- `FactSourcePort`
- `FactRepository`
- `FactArbiter`
- `PhaseQuery`
- `PhaseViewState`

## 日志
本模块只定义诊断语义；具体日志 Sink 后续通过 Port/Adapter 实现。

## 失败
来源失败必须显式返回错误码；同级事实冲突必须返回 `Conflict`，禁止静默覆盖。

## 测试
`gradle :core:application:test`

## 故障定位
数据来源优先级、冲突或降级异常，先查 `FactArbiter` / Source Port，再查具体 Adapter。
