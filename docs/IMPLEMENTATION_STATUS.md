# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- LNR-019 branch: `feature/lnr-019-live-snapshot-hud`
- LNR-019 baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
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
| LNR-019 | Global LIVE Snapshot / Timeline / Match HUD | TESTING |

## LNR-016 Delivered Truth
- first real global Riot LIVE lifecycle baseline (`RiotGlobalLiveStateSource`);
- canonical target discovery + provider identity mapping;
- runtime memory-only Riot Key and on-device `LNR-SRC-LIVE-002~005` diagnostics;
- installable debug APK;
- PR #8 merged as `c7cb479175d6e64560d4478418a3ba73c36ddbce`;
- online BLG vs AL evidence remains external.

## LNR-019 Current Truth
- `LiveSnapshotSourcePort` separates gameplay frames from lifecycle authority.
- `LiveSnapshotService` validates canonical MatchId/GameId/gameNumber/team identity and only then ingests through `LiveTimelineService`.
- `RiotGlobalLiveSnapshotSource` uses existing provider identity → EventDetails active game → LiveStats last real frame.
- canonical `LiveGameSnapshot` now carries team gold/kills/towers/dragons/barons and player level/KDA/CS/gold/champion where Riot provides them.
- missing fields remain null; UI renders unknown instead of zero.
- LIVE page has a full-width `GAME DATA / 实时真帧` card, explicit source status, team comparison and gold difference only when both sides have real gold.
- real snapshots are persisted into the existing canonical `GameTimeline`; lifecycle still belongs solely to `LiveMatchStateService`.
- no LPL/LCK branch was introduced.

## LNR-019 Verification
- initial work accidentally used task id LNR-017, conflicting with the reserved AI/OCR task. Historical branch/record preserved; formal continuation is LNR-019.
- run `34699837518`: Architecture PASS / Core test compile FAIL because the new Core test used unavailable `kotlinx.coroutines.runBlocking` and JUnit imports. Production code was not the failing surface.
- fix `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5`: test aligned to existing `kotlin.test` + local continuation harness.
- run `34699942180`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- artifact id `10300336311`, digest `sha256:f0e5ab811470a82bcf0b2f402ca252b29b49da6288343c1263cc6c4268ebe245`.
- final LNR-019 exact-head and PR Gate remain pending until documentation closeout is committed.

## Waiting External Test / Honest Gaps
- BLG vs AL online Riot target/EventDetails/LiveStats lifecycle + snapshot behavior still requires Android real-device evidence.
- fixture/CI PASS is not online evidence.
- current snapshot path stores real frames but does not yet derive Kill/Objective/GoldLead events from deltas; unsupported event inference remains forbidden.
- full Android system RiftScreen/overlay, Watch/Player/OTA, AI/OCR and final migration audit remain later work.
- Cito remains DEFERRED.

## Next
Close LNR-019 docs → final exact-head CI/APK → PR Gate/merge → use resulting APK for BLG vs AL. Record real-device result as PASS / DEGRADED / FAIL with visible `LNR-SRC-LIVE-*` diagnostics.
