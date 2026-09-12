# LNR-014 — LIVE Source Adapters / Local Persistence / Composition Wiring

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `b3423f2f2fd66758a20059110e541543f209f082`
- Working branch: `feature/lnr-014-live-adapters-persistence`
- Current status: `IN PROGRESS`

## 1. Request / Goal

将 LNR-013 已完成的 LIVE Core/Application 真相层接到 Android 基础设施：本地持久化、Composition Root、真实 Provider Adapter 与最终 LIVE UI。

2026-09-12 用户追加约束：Cito 当前无法在线测试，先放置。该限制已转化为工程状态：`WAITING EXTERNAL TEST / DEFERRED`，不得为了任务收口伪造在线验收。

## 2. Constitution Preflight

结果：`PASS`。

已读取/遵循：
- `docs/ENGINEERING_CONSTITUTION.md`；
- `docs/DEVELOPMENT_PLAN.md`；
- `docs/IMPLEMENTATION_STATUS.md`；
- `docs/FEATURE_BASELINE.md`；
- LNR-013 Core/Application contracts；
- 旧 RiftLab Cito REST/WSS 实现，仅作为行为/接口参考。

## 3. Scope

### In scope
- Android local `LiveMatchStateRepository`；
- Android local `LiveTimelineRepository`；
- schema version / atomic write / corruption handling；
- Android Adapter/Persistence unit-test Gate；
- target-aware LIVE query contract；
- Composition Root wiring；
- LIVE degraded/no-provider path；
- 后续 LIVE UI 只消费 Application truth。

### Deferred / Waiting external
- Cito credentialed REST online fetch；
- Cito WSS entitlement / socket behavior；
- 真实赛事场间 → 新局 → 断线重连；
- Cito 实机证据。

### Explicit non-goals
- 不伪造 Cito 在线结果；
- 不假定 WSS entitlement；
- 不复制旧 RiftLab process-wide singleton bus / global target registry；
- 不在 UI 直接解析 Provider payload；
- 不在本任务抢跑 RiftScreen/HUD 视觉增强。

## 4. Design Decisions

### D1 — Cito 暂缓不阻塞本地基础设施

没有 Provider 时 `LiveMatchStateService(sources = emptyList())` 必须是合法显式降级态，返回 `UNAVAILABLE/last-known`，不能生成虚假比赛状态。

### D2 — Local persistence 是 Adapter

Domain/Application 不依赖 `File / JSONObject / Android`。本地 JSON schema 属 `:app` 实现细节。

### D3 — `schema_version=1`

LIVE State 与 Timeline 均带显式 schema version。未知 schema 必须失败，不静默按空数据继续。

### D4 — 原子写入

使用 sibling temp file 后原子替换；禁止先删除 last-known-good 再写新文件。

### D5 — 稳定文件名

canonical Match/Game ID 经 SHA-256 生成文件名，避免路径字符泄漏与平台路径问题；JSON 内仍保存完整 canonical ID 并在读取时复核。

### D6 — Timeline 显式 type schema

Snapshot / MatchStateChanged / Kill / Objective / GoldLead / Draft 等标准事件逐字段序列化，不依赖反射类名，不持久化 Provider raw payload/free text。

### D7 — LIVE target hint 不泄漏 Provider ID

`LiveMatchSourceQuery` 可携带 canonical MatchId、两队 TeamRef、计划开始时间。Adapter 可用这些事实做 Provider 匹配，但 Provider raw match ID 不成为 Domain identity。

### D8 — Cito REST 基线、WSS 可选增强

旧 RiftLab 已证明 REST 可承担 bootstrap/reconcile/fallback。WSS entitlement 当前不可验证，所以不得标记为已支持。

## 5. Files Changed So Far

### Added
- `app/src/main/java/com/laner/app/data/live/JsonLiveMatchStateRepository.kt`
- `app/src/main/java/com/laner/app/data/live/JsonLiveTimelineRepository.kt`
- `app/src/test/java/com/laner/app/data/live/JsonLiveMatchStateRepositoryTest.kt`
- `app/src/test/java/com/laner/app/data/live/JsonLiveTimelineRepositoryTest.kt`
- 本开发记录。

### Modified
- `.github/workflows/android-build.yml`
- `app/build.gradle.kts`
- `app/src/main/java/com/laner/app/LanerAppGraph.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveStatePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveMatchStateService.kt`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`

## 6. Tests / Verification

### Added coverage
- Live State round-trip；
- no temp file after successful atomic publish；
- corrupt JSON explicit failure；
- unsupported schema explicit failure；
- Timeline snapshot/event round-trip；
- standard event variants explicit serialization；
- Android Adapter/Persistence tests now part of CI via `:app:testDebugUnitTest`。

### Preserved failure evidence
- run `34692037250`: Architecture/Core PASS；Android unit tests FAIL；Android assemble skipped。
- root cause: Kotlin expression-body `@Test` methods produced JUnit4-incompatible method signatures, causing `InvalidTestClassError` during test class initialization。
- fix commit: `86ca106cf6372a4f23f4f83faaa997eef3f5bad5`，改为 standard block-body test methods。
- run `34692350405` at `afa4bf7fb7621bdc06b9043639dbdb613bb789a6`: Architecture/Core/App unit tests/Android build PASS。
- later Composition/docs commits require final exact-head rerun before closeout。

## 7. Security / Data / Compatibility

- 本轮不提交 Cito API key；
- 未新增 secret 到 Git/logs/fixtures；
- corrupt/unsupported local data 不静默吞；
- no schema migration yet because v1 is first persisted LIVE schema；
- Cito absence must degrade, not crash PRE/POST or local LIVE archive。

## 8. Known Issues / Follow-ups

- Cito online verification unavailable now → `WAITING EXTERNAL TEST / DEFERRED`；
- LIVE UI target selection/Application truth consumption still IN PROGRESS；
- real snapshot/event Provider normalization still pending；
- WSS optional realtime fabric pending external capability verification；
- final branch/PR Gate pending。

## 9. Rollback

- Revert LNR-014 branch/PR removes new persistence and wiring；
- persisted LIVE v1 files are isolated under app files `live/` paths and are not shared remote state；
- no destructive migration of existing user data in this task so far。

## 10. Compliance

- Core platform/network boundary: `PASS`；
- schema/versioned persistence: `PASS`；
- atomic write policy: `PASS`；
- secret handling: `PASS`；
- failed test history preserved: `PASS`；
- Cito unsupported online claim avoided: `PASS`；
- final task compliance: `IN PROGRESS`。
