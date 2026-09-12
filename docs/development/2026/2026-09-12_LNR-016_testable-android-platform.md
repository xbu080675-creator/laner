# LNR-016 — Testable Android Platform / Riot LIVE Baseline / APK Delivery

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@939567a011a7aef27a6c17a2134a297c1151457b`
- Working branch: `feature/lnr-016-testable-android-platform`
- Status: `IN PROGRESS`

## Request / Goal
在今晚 BLG vs AL 再次测试前，优先交付一个可安装、可现场配置 Riot Key、可验证 Global Riot LIVE 生命周期的数据测试包；同时继续推进 Android 平台轮次，不等待 Cito。

## Constitution Preflight
`PASS`。已读取最新工程宪法、main、开发计划、实现状态、LIVE Application contract、现有 Riot Global POST/LiveStats Adapter、Android Composition/UI/CI。

## Acceptance
- Android debug APK 必须由 CI 作为 artifact 产出；
- APK 不需要把 Riot Key 编译进包才能现场测试；
- 临时 Riot Key 不落盘、不写日志/Git/provenance，进程退出即清除；
- LIVE Application 不再 `sources = emptyList()`；
- Global Riot LIVE Adapter 只用 canonical teams/time 做 target discovery，provider ids 不成为 Domain ID；
- BLG/AL 与其他赛区共用同一 discovery/parser；
- 没有真实 frame/状态证据时保持降级/未知，不伪造 IN_GAME；
- Architecture/Core/App unit/Android compile Gate 全 PASS 后才交 APK。

## Scope
- `RiotGlobalLiveStateSource`；
- runtime in-memory credential override；
- Composition wiring；
- debug APK Actions artifact；
- version `2.0.0-dev.3`；
- adapter regression tests；
- LIVE test diagnostics wording/documentation。

## Non-goals for this emergency test slice
- Cito online/WSS；
- full live gold/kills snapshot ingestion into canonical Timeline（后续同 LNR-016 深化）；
- HUD/RiftScreen full feature set；
- Media3/WebView；
- AI/OCR；
- pretending online BLG/AL PASS before device evidence exists。

## Design
Riot LIVE Adapter flow:
`LiveMatchSourceQuery(canonical match + teams + scheduled time) → Riot global schedule unique event → provider identity mapping → getEventDetails → current game → LiveStats real frame probe → ProviderLiveObservation → LiveMatchStateService → UI`。

Runtime credential is memory-only. Initial file persistence implementation was immediately rejected during Constitution review because ordinary app-private storage is not a formal Secret Store; those files/tests were removed before delivery. This correction is part of task history.

## Current changes
- added `RiotGlobalLiveStateSource.kt`；
- AppGraph wires Riot LIVE source；
- added LPL+LCK shared discovery fixture tests；
- added `RiotCredentialPanel` and in-memory runtime override；
- CI uploads `app-debug.apk` artifact；
- bumped debug test build to `2.0.0-dev.3` / versionCode 3。

## Security
- runtime Key exists only in Compose process memory；
- no persistence/log/provenance；
- build-time key remains optional external environment/Gradle-property injection；
- CI public artifact contains no credential by default。

## Tests
Latest exact-head CI: pending. Previous commits triggered intermediate runs and are not delivery evidence.

## Rollback
All changes are isolated on `feature/lnr-016-testable-android-platform`; before merge, delete branch to rollback. Runtime credential has no persisted migration.

## Compliance
Current: `IN PROGRESS`. Final exact-head CI + APK artifact + docs/status sync required before test delivery.
