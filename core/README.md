# Core 模块

## 模块职责

`core` 是 Laner/RiftLab 迁移后的领域核心与 Port 所在模块。它只保存赛事领域模型、标准化数据契约、纯业务规则以及外部能力的接口定义，不负责 Android 生命周期、UI、网络请求、磁盘、Keystore、OCR 或具体 Provider。

## 输入

- 标准化赛事、队伍、选手、赛程和实时快照数据。
- Adapter 通过 Port 返回的领域对象。
- 纯 Kotlin/JDK 可表达的时间、标识、协议参数与安全校验信息。

## 输出

- 稳定领域模型。
- `ScheduleDataSource`、`StandingsDataSource`、`TeamDataSource`、`LiveMatchDataSource`、`AiInsightEngine` 等 Port 契约。
- 不含平台副作用的领域判断、协议校验、安全决策与覆盖率报告。

## 依赖

允许：
- Kotlin/JDK。
- `kotlinx-coroutines-core`（仅用于 `Flow` 契约）。

禁止：
- Android API。
- Compose / Coil / Media3 / MLKit。
- OkHttp 或任何具体网络 Provider SDK。
- 文件系统、SharedPreferences、Android Keystore 等平台存储实现。

## Public API

当前迁移兼容期保留原 `com.riftlab.app.data` 包名，避免为了分层而修改业务调用语义。模块边界由 Gradle 强制；后续如需包名重命名，必须作为独立迁移任务处理。

主要公开契约：
- `ScheduledEsportsMatch` / `EsportsTeamRef` / `LiveSnapshot` 等领域模型。
- `DataSources.kt` 中的 Port。
- `ComprehensiveData*` 数据图与覆盖率契约。
- `LocalLiveInsightEngine` 的确定性本地解释实现。
- `MatchIdentityPolicy` / `LiveMatchTargetRegistry` / `LiveFrameIdentityGate` 的比赛身份规则。
- `OpenClawSecurityPolicy` / `RiftClawContract` / `RiftClawInjectionGuard` 的 localhost 协议与纯安全校验规则。
- `Qualification*` 资格领域模型、LPL/Worlds/LCP 官方资格静态快照与规则。
- `TournamentEdition*` 年度赛事档案模型，以及 `TournamentResearch*` 纯赛事研究模型/推导器与 schema。
- `CompletedSeriesSnapshot`、`MatchDetail*`、`TournamentEventHistorySnapshot` 等赛后/历史赛事领域模型。
- `LplChampionshipPoints2026` / `Worlds2026QualifiedTeams` 的已核实资格快照与领域状态。

## 日志

Core 不直接写平台日志。需要观测的信息通过返回值/状态交给上层记录；禁止 Core 直接依赖 Android Log。

## 错误

Core 不吞掉平台异常，也不制造 Provider fallback。平台错误必须在 Adapter 侧转换为已有领域状态后进入 Core。

## 测试

- `:core:test`：纯 JVM 测试。
- `:app:assembleDebug`：验证 Adapter/UI 与 Core 的二进制接线。
- 架构门禁必须确保 `core/src` 不出现 Android、UI、网络和具体 Provider 依赖。
