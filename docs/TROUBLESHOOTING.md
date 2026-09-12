# Laner Troubleshooting

## 使用方式
历史故障必须形成稳定 ID，并至少记录：现象、影响范围、首查模块/接口、日志或错误码、复现、根因、修复任务、永久回归测试。

业务错误码格式：`LNR-<MODULE>-<STAGE>-<NNN>`。

## PRE_MATCH

### `LNR-SRC-PRE-001~004` — Riot Global Schedule
- `001` credential 未配置；只允许外部注入或测试期内存临时输入。
- `002` global schedule 核心请求失败 → UNAVAILABLE，不造数据。
- `003` catalogue 子接口失败 → DEGRADED，保留已取得真实 schedule。
- `004` older/newer pagination 失败 → DEGRADED，保留成功页面。

### `LNR-SRC-PRE-006~009` — Roster / Starting / Staff
Roster Pool 不得替代首发；Starting evidence 必须校验日期、对阵、赛事、五位置；同 Authority 冲突显式保留；Staff mirror 不冒充直接 Riot API。

### `LNR-UI-PRE-001` — PRE Context `produceState` 编译失败
首次发现：LNR-011 / run `34688581238`。四 named keys 与当前 Compose overload 不兼容；修成三个稳定 keys。run `34688715420` PASS。

## LIVE_MATCH

### Legacy 场间识别行为
2026-09-12 用户实机确认旧 RiftLab 可区分场间/未开局与真实新局。迁移后若提前 IN_GAME、上一局卡住或新局不切换，均视为回归。

### `LNR-LIVE-CORE-001` — 新鲜 IN_GAME 被旧 POST_GAME 推进
首次发现：LNR-013 / run `34691364933`。根因是 stale 判定错误依赖 lifecycle rank。修复：同一 Game 中，任何早于当前 freshness 的 lifecycle-changing observation 均为 stale。永久回归：`delayedPostGameAfterNewerInGameHeartbeatIsIgnored`。run `34691458209` PASS。

### `LNR-LIVE-INFRA-001` — JUnit4 拒绝 expression-body @Test
首次发现：LNR-014 / run `34692037250`。Kotlin expression-body 产生非-void JVM test signature。修复 commit `86ca106c...`，永久保留 `:app:testDebugUnitTest` Gate。

### `LNR-UI-LIVE-002` — Runtime Riot Key 面板导致 Android 编译失败
首次发现：LNR-016 / run `34697683655`。

**现象**：Architecture 与 Domain/Application PASS，但 `:app:compileDebugKotlin` 在 `RiotCredentialPanel.kt` 失败，APK upload 被 Gate 阻止。

**根因**：Compose 文案 `when` 的 `else` expression 后误留尾逗号，Kotlin 将表达式解析为非法/`Any`，随后 `Text(text=...)` 类型检查失败。

**修复**：commit `2cddeb872d7854829b54750db31f8739e37f0d2a` 移除错误语法，不改变 runtime credential 安全语义。

**永久证据**：run `34697846793` Architecture / Core / App unit / Android build PASS，并成功生成 APK artifact `10299088400`。

### `LNR-LIVE-TEST-002` — Live Snapshot Core 测试错误引入未配置测试依赖
首次发现：LNR-019 的初始误编号 branch / run `34699837518`。

**现象**：Architecture Gate PASS；`:core:application:compileTestKotlin` FAIL；Android Adapter/build/APK 被 Gate 正确跳过。

**根因**：新建 `LiveSnapshotServiceTest` 错误使用 `kotlinx.coroutines.runBlocking` 与 `org.junit.*`，而 `:core:application` 的既有测试契约使用 `kotlin.test` + 本地 `Continuation` suspend harness。生产 `LiveSnapshotService` 未进入失败路径。

**修复**：commit `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5` 将测试改为既有 Core harness，不新增无必要依赖、不改生产逻辑。

**永久回归**：`LiveSnapshotServiceTest` 覆盖 valid canonical ingest、raw/noncanonical GameId rejection、wrong-team rejection。

**回归证据**：run `34699942180` Architecture / Domain+Application / Android Adapter unit / Android build / APK upload 全 PASS。

**排障路径**：`Core test compile fail → 检查本模块既有 test framework → 禁止为单测随意增加依赖 → :core:application:test`。

### `LNR-UI-LIVE-003` — LNR-020 Foundation 测试仍绑定已删除 UI-private 选择逻辑
首次发现：LNR-020 / run `34704014273`。

**现象**：Architecture/Core PASS，App unit compile FAIL；旧 `LiveMatchScreenTest` 仍引用已经删除的 UI-private `selectLiveTarget`。

**根因**：比赛目标选择已经迁入 Application `LiveTargetSelector`，但历史 Presentation test 没有同步迁移到新的边界。

**修复**：commit `8b17f94ed75ba417eed017bd4c4d45ba63b5e9a9`，测试改为验证 Application target policy；后续 push run `34704124799` / PR run `34704127811` PASS。

**永久回归**：`LiveMatchScreenTest` 继续验证 EVENT_LIVE 优先、completed 不作为 fallback、completed-only 不产生 LIVE target；LNR-020 合规整改进一步由 `LiveMatchContextServiceTest` 锁定完整 Application context orchestration，Presentation 不再自己组合四个 Service。

**排障路径**：`UI test compile fail → 检查业务规则是否已迁入 Application → 测试公开 Use Case/Query → 禁止为测试恢复 UI-private 业务规则`。

### `LNR-LIVE-TEST-003` — Draft HUD 新测试断言自身错误
首次发现：LNR-020 / PR run `34705468018`。

**现象**：Architecture/Core 与 production `compileDebugKotlin` PASS；App tests 35 个中 1 个失败。

**根因**：测试错误要求最后一个正常 LEFT UNDO 文本不得出现 LEFT；真正要验证的是 unknown team 的 `Garen` 不能污染左右侧。失败面在新测试断言，不在生产 Draft mapper。

**修复**：commit `871e1a0ad257c465dc51720df46fb66cceeae7cc`，改为检查 `Garen` 不存在于任一侧 picks；run `34705512479` PASS。

**永久回归**：`DraftHudPresentationMapperTest` 持续覆盖 unknown team 隔离、PICK/LOCK/BAN、UNDO 与非 DRAFT 不激活。

**排障路径**：`单测失败且 production compile PASS → 先核对断言与需求语义 → 不为迎合错误断言篡改生产逻辑`。

### `LNR-UI-LIVE-004` — 新增 Tactical sealed event 后 LIVE Timeline UI 未同步穷举
首次发现：LNR-021 / run `34709821178`。

**现象**：Architecture boundary PASS；Domain/Application tests PASS；Android Adapter Unit 阶段在 production `:app:compileDebugKotlin` 失败；Android debug compile 与 APK upload 被 Gate 正确阻断。

**编译错误**：`LiveMatchScreen.kt:eventLabel()` 的 sealed `when` 未新增 `MultiKillWindowEvent` / `TeamFightWindowEvent` 分支。

**根因**：LNR-021 扩展了 Domain `MatchEvent` sealed hierarchy，但已有 Presentation timeline formatter 没有在同一切片同步穷举。事件模型/派生测试已通过，失败面属于 Android Presentation 编译完整性。

**修复**：commit `403bba4e874ad37978179618e84dceaafb9f06f8` 补齐两个标准事件的展示分支，不通过 `else` 隐藏未来新增事件遗漏。

**永久回归**：保持 `MatchEvent` formatter 的 exhaustive `when`；CI `:app:testDebugUnitTest` / `:app:compileDebugKotlin` 必须在每次 Domain sealed event 扩展时执行。后续 implementation baseline run `34710012697` 已 Architecture/Core/App Unit/Android compile/APK upload 全 PASS。

**排障路径**：`Core PASS + Android compile sealed-when fail → 检查 Domain sealed hierarchy 新增类型 → 同步所有 Presentation exhaustive mapper → 不用 catch-all else 掩盖遗漏`。

### LNR-021 Tactical stale-card 防护
Tactical HUD 同时使用：
- 游戏时间 TTL：标准事件只在 canonical current game second 的 25s 内可候选；
- wall-clock freshness：Verified presentation 自 event provenance `observedAtEpochMillis` 起最多 30s 可显示。

原因：Provider 断流时 game clock / latest snapshot 可能冻结，如果只有游戏时间 TTL，旧 Tactical card 会永久停留。`TacticalHudPresentation.isDisplayableAt()` 是永久回归入口；Preview 明确不使用 Provider freshness，因为它固定是 `LOCAL PREVIEW · NOT FACT`。

### `LNR-APP-LIVE-003` — Current LIVE Context Query 意外失败
LNR-020 合规整改新增。`LiveMatchContextService` 是当前 LIVE 上下文唯一 Application 编排入口；若 Schedule/Target/Lifecycle/Snapshot/Timeline 组合链出现非业务降级类异常，返回 typed `Failed` 并通过 `[Laner:LIVE]` 记录 `LNR-APP-LIVE-003`。UI/Overlay 不得自行复制相同编排作为 fallback。

永久回归：`LiveMatchContextServiceTest.unexpectedApplicationFailureIsDiagnosedWithStableCode`。

### `LNR-OVR-WINDOW-001~004` — Android Overlay WindowManager
LNR-020 合规整改新增。所有 RiftScreen / Draft HUD / Dock / Tactical HUD WindowManager 操作统一经过 `OverlayWindowHost`：
- `001` add 失败；
- `002` update 失败；
- `003` remove 失败；
- `004` display bounds 获取失败，使用 displayMetrics 安全退化边界。

日志统一 `[Laner:OVERLAY]`，上下文至少包含 `window / operation / error_type`；禁止无 `onFailure` 的静默 `runCatching`。永久回归 `OverlayWindowOperationTest` 锁定错误码唯一性与 retryability 分类。Android ROM/权限/Window token 的真实行为仍必须实机验证。

### `LNR-OVR-REFRESH-001` — Overlay Presentation Mapping 失败
`LiveMatchContextService` 成功返回后，如果 Rift/Draft/Tactical Presentation Mapper 自身抛出异常，Foreground Service 不得静默停止轮询。记录 `[Laner:OVERLAY] / LNR-OVR-REFRESH-001`，并退化为“读取失败 + 错误码”的等待态；不得制造赛事事实。

### Riot Global LIVE 设备诊断码
- `LNR-SRC-LIVE-002`：Riot API Key 未配置（Lifecycle）。
- `LNR-SRC-LIVE-003`：目标缺少双方队伍或 scheduled start。
- `LNR-SRC-LIVE-004`：无法从 Riot global schedule 唯一定位 event / identity 缺失。
- `LNR-SRC-LIVE-005`：EventDetails / LiveStats 等 Riot LIVE lifecycle 请求失败。
- `LNR-SRC-LIVE-006`：Riot API Key 未配置（Gameplay Snapshot）。
- `LNR-SRC-LIVE-007`：Riot LIVE Snapshot EventDetails/LiveStats 请求或解析失败。
- `LNR-APP-LIVE-002`：Provider snapshot 的 canonical Match/Game/team identity 校验失败；不得写入 Timeline。

设备错误码只用于定位，UI 不得绕过 Application 修正赛事事实。

## POST_MATCH

### `LNR-POST-TEST-001` — 非法测试 ErrorCode 导致 POST Core 测试提前失败
首次发现：LNR-015 / run `34693494001`。fixture 使用非法 `LNR-SRC-POST-TEST`；修为合法 `LNR-SRC-POST-999`，不放宽生产规则。run `34693630753` PASS。

### `LNR-POST-TIMELINE-001` — POST 历史真帧被 Domain source-class 不变量拒绝
首次发现：LNR-015 / run `34695777894`。Application 已允许 POST historical facts，但 Domain Timeline 仍只允许 LIVE。修复后 Domain 允许 LIVE/POST factual sources，继续拒绝 PRE/AI；run `34695924994` PASS。

## 当前阶段
M1 Feature Migration。LNR-020 已完成并通过独立合规整改；Block 1 冻结。LNR-021 正在完成 Tactical HUD + live event layer 的最终 Gate/PR 收口。自动实现覆盖 LIVE-014 / LIVE-015 / LIVE-030，但真实 Riot online 与 Android overlay 行为继续保持 `WAITING EXTERNAL TEST`。Cito 继续 DEFERRED。
