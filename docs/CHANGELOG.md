# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### M1 Feature Migration

#### LNR-017 — Starting Roster Vision / Staged Diagnostics
- Preserved schema-v3 normalized `announcements` instead of dropping official image-post discovery metadata.
- Added Application-owned starting-roster assist stages so the device can distinguish no announcement, announcement discovered, OCR failure, OCR partial, complete-but-unverified OCR and formal normalized evidence availability.
- Added bundled ML Kit Latin/Chinese/Japanese/Korean OCR for deterministic first-run device tests without Play Services model download.
- Added geometry-aware two-column lineup extraction with role anchors and strict player-handle noise filtering.
- Locked truth boundary: OCR authority is `DERIVED`; even complete five-role OCR never becomes Official Starting Roster without normalized date/matchup evidence validation.
- Added PRE 60-second low-frequency roster-assist refresh, manual recheck and visible `LNR-SRC-PRE-010~013` diagnostics.
- Bumped Android test build to `2.0.0-dev.4 / versionCode 4`.
- Preserved run `34700136865` failure (wrong ML Kit Latin options package + Compose cross-module nullable smart cast) and run `34700236839` failure (new app tests incorrectly used `kotlin.test` instead of project JUnit4).
- code head `3879249053832c606f30a6122c31269db9327812` / run `34700457400`: Architecture / Core / App unit / Android build / APK upload PASS; artifact `10300252185`.
- Real official-post discovery, image download, Android OCR and final lineup evidence transition remain `WAITING EXTERNAL TEST` until tonight's device test.

#### LNR-016 — Testable Android Platform / Riot Global LIVE
- Added Riot Global LIVE baseline instead of an empty LIVE source list.
- Added runtime process-memory Riot credential input and direct device-visible LIVE diagnostic codes.
- CI now uploads installable debug APKs after all gates pass.
- BLG/AL and T1/GEN fixtures share the same global discovery/parser path; no regional business branch was added.
- final feature run `34698280239` and PR #8 run `34698393156` passed all gates + APK upload; merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`.
- Real BLG vs AL Riot online/device validation remains `WAITING EXTERNAL TEST`.

#### LNR-015 — Global POST foundation
- Added strongly separated POST facts: Series Result, Completed Game, Player Stats, Awards, Replay Metadata and Historical Timeline; none may silently infer another.
- Added global canonical GameId rule `(canonical MatchId, gameNumber)` and ADR-003; provider event/match/game IDs remain mapping/provenance metadata.
- Added `PostMatchService` with capability-based Global-first source orchestration. Application contains no LPL/LCK/LEC/LCP business branches; regional sources are supplements only.
- Added fallback-only POST archive, provider identity mapping, verified Awards, Riot global Result/Replay and global historical LiveStats Timeline.
- Added POST Compose and explicit per-game historical backfill; no automatic whole-series historical scan.
- Preserved true CI failures and permanent regressions; PR #7 merged as `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca`.
- Real Riot online fetch and Android device POST verification remain `WAITING EXTERNAL TEST`.

#### LNR-014 — LIVE Android infrastructure
- Added Android local LIVE State/Timeline repositories with schema versioning, atomic replacement, corruption/unsupported-schema failure and full event round-trip tests.
- Added `:app:testDebugUnitTest` as a permanent CI Gate.
- Wired LIVE Application truth and Timeline read UI; no realtime source is a valid explicit `UNAVAILABLE` state.
- Cito online REST/WSS validation remains `DEFERRED / WAITING EXTERNAL TEST`.

#### LNR-013 — LIVE Core/Application
- Added authoritative LIVE lifecycle reducer, provider arbitration, standardized state events and provider-neutral Timeline.
- Locked intermission/new-game/stale-order/terminal-series behavior and semantic Timeline dedupe.

#### LNR-010~012 — PRE migration
- Added global competition catalogue/schedule, PRE roster/starting/staff/form/H2H, Tournament Edition/Standings/Qualification foundations with provenance, evidence and honest degradation.

### Migration Foundation
- Locked legacy behavior baseline and established `docs/FEATURE_BASELINE.md` as the complete migration checklist.
- Froze dependency DAG `:core:domain ← :core:application ← :app` and global competition management.
- Established engineering constitution, immutable development records, PRE/LIVE/POST product axis and `Feature = Persona × Match Phase × User Question`.
