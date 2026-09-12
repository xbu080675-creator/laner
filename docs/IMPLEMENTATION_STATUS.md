# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- LNR-019 merge: `64150f8cced159950013e4bdc3bbd9cf794a87b7`
- LNR-020 original merge (PR #10): `967e112d6efcf8e6daa86f0b007cedc39b63b04c`
- LNR-020 original post-merge main Gate: run `34706047380` PASS
- Constitution incident: `INC-LNR-020-001 / CLOSED`
- Compliance remediation merge (PR #12): `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`
- Remediation exact-head Gate: run `34708127194` PASS
- Remediation post-merge main Gate: run `34708285172` PASS
- Remediation closeout merge (PR #13): `e92bb65b2469cbeb23e56a531890945880f99eba`
- Closeout post-merge main Gate: run `34708701976` PASS
- Baseline SHA policy: long-lived status docs record stable task/PR merge anchors, not a self-referential “current main SHA” that becomes stale when the document itself is merged.
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
| LNR-020 | RiftScreen / Draft HUD Android Overlay | WAITING EXTERNAL TEST (functional) / COMPLIANCE PASS |

## LNR-020 Delivered Functional Truth
- RiftScreen is rebuilt on process-level `LanerApplication / LanerAppGraph`; the legacy `MatchSessionStore` architecture was not copied.
- original LNR-020 functional code is merged by PR #10 at `967e112d…`; post-merge main run `34706047380` passed its automated Gate.
- RiftScreen has `MINI / COMPACT / EXPANDED`, drag, close and host-foreground auto-hide/background-show behavior.
- verified Draft HUD activates only from canonical `DRAFT` lifecycle and canonical `DraftChangedEvent` facts.
- schedule order is only left/right until a real side-selection fact exists; role/matchup is not inferred from missing evidence.
- Android-only `DraftHudPreviewSession` is explicitly `LOCAL PREVIEW · NOT FACT` and cannot write Core/repositories/Timeline.
- HUD supports Edit / Lock, per-module drag, scale, alpha, visibility, reset and independent landscape/portrait layout profiles.
- Lock applies real `FLAG_NOT_TOUCHABLE` to the full-screen HUD while the edge dock remains operable.
- `LIVE-024~029` remain `WAITING EXTERNAL TEST`; `LIVE-012` remains TODO because HUD Presentation is not a real Draft Provider.

## LNR-020 Compliance Remediation Truth
`INC-LNR-020-001` confirmed seven engineering-process/architecture deviations. The incident is now closed without rewriting the original task history.

Current chain is:
```text
GlobalScheduleService
→ LiveTargetSelector
→ LiveMatchStateService + LiveSnapshotService
→ canonical GameId selection
→ LiveTimelineService
→ LiveMatchContextService / LiveMatchContextResult
→ Presentation Mapper
→ RiftScreenWindowController / DraftHudWindowController
→ OverlayWindowHost
→ WindowManager
```

Remediation delivered:
- single Application `LiveMatchContextService` so Compose LIVE and Overlay no longer duplicate current-LIVE orchestration;
- typed `NoTarget / Ready / Failed` result and `LNR-APP-LIVE-003` unexpected-failure diagnostics;
- `OverlayWindowHost` with `[Laner:OVERLAY]` and stable `LNR-OVR-WINDOW-001~004` codes instead of silent WindowManager `runCatching`;
- dedicated `RiftScreenWindowController` and `DraftHudWindowController`, reducing `RiftScreenOverlayService` to lifecycle/scheduling/composition duties;
- `LNR-OVR-REFRESH-001` for Presentation mapper failures;
- permanent `LiveMatchContextServiceTest` and `OverlayWindowOperationTest` regressions;
- LNR-020 Failure A/B registered in `TROUBLESHOOTING.md`;
- root README / ARCHITECTURE / PROJECT_SCOPE / plan/status/module docs corrected from stale M0 or in-progress facts to the real M1 state.

Remediation evidence:
- PR #12 final head `249c42208ab6105ad26b78215b47fbd754d889e9`;
- exact-head run `34708127194`: Architecture/Core/App Unit/Android compile/APK upload PASS;
- artifact `10302087059`, digest `sha256:59eca5214f84e4a231a613b90663fd4d30f613629f3128b4f846716ab56a4509`;
- PR #12 merge `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`;
- post-merge main run `34708285172`: Architecture/Core/App Unit/Android compile/APK upload PASS;
- main artifact `10301779315`, digest `sha256:69d61f7bea45a209f56f171e0e9c4f48f24953fdd802b03ade4013851b5a34c4`;
- PR #13 documentation closeout merge `e92bb65b2469cbeb23e56a531890945880f99eba`;
- closeout post-merge main run `34708701976`: Architecture/Core/App Unit/Android compile/APK upload PASS.

**Compliance status: PASS / incident CLOSED.**

## LNR-020 Historical Verification
- foundation run `34704014273`: Architecture/Core PASS; App unit compile FAIL because legacy UI test still referenced deleted private `selectLiveTarget`; failure preserved.
- fix `8b17f94ed75ba417eed017bd4c4d45ba63b5e9a9` aligned the regression with Application `LiveTargetSelector`.
- foundation push run `34704124799` and PR run `34704127811`: Architecture/Core/App Unit/Android build/APK upload all PASS.
- Draft HUD PR run `34705468018`: Architecture/Core and production `compileDebugKotlin` PASS; 35 App tests with one incorrect new-test assertion failure; production implementation was not the failing surface.
- test fix `871e1a0ad257c465dc51720df46fb66cceeae7cc` checks the intended unknown-team isolation property.
- PR run `34705512479`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- original final exact-head feature run `34705831066` / PR run `34705833067` PASS; artifact `10301775664`.
- PR #10 merged to main as `967e112d…`; post-merge run `34706047380` PASS.
- incident and remediation history remain preserved in `docs/development/`; no historical FAIL was rewritten into PASS.

## Waiting External Test / Honest Gaps
- Android system-overlay permission flow cannot be proven by JVM/CI.
- foreground hide / background show, drag/bounds, MINI/COMPACT/EXPANDED and close require real-device verification.
- Draft HUD Edit/Lock, module drag, scale, alpha, visibility, reset and portrait/landscape profile switching require real-device verification.
- Lock touch-through requires verification while interacting with the underlying game/viewer app.
- real verified Draft data requires a real Draft source; `LIVE-012` remains TODO.
- Tactical HUD remains `LIVE-030 TODO` and was not included in remediation.
- `LIVE-014` Kill/MultiKill/TeamFightWindow and `LIVE-015` GoldLeadChange remain TODO.
- BLG vs AL / other live Riot online evidence from LNR-019 also remains external.
- Cito remains DEFERRED.

## Next
`INC-LNR-020-001` 已关闭。Block 2 `Tactical HUD + live event layer` 现在可以在**新的 Constitution Preflight** 后开始；LNR-020 Android 真机证据继续独立回填，不阻塞下一工程切片，也不得被 CI 冒充 PASS。
