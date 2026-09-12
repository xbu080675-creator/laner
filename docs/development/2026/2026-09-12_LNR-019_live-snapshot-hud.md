# LNR-019 — Global LIVE Snapshot / Timeline / Match HUD

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
- Working branch: `feature/lnr-019-live-snapshot-hud`
- Status: `TESTING`

## Goal
Prioritize tonight's real match validation path:
`Global Riot LIVE real frame → canonical LiveGameSnapshot → Application validation → GameTimeline → LIVE visible metrics`.

Global-first is mandatory; Region is only a data dimension.

## Administrative correction
Initial work was mistakenly started as `LNR-017` on `feature/lnr-017-live-snapshot-hud`, but repository planning already reserves LNR-017 for Local AI/OCR and LNR-018 for final Migration Audit. History was not rewritten: old branch/record remains, code head `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5` was continued unchanged as formal LNR-019.

## Constitution Preflight
`PASS` after task-ID correction. Reviewed latest constitution, main baseline/status, LNR-016 delivery, LIVE Domain/Application/Adapter/UI, provider identity and Riot historical LiveStats parser.

## Scope / Non-goals
In scope: Global Riot gameplay snapshot, canonical validation, Timeline ingest, full-width in-app LIVE visualization, tests/diagnostics/APK.

Non-goals: AI Insight, full draggable system Overlay/RiftScreen editor, Watch/Player/OTA, unsupported event inference, Cito online verification.

## Design
Lifecycle and gameplay remain separate authorities:
```text
ScheduledSeries
├─ LiveMatchStateService ← RiotGlobalLiveStateSource      # lifecycle authority
└─ LiveSnapshotService  ← RiotGlobalLiveSnapshotSource   # gameplay facts only
                         ↓ canonical validation
                    LiveTimelineService
                         ↓
                  canonical GameTimeline
                         ↓
                   LiveMatchScreen
```

`LiveSnapshotService` cannot progress lifecycle. Provider IDs stay Adapter/mapping-local. UI cannot call Provider/Repository directly.

## Files changed
### Core/Application
- `LiveSnapshotPort.kt` — provider-neutral snapshot Port/result/status contracts.
- `LiveSnapshotService.kt` — arbitration, canonical Match/Game/team validation, provenance, Timeline ingest, `LNR-APP-LIVE-002`.
- `LiveSnapshotServiceTest.kt` — valid ingest + wrong GameId/team rejection.
- `core/application/README.md` — LIVE lifecycle/snapshot boundary.

### Android Adapter / Composition / UI
- `RiotGlobalLiveSnapshotSource.kt` — provider identity → EventDetails active game → LiveStats latest real frame.
- `RiotGlobalLiveSnapshotSourceTest.kt` — fixture parser, null-vs-zero, unknown team rejection.
- `LanerAppGraph.kt` — source/service wiring.
- `MainActivity.kt` / `LanerRoot.kt` — service handoff.
- `LiveMatchScreen.kt` — full-width verified gameplay data card.
- `app/README.md` — current LIVE chain, diagnostics and tests.

### Governance
- `DEVELOPMENT_PLAN.md`
- `IMPLEMENTATION_STATUS.md`
- `FEATURE_BASELINE.md`
- `TROUBLESHOOTING.md`
- `CHANGELOG.md`
- this record.

## Functional behavior
Riot real frame fields are standardized when explicitly present:
- Team: gold, total kills, towers, dragons, barons.
- Player: level, K/D/A, CS, gold, champion.

Missing stays `null`. UI uses “未知 / ?” and only computes gold lead when both team gold values exist. LIVE-009 stays IN PROGRESS because Herald/Atakhan are not yet globally normalized. Team total kills support does not imply KillEvent/MultiKill support; LIVE-014 remains TODO.

## Security / Data / Compatibility
- Riot key behavior from LNR-016 unchanged; no credential persistence/logging added.
- Provider game/team IDs do not become canonical IDs.
- Existing Timeline schema reused; no storage migration.
- Invalid canonical snapshot is rejected before repository write.
- Snapshot adapter failure does not mutate lifecycle truth.

## Performance / Network
A manual LIVE refresh performs lifecycle refresh plus one snapshot fetch path. No background high-frequency polling was added in LNR-019. Timeline persists only validated real snapshots; no interpolation.

## Tests
### Application
- valid canonical snapshot → Timeline write;
- provider/raw GameId → `LNR-APP-LIVE-002`, no write;
- wrong teams → reject, no write.

### Android Adapter
- BLG/AL-style fixture parses canonical G4 team/player metrics;
- explicit `barons=0` remains 0;
- absent red `barons` remains null;
- unknown provider team identity rejects frame.

### CI history
- run `34699837518`: Architecture PASS; Core test compile FAIL. New test incorrectly imported unavailable `kotlinx.coroutines.runBlocking` / `org.junit.*`; production code was not the failing surface. Android stages were correctly skipped.
- fix `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5`: use existing `kotlin.test` + local Continuation suspend harness; no production changes.
- run `34699942180`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- artifact `10300336311`, workflow artifact digest `sha256:f0e5ab811470a82bcf0b2f402ca252b29b49da6288343c1263cc6c4268ebe245`.
- formal LNR-019 final exact-head / PR Gate: pending.

## Status sync
- DEVELOPMENT_PLAN: LNR-016 corrected to WAITING EXTERNAL TEST; reserved LNR-017/018 preserved; LNR-019 registered.
- IMPLEMENTATION_STATUS: LNR-019 TESTING.
- FEATURE_BASELINE: LIVE-005/006/007/008/010 → WAITING EXTERNAL TEST; LIVE-009 remains IN PROGRESS.
- TROUBLESHOOTING: test-harness failure + new LIVE diagnostics archived.
- CHANGELOG/module README synced.

## Known issues / external validation
- Real BLG vs AL EventDetails/LiveStats snapshot behavior is `WAITING EXTERNAL TEST` until Android device evidence exists.
- Snapshot frames are persisted, but automatic Kill/Objective/GoldLead delta-event derivation is not part of this task.
- Full Android system overlay/RiftScreen remains future work.
- Cito remains DEFERRED.

## Rollback
Feature branch is isolated. No schema migration. Before merge, revert LNR-019 commits or delete branch; after merge, revert task merge commit. Existing LNR-016 lifecycle path remains independently functional.

## Compliance conclusion
Preflight PASS. Architecture remains `Domain ← Application ← Adapter/UI`; Global-first preserved; no UI→Provider; no missing-value fabrication; failures retained; final exact-head and PR Gate required before merge.
