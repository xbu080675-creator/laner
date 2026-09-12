# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- LNR-019 merge: `64150f8cced159950013e4bdc3bbd9cf794a87b7`
- Post-merge closeout: `2be807e252272e91d6ba82b6d0b51e5ec6460183`
- Project phase: `M1 / Feature Migration`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`

## Task Status
| Task | Title | Status |
|---|---|---|
| LNR-000~009 | Constitution / baseline / architecture / product foundation | DONE |
| LNR-010 | Global catalogue / schedule | WAITING EXTERNAL TEST |
| LNR-011 | PRE Roster / Staff / Form / H2H | WAITING EXTERNAL TEST |
| LNR-012 | Standings / Qualification / Tournament Edition | WAITING EXTERNAL TEST |
| LNR-013 | LIVE Match State / Arbitration / Event / Timeline Core | DONE |
| LNR-014 | LIVE Android persistence / composition / UI | WAITING EXTERNAL TEST |
| LNR-015 | Global POST Result / Archive / Historical Timeline / Replay | WAITING EXTERNAL TEST |
| LNR-016 | Testable Android Platform / Riot Global LIVE / APK Delivery | WAITING EXTERNAL TEST |
| LNR-017 | Local AI / OCR / Roster Assist | TODO |
| LNR-018 | Compatibility / Full Regression / Migration Audit | TODO |
| LNR-019 | Global LIVE Snapshot / Timeline / Match HUD | WAITING EXTERNAL TEST |

## LNR-019 Delivered Truth
- `LiveSnapshotSourcePort` separates gameplay facts from lifecycle authority.
- `LiveSnapshotService` validates canonical MatchId/GameId/gameNumber/team identity before `LiveTimelineService` persistence.
- `RiotGlobalLiveSnapshotSource` resolves provider identity → EventDetails active game → LiveStats latest real frame.
- canonical `LiveGameSnapshot` carries team gold/kills/towers/dragons/barons and player level/KDA/CS/gold/champion when explicitly provided.
- missing fields stay null; UI renders unknown instead of zero.
- LIVE page now has full-width `GAME DATA / 实时真帧`, source diagnostics, team comparison and conditional gold lead.
- verified snapshots reuse the existing canonical `GameTimeline`; `LiveMatchStateService` remains the sole lifecycle authority.
- no region-specific business branch was introduced.

## Verification
- task-ID collision was corrected without rewriting history: LNR-017 remains reserved for AI/OCR; formal task is LNR-019.
- run `34699837518`: Architecture PASS / Core test compile FAIL because the new test used unavailable coroutine/JUnit APIs; failure preserved.
- fix `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5`: aligned to existing Core test harness; no production behavior change.
- run `34699942180`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- final feature head `502ef74930cd0f1a7670f6dab084d995991cd4da` / run `34700371864`: all Gates + APK upload PASS.
- preferred feature artifact `10300027543`, digest `sha256:e469bd19c7421df311d1ba207b2714c41321d14444c2e64c5ca7500017d5e075`.
- PR #9 run `34700481940`: all Gates + APK upload PASS.
- PR #9 merged as `64150f8cced159950013e4bdc3bbd9cf794a87b7`.

## Waiting External Test / Honest Gaps
- BLG vs AL online Riot target/EventDetails/LiveStats lifecycle + snapshot behavior still requires Android real-device evidence.
- fixture/CI PASS is not online evidence.
- team total kill count support does not imply KillEvent/MultiKill derivation.
- Baron is normalized, but Herald/Atakhan are not yet global; LIVE-009 remains IN PROGRESS.
- automatic Kill/Objective/GoldLead delta-event derivation is not part of LNR-019.
- full Android system RiftScreen/overlay, Watch/Player/OTA, AI/OCR and final migration audit remain later work.
- Cito remains DEFERRED.

## Next
Use the LNR-019 APK for BLG vs AL real-device verification. The next engineering slice can build RiftScreen/secondary-display presentation on top of the now-verified Application snapshot contract without changing Provider/UI boundaries.
