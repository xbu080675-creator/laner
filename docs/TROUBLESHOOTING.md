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

### `LNR-SRC-PRE-010~013` — Starting Roster Assist / OCR
- `010`：已发现匹配官方公告，但当前没有可用 OCR Adapter；
- `011`：公告无可 OCR 图片，或 OCR 结果不完整/有歧义；
- `012`：OCR 已给出完整五位置候选，但仍是 `DERIVED / UNVERIFIED`，等待 normalized 日期/对阵证据；
- `013`：图片下载或 OCR engine 失败。

这些错误码只描述辅助链状态，不得由 UI 据此自行确认首发。

### `LNR-UI-PRE-001` — PRE Context `produceState` 编译失败
首次发现：LNR-011 / run `34688581238`。四 named keys 与当前 Compose overload 不兼容；修成三个稳定 keys。run `34688715420` PASS。

### `LNR-ROSTER-OCR-001` — LNR-017 首次 Android Gate 编译失败
首次发现：run `34700136865`。

**现象**：Architecture 与 Domain/Application PASS；`:app` compile/test phase FAIL，Android build/APK upload 被阻止。

**根因 A**：bundled ML Kit Latin recognizer 的 `TextRecognizerOptions` 错误引用上一级 package；正确路径为 `com.google.mlkit.vision.text.latin.TextRecognizerOptions`。

**根因 B**：PRE Compose 面板直接对跨模块 public nullable `inspection.error` 做 smart cast；Kotlin 不保证该属性稳定，编译拒绝。

**修复**：
- `ad23d8cf493c773b5ff3d6dd6b07b3333a380171` 修正 ML Kit Latin package；
- `a711dce3947b80405af741d69a8c342b191c121c` 先复制 `inspection.error` 到 local value 再分支。

**永久验证**：后续 run `34700236839` 已越过这两个生产编译错误；最终 code head run `34700457400` Architecture / Core / App unit / Android build / APK upload 全 PASS。

### `LNR-ROSTER-OCR-002` — 新 Android tests 使用未声明 `kotlin.test`
首次发现：run `34700236839`。

**现象**：Architecture/Core PASS，生产 `:app:compileDebugKotlin` 已成功；`:app:compileDebugUnitTestKotlin` 报 `kotlin.test` / `Test` / assertions unresolved。

**根因**：`:app` 明确使用 JUnit4 `junit:junit:4.13.2`，两个新测试误从 `kotlin.test` 导入 API。

**修复**：
- `5d763c34301858293ceef6b5257077dc9b866ce3`；
- `3879249053832c606f30a6122c31269db9327812`；
统一改用 `org.junit.Test` 与 `org.junit.Assert`，不增加多余测试依赖。

**永久验证**：run `34700457400` App unit PASS，随后 Android build/APK upload PASS。

**排障路径**：`Roster assist Gate fail → 先看 Architecture/Core → :app compile → :app unit imports → ML Kit dependency/package → Compose cross-module nullable → APK upload`。

## LIVE_MATCH

### Legacy 场间识别行为
2026-09-12 用户实机确认旧 RiftLab 可区分场间/未开局与真实新局。迁移后若提前 IN_GAME、上一局卡住或新局不切换，均视为回归。

### `LNR-LIVE-CORE-001` — 新鲜 IN_GAME 被旧 POST_GAME 推进
首次发现：LNR-013 / run `34691364933`。根因是 stale 判定错误依赖 lifecycle rank。修复：同一 Game 中，任何早于当前 freshness 的 lifecycle-changing observation 均为 stale。永久回归：`delayedPostGameAfterNewerInGameHeartbeatIsIgnored`。run `34691458209` PASS。

### `LNR-LIVE-INFRA-001` — JUnit4 拒绝 expression-body @Test
首次发现：LNR-014 / run `34692037250`。Kotlin expression-body 产生非-void JVM test signature。修复 commit `86ca106c...`，永久保留 `:app:testDebugUnitTest` Gate。

### `LNR-UI-LIVE-002` — Runtime Riot Key 面板导致 Android 编译失败
首次发现：LNR-016 / run `34697683655`。Architecture/Core PASS，但 `RiotCredentialPanel.kt` 的 Compose `when` 尾逗号造成非法表达式；fix `2cddeb872d7854829b54750db31f8739e37f0d2a`。run `34697846793` PASS 并生成 APK。

### Riot Global LIVE 设备诊断码
- `LNR-SRC-LIVE-002`：Riot API Key 未配置。
- `LNR-SRC-LIVE-003`：目标缺少双方队伍或 scheduled start。
- `LNR-SRC-LIVE-004`：无法从 Riot global schedule 唯一定位 event / identity 缺失。
- `LNR-SRC-LIVE-005`：EventDetails / LiveStats 等 Riot LIVE 请求失败。

## POST_MATCH

### `LNR-POST-TEST-001` — 非法测试 ErrorCode 导致 POST Core 测试提前失败
首次发现：LNR-015 / run `34693494001`。fixture 使用非法 `LNR-SRC-POST-TEST`；修为合法 `LNR-SRC-POST-999`，不放宽生产规则。run `34693630753` PASS。

### `LNR-POST-TIMELINE-001` — POST 历史真帧被 Domain source-class 不变量拒绝
首次发现：LNR-015 / run `34695777894`。Application 已允许 POST historical facts，但 Domain Timeline 仍只允许 LIVE。修复后 Domain 允许 LIVE/POST factual sources，继续拒绝 PRE/AI；run `34695924994` PASS。

## 当前阶段
M1 Feature Migration。LNR-017 正在收口今晚可实机验证的官方首发图片发现/OCR/诊断链；真实官方发布与设备 OCR 仍是 `WAITING EXTERNAL TEST`。Fixture/CI PASS 不冒充真实首发抓取 PASS。
