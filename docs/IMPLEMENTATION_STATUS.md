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

## LNR-017 Current Truth
- normalized roster schema-v3 `announcements` are no longer discarded.
- raw official announcements remain discovery metadata and cannot become `OfficialStartingRoster` by themselves.
- Application owns `RosterAssistStage` and target/announcement correlation; Android OCR is behind `StartingRosterVisionPort`.
- bundled ML Kit OCR always runs Latin; LPL/LCP/PCS add Chinese, LCK adds Korean, LJL adds Japanese.
- two-column poster parser uses OCR geometry to separate left/right candidates.
- OCR authority is `DERIVED`; even a complete five-role result is explicitly `OCR_COMPLETE_UNVERIFIED` until normalized date/matchup evidence arrives.
- PRE UI has a visible `STARTING ROSTER PIPELINE` panel with 60s polling and manual recheck.
- panel distinguishes no announcement / discovered / OCR failure / partial / complete-unverified / normalized evidence available.
- stable diagnostics: `LNR-SRC-PRE-010~013`.
- build target bumped to `2.0.0-dev.4 / versionCode 4`.

## Verification So Far
- run `34700136865`: Architecture PASS, Domain/Application PASS, Android Adapter/compile phase FAIL.
- preserved root causes: wrong ML Kit Latin options package + cross-module nullable smart-cast in Compose.
- fixes: `ad23d8cf493c773b5ff3d6dd6b07b3333a380171` and `a711dce3947b80405af741d69a8c342b191c121c`.
- latest exact-head CI: pending.

## Existing External Test Boundary
- BLG vs AL Riot LIVE online/device evidence remains pending from LNR-016.
- tonight's actual official starting-roster publication + Android OCR/diagnostic behavior is `WAITING EXTERNAL TEST` until user tests the new APK.
- fixture/CI PASS never equals social-source/real-image/device PASS.

## Next
LNR-017 exact-head Gate → APK artifact → docs/status closure → PR Gate/merge → user real-device lineup test. If the chain fails, diagnose by visible stage/code before changing architecture.
