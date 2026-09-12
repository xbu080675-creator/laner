# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、未来 Overlay/Media/OTA、具体 Source Adapter 与本地持久化 Adapter。Core 禁止反向依赖本模块。

## 输入 / 输出 / 依赖
只消费 `:core:application` 公开 Use Case/Port 与 `:core:domain` 只读模型；输出 Android UI 与平台副作用。允许依赖 Core、Android/Compose/网络/文件系统。

## Public API / Composition Root
- `MainActivity / LanerAppGraph / LanerRoot`
- `PreMatchScreen / LiveMatchScreen / PostMatchScreen`
- Riot PRE/LIVE/POST Adapters
- `RiotGlobalLiveStateSource`
- `RiotGlobalLiveSnapshotSource`
- `JsonLiveMatchStateRepository / JsonLiveTimelineRepository`
- POST archive / provider identity persistence Adapters

所有 Adapter 只实现 Core Port，不向 UI 暴露 Provider payload。

## LIVE 数据链

生命周期：
```text
GlobalScheduleService
→ canonical ScheduledSeries
→ LiveMatchSourceQuery
→ RiotGlobalLiveStateSource
→ LiveMatchStateService
→ LiveStateResolution
→ LiveMatchScreen
```

Gameplay 真帧：
```text
ProviderMatchIdentityRepository
→ Riot EventDetails active game
→ Riot LiveStats latest real frame
→ RiotGlobalLiveSnapshotSource
→ LiveSnapshotService
   canonical Match/Game/team validation
→ LiveTimelineService
→ JsonLiveTimelineRepository
→ LiveMatchScreen GAME DATA
```

**两条链不能合并职责**：`LiveMatchStateService` 是 lifecycle 权威；`LiveSnapshotService` 只验证/仲裁 gameplay frame 并写 Timeline，不自行推进比赛状态。

LIVE Snapshot 当前可展示：双方经济、击杀总数、防御塔、龙、男爵，以及选手 level/KDA/CS/gold/champion（以上游真实字段为准）。缺失字段保持 null，UI 显示“未知”，不补 0。

Cito online 继续 `DEFERRED / WAITING EXTERNAL TEST`；Riot Global 是当前真实 baseline，但 CI fixture 不等价于赛事现场 online PASS。

## Local persistence
- LIVE State / Timeline schema_version=1；
- canonical ID → SHA-256 稳定文件名；JSON 内复核完整 canonical ID；
- sibling temp + atomic replace；corrupt/unsupported schema 显式失败；
- Timeline 只保存标准 Snapshot/Event，不保存 raw Provider payload。

## Credential
Riot credential 不进入 Git/日志/fixture。正式注入支持环境变量/Gradle Property；LNR-016 测试 build 额外支持进程内存 runtime Key，进程退出即清除，不持久化。

## 日志 / 错误码
- App `[Laner:APP]` / Source `[Laner:SRC]` / PRE `[Laner:PRE]` / LIVE `[Laner:LIVE]`。
- `LNR-SRC-LIVE-002~005`：Riot lifecycle target/discovery/request diagnostics。
- `LNR-SRC-LIVE-006`：Gameplay Snapshot Riot Key 缺失。
- `LNR-SRC-LIVE-007`：Gameplay Snapshot EventDetails/LiveStats 请求或解析失败。
- `LNR-APP-LIVE-002`：snapshot canonical Match/Game/team validation 失败，不得写 Timeline。

Provider 缺失/失败/冲突必须显式降级，不制造事实。

## 测试
- Core：`:core:domain:test :core:application:test`
- Android Adapter：`:app:testDebugUnitTest`
- Android：`:app:assembleDebug`
- CI：Architecture → Core → App Unit → Android build → debug APK artifact。

LNR-019：
- run `34699837518`：Architecture PASS / Core test compile FAIL（测试错误使用未配置 coroutine/JUnit harness），失败已留档；
- fix `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5`；
- run `34699942180`：Architecture/Core/App/Android/APK upload 全 PASS；
- real BLG vs AL online/device：`WAITING EXTERNAL TEST`。

## 故障定位
PRE：`UI → GlobalSchedule/PreMatchContext → Port → Adapter`。

LIVE lifecycle：`LiveMatchScreen → LiveMatchStateService → RiotGlobalLiveStateSource`。

LIVE gameplay：`LiveMatchScreen → LiveSnapshotService → RiotGlobalLiveSnapshotSource → LiveTimelineService → JsonLiveTimelineRepository`。

数据错误不得先在 UI 补丁；Provider parse 回 Adapter，canonical validation/仲裁回 Application/Domain。
