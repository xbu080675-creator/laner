# LNR-013 — LIVE Match State / Provider Arbitration / Unified Event / Timeline

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `f6e00c9a3b960fe8370f20da7d5a10675c66c69b`
- Working branch: `feature/lnr-013-live-state-timeline`
- Task status: `DONE` as Core/Application foundation
- Product migration status: `IN PROGRESS` until LNR-014 wires real Provider/persistence/UI

## 1. Request / Goal

建立 Laner 的 LIVE Core/Application 真相层，保留并加强旧 RiftLab 已经通过实机验证的“场间未开局 vs 新局真实开局”边界。

本任务目标：
- 建立权威 LIVE Match State；
- 保证 `赛事开始 != 游戏进入`；
- Provider 只提供观察，权威 lifecycle 由 Application 仲裁；
- 统一 lifecycle / kill / objective / gold / draft 等标准事件；
- duplicate / out-of-order / reconnect 幂等；
- 建立 provider-neutral Timeline append/replay contract；
- 本轮只做 `:core:domain` + `:core:application`，不抢跑真实 LIVE Adapter / RiftScreen / HUD。

## 2. Constitution Preflight

结果：`PASS`。

读取并遵循：
- `docs/ENGINEERING_CONSTITUTION.md`；
- `docs/ARCHITECTURE_FREEZE.md`；
- `docs/FEATURE_BASELINE.md`；
- `docs/audits/2026-09-12_legacy_live_intermission_verification.md`；
- 旧 RiftLab `MatchSessionStore.kt`；
- 旧 RiftLab `MatchTimelineStore.kt`；
- 当前 Laner `MatchLifecycle.kt` / `MatchEvent.kt` / `MatchModels.kt` / Source Model。

旧实现仅作行为证据，不复制其 Global Store / Android-bound persistence 结构。

## 3. Scope

### In scope
- pure Kotlin `LiveMatchState` / `LiveStateSignal` / Evidence / TransitionResult；
- `LiveMatchStateReducer`；
- `LiveStateSourcePort` / `LiveMatchStateRepository`；
- `LiveMatchStateService` Provider Arbitration；
- standardized lifecycle event emission；
- typed `DraftActionType`；
- provider-neutral `GameTimeline / TimelineSnapshotPoint`；
- factual event semantic identity；
- `LiveTimelineRepository` / `LiveTimelineService`；
- duplicate / out-of-order / reconnect / same-second snapshot arbitration tests；
- Core module README、状态、计划、Troubleshooting、Changelog 同步。

### Explicit non-goals
- 不接 Riot/LPL/Cito 真实 LIVE Adapter；
- 不实现 Android LIVE/Timeline 文件 Repository；
- 不接 RiftScreen / Tactical HUD / Overlay；
- 不迁移实时经济、击杀、资源 Provider payload parsing；
- 不在 UI 中自行推断 lifecycle；
- 不复制旧 RiftLab Global Store；
- 不宣称 LIVE 产品链已经完成。

## 4. Design Decisions

### D1 — 赛事开始不是游戏开始
`EVENT_LIVE_PRE_GAME` 属于 LIVE_MATCH 阶段，但不是 `IN_GAME`。只有明确 Draft/Loading/verified gameplay frame 等证据才能继续推进。

### D2 — Reducer 是 lifecycle 唯一权威规则
Adapter 不能直接修改权威状态，所有 lifecycle signal 必须经过 `LiveMatchStateReducer`。

### D3 — 新 Game 不继承旧 gameId
Game Number 从 G1 进入 G2 而 Provider 暂无 G2 gameId 时，`currentGameId` 必须清空。

### D4 — 旧 Game 延迟帧不能回滚新 Game
Game Number / Game ID 与 lifecycle boundary 一起判断 stale/future/conflict；G2 已开始后 G1 延迟信号只能被忽略。

### D5 — heartbeat 更新 freshness，不制造伪 transition
同一 Game、同一 lifecycle 的新 observation 返回 `Ignored(DUPLICATE)`，但刷新 `lastObservedAtEpochMillis` / provenance。

### D6 — 旧 observation 不能凭 lifecycle rank 越级
同一 Game 内只要 lifecycle-changing observation 的时间早于当前权威 freshness，即为 `STALE_OBSERVATION`。更高 lifecycle rank 不能覆盖更晚时间的事实。

### D7 — Series complete 是终态
`SERIES_COMPLETE` 后任何 LIVE signal 都不能把 Series 拉回进行中。

### D8 — Provider Arbitration 位于 Application
候选优先规则：
1. 超出 REALTIME freshness window 时明显更新 observation 优先；
2. 同窗口 evidence：`VERIFIED_FRAME > PROVIDER_EXPLICIT > DERIVED`；
3. authority；
4. revision；
5. timestamp。

### D9 — Application 可补必要中间状态，但必须标 Derived
若 Provider 明确进入 `BETWEEN_GAMES / SERIES_COMPLETE` 而当前还是 `IN_GAME`，Application 可补 `POST_GAME`，但补出的状态事件必须为 derived evidence。

### D10 — lifecycle transition 必须成为标准事件
真正生命周期变化输出 `MatchStateChanged`；同状态 heartbeat 不生成 transition event。

### D11 — Timeline 不保存 Provider payload/free text
Timeline 只保存标准化 `LiveGameSnapshot` 与 `MatchEvent`。

### D12 — Event identity 与 transport sequence 解耦
`semanticKey()` 不包含 transport sequence / provenance。Objective `detail` 属描述信息，不参与事实 identity。

### D13 — 乱序允许回填
合法历史事件可晚到并按 `gameTimeSeconds + sequence + semanticKey` 稳定重排。

### D14 — 同秒 snapshot 不使用 last-write-wins
同秒 snapshot 按 provenance authority / timestamp / revision 仲裁。

### D15 — Draft action 收敛为枚举
`DraftChangedEvent.action` 使用 `DraftActionType = PICK/BAN/LOCK/UNDO/OTHER`，Adapter 只能翻译到标准类型。

## 5. Files Changed

### Added
- `core/domain/src/main/kotlin/com/laner/core/domain/LiveState.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/Timeline.kt`
- `core/domain/src/test/kotlin/com/laner/core/domain/LiveMatchStateReducerTest.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveStatePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveMatchStateService.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveTimelinePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveTimelineService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveMatchStateServiceTest.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveTimelineServiceTest.kt`
- `docs/development/2026/2026-09-12_LNR-013_live-state-arbitration-timeline.md`

### Modified
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchEvent.kt`
- `core/domain/README.md`
- `core/application/README.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/FEATURE_BASELINE.md`
- `docs/CHANGELOG.md`
- `docs/TROUBLESHOOTING.md`

### Deleted
- None.

## 6. Functional / Architecture / API / Schema Changes

- 新增 LIVE lifecycle authority Domain；
- 新增 LIVE Source / State Repository / Timeline Repository Ports；
- 新增 Application Provider Arbitration；
- 新增 provider-neutral Timeline contract；
- `MatchEvent` 新增 `VERIFIED_FRAME` evidence；
- Draft action 从 String 改为 `DraftActionType`；
- lifecycle transition 统一输出 `MatchStateChanged`；
- Core 仍不依赖 Android/HTTP/JSON/Provider SDK；
- 本轮没有 persistent schema migration，仅定义 persistence Ports。

## 7. Tests / Verification

### Domain regressions
已验证：
- `EVENT_LIVE_PRE_GAME != IN_GAME`；
- verified frame 可跳过缺失 Draft/Loading signal 确认真实开局；
- IN_GAME → POST_GAME → BETWEEN_GAMES → next Game；
- G2 缺 gameId 时清空 G1 gameId；
- old-game delayed signal 不得回滚新局；
- future-game during active game = Conflict；
- duplicate heartbeat 刷新 freshness 但保持事件幂等；
- 新 heartbeat 后的旧 POST_GAME = STALE_OBSERVATION；
- SERIES_COMPLETE terminal。

### Application regressions
已验证：
- fresh verified frame beats stale official event-live；
- realtime window 内 verified frame beats weaker textual status；
- weaker series-end 不能覆盖 stronger verified live frame；
- provider failure + valid source = DEGRADED but keeps valid fact；
- no valid source = UNAVAILABLE + preserve current state；
- wrong match observation 在 arbitration 前拒绝；
- lifecycle transitions 输出标准 `MatchStateChanged` 和正确 evidence；
- reconnect duplicate event semantic dedupe；
- out-of-order event replay；
- same-second snapshot provenance selection；
- invalid cross-game event rejection。

### CI evidence
- run `34690850479`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS。
- run `34691124746`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS。
- run `34691364933`: Architecture Gate PASS / Domain Tests **FAIL** / Android skipped。
  - failing regression: `delayedPostGameAfterNewerInGameHeartbeatIsIgnored`；
  - root cause: stale observation 判断错误依赖 lifecycle rank，导致较旧 POST_GAME 可越过较新 IN_GAME heartbeat；
  - historical failure 已写入 `docs/TROUBLESHOOTING.md`。
- fix commit `22668b37d22be5969ec59c99ac687f57c52a1ad3`：同一 Game 的旧 lifecycle-changing observation 一律 stale。
- run `34691458209`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS。
- 文档收口后的 final exact-head / PR Gate：在 PR 收口阶段验证并在 merge 后回填。

## 8. Risks / Compatibility / Security / Performance / Data

- 本轮不处理 Provider credentials，不新增 secret surface。
- Arbitration 是 O(configured sources)，轮询频率由后续 Adapter/Orchestration task 决定。
- Timeline merge 当前按单 Game 集合处理；长期归档需要 Android Repository 的 bounded storage / pagination / archive policy。
- `stateEvents.sequence` 是排序辅助，不等同 Provider transport sequence；未来全局事件存储若需要 monotonic sequence，应由 Event Repository 分配。
- 本轮无 Android persistence，因此不宣称进程杀死/断电恢复完成。

## 9. Known Issues / Blockers / Follow-ups

- 真实 Riot/LPL/Cito LIVE Sources 尚未接入；
- Live State Repository 仍只有 Port/test memory implementation；
- Timeline Repository 仍只有 Port/test memory implementation；
- LIVE UI/HUD 尚未消费权威 state；
- 实时经济/击杀/塔/龙/男爵/player state Provider normalization 尚未迁移；
- 下一任务：`LNR-014 — LIVE Source Adapters / Local Persistence / Composition Wiring`。

## 10. Rollback

- Revert LNR-013 PR/merge commit 可移除本任务全部 LIVE Core/Application 能力。
- 本轮无 persistent schema / remote DB migration，无数据 rollback。

## 11. Repository Evidence

- Branch: `feature/lnr-013-live-state-timeline`
- Baseline: `f6e00c9a3b960fe8370f20da7d5a10675c66c69b`
- Key fix: `22668b37d22be5969ec59c99ac687f57c52a1ad3`
- Code-level final PASS before documentation closeout: run `34691458209`
- PR / merge / post-doc exact-head CI: merge 后回填本记录。

## 12. Status Sync

- LNR-013 task: `DONE` as Core/Application foundation；
- `LIVE-001/002`: `IN PROGRESS`；
- `LIVE-003/004`: `IN PROGRESS`；
- `LIVE-013`: `IN PROGRESS`；
- `LIVE-017/018/019`: `IN PROGRESS`；
- LNR-014: `TODO`。

这些产品条目不能因为 Core contract 完成而虚报 DONE。

## 13. Post-change Compliance Review

- Core 无 Android/OkHttp/JSON/platform import：`PASS`；
- Adapter 不拥有 lifecycle authority：`PASS`；
- UI 无新增业务判断：`PASS`；
- 赛事开始 != 游戏开始：`PASS`；
- old-game / stale observation 防回滚：`PASS`；
- duplicate/reconnect 幂等：`PASS`；
- Provider free text 不进入 Timeline identity：`PASS`；
- 模块 README：`PASS`；
- Troubleshooting 历史 bug 留档：`PASS`；
- 状态/计划/功能基线同步：`PASS`；
- exact-head CI / PR Gate：合并前必须 PASS；
- real Provider / persistence / UI wiring：`N/A for LNR-013`，进入 LNR-014。

结论：LNR-013 Core/Application 范围 `DONE`；仓库合并仍必须经过 exact-head CI + PR Gate。
