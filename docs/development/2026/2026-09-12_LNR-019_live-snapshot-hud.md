# LNR-019 — Global LIVE Snapshot / Timeline / Match HUD

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
- Feature head: `502ef74930cd0f1a7670f6dab084d995991cd4da`
- PR: `#9`
- Merge commit: `64150f8cced159950013e4bdc3bbd9cf794a87b7`
- Final status: `WAITING EXTERNAL TEST`

## Goal
Prioritize the real-match validation path:
`Global Riot LIVE real frame → canonical LiveGameSnapshot → Application validation → GameTimeline → LIVE visible metrics`.

Global-first remains mandatory; Region is only a data dimension.

## Administrative correction
Initial work mistakenly used `LNR-017`, which is already reserved for Local AI/OCR; LNR-018 is reserved for final Migration Audit. History was not rewritten. Old branch/record remains; code was continued unchanged as formal LNR-019.

## Constitution / Architecture
Preflight and Post-change Compliance Review: `PASS`.

Lifecycle and gameplay stay separate:
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

`LiveSnapshotService` cannot progress lifecycle. UI cannot call Provider/Repository. Provider IDs remain Adapter/mapping-local.

## Delivered
- `LiveSnapshotSourcePort / ProviderLiveSnapshot / LiveSnapshotResolution`.
- `LiveSnapshotService` with `LNR-APP-LIVE-002` canonical Match/Game/team validation and Timeline ingest.
- `RiotGlobalLiveSnapshotSource` with `LNR-SRC-LIVE-006~007`.
- Riot provider identity → EventDetails active game → LiveStats last real frame → canonical `LiveGameSnapshot`.
- Team gold/kills/towers/dragons/barons and player level/KDA/CS/gold/champion where explicitly present.
- Existing canonical `GameTimeline` reused; no new LIVE store/schema.
- Full-width `GAME DATA / 实时真帧` LIVE UI; gold lead only when both real gold values exist.
- null remains unknown; explicit zero remains zero.
- Application regressions for valid ingest / noncanonical GameId / wrong teams.
- Adapter fixture regressions for canonical parsing / missing-vs-zero / unknown team identity.
- Development plan/status/Feature Baseline/Troubleshooting/Changelog/module README synced.

## Honest feature boundaries
- LIVE-005/006/007/008/010 automated implementation is present but awaits real online/device evidence.
- LIVE-006 currently means team total kill count, not KillEvent/MultiKill derivation.
- LIVE-009 remains IN PROGRESS: Baron field exists, Herald/Atakhan are not globally normalized.
- automatic Kill/Objective/GoldLead delta-event derivation is not part of LNR-019.
- full Android system RiftScreen/overlay remains later work.

## Security / Data / Performance
- Riot key behavior from LNR-016 unchanged; no new credential persistence/logging.
- Provider game/team IDs never become canonical IDs.
- Invalid canonical snapshot rejected before repository write.
- Snapshot failure does not mutate lifecycle truth.
- Manual LIVE refresh performs one lifecycle path plus one snapshot path; no high-frequency background polling added.
- No interpolation or invented missing metrics.

## Failure history
### run `34699837518`
Architecture PASS; Core test compile FAIL; downstream Android stages skipped.

Root cause: new `LiveSnapshotServiceTest` incorrectly imported unavailable `kotlinx.coroutines.runBlocking` and `org.junit.*` instead of using this Core module's existing `kotlin.test` + local Continuation harness. Production logic was not the failing surface.

Fix: `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5`; no production behavior changed.

Permanent regressions: valid canonical ingest, noncanonical GameId rejection/no write, wrong team rejection/no write.

## Verification evidence
- `34699942180`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- interim artifact `10300336311`, digest `sha256:f0e5ab811470a82bcf0b2f402ca252b29b49da6288343c1263cc6c4268ebe245`.
- final feature head `502ef74930cd0f1a7670f6dab084d995991cd4da` / run `34700371864`: all Gates + APK upload PASS.
- final feature artifact `10300027543`, digest `sha256:e469bd19c7421df311d1ba207b2714c41321d14444c2e64c5ca7500017d5e075`.
- PR #9 run `34700481940`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- PR #9 artifact `10299868008`, digest `sha256:98a77e9964aa4bf266e31986e0ac6533d8da5985066fda1b5fcd8dfad010d29f`.
- merge `64150f8cced159950013e4bdc3bbd9cf794a87b7`.

## External validation
Real BLG vs AL Android EventDetails/LiveStats snapshot behavior is still `WAITING EXTERNAL TEST`. Fixture/CI PASS is not reported as online evidence. Cito remains DEFERRED.

## Rollback
Revert merge `64150f8cced159950013e4bdc3bbd9cf794a87b7` plus this closeout if full rollback is required. No storage migration was introduced; LNR-016 lifecycle path remains independently functional.

## Compliance conclusion
Architecture/tests/docs/status/troubleshooting/remote Git evidence are consistent. Automated delivery is complete; real Provider/device certification is pending by design.

**Final: `WAITING EXTERNAL TEST`.**
