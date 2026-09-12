# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- Current closeout: `7eecba27c33d8c755287bf51737d1488e0b1961d`
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

## LNR-016 Delivered Truth
- `RiotGlobalLiveStateSource` is the first real global LIVE baseline; LIVE no longer has an empty source list.
- target discovery uses canonical teams + scheduled time and persists provider identity only in `ProviderMatchIdentityRepository`.
- EventDetails + LiveStats real-frame probe produce `ProviderLiveObservation`; Application remains lifecycle authority.
- canonical GameId remains Laner-owned.
- BLG/AL and T1/GEN fixtures use the same parser; no region business branch was introduced.
- Android test build supports runtime Riot Key input without recompilation; the temporary key exists only in process memory and disappears on process exit.
- LIVE UI shows `LNR-SRC-LIVE-002~005` diagnostics directly on device.
- CI uploads installable debug APK; build `2.0.0-dev.3 / versionCode 3`.

## Verification
- `34697683655` FAIL: Architecture/Core PASS, Android compile FAIL due malformed Compose `when`; failure preserved.
- fix `2cddeb872d7854829b54750db31f8739e37f0d2a`.
- `34697846793` PASS: first installable APK.
- final feature head `9ddaab98ee430360865ca36484bc7fede42d7fe6` / run `34698280239`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.
- preferred artifact id `10299341967`, digest `sha256:9c9b883ef32bf08d0a8e841789807d0b421f32052032a21471d972ac3e1a81aa`.
- extracted APK SHA-256 `e6dc6bdf5e802a1d006de8ac8e3b134b5779e436ef7f9e0b4990350540204bf7`.
- PR #8 run `34698393156`: all gates + APK upload PASS.
- merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`.

## Waiting External Test / Honest Gaps
- BLG vs AL online Riot target/EventDetails/LiveStats behavior still requires Android real-device evidence.
- CI/fixture PASS is not online evidence.
- Cito remains DEFERRED.
- full live gold/kills/objective Timeline ingestion, RiftScreen/HUD, Watch/Player/OTA, AI/OCR and final migration audit remain later work.

## Next
Use the preferred APK for BLG vs AL. Record device result as PASS / DEGRADED / FAIL and use visible `LNR-SRC-LIVE-*` code to locate any failure before changing architecture.
