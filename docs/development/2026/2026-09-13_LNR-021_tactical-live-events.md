# LNR-021 — Tactical HUD / LIVE Event Derivation

- Date: 2026-09-13 Asia/Taipei
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `87f90a89ad7a35fdb9717ef1003fa984bba4fdab`
- Working branch: `feature/lnr-021-tactical-live-events`
- Block: 2 / 8
- Task status: `WAITING EXTERNAL TEST` as functional target; delivery closeout currently `IN PROGRESS`
- Block 1: frozen; no LNR-020 functional modification allowed in this task

## 1. Request / Goal

用户冻结第一块后明确进入第二块：`Tactical HUD + live event layer`。

目标不是复制旧 RiftLab 的巨型 `RiftOverlayService` / simulation store，而是保留用户可见行为和产品价值，在 Laner 当前 Application truth 架构上实现：
- Kill delta；
- MultiKill window；
- TeamFight window；
- Gold lead change；
- 可证明的 Objective delta；
- Tactical HUD；
- Draft > Tactical > RiftScreen 旧版可见优先级；
- 本地 Preview 仅作视觉夹具；
- 所有事件可追溯、可重算、可持久化、不造缺失事实。

对应 Feature Baseline：
- `LIVE-014 Kill / MultiKill / TeamFightWindow`；
- `LIVE-015 GoldLeadChange`；
- `LIVE-030 Tactical HUD / 战术副屏`。

## 2. Constitution Preflight

结果：`PASS`。

编码前重新读取并核对：
- `docs/ENGINEERING_CONSTITUTION.md`；
- 最新 `main@87f90a89ad7a35fdb9717ef1003fa984bba4fdab`；
- `docs/FEATURE_BASELINE.md`；
- `docs/DEVELOPMENT_PLAN.md`；
- `docs/IMPLEMENTATION_STATUS.md`；
- `docs/TROUBLESHOOTING.md`；
- Core Domain/Application README；
- LNR-013 / LNR-019 / LNR-020 开发记录；
- 当前 `MatchEvent / Timeline / LiveTimelineService / LiveMatchContextService / LiveSnapshotService`；
- 当前 Android Overlay controllers / service / persistence；
- 旧 RiftLab `RiftOverlayService` / Tactical HUD 行为证据。

Preflight 确认：
- 第一块 LNR-020 已冻结，不复用旧整改草稿；
- `LIVE-006` 当前是 team kill totals，不等于 KillEvent；
- `LIVE-014 / 015 / 030` 均为 TODO；
- Presentation 必须只消费 Application/Domain truth，不得重新做赛事业务派生；
- old Tactical simulation 的 HP/CD/位置等字段没有 canonical evidence，不能迁移成“真实 HUD”。

## 3. Scope / Not Doing

### In scope
- 扩展标准 MatchEvent 表达 aggregate kill / multi-kill window / team-fight window；
- Application 从 canonical Timeline snapshots 做确定性事件派生；
- derived event reconcile / idempotency；
- Tower/Dragon/Baron delta；
- GoldLeadChanged deadband；
- Timeline persistence schema v2 + v1 read migration；
- Tactical HUD Presentation/View/WindowController；
- Android-only Tactical Preview；
- Draft > Tactical > RiftScreen 优先级；
- game-time + wall-clock stale card protection；
- Core/App/Adapter/Presentation tests；
- docs/status/troubleshooting/changelog/module README。

### Explicit non-goals
- 不接真实 Draft Provider，`LIVE-012` 不动；
- 不宣称官方 Double/Triple/Quadra/Penta Kill；
- 不从 team kill delta 猜 killer/victim；
- 不从 dragon count 猜龙种/龙魂/远古龙；
- 不生成 HP、技能 CD、召唤师技能 CD、位置等当前 canonical snapshot 没有的字段；
- 不实现 Herald/Atakhan 全局字段，`LIVE-009` 继续 IN PROGRESS；
- 不做 Watch Hub / Player；
- 不做 AI Insight；
- 不改 LNR-020 Draft layout/edit/lock 功能。

## 4. Architecture / Design Decisions

### D1 — 事件派生属于 Application，不属于 HUD
链路：
```text
Verified LiveGameSnapshot
→ LiveTimelineService
→ canonical GameTimeline snapshots
→ LiveEventDerivationService
→ standard MatchEvent
→ LiveTimelineService.reconcileGeneratedEvents
→ LiveMatchContextResult
→ TacticalHudPresentationMapper
→ TacticalHudWindowController
→ OverlayWindowHost
```

Presentation 只解释标准事件，不比较 raw frame，不计算业务事实。

### D2 — 差分只证明差分本身
Team kills `3 → 4` 只证明“该队在窗口内 +1 kill”。没有更强证据时：
- killerId = null；
- victimId = null；
- 不生成虚构 player identity。

### D3 — Player kill delta 需要完整解释 team delta
只有前后两帧 canonical PlayerId 可比较，且该队 player kill increments 的总和与 team kill delta 完全一致时，才允许将 aggregate delta 细化到 player-bound KillEvent。

### D4 — MultiKillWindow 不是官方 MultiKill
条件：同一 player 的 kill delta >=2 且 snapshot 间隔 <=20s。

标准名固定 `MultiKillWindowEvent`，UI 固定称“采样窗口多杀”。不能显示官方 Double/Triple/Quadra/Penta 标签，除非未来 Provider explicit event 明确提供。

### D5 — TeamFightWindow 是 DERIVED_WINDOW
<=20s 窗口内双方总 kill delta >=3 时生成 `TeamFightWindowEvent`。它表示“高密度战斗窗口”，不宣称 Riot 官方团战分类。

### D6 — GoldLeadChange 用 deadband 防抖
只有前后可信帧都超过 ±250g 且领先方由一侧明确切换到另一侧才生成 `GoldLeadChangedEvent`。不在 0 附近抖动，不为每帧经济差制造事件。

### D7 — Objective 只使用可证明计数
当前可派生：Tower / Dragon / Baron。

Dragon count 只标准化 `DRAGON`，detail 明确“龙种未知；仅由总数差分确认”。不生成 SOUL / ELDER_DRAGON。Herald/Atakhan 没有全局标准字段，保持未实现。

### D8 — 派生事件必须可重算
固定 generator provider：`laner-live-event-derivation`。

`LiveTimelineService.reconcileGeneratedEvents()`：
- 删除/替换该 generator 自己的旧派生事件；
- 保留 Provider explicit / Draft / lifecycle / 其他 provider 事件；
- late/out-of-order / stronger same-second snapshot 到达后可重算，避免旧派生事实残留。

### D9 — Provider explicit 事实优先避免重复
若同 snapshot interval 已存在可解释的 Provider explicit Kill/Objective/GoldLead event，本地 derivation 不再重复制造同数量事实。

### D10 — Timeline schema v2，向前读取 v1
新增 event type/field 后持久化版本从 v1 升 v2：
- v1 继续可读；
- 下一次写入自动升级 v2；
- corrupt / unsupported schema 继续显式失败；
- 不要求用户手动清缓存。

### D11 — Tactical HUD 不成为永久 scoreboard
Verified Tactical event 两层 freshness：
- game-time TTL = 25s；
- wall-clock TTL = 30s from event provenance `observedAtEpochMillis`。

第二层用于 Provider 停更/游戏时间冻结；过期后 `TacticalHudWindowController` 自动退场并恢复下层 RiftScreen。

### D12 — Preview 与赛事事实硬隔离
`TacticalHudPreviewSession` 只在 Android Presentation 层；固定 `LOCAL PREVIEW · NOT FACT`；不进入 Core、source arbitration、Timeline、Repository。

### D13 — Overlay 可见优先级保持旧行为
```text
Draft HUD > Tactical HUD > RiftScreen
```

Draft verified/preview 激活时 Tactical/Rift 隐藏；Draft inactive 后 Tactical verified/preview 可激活；否则回普通 RiftScreen。

## 5. Files Changed

基于当前 branch 与 baseline main compare，生产/测试/文档文件如下。

### Added
- `core/application/src/main/kotlin/com/laner/core/application/LiveEventDerivationService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveEventDerivationServiceTest.kt`
- `app/src/main/java/com/laner/app/overlay/TacticalHudPresentation.kt`
- `app/src/main/java/com/laner/app/overlay/TacticalHudOverlayView.kt`
- `app/src/main/java/com/laner/app/overlay/TacticalHudPreviewSession.kt`
- `app/src/main/java/com/laner/app/overlay/TacticalHudWindowController.kt`
- `app/src/test/java/com/laner/app/overlay/TacticalHudPresentationMapperTest.kt`
- `docs/development/2026/2026-09-13_LNR-021_tactical-live-events.md`

### Modified
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchEvent.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/Timeline.kt`
- `core/domain/README.md`
- `core/application/src/main/kotlin/com/laner/core/application/LiveTimelineService.kt`
- `core/application/src/main/kotlin/com/laner/core/application/LiveMatchContextService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveMatchContextServiceTest.kt`
- `core/application/README.md`
- `app/src/main/java/com/laner/app/LanerAppGraph.kt`
- `app/src/main/java/com/laner/app/data/live/JsonLiveTimelineRepository.kt`
- `app/src/test/java/com/laner/app/data/live/JsonLiveTimelineRepositoryTest.kt`
- `app/src/main/java/com/laner/app/overlay/RiftScreenOverlayService.kt`
- `app/src/main/java/com/laner/app/ui/LiveMatchScreen.kt`
- `app/README.md`
- `docs/FEATURE_BASELINE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/TROUBLESHOOTING.md`
- `docs/CHANGELOG.md`

### Deleted
- None.

## 6. Tests / Verification

### Core/Application permanent regressions
`LiveEventDerivationServiceTest` 覆盖：
- team kill delta 不造 killer/victim；
- player kill delta 完全解释 team delta 后生成 player-bound event；
- multi-kill window threshold / interval；
- team-fight window；
- Dragon/Baron/Tower delta；
- GoldLead deadband / true lead flip；
- Provider explicit event 抑制重复 local-derived event；
- generator reconcile 不删除外部事实；
- snapshot ordering / monotonic comparable boundary。

### Adapter persistence regressions
`JsonLiveTimelineRepositoryTest` 覆盖：
- v2 round-trip 保存新增 events / fields / evidence / provenance；
- v1 legacy timeline 可读取；
- v1 下一次写入转 v2；
- corrupt timeline 显式失败；
- unsupported schema 显式失败；
- temp file atomic write 行为保持。

### Tactical Presentation regressions
`TacticalHudPresentationMapperTest` 覆盖：
- 非 IN_GAME 不激活；
- game-time TTL 超时不显示；
- wall-clock TTL 过期不显示；
- Preview 不冒充 Provider freshness；
- 同秒 TeamFight 优先于 aggregate Kill；
- MultiKill 文案禁止官方 Triple/Double claim；
- Dragon subtype 明确未知。

## 7. Failure History

### Failure A — run `34709821178`
- head: `981f98a423147e35bfd999c2f62a4166f4be6bdf`
- Architecture boundary: PASS
- Domain/Application tests: PASS
- Android Adapter Unit stage: FAIL during production `:app:compileDebugKotlin`
- Android debug compile/APK: correctly skipped

Compiler error：
`LiveMatchScreen.kt:eventLabel()` sealed `when` 未穷举 `MultiKillWindowEvent / TeamFightWindowEvent`。

Root cause：Domain sealed event hierarchy 已扩展，但已有 Presentation timeline formatter 未在同一切片同步。

Fix：`403bba4e874ad37978179618e84dceaafb9f06f8`。

永久规则：Domain sealed MatchEvent 新增类型后，所有 Presentation formatter 必须继续 exhaustive；不得用 catch-all `else` 隐藏未来遗漏。

已同步：`docs/TROUBLESHOOTING.md / LNR-UI-LIVE-004`。

### Subsequent implementation baseline
- head `b83a83c0c8908b8da1755d306958352fbfe389cf`
- run `34710012697`
- Architecture PASS
- Domain/Application PASS
- Android Adapter Unit PASS
- Android debug compile PASS
- APK upload PASS
- artifact `10303071228`
- digest `sha256:1e0d24bd8e5423216442a97d70c6307f128a93f22476e462b9942e62da906d6f`

此 baseline 之后继续加入 stale-card wall-clock hardening；因此它是中间 PASS，不作为最终 exact-head 证据。

## 8. Data / Schema / Migration

- `JsonLiveTimelineRepository` schema version: `1 → 2`；
- read supports v1 and v2；
- write always emits v2；
- new types: MultiKillWindow / TeamFightWindow；
- Kill/Objectives/GoldLead 支持 count / observed window 等新字段；
- v1 missing new fields 使用兼容默认值，不制造新事实；
- no remote DB migration；
- rollback 若降回仅支持 v1 的旧 APK，需要清理/迁移 v2 timeline 文件，因此发布层回滚必须考虑本地 timeline compatibility。

## 9. Security / Privacy / Performance

- 不新增 credential surface；
- 不记录 Riot key；
- event derivation 只处理单 Game Timeline snapshots；当前 O(n snapshots + events)，5s snapshot 节奏下可接受；
- reconcile 只替换本 generator events，避免跨来源数据破坏；
- Preview 不持久化赛事事实；
- HUD 没有网络调用，不接 raw provider payload。

## 10. Known Gaps / External Validation

- 真实 Riot online 是否以当前采样频率稳定捕获 kill/objective/gold transitions：`WAITING EXTERNAL TEST`；
- Android Tactical overlay 真机视觉、系统权限、touch-through、Draft>Tactical>Rift 层级：`WAITING EXTERNAL TEST`；
- Provider 中断后 30s stale-card 自动退场：JVM logic 已测，真机行为仍需补证；
- Herald / Atakhan 未全局标准化；
- 官方 multi-kill classification / killer-victim pairing 仍需明确 Provider event source；
- Draft real provider 不属于本任务。

## 11. Rollback

代码回滚：revert LNR-021 merge commit（merge 后在 closeout 记录稳定锚点）。

数据注意：本任务把 local Timeline schema 升级 v2。当前代码可读 v1；若回滚到 LNR-020 的 v1-only APK，已被 v2 写出的 timeline 文件可能被旧代码拒绝。回滚方案必须：
1. 先备份 `live/timeline`；
2. 删除 v2 timeline cache 或提供 downgrade migration；
3. 不影响 canonical LIVE state schema v1、POST archive、provider identity 文件。

## 12. Status Sync

当前分支已同步：
- `LIVE-014 → WAITING EXTERNAL TEST`；
- `LIVE-015 → WAITING EXTERNAL TEST`；
- `LIVE-030 → WAITING EXTERNAL TEST`；
- `LIVE-009` 继续 IN PROGRESS；
- `LIVE-012` 继续 TODO；
- LNR-021 task → WAITING EXTERNAL TEST after automated delivery；
- 第 3 块仍未开始。

## 13. Post-change Compliance Review — preliminary

当前静态复查：
- Core platform import：PASS；
- Presentation 不派生赛事业务事实：PASS；
- Region business branch：NONE；
- raw Provider payload reaches HUD：NO；
- duplicate source-of-truth：未发现；
- local Preview truth contamination：未发现；
- silent WindowManager exception：未新增，继续走 OverlayWindowHost；
- schema migration path：present；
- failure history preserved：PASS；
- module README/status/baseline/troubleshooting：已同步；
- real-device items honest：WAITING EXTERNAL TEST。

正式 Post-change Compliance Review 必须在最终 exact-head/PR Gate 后重做并在 closeout 固化。

---

# §15 Fixed Task Delivery Sheet

1. **Task / title / final status**：`LNR-021 — Tactical HUD / LIVE Event Derivation`；当前 `WAITING EXTERNAL TEST / DELIVERY INCOMPLETE`。
2. **Baseline branch / start / end commit**：`main@87f90a89ad7a35fdb9717ef1003fa984bba4fdab` → working branch current head；最终 head 待 Gate 冻结。
3. **Constitution Preflight**：`PASS`，编码前重新读取当前宪法/主线/状态/相关源码/tests/legacy evidence。
4. **Modules**：`:core:domain`、`:core:application`、`:app`、docs。
5. **Related clauses**：§0 repository truth；§1/1.1 mandatory loop + Preflight；§2 records；§3 dependency/single authority/presentation boundary；§7 schema migration；§8 observability；§11 troubleshooting；§13 status sync；§15 delivery sheet；L-3 quality；L-5 UI consumes Application truth。
6. **Exact files add/modify/delete**：见本记录 §5；当前 compare 共 25 个既有 changed files + 本开发记录；无删除。
7. **Implementation / design reason**：用 canonical snapshot deterministic derivation 替代旧 simulation/global-store；HUD 只解释标准事件；缺失事实不猜。
8. **Tests**：positive/negative/boundary/non-target/regression/persistence migration/Presentation TTL 已覆盖；Android real-device integration = `WAITING EXTERNAL TEST`。
9. **Script / Gate verification**：Failure A `34709821178` preserved；implementation baseline `34710012697` PASS；final exact-head/PR/main Gate pending。
10. **Logs / fault location**：Failure A 定位 `LiveMatchScreen.eventLabel()`；Troubleshooting ID `LNR-UI-LIVE-004`。
11. **Impact**：新增 LIVE derived events、Timeline schema v2、Tactical HUD；不改 lifecycle authority，不改 Draft truth，不改 Watch/POST/PRE。
12. **Known issues / follow-up**：real Riot online + Android overlay + stale-card device evidence；Herald/Atakhan；official explicit kill/multikill pairing sources。
13. **Rollback**：revert merge + 处理 v2 timeline cache compatibility；见 §11。
14. **Status sync**：Feature Baseline / Plan / Implementation Status / Troubleshooting / Changelog / module README 已更新。
15. **Commit / push / PR / release**：branch commits pushed；PR/merge/release pending。
16. **Post-change Compliance Review**：`PRELIMINARY PASS / FINAL PENDING exact-head + PR + main Gate`。
17. **Final conclusion**：`DELIVERY INCOMPLETE`。禁止在最终 Gate/PR/merge/main closeout 前宣称第二块冻结或 DONE。
