# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- LNR-019 merge: `64150f8cced159950013e4bdc3bbd9cf794a87b7`
- LNR-020 PR: `#10 / feature/lnr-020-riftscreen-overlay`
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
| LNR-020 | RiftScreen / Draft HUD Android Overlay | WAITING EXTERNAL TEST |

## LNR-020 Delivered Truth
- RiftScreen is rebuilt on process-level `LanerApplication / LanerAppGraph`; the legacy `MatchSessionStore` architecture was not copied.
- overlay facts flow through `GlobalScheduleService → LiveTargetSelector → LiveMatchStateService + LiveSnapshotService → LiveTimelineService → Presentation → WindowManager`.
- RiftScreen has `MINI / COMPACT / EXPANDED`, drag, close and host-foreground auto-hide/background-show behavior.
- verified Draft HUD activates only from canonical `DRAFT` lifecycle and canonical `DraftChangedEvent` facts.
- schedule order is only left/right until a real side-selection fact exists; role/matchup is not inferred from missing evidence.
- Android-only `DraftHudPreviewSession` is explicitly `LOCAL PREVIEW · NOT FACT` and cannot write Core/repositories/Timeline.
- HUD supports Edit / Lock, per-module drag, scale, alpha, visibility, reset and independent landscape/portrait layout profiles.
- Lock applies real `FLAG_NOT_TOUCHABLE` to the full-screen HUD while the edge dock remains operable.
- `LIVE-024~029` advance to `WAITING EXTERNAL TEST`; `LIVE-012` remains TODO because HUD Presentation is not a real Draft Provider.

## LNR-020 Verification
- foundation run `34704014273`: Architecture/Core PASS; App unit compile FAIL because legacy UI test still referenced deleted private `selectLiveTarget`; failure preserved.
- fix `8b17f94ed75ba417eed017bd4c4d45ba63b5e9a9` aligned the regression with Application `LiveTargetSelector`.
- foundation push run `34704124799` and PR run `34704127811`: Architecture/Core/App Unit/Android build/APK upload all PASS.
- Draft HUD PR run `34705468018`: Architecture/Core and production `compileDebugKotlin` PASS; 35 App tests with one incorrect new-test assertion failure; production implementation was not the failing surface.
- test fix `871e1a0ad257c465dc51720df46fb66cceeae7cc` checks the intended unknown-team isolation property.
- PR run `34705512479`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- artifact `10301324396`, digest `sha256:ac2953bc1a29ebaead91958a689909e4bae6c3374a795d5ab3956398ec81d424`.
- documentation closeout is committed on the feature branch; final exact-head PR Gate must also remain green before merge.

## Waiting External Test / Honest Gaps
- Android system-overlay permission flow cannot be proven by JVM/CI.
- foreground hide / background show, drag/bounds, MINI/COMPACT/EXPANDED and close require real-device verification.
- Draft HUD Edit/Lock, module drag, scale, alpha, visibility, reset and portrait/landscape profile switching require real-device verification.
- Lock touch-through requires verification while interacting with the underlying game/viewer app.
- real verified Draft data requires a real Draft source; `LIVE-012` remains TODO.
- Tactical HUD is intentionally not part of LNR-020 and remains `LIVE-030 TODO` for the next engineering block.
- BLG vs AL / other live Riot online evidence from LNR-019 also remains external.
- Cito remains DEFERRED.

## Next
Merge LNR-020 only after the final exact-head PR Gate is green. Then move to block 2: Tactical HUD + live event layer, while Android real-device LNR-020 evidence can be backfilled independently.
