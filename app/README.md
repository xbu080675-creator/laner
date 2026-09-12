# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay/Media/OTA、具体 Source Adapter 与本地持久化 Adapter。Core 禁止反向依赖本模块。

## 输入 / 输出 / 依赖
只消费 `:core:application` 公开 Use Case/Port 与 `:core:domain` 只读模型；输出 Android UI 与平台副作用。允许依赖 Core、Android/Compose/网络/文件系统。

## Public API / Composition Root
- `MainActivity / LanerApplication / LanerAppGraph / LanerRoot`
- `PreMatchScreen / LiveMatchScreen / PostMatchScreen`
- `RiftScreenController / RiftScreenOverlayService`
- `OverlayWindowHost / RiftScreenWindowController / DraftHudWindowController`
- `RiftScreenOverlayView / DraftHudOverlayView / DraftHudControlView / DraftHudLayoutStore`
- Riot PRE/LIVE/POST Adapters
- `RiotGlobalLiveStateSource`
- `RiotGlobalLiveSnapshotSource`
- `JsonLiveMatchStateRepository / JsonLiveTimelineRepository`
- POST archive / provider identity persistence Adapters

所有 Adapter 只实现/消费 Core Port 与标准模型，不向 UI/Overlay 暴露 Provider payload。

## LIVE 数据链

当前 LIVE Presentation 统一入口：
```text
GlobalScheduleService
→ LiveTargetSelector
→ LiveMatchStateService + LiveSnapshotService
→ canonical GameId selection
→ LiveTimelineService
→ LiveMatchContextService / LiveMatchContextResult
→ LiveMatchScreen / RiftScreenOverlayService
```

Lifecycle 与 Gameplay 内部仍是两条独立证据链：
```text
LiveMatchStateService
← RiotGlobalLiveStateSource
```

```text
ProviderMatchIdentityRepository
→ Riot EventDetails active game
→ Riot LiveStats latest real frame
→ RiotGlobalLiveSnapshotSource
→ LiveSnapshotService
   canonical Match/Game/team validation
→ LiveTimelineService
→ JsonLiveTimelineRepository
```

**两条事实链不能合并职责**：`LiveMatchStateService` 是 lifecycle 权威；`LiveSnapshotService` 只验证/仲裁 gameplay frame 并写 Timeline，不自行推进比赛状态。**两个 Presentation surface 也不得各自重新组合这些 Service**；当前组合 Use Case 只有 `LiveMatchContextService`。

LIVE Snapshot 当前可展示：双方经济、击杀总数、防御塔、龙、男爵，以及选手 level/KDA/CS/gold/champion（以上游真实字段为准）。缺失字段保持 null，UI 显示“未知”，不补 0。

Cito online 继续 `DEFERRED / WAITING EXTERNAL TEST`；Riot Global 是当前真实 baseline，但 CI fixture 不等价于赛事现场 online PASS。

## RiftScreen / Draft HUD

RiftScreen 与 Draft HUD 均为 Android Presentation Adapter，不拥有赛事事实：
```text
LiveMatchContextResult
→ display-only Presentation Mapper
→ RiftScreenWindowController / DraftHudWindowController
→ OverlayWindowHost
→ Android WindowManager
```

- `LanerApplication` 提供进程级 Composition Root，因此 Activity 与 Foreground Service 共用同一组 Application services；不恢复旧 `MatchSessionStore` 大总线。
- `RiftScreenOverlayService` 只负责 Foreground Service 生命周期、通知/Action、刷新调度、Preview collection、Application result 到 Presentation 的连接和组件组装；不直接维护 WindowManager 参数、Rift 拖拽或 Draft Edit/Lock 细节。
- `RiftScreenWindowController` 保留 `MINI / COMPACT / EXPANDED`、拖动、屏幕边界 clamp、关闭等窗口行为。
- `DraftHudWindowController` 负责 Verified/Preview 显示优先级、HUD/Dock window、Edit/Lock 和模块控制 wiring。
- `OverlayWindowHost` 是 WindowManager 唯一平台操作入口，add/update/remove/bounds 失败必须发出 `[Laner:OVERLAY]` diagnostics，不允许静默吞异常。
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
- Draft HUD Layout 只保存用户布局参数，不保存赛事事实；当前 namespace `laner_draft_hud_layout_v1` 未在本次整改中变更。

## Credential
Riot credential 不进入 Git/日志/fixture。正式注入支持环境变量/Gradle Property；LNR-016 测试 build 额外支持进程内存 runtime Key，进程退出即清除，不持久化。

## 日志 / 错误码
- App `[Laner:APP]` / Source `[Laner:SRC]` / PRE `[Laner:PRE]` / LIVE `[Laner:LIVE]` / Overlay `[Laner:OVERLAY]`。
- `LNR-SRC-LIVE-002~005`：Riot lifecycle target/discovery/request diagnostics。
- `LNR-SRC-LIVE-006`：Gameplay Snapshot Riot Key 缺失。
- `LNR-SRC-LIVE-007`：Gameplay Snapshot EventDetails/LiveStats 请求或解析失败。
- `LNR-APP-LIVE-002`：snapshot canonical Match/Game/team validation 失败，不得写 Timeline。
- `LNR-APP-LIVE-003`：Current LIVE Context Query 意外失败。
- `LNR-OVR-WINDOW-001~004`：overlay add/update/remove/bounds 平台故障。
- `LNR-OVR-REFRESH-001`：Overlay Presentation mapping 意外失败。

Provider 缺失/失败/冲突必须显式降级，不制造事实；WindowManager 平台异常必须可观测，不允许无日志 `runCatching`。

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

LNR-020 历史自动证据：
- foundation fix 后 push run `34704124799` / PR run `34704127811` 全 Gate PASS；
- Draft HUD 完成切片在 `871e1a0ad257c465dc51720df46fb66cceeae7cc` 的 PR run `34705512479` 全 Gate PASS；
- PR #10 merge `967e112d6efcf8e6daa86f0b007cedc39b63b04c` 后 main run `34706047380` PASS；
- 原功能 Android system-overlay 行为仍为 `WAITING EXTERNAL TEST`。

LNR-020 合规整改：
- 事故 `INC-LNR-020-001`：`CLOSED`；
- PR #12 final head `249c42208ab6105ad26b78215b47fbd754d889e9`；
- exact-head run `34708127194`：Architecture/Core/App Unit/Android compile/APK upload PASS；
- PR #12 merge `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`；
- post-merge main run `34708285172`：Architecture/Core/App Unit/Android compile/APK upload PASS；
- `LiveMatchContextServiceTest` 覆盖 Current LIVE Context；
- `OverlayWindowOperationTest` 锁定 WindowManager diagnostics 错误码；
- Android 真机项继续 `WAITING EXTERNAL TEST`，不因合规整改关闭而升级。

## 故障定位
PRE：`UI → GlobalSchedule/PreMatchContext → Port → Adapter`。

Current LIVE：`LiveMatchScreen/RiftScreenOverlayService → LiveMatchContextService → LiveMatchStateService + LiveSnapshotService → LiveTimelineService`。

LIVE lifecycle：`LiveMatchContextService → LiveMatchStateService → RiotGlobalLiveStateSource`。

LIVE gameplay：`LiveMatchContextService → LiveSnapshotService → RiotGlobalLiveSnapshotSource → LiveTimelineService → JsonLiveTimelineRepository`。

RiftScreen Window：`RiftScreenOverlayService → RiftScreenWindowController → OverlayWindowHost → LNR-OVR-WINDOW-*`。

Draft HUD：`RiftScreenOverlayService → LiveMatchContextResult → DraftHudPresentationMapper → DraftHudWindowController → DraftHudOverlayView/ControlView`。本地预览只允许停留在 `DraftHudPreviewSession → DraftHudPresentation → View`。

数据错误不得先在 UI 补丁；Provider parse 回 Adapter，canonical validation/仲裁回 Application/Domain；平台窗口错误回 Overlay diagnostics。
