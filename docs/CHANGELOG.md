# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### M1 Feature Migration

#### LNR-019 — Global LIVE Snapshot / Timeline / Match HUD
- Added provider-neutral `LiveSnapshotSourcePort` and `LiveSnapshotService`, deliberately separate from lifecycle authority.
- Added Riot global LiveStats gameplay-frame adapter using canonical provider identity mapping + EventDetails active game + latest real LiveStats frame.
- Standardized team gold/kills/towers/dragons/barons and player level/KDA/CS/gold/champion into canonical `LiveGameSnapshot` while preserving missing fields as null.
- Application revalidates canonical MatchId/GameId/team identity and `IN_GAME` snapshot semantics before Timeline persistence; invalid facts use `LNR-APP-LIVE-002` and cannot poison the repository.
- Real LIVE snapshots now reuse the canonical `GameTimeline`; no second LIVE store was introduced.
- Added full-width LIVE `GAME DATA / 实时真帧` UI with team comparison, conditional gold difference, player rows, source diagnostics and explicit unknown values.
- Added source errors `LNR-SRC-LIVE-006~007`; lifecycle errors remain independent.
- No LPL/LCK business branch was added; the same source/application path is global.
- Initial task was accidentally numbered LNR-017, colliding with the reserved AI/OCR task. Historical branch/record is preserved and formal delivery continues as LNR-019.
- run `34699837518` preserved a real test-harness failure: Core test used unavailable coroutine/JUnit dependencies; fix `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5` aligned it with the existing Core test harness without changing production code.
- run `34699942180` then passed Architecture / Domain+Application / Android Adapter unit / Android build / APK upload and produced artifact `10300336311`.
- Real BLG vs AL Android online data remains `WAITING EXTERNAL TEST`; fixture/CI PASS is not online evidence.

#### LNR-016 — Testable Android Global LIVE baseline
- Added first real Global Riot lifecycle source, provider identity discovery, runtime memory-only Riot Key input, on-device LIVE diagnostics and CI debug-APK artifact delivery.
- Final feature/PR Gates passed and PR #8 merged; online BLG vs AL evidence remains external.

#### LNR-015 — Global POST foundation
- Added strongly separated POST facts, canonical GameId, capability-based Global-first source orchestration, fallback-only archive and provider identity mapping.
- Added verified Awards, Riot global Result/Replay, Riot historical LiveStats Timeline and POST UI/on-demand historical recovery.
- Preserved honest gaps: global per-game winner/stat completeness remains IN PROGRESS without explicit evidence.
- Historical CI failures and permanent regressions remain archived in `docs/TROUBLESHOOTING.md`.

#### LNR-014 — LIVE Android infrastructure
- Added Android local LIVE State/Timeline repositories with schema versioning, atomic replacement, corruption/schema failure and Adapter unit-test Gate.
- Cito online remains `DEFERRED / WAITING EXTERNAL TEST`.

#### LNR-013 — LIVE Core/Application
- Added authoritative LIVE lifecycle reducer, provider arbitration, standardized events and provider-neutral Timeline.
- Locked intermission/new-game/stale-order/terminal-series behavior and semantic Timeline dedupe.

#### LNR-010~012 — PRE migration
- Added global competition catalogue/schedule, PRE roster/starting/staff/form/H2H, Tournament Edition/Standings/Qualification foundations with provenance and honest degradation.

### Migration Foundation
- Locked legacy behavior baseline and `docs/FEATURE_BASELINE.md` migration checklist.
- Froze dependency DAG `:core:domain ← :core:application ← :app` and global competition management.
- Established engineering constitution, immutable development records, PRE/LIVE/POST product axis and `Feature = Persona × Match Phase × User Question`.
