# LNR-014 — LIVE Source Adapters / Local Persistence / Composition Wiring

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `b3423f2f2fd66758a20059110e541543f209f082`
- Working branch: `feature/lnr-014-live-adapters-persistence`
- PR: `#6`
- Merge commit: `dad441aa64f2f7983c123315a40dc8edbc369a47`
- Final task status: `WAITING EXTERNAL TEST`

## 1. Request / Goal

将 LNR-013 已完成的 LIVE Core/Application 真相层接到 Android 基础设施：本地持久化、Composition Root、LIVE Application-truth 页面与 Timeline 读取，并为真实 LIVE Provider 保留可替换 Adapter 契约。

2026-09-12 用户追加约束：Cito 当前无法在线测试，先放置。因此 Cito 在线链被明确标为 `WAITING EXTERNAL TEST / DEFERRED`；它不得阻塞本地基础设施、UI 或后续 POST 迁移，也不得为了任务收口伪造在线验收。

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

### Completed in scope
- Android local `LiveMatchStateRepository`；
- Android local `LiveTimelineRepository`；
- `schema_version=1` / atomic write / corruption handling；
- Android Adapter/Persistence unit-test Gate；
- target-aware LIVE query contract；
- Composition Root wiring；
- LIVE target selection；
- LIVE authoritative lifecycle/status UI；
- no-provider explicit degradation；
- Timeline Application read query；
- LIVE 页面本地 Timeline 摘要/标准事件展示。

### Deferred / waiting external
- Cito credentialed REST online fetch；
- Cito WSS entitlement / socket behavior；
- 真实 Provider 驱动的经济/击杀/资源/Player state normalization；
- 真实赛事场间 → 新局 → 断线重连新架构实机证据。

### Explicit non-goals
- 不伪造 Cito 在线结果；
- 不假定 WSS entitlement；
- 不复制旧 RiftLab process-wide singleton bus / global target registry；
- 不在 UI 直接解析 Provider payload；
- 不在本任务抢跑 RiftScreen/HUD 视觉增强。

## 4. Design Decisions

### D1 — Cito 暂缓不阻塞主线

没有 Provider 时 `LiveMatchStateService(sources = emptyList())` 是合法显式降级态：返回 `UNAVAILABLE` 并保留 last-known state，不生成虚假比赛状态。

### D2 — Local persistence 是 Adapter

Domain/Application 不依赖 `File / JSONObject / Android`。本地 JSON schema 属 `:app` 实现细节。

### D3 — `schema_version=1`

LIVE State 与 Timeline 均带显式 schema version。未知 schema 必须失败，不静默按空数据继续。

### D4 — 原子写入

使用 sibling temp file 后原子替换；禁止先删除 last-known-good 再写新文件。

### D5 — 稳定文件名

canonical Match/Game ID 经 SHA-256 生成文件名，避免路径字符泄漏与平台路径问题；JSON 内仍保存完整 canonical ID 并在读取时复核。

### D6 — Timeline 显式 type schema

Snapshot / MatchStateChanged / Kill / Objective / GoldLead / Draft 等标准事件逐字段序列化，不依赖反射类名，不持久化 Provider raw payload。

### D7 — LIVE target hint 不泄漏 Provider ID

`LiveMatchSourceQuery` 携带 canonical MatchId、两队 TeamRef、计划开始时间。Adapter 可用这些事实发现自己的外部 match identity，但 Provider raw ID 不成为 Domain identity。

### D8 — 当前目标选择属于 presentation policy，不属于游戏状态证明

LIVE 页面先从标准赛程确定候选目标：`EVENT_LIVE` 优先；无 EVENT_LIVE 时仅从非 COMPLETED 中选最近比赛。这个选择只决定“查看哪场”，绝不能证明 `IN_GAME`。

### D9 — Timeline 读取必须经过 Application

UI 通过 `LiveTimelineService.load(gameId)` 读取本地 Timeline，不直接持有 `LiveTimelineRepository`。未来任何 Provider 或存储替换均不改变 UI 依赖方向。

### D10 — Cito REST 基线、WSS 可选增强

旧 RiftLab 行为表明 REST 可承担 bootstrap/reconcile/fallback。WSS entitlement 当前不可验证，因此不得标记为已支持。

## 5. Files Changed

### Added
- `app/src/main/java/com/laner/app/data/live/JsonLiveMatchStateRepository.kt`
- `app/src/main/java/com/laner/app/data/live/JsonLiveTimelineRepository.kt`
- `app/src/main/java/com/laner/app/ui/LiveMatchScreen.kt`
- `app/src/test/java/com/laner/app/data/live/JsonLiveMatchStateRepositoryTest.kt`
- `app/src/test/java/com/laner/app/data/live/JsonLiveTimelineRepositoryTest.kt`
- `app/src/test/java/com/laner/app/ui/LiveMatchScreenTest.kt`
- 本开发记录。

### Modified
- `.github/workflows/android-build.yml`
- `app/build.gradle.kts`
- `app/README.md`
- `app/src/main/java/com/laner/app/LanerAppGraph.kt`
- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveStatePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveMatchStateService.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveTimelineService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveTimelineServiceTest.kt`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/TROUBLESHOOTING.md`

## 6. Tests / Verification

### Persistence coverage
- Live State round-trip；
- successful publish 后无 temp 残留；
- corrupt JSON explicit failure；
- unsupported schema explicit failure；
- Timeline Snapshot/Event round-trip；
- 所有标准事件变体显式序列化/恢复。

### Presentation/Application coverage
- `EVENT_LIVE` 永远优先为 LIVE 页面候选；
- fallback 忽略 `COMPLETED`；
- completed-only 返回 null；
- Timeline read 通过 `LiveTimelineService` 暴露；
- no-provider 为合法 UNAVAILABLE 降级。

### Preserved failure evidence
- run `34692037250`: Architecture/Core PASS；Android unit tests FAIL；Android assemble skipped。
- 根因：Kotlin expression-body `@Test` 方法产生 JUnit4 不兼容的非-void method signature，引发 `InvalidTestClassError`。
- fix commit：`86ca106cf6372a4f23f4f83faaa997eef3f5bad5`。

### Successful evidence
- run `34692350405` @ `afa4bf7f...`：Architecture/Core/App unit tests/Android build PASS；
- run `34692588440` @ `88fa4dd5...`：Composition + LIVE Application-truth UI 全 PASS；
- run `34692688208` @ `b4fdb616...`：LIVE target selection 回归 PASS；
- code head `4d64749c07ce6f91bebad91de91f4fb70ed0bd04` / run `34692936906`：全 PASS；
- final branch head `838b6f7143d87d476a7874cd682be157f1f52708` / run `34693150443`：全 PASS；
- PR #6 run `34693236484`：Architecture / Domain+Application / Android Adapter Unit Tests / Android Debug Compile 全 PASS；
- merged to `main` as `dad441aa64f2f7983c123315a40dc8edbc369a47`。

## 7. Security / Data / Compatibility

- 未提交 Cito API key；
- 未新增 secret 到 Git/logs/fixtures；
- corrupt/unsupported local data 不静默吞；
- v1 为首个 LIVE persistence schema，无历史 destructive migration；
- Cito absence 只降级 LIVE，不破坏 PRE/POST 或本地 archive；
- Provider raw payload 不进入持久化标准事件。

## 8. Known Issues / Follow-ups

- Cito online verification：`WAITING EXTERNAL TEST / DEFERRED`；
- WSS realtime fabric：待 capability/entitlement 外部验证；
- 实时经济/击杀/塔/龙/男爵/Player state Provider normalization：后续 LIVE Provider 补证项；
- real-source continuous Timeline capture：待外部数据源；
- RiftScreen/HUD：后续 Android platform round；
- Cito 补证不阻塞 LNR-015 POST 迁移。

## 9. Rollback

- Revert PR #6 / merge commit 可移除本轮 persistence/composition/LIVE UI；
- persisted LIVE v1 files 隔离在 app files `live/` 路径，不是远端事实源；
- 本任务没有破坏性迁移既有用户数据。

## 10. Compliance

- Core platform/network boundary: `PASS`；
- UI → Application → Port direction: `PASS`；
- schema/versioned persistence: `PASS`；
- atomic write policy: `PASS`；
- corruption explicit failure: `PASS`；
- secret handling: `PASS`；
- Android Adapter tests in CI: `PASS`；
- failed test history preserved: `PASS`；
- exact-head + PR Gate: `PASS`；
- Cito unsupported online claim avoided: `PASS`；
- automated local scope: `PASS`；
- external Cito/real-source scope: `WAITING EXTERNAL TEST / DEFERRED`；
- final task state: `WAITING EXTERNAL TEST`。
