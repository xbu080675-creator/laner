# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- LNR-016 working branch: `feature/lnr-016-testable-android-platform`
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
| LNR-016 | Testable Android Platform / Riot Global LIVE / APK Delivery | TESTING |

## LNR-016 Current Truth
- LIVE `sources = emptyList()` has been replaced by `RiotGlobalLiveStateSource` as the first global LIVE baseline.
- Target resolution uses canonical teams + scheduled time, then stores Riot external identity only in `ProviderMatchIdentityRepository`.
- `getEventDetails` + LiveStats real-frame probe feed `ProviderLiveObservation`; Application still decides lifecycle.
- canonical GameId remains `GameIdentity.canonical(matchId, gameNumber)`.
- BLG/AL and T1/GEN fixtures use the same target-discovery parser; no LPL/LCK business branch exists.
- Android test build can accept a Riot Key at runtime without recompilation. Temporary key is memory-only and disappears with the app process.
- Public CI artifact contains no Riot credential by default.
- LIVE UI now surfaces stable `LNR-SRC-LIVE-002~005` source diagnostics for device-side debugging without adb.
- CI now uploads `app-debug.apk`; build version is `2.0.0-dev.3` / versionCode 3.

## LNR-016 Verification
- run `34697683655`: Architecture/Core PASS, Android compile FAIL due malformed Compose `when` in runtime credential panel; failure preserved.
- fix head `2cddeb872d7854829b54750db31f8739e37f0d2a`.
- run `34697846793`: Architecture / Domain+Application / Android Adapter unit / Android debug build PASS.
- artifact `laner-debug-2cddeb872d7854829b54750db31f8739e37f0d2a`, id `10299088400`, digest `sha256:86e833a7d7a27c165a37682e0763464b25ff2be9a6c5e9fac577a5a1af48a650`.
- latest head adds visible LIVE diagnostics; final exact-head Gate is pending.

## Waiting External Test / Honest Gaps
- BLG vs AL Riot online target/EventDetails/LiveStats behavior has NOT yet been verified on device.
- Fixture/CI PASS is not online evidence.
- Cito remains DEFERRED and optional.
- Full live gold/kills/objective snapshot ingestion, HUD/RiftScreen, Watch/Player/OTA, AI/OCR and final migration audit are later slices.

## Next
Finish LNR-016 final exact-head Gate → preferred APK artifact → PR Gate/merge → Android real-device BLG vs AL test. Do not report online PASS before that device evidence exists.
