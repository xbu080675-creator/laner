# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- LNR-017 branch: `feature/lnr-017-starting-roster-vision`
- LNR-017 baseline: `b4904cae43b9e296e6badf989ef1769263a455fa`
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
| LNR-017 | Starting Roster Vision / Staged Diagnostics | TESTING |

## LNR-017 Delivered Code Truth
- normalized schema-v3 `announcements` are preserved instead of discarded；
- official announcement discovery remains distinct from official starting-roster fact；
- Application owns `RosterAssistStage` and correlation; Android OCR stays behind `StartingRosterVisionPort`；
- bundled ML Kit: Latin + LPL/LCP/PCS Chinese + LCK Korean + LJL Japanese；
- two-column geometry extraction separates left/right candidates；
- OCR authority is always `DERIVED`；five-role completeness remains `OCR_COMPLETE_UNVERIFIED`；
- normalized formal evidence still goes through existing `PreMatchContextService` validation；
- PRE UI exposes a 60s low-frequency `STARTING ROSTER PIPELINE` card + manual recheck；
- visible diagnostics `LNR-SRC-PRE-010~013`；
- test build `2.0.0-dev.4 / versionCode 4`。

## Verification
- `34700136865`: Architecture/Core PASS, App phase FAIL；wrong ML Kit Latin package + Compose cross-module nullable smart cast；failure preserved。
- fixes `ad23d8cf493c773b5ff3d6dd6b07b3333a380171` + `a711dce3947b80405af741d69a8c342b191c121c`。
- `34700236839`: Architecture/Core PASS，production app compile passed，App unit compile FAIL because new tests used undeclared `kotlin.test` API；failure preserved。
- fixes `5d763c34301858293ceef6b5257077dc9b866ce3` + `3879249053832c606f30a6122c31269db9327812`。
- code head `3879249053832c606f30a6122c31269db9327812` / run `34700457400`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS。
- artifact `10300252185`, `laner-debug-3879249053832c606f30a6122c31269db9327812`, digest `sha256:91bc80ef7988ee7687b2a0bb5e024317fd702679b5daaa00af41b9edb580f241`。
- docs exact-head / PR Gate / merge: pending。

## External Test Boundary
- BLG vs AL Riot LIVE online/device evidence remains pending from LNR-016；
- tonight's actual official lineup publication, image download, Android OCR, stage transitions and final normalized evidence behavior remain `WAITING EXTERNAL TEST`；
- fixture/CI PASS never equals social-source/real-image/device PASS。

## Next
final docs exact-head Gate → PR Gate → merge → user real-device lineup test。If real test fails, use visible stage/code to locate discovery vs image vs OCR vs evidence-validation break before changing architecture。
