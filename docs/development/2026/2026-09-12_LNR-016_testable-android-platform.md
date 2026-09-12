# LNR-016 — Testable Android Platform / Riot LIVE Baseline / APK Delivery

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@939567a011a7aef27a6c17a2134a297c1151457b`
- Working branch: `feature/lnr-016-testable-android-platform`
- Status: `TESTING`

## Request / Goal
在 BLG vs AL 再次测试前优先交付可安装、可现场配置 Riot Key、可验证 Global Riot LIVE 生命周期的数据测试包；不等待 Cito。

## Constitution Preflight
`PASS`。已读取工程宪法、main、计划/状态、LIVE Application contract、Riot Global POST/LiveStats Adapter 与 Android Composition/UI/CI。

## Acceptance
- CI 产出可安装 debug APK artifact；
- 不把 Riot Key 编译进公共测试包也能现场配置；
- 临时 Key 只在进程内存，不落盘/日志/Git/provenance；
- LIVE Application 不再 `sources = emptyList()`；
- Global Riot LIVE 只用 canonical teams/time 定位目标，provider ids 不成为 Domain ID；
- BLG/AL、LCK 等共用同一 discovery/parser；
- 无真实 frame/状态证据保持未知/降级，不伪造 IN_GAME；
- UI 直接显示稳定来源错误码，便于无 adb 实机排障；
- Architecture/Core/App unit/Android compile 全 PASS 才交包。

## Design
`LiveMatchSourceQuery → Riot global schedule unique event → ProviderMatchIdentityRepository → getEventDetails → current provider game → LiveStats real frame probe → ProviderLiveObservation → LiveMatchStateService → UI`。

canonical GameId 仍由 `GameIdentity.canonical(matchId, gameNumber)` 生成。

## Implemented
- `RiotGlobalLiveStateSource`：global target discovery + EventDetails + LiveStats frame probe；
- stable LIVE errors `LNR-SRC-LIVE-002~005`；
- AppGraph wires Riot Global LIVE as first real LIVE source；
- BLG/AL + T1/GEN fixtures exercise same discovery parser；
- `RiotCredentialPanel` + MainActivity memory-only temporary key；
- changing key rebuilds `LanerAppGraph` immediately；
- LIVE UI shows `SOURCE DIAGNOSTICS` with error code/message；
- CI uploads `app-debug.apk` artifact for successful runs；
- test version `2.0.0-dev.3`, versionCode 3。

## Security correction
Initial implementation briefly introduced an app-private plaintext credential file. Constitution review rejected it because ordinary private storage is not an approved Secret Store. The file implementation and tests were removed before delivery. Runtime test key is now process-memory only and disappears when the process exits.

## Failure History
### run `34697683655` — real Android compile failure
- Architecture: PASS
- Domain/Application: PASS
- Android Adapter/compile: FAIL
- APK upload: skipped

Root cause: `RiotCredentialPanel.kt` had malformed `when` syntax caused by a trailing comma after the `else` expression; Compose `Text` therefore received an `Any`/invalid expression.

Fix: commit `2cddeb872d7854829b54750db31f8739e37f0d2a` repairs the `when` expression without changing credential semantics.

### run `34697846793` — first installable green build
Head `2cddeb872d7854829b54750db31f8739e37f0d2a`.
Architecture / Domain+Application / Android Adapter unit tests / Android debug build: PASS.
Artifact:
- id `10299088400`
- name `laner-debug-2cddeb872d7854829b54750db31f8739e37f0d2a`
- artifact digest `sha256:86e833a7d7a27c165a37682e0763464b25ff2be9a6c5e9fac577a5a1af48a650`
- expires 2026-09-15

This is a valid fallback test APK, but a later head adds in-app source diagnostics and must pass its own Gate before becoming the preferred build.

### final diagnostics head
Commit `b3549098852061c962e33bc2f1b2650828f4e07a` adds visible `LNR-SRC-LIVE-*` diagnostics. Exact-head run `34698154124`: pending.

## Online/Device evidence
BLG vs AL online fetch and Android real-device result remain `WAITING EXTERNAL TEST`. Fixture/CI PASS is not reported as online success.

## Rollback
Feature branch is isolated. Runtime key has no persisted migration. Before merge, delete/revert branch; after merge, revert task merge commit.

## Compliance
Code/tests/security correction/failure evidence are archived. Final exact-head Gate + preferred APK artifact + PR Gate + merge/status sync remain before task closeout.
