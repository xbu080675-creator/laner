# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay/Media/OTA、具体 Source Adapter 与本地持久化 Adapter。Core 禁止反向依赖本模块。

## 输入 / 输出 / 依赖
只消费 `:core:application` 公开 Use Case/Port 与 `:core:domain` 只读模型；输出 Android UI 与平台副作用。允许依赖 Core、Android/Compose/网络/文件系统。

## Public API / Composition Root
- `MainActivity / LanerApplication / LanerAppGraph / LanerRoot`
- `PreMatchScreen / LiveMatchScreen / PostMatchScreen`
- `RiftScreenController / RiftScreenOverlayService / RiftScreenOverlayView`
- `DraftHudOverlayView / DraftHudControlView / DraftHudLayoutStore`
- Riot PRE/LIVE/POST Adapters
- `RiotGlobalLiveStateSource`
- `RiotGlobalLiveSnapshotSource`
- `JsonLiveMatchStateRepository / JsonLiveTimelineRepository`
- POST archive / provider identity persistence Adapters

所有 Adapter 只实现/消费 Core Port 与标准模型，不向 UI/Overlay 暴露 Provider payload。

## LIVE 数据链

生命周期：
```text
GlobalScheduleService
→ canonical ScheduledSeries
→ LiveMatchSourceQuery
→ RiotGlobalLiveStateSource
→ LiveMatchStateService
→ LiveStateResolution
→ LiveMatchScreen / RiftScreen
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
→ LiveMatchScreen / RiftScreen
```

**两条链不能合并职责**：`LiveMatchStateService` 是 lifecycle 权威；`LiveSnapshotService` 只验证/仲裁 gameplay frame 并写 Timeline，不自行推进比赛状态。

LIVE Snapshot 当前可展示：双方经济、击杀总数、防御塔、龙、男爵，以及选手 level/KDA/CS/gold/champion（以上游真实字段为准）。缺失字段保持 null，UI 显示“未知”，不补 0。

Cito online 继续 `DEFERRED / WAITING EXTERNAL TEST`；Riot Global 是当前真实 baseline，但 CI fixture 不等价于赛事现场 online PASS。

## RiftScreen / Draft HUD

RiftScreen 与 Draft HUD 均为 Android Presentation Adapter，不拥有赛事事实：
```text
GlobalScheduleService
→ LiveTargetSelector
→ LiveMatchStateService + LiveSnapshotService
→ LiveTimelineService
→ display-only Presentation Mapper
→ Android WindowManager
```

- `LanerApplication` 提供进程级 Composition Root，因此 Activity 与 Foreground Service 共用同一组 Application services；不恢复旧 `MatchSessionStore` 大总线。
- RiftScreen 保留 `MINI / COMPACT / EXPANDED`、拖动、关闭、App 前台自动隐藏 / 后台显示。
- Verified Draft HUD 只在 canonical lifecycle 为 `DRAFT` 时激活，只读取 canonical `DraftChangedEvent`。没有 side-selection 事实时只称“左/右侧”，不把赛程顺序冒充蓝/红方；没有角色/对位证据时不推断。
- `DraftHudPreviewSession` 是 Android-only 本地视觉夹具，固定标记 `LOCAL PREVIEW · NOT FACT`，不进入 Core、Source Arbitration、Repository 或 Timeline。
- HUD Edit 模式允许模块拖动、Scale、Alpha、Visibility、Reset；配置按横屏/竖屏独立持久化。
- HUD Lock 后，全屏 HUD Window 加 `FLAG_NOT_TOUCHABLE` 真正触摸穿透；边缘控制 Dock 保持可操作，用于重新进入编辑模式。
- 系统悬浮窗权限、拖动/边界、横竖屏 Profile、锁定触摸穿透仍必须在 Android 真机补证，状态为 `WAITING EXTERNAL TEST`。

## Local persistence
- LIVE State / Timeline schema_version=1；
- canonical ID → SHA-256 稳定文件名；JSON 内复核完整 canonical ID；
- sibling temp + atomic replace；corrupt/unsupported schema 显式失败；
- Timeline 只保存标准 Snapshot/Event，不保存 raw Provider payload；
- Draft HUD Layout 只保存用户布局参数，不保存赛事事实。

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

LNR-020 当前自动证据：
- foundation fix 后 push run `34704124799` / PR run `34704127811` 全 Gate PASS；
- Draft HUD 完成切片在 `871e1a0ad257c465dc51720df46fb66cceeae7cc` 的 PR run `34705512479` 全 Gate PASS；
- artifact `10301324396`, digest `sha256:ac2953bc1a29ebaead91958a689909e4bae6c3374a795d5ab3956398ec81d424`；
- Android system-overlay 行为仍为 `WAITING EXTERNAL TEST`。

## 故障定位
PRE：`UI → GlobalSchedule/PreMatchContext → Port → Adapter`。

LIVE lifecycle：`LiveMatchScreen/RiftScreen → LiveMatchStateService → RiotGlobalLiveStateSource`。

LIVE gameplay：`LiveMatchScreen/RiftScreen → LiveSnapshotService → RiotGlobalLiveSnapshotSource → LiveTimelineService → JsonLiveTimelineRepository`。

Draft HUD：`RiftScreenOverlayService → LiveMatchStateService + canonical Timeline → DraftHudPresentationMapper → DraftHudOverlayView`。本地预览只允许停留在 `DraftHudPreviewSession → DraftHudPresentation → View`。

数据错误不得先在 UI 补丁；Provider parse 回 Adapter，canonical validation/仲裁回 Application/Domain。
