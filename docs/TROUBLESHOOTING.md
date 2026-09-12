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
M1 Feature Migration。LNR-019 正在收口 Global LIVE Snapshot / canonical Timeline / LIVE 可视化测试包；真实 BLG vs AL 在线结果仍必须由 Android 实机补证，Fixture/CI PASS 不等价于 online PASS。Cito 继续 DEFERRED。
