# LNR-013 — LIVE Match State / Provider Arbitration / Unified Event / Timeline

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `f6e00c9a3b960fe8370f20da7d5a10675c66c69b`
- Working branch: `feature/lnr-013-live-state-timeline`
- Final status: `TESTING`

## 1. Request / Goal

建立 Laner 的 LIVE Core/Application 真相层，保留并加强旧 RiftLab 已经通过实机验证的“场间未开局 vs 新局真实开局”边界。

本任务核心目标：

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

- pure Kotlin `LiveMatchState`；
- `LiveStateSignal / Evidence / TransitionResult`；
- `LiveMatchStateReducer`；
- `LiveStateSourcePort`；
- `LiveMatchStateRepository`；
- `LiveMatchStateService` Provider Arbitration；
- standardized lifecycle event emission；
- `DraftActionType` 标准化；
- provider-neutral `GameTimeline / TimelineSnapshotPoint`；
- event semantic identity；
- `LiveTimelineRepository` Port；
- `LiveTimelineService` ingestion / replay contract；
- duplicate / out-of-order / reconnect / same-second snapshot arbitration tests；
- Core module README 更新。

### Explicit non-goals

- 不接 Riot/LPL/Cito 真实 LIVE Adapter；
- 不实现 Android 文件 Timeline Repository；
- 不接 RiftScreen / Tactical HUD / Overlay；
- 不迁移实时经济、击杀、资源 Provider payload parsing；
- 不在 UI 中自行推断 lifecycle；
- 不把旧 RiftLab Global Store 复制进 Laner；
- 不宣称 LIVE 产品链已完成。

## 4. Design Decisions

### D1 — 赛事开始不是游戏开始

`EVENT_LIVE_PRE_GAME` 是 LIVE_MATCH 阶段，但不是 `IN_GAME`。只有明确 Draft/Loading/verified gameplay frame 等证据才能继续推进。

### D2 — Reducer 是 lifecycle 唯一权威规则

Adapter 不能直接修改权威状态。所有 lifecycle signal 必须经过 `LiveMatchStateReducer`。

### D3 — 新 Game 不继承旧 gameId

当 Game Number 已从 G1 进入 G2，而 Provider 暂时未给 G2 gameId，`currentGameId` 必须清空，禁止把 G1 gameId 错挂到 G2。

### D4 — 旧 Game 延迟帧不能回滚新 Game

Game Number / Game ID 与 lifecycle boundary 一起判断 stale/future/conflict。G2 已开始后，G1 的延迟 POST_GAME 只能被忽略。

### D5 — LIVE heartbeat 更新 freshness，但不制造伪 transition

同一 Game、同一 lifecycle 的更新 observation 是 transport/事实心跳，不是状态变化。Reducer 返回 `Ignored(DUPLICATE)`，但在 observation 更新时刷新 `lastObservedAtEpochMillis` / provenance。

这样可以拒绝晚于旧状态但早于最新 heartbeat 的延迟 lifecycle signal。

### D6 — Series complete 是终态

`SERIES_COMPLETE` 后任何 LIVE signal 都不能把 Series 拉回进行中。

### D7 — Provider Arbitration 位于 Application

候选优先规则：

1. 超出 REALTIME freshness window 时，明显更新的 observation 优先；
2. 同窗口 evidence strength：`VERIFIED_FRAME > PROVIDER_EXPLICIT > DERIVED`；
3. authority；
4. revision；
5. source/observed timestamp。

因此，新鲜 verified gameplay frame 可以压过陈旧高 Authority 的 `event live` 文本；同窗口低质量 `series ended` 也不得结束被强 verified frame 证明仍在进行的比赛。

### D8 — Application 可补必要中间状态，但必须标 Derived

若 Provider 已明确进入 `BETWEEN_GAMES` / `SERIES_COMPLETE`，而当前状态还是 `IN_GAME`，Application 可以补 `POST_GAME`；补出的 `MatchStateChanged` 必须使用 derived evidence，后续真实 provider 状态仍保持 provider explicit evidence。

### D9 — lifecycle transition 必须成为标准事件

真正的生命周期变化输出 `MatchStateChanged`。普通相同 lifecycle heartbeat 不生成状态变化事件。

### D10 — Timeline 不保存 Provider payload/free text

Timeline 只保存标准化 `LiveGameSnapshot` 与 `MatchEvent`。

Provider-specific payload、transport DTO、自由描述文本不得成为 Timeline schema。

### D11 — Event identity 与 transport sequence 解耦

`semanticKey()` 不包含 Provider transport sequence / provenance。不同 Provider 或重连后对同一事实分配不同序列号，仍能幂等去重。

Objective `detail` 属描述信息，不参与事实 identity。

### D12 — 乱序允许回填

Timeline 接受晚到但合法的历史事件，并按 `gameTimeSeconds + sequence + semantic key` 稳定重排；不能因为网络乱序直接丢掉真实事件。

### D13 — 同秒 snapshot 不使用 last-write-wins

同一 game time snapshot 冲突按 provenance authority / timestamp / revision 选优。

### D14 — Draft action 收敛为枚举

`DraftChangedEvent.action` 从自由字符串收敛为 `DraftActionType = PICK/BAN/LOCK/UNDO/OTHER`。Adapter 后续只能翻译到标准类型。

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
- status / plan / feature baseline / changelog 在任务收口时同步。

### Deleted

- None.

## 6. Functional / Architecture / API / Schema Changes

- 新增 LIVE lifecycle authority Domain；
- 新增 LIVE Source / State Repository / Timeline Repository Ports；
- 新增 Application Provider Arbitration；
- 新增 provider-neutral Timeline contract；
- `MatchEvent` 新增 `VERIFIED_FRAME` evidence；
- Draft action 由 String 改为 `DraftActionType`；
- lifecycle transition 统一输出 `MatchStateChanged`；
- Core 仍不依赖 Android/HTTP/JSON/Provider SDK；
- 没有持久数据 schema migration；本轮只有 Repository contract，无 Android persistence implementation。

## 7. Tests / Verification

### Domain regressions

已加入：

- `EVENT_LIVE_PRE_GAME` 不等于 `IN_GAME`；
- verified frame 可在 Provider 缺失 Draft/Loading signal 时确认真实开局；
- IN_GAME → POST_GAME → BETWEEN_GAMES → next Game；
- G2 缺 gameId 时清空 G1 gameId；
- old-game delayed signal 不得回滚新局；
- current game 活跃时 future-game signal = Conflict；
- duplicate heartbeat 刷新 freshness 但保持幂等；
- 新 heartbeat 后的旧 POST_GAME = STALE_OBSERVATION；
- SERIES_COMPLETE terminal。

### Application regressions

已加入：

- fresh verified frame beats stale official event-live；
- realtime window 内 verified frame beats weaker textual status；
- weaker series-ended observation 不能覆盖 stronger verified live frame；
- provider failure + valid source = DEGRADED but keeps valid fact；
- no valid source = UNAVAILABLE + preserve current state；
- wrong match observation 在 arbitration 前拒绝；
- lifecycle transitions 输出标准 `MatchStateChanged` 和正确 evidence；
- reconnect duplicate event semantic dedupe；
- out-of-order event replay；
- same-second snapshot provenance selection；
- invalid cross-game event rejection。

### CI evidence

- run `34690850479`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS（第一版 LIVE state + arbitration）。
- run `34691124746`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS（Timeline + standardized lifecycle event）。
- heartbeat stale-order regression 与文档收口后的 final exact-head CI：待最终运行结果回填。

## 8. Risks / Compatibility / Security / Performance / Data

- 本轮不处理 Provider credentials，不新增 secret surface。
- Arbitration 是 O(number of configured sources)，当前未引入轮询频率策略；频率由后续 Source Orchestration/Adapter task 定义。
- Timeline merge 使用当前 game timeline 集合内 semantic-key map；大规模长期归档前需要 Android Repository 做 bounded storage / pagination / archive policy。
- `stateEvents.sequence` 当前是标准事件排序辅助，不等同 Provider transport sequence；后续持久事件总线如需要全局 monotonic sequence，应由 Event Repository 分配。
- no Android persistence in this task，因此不宣称断电/进程杀死恢复已完成。

## 9. Known Issues / Blockers / Follow-ups

- 真实 Riot/LPL/Cito LIVE Sources 尚未接入；
- Live state Repository 仍只有 Port/test memory implementation；
- Timeline Repository 仍只有 Port/test memory implementation；
- LIVE UI/HUD 尚未消费权威 state；
- 实时经济/击杀/塔/龙/男爵/player state 的 Provider normalization 尚未迁移；
- 需要下一任务完成 Adapter + Android local persistence + Composition Root wiring + external/real-device evidence。

## 10. Rollback

- Revert LNR-013 PR/merge commit 即可移除全部 LIVE Core/Application 新增能力。
- 本轮没有 persistent schema / remote DB migration，无数据 rollback。

## 11. Final Repository Evidence

- Branch: `feature/lnr-013-live-state-timeline`
- PR: closeout 后回填
- Merge commit: closeout 后回填
- Final CI: closeout 后回填

## 12. Status Sync

本任务是 LIVE 的 Core/Application 基础层任务，本身可在 Core test + architecture + compile 完成后 `DONE`；但以下产品功能不会因此自动 DONE：

- `LIVE-001/002`：Core invariant 已完成，真实 Provider/Composition 尚未接，功能清单保持 `IN PROGRESS`；
- `LIVE-003/004`：Arbitration contract 已完成，真实多 Provider 尚未接，保持 `IN PROGRESS`；
- `LIVE-013`：标准事件 Domain 已建立，但真实 Adapter 事件 normalization 未接，保持 `IN PROGRESS`；
- `LIVE-017/018/019`：Timeline contract/evidence 已建立，但 Android persistence / continuous capture 未接，保持 `IN PROGRESS`。

## 13. Post-change Compliance Review

- Core 无 Android/OkHttp/JSON/platform import：由 Architecture Gate 验证；
- Adapter 不拥有 lifecycle authority：`PASS`；
- UI 无新业务判断：`PASS`（本轮未改 UI）；
- 赛事开始 != 游戏开始：`PASS`（Domain regression）；
- old-game / stale observation 防回滚：`PASS`（Domain regression）；
- duplicate/reconnect 幂等：`PASS`（Domain/Application regression）；
- Provider free text 不进入 Timeline identity：`PASS`；
- 模块 README 已同步：`PASS`；
- Final exact-head CI：`TESTING`；
- PR / merge：`PENDING`。

结论：`TESTING`。最终 exact-head CI + PR Gate 通过后，LNR-013 可作为 Core/Application 基础层任务标记 `DONE`。
