# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay/Media/OTA、具体 Source Adapter 与本地持久化 Adapter。Core 禁止反向依赖本模块。

## 输入 / 输出 / 依赖
只消费 `:core:application` 公开 Use Case/Port 与 `:core:domain` 只读模型；输出 Android UI 与平台副作用。允许依赖 Core、Android/Compose、网络、文件系统。

## Public API / Composition Root
- `MainActivity / LanerApplication / LanerAppGraph / LanerRoot`
- `PreMatchScreen / LiveMatchScreen / PostMatchScreen`
- `RiftScreenController / RiftScreenOverlayService`
- `OverlayWindowHost / RiftScreenWindowController / DraftHudWindowController / TacticalHudWindowController`
- `RiftScreenOverlayView / DraftHudOverlayView / DraftHudControlView / DraftHudLayoutStore`
- `TacticalHudPresentationMapper / TacticalHudOverlayView / TacticalHudPreviewSession`
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
→ LiveEventDerivationService
→ LiveTimelineService.reconcileGeneratedEvents
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

**两条事实链不能合并职责**：`LiveMatchStateService` 是 lifecycle 权威；`LiveSnapshotService` 只验证/仲裁 gameplay frame 并写 Timeline，不自行推进比赛状态。Presentation 不得自己编排 Schedule/State/Snapshot/Timeline；当前组合 Use Case 只有 `LiveMatchContextService`。

LIVE Snapshot 当前可展示：双方经济、击杀总数、防御塔、龙、男爵，以及选手 level/KDA/CS/gold/champion（以上游真实字段为准）。缺失字段保持 null，UI 显示“未知”，不补 0。

LNR-021 在 canonical Timeline snapshot 之上增加确定性事件派生：Kill delta、MultiKillWindow、TeamFightWindow、GoldLeadChange、Tower/Dragon/Baron delta。它们只使用已存在且可比较的事实字段；没有明确配对证据时不造 killer/victim，没有龙种证据时不猜龙种/龙魂/远古龙。

Cito online 继续 `DEFERRED / WAITING EXTERNAL TEST`；Riot Global 是当前真实 baseline，但 CI fixture 不等价于赛事现场 online PASS。

## RiftScreen / Draft HUD / Tactical HUD

三种 Overlay 均为 Android Presentation Adapter，不拥有赛事事实：
```text
LiveMatchContextResult
→ display-only Presentation Mapper
→ RiftScreenWindowController / DraftHudWindowController / TacticalHudWindowController
→ OverlayWindowHost
→ Android WindowManager
```

窗口优先级沿用旧版可见行为：
```text
Draft HUD > Tactical HUD > normal RiftScreen
```

- `LanerApplication` 提供进程级 Composition Root，因此 Activity 与 Foreground Service 共用同一组 Application services；不恢复旧 `MatchSessionStore` 大总线。
- `RiftScreenOverlayService` 只负责 Foreground Service 生命周期、通知/Action、刷新调度、Preview collection、Application result 到 Presentation 的连接和组件组装；不直接维护 WindowManager 参数、Rift 拖拽、Draft Edit/Lock 或 Tactical window flags。
- `RiftScreenWindowController` 保留 `MINI / COMPACT / EXPANDED`、拖动、屏幕边界 clamp、关闭等窗口行为。
- `DraftHudWindowController` 负责 Verified/Preview 显示优先级、HUD/Dock window、Edit/Lock 和模块控制 wiring。
- `TacticalHudWindowController` 只负责 touch-through Tactical window 与 Verified/Preview 选择，不推导赛事事实。
- `OverlayWindowHost` 是 WindowManager 唯一平台操作入口，add/update/remove/bounds 失败必须发出 `[Laner:OVERLAY]` diagnostics，不允许静默吞异常。
- Verified Draft HUD 只在 canonical lifecycle 为 `DRAFT` 时激活，只读取 canonical `DraftChangedEvent`。没有 side-selection 事实时只称“左/右侧”，不把赛程顺序冒充蓝/红方；没有角色/对位证据时不推断。
- Verified Tactical HUD 只在 canonical lifecycle=`IN_GAME` 且存在近期标准 Tactical event 时激活；游戏时间 TTL 为 25s，另有 30s wall-clock freshness，防止 Provider 断流后旧卡片永久挂屏。
- Tactical HUD 不展示当前数据链不存在的 HP、技能冷却、召唤师技能 CD、位置等信息；不得为了复刻旧模拟 UI 伪造字段。
- MultiKill 只显示“采样窗口多杀”，不能冒充官方 Double/Triple/Quadra/Penta；TeamFight 只显示“团战窗口”，不能冒充官方团战分类。
- `DraftHudPreviewSession` / `TacticalHudPreviewSession` 都是 Android-only 本地视觉夹具，固定标记 `LOCAL PREVIEW · NOT FACT`，不进入 Core、Source Arbitration、Repository 或 Timeline。
- HUD Edit 模式允许 Draft 模块拖动、Scale、Alpha、Visibility、Reset；配置按横屏/竖屏独立持久化。
- Draft HUD Lock 后，全屏 HUD Window 加 `FLAG_NOT_TOUCHABLE` 真正触摸穿透；边缘控制 Dock 保持可操作。
- Tactical HUD 自身是全屏 touch-through window，不抢底层观赛操作。
- 系统悬浮窗权限、后台窗口优先级、Tactical 卡片视觉/触摸穿透仍必须在 Android 真机补证，状态为 `WAITING EXTERNAL TEST`。

## Local persistence
- LIVE State schema_version=1；
- LIVE Timeline schema_version=2；LNR-021 新事件字段/类型进入 v2；
- `JsonLiveTimelineRepository` 继续读取 v1，并在下一次写入时升级为 v2；不要求用户手动清缓存；
- canonical ID → SHA-256 稳定文件名；JSON 内复核完整 canonical ID；
- sibling temp + atomic replace；corrupt/unsupported schema 显式失败；
- Timeline 只保存标准 Snapshot/Event，不保存 raw Provider payload；
- Draft HUD Layout 只保存用户布局参数，不保存赛事事实；namespace 仍为 `laner_draft_hud_layout_v1`。

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

LNR-020：
- 原功能 PR #10 merge `967e112d6efcf8e6daa86f0b007cedc39b63b04c`；
- 合规整改 PR #12 merge `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`；
- `INC-LNR-020-001` 已 CLOSED；Android overlay 真机行为仍为 `WAITING EXTERNAL TEST`。

LNR-021：
- Failure A run `34709821178`：Architecture/Core PASS；Android `compileDebugKotlin` 因 `LiveMatchScreen.eventLabel()` 未穷举新增 sealed event 而 FAIL；生产事件层不是失败面；
- fix commit `403bba4e874ad37978179618e84dceaafb9f06f8` 补齐 `MultiKillWindowEvent / TeamFightWindowEvent` 展示分支；
- implementation baseline run `34710012697` on `b83a83c0c8908b8da1755d306958352fbfe389cf`：Architecture/Core/App Unit/Android compile/APK upload 全 PASS；artifact `10303071228`；
- wall-clock stale-card regression 后的最终 exact-head / PR / main Gate 以 LNR-021 开发记录为准。

## 故障定位
PRE：`UI → GlobalSchedule/PreMatchContext → Port → Adapter`。

Current LIVE：`LiveMatchScreen/RiftScreenOverlayService → LiveMatchContextService → LiveMatchStateService + LiveSnapshotService → LiveTimelineService`。

LIVE lifecycle：`LiveMatchContextService → LiveMatchStateService → RiotGlobalLiveStateSource`。

LIVE gameplay：`LiveMatchContextService → LiveSnapshotService → RiotGlobalLiveSnapshotSource → LiveTimelineService → JsonLiveTimelineRepository`。

LIVE event derivation：`LiveMatchContextService → GameTimeline → LiveEventDerivationService → LiveTimelineService.reconcileGeneratedEvents → JsonLiveTimelineRepository`。

RiftScreen Window：`RiftScreenOverlayService → RiftScreenWindowController → OverlayWindowHost → LNR-OVR-WINDOW-*`。

Draft HUD：`RiftScreenOverlayService → LiveMatchContextResult → DraftHudPresentationMapper → DraftHudWindowController → DraftHudOverlayView/ControlView`。本地预览只允许停留在 `DraftHudPreviewSession → DraftHudPresentation → View`。

Tactical HUD：`RiftScreenOverlayService → LiveMatchContextResult.timeline → TacticalHudPresentationMapper → TacticalHudWindowController → TacticalHudOverlayView`。本地预览只允许 `TacticalHudPreviewSession → TacticalHudPresentation → View`；不得进入 Timeline。

数据错误不得先在 UI 补丁；Provider parse 回 Adapter，canonical validation/仲裁/事件派生回 Application/Domain；平台窗口错误回 Overlay diagnostics。
