# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### M1 Feature Migration

#### LNR-021 — Tactical HUD / LIVE Event Derivation
- Added deterministic `LiveEventDerivationService` on canonical Timeline snapshots; Presentation does not derive match facts.
- Added conservative aggregate Kill delta, player-backed kill delta, `MultiKillWindowEvent`, `TeamFightWindowEvent`, Tower/Dragon/Baron delta and `GoldLeadChangedEvent`.
- Missing evidence remains missing: no invented killer/victim pairing, no dragon subtype/soul/elder inference, no fake HP/cooldown/position data.
- `INC-LNR-021-001` remediation tightened TeamFight derivation: both team kill deltas must be known before a TeamFightWindow can exist; an unknown side is never rewritten as zero.
- Added permanent factual-safety regressions for one-sided unknown counters, team counter regression, player counter regression and missing player rows.
- Added `LiveTimelineService.reconcileGeneratedEvents` so late/out-of-order or stronger same-second snapshots can replace only Laner-generated derived events while preserving Provider explicit / Draft / lifecycle events.
- Upgraded LIVE Timeline persistence to schema v2 for the new event fields/types while retaining v1 read compatibility and write-forward migration.
- `INC-LNR-021-001` remediation preserves and verifies the exact pre-upgrade v1 payload in a sibling `.schema-v1.bak`; source and recovery are fully decoded and canonical GameId-checked before v2 replacement. Wrong identity, malformed v1, or mismatched recovery blocks overwrite.
- Added Tactical HUD Presentation/View/WindowController and preserved visible overlay priority `Draft > Tactical > RiftScreen`.
- Tactical Verified cards use 25s game-time TTL plus 30s wall-clock freshness so a frozen game clock after provider loss cannot leave a stale tactical card permanently visible.
- Added Android-only Tactical Preview marked `LOCAL PREVIEW · NOT FACT`; it never enters Core, source arbitration, repositories or Timeline.
- Preserved Failure A: run `34709821178` passed Architecture/Core but failed Android production compile because `LiveMatchScreen.eventLabel()` had not yet exhausted the expanded `MatchEvent` sealed hierarchy. Fix `403bba4e874ad37978179618e84dceaafb9f06f8` added the missing `MultiKillWindowEvent / TeamFightWindowEvent` branches without hiding future omissions behind a catch-all `else`.
- Implementation baseline head `b83a83c0c8908b8da1755d306958352fbfe389cf` / run `34710012697` passed Architecture/Core/App Unit/Android compile/APK upload and produced artifact `10303071228` (`sha256:1e0d24bd8e5423216442a97d70c6307f128a93f22476e462b9942e62da906d6f`).
- Original delivery final push `34712441374`, PR `34712444119`, and post-merge main `34712560708` all passed; those historical Gates do not replace the independent constitution remediation Gate.
- Constitution remediation PR #17 exact head `a62df876bf21f6009ea4a7fc1c9227fd48e219b1` passed run `34732025919`; merge `2d7ff40034a8e834f20c02bef41f8c088e98f479` passed post-merge main run `34732179957` with Architecture / Domain+Application / Android Adapter Unit / Android debug compile / APK upload all green.
- Independent post-remediation review found the six confirmed `INC-LNR-021-001` deviations remediated with no new confirmed violation in scope. Incident certification is `CLOSURE READY`; formal `CLOSED` waits for the docs-only closeout PR and its post-merge main Gate.
- `LIVE-014 / LIVE-015 / LIVE-030` remain `WAITING EXTERNAL TEST`; real Riot online event triggering and Android overlay behavior are not claimed PASS from CI.

#### LNR-020 — RiftScreen / Draft HUD Android Overlay
- Rebuilt the legacy RiftScreen behavior on process-level Application services instead of copying the legacy `MatchSessionStore` architecture.
- Added system-overlay Foreground Service presentation using canonical schedule/lifecycle/snapshot/Timeline truth only.
- Restored RiftScreen `MINI / COMPACT / EXPANDED`, drag, close, foreground auto-hide and background presentation behavior.
- Added a full-screen Draft HUD driven only by canonical `DRAFT` lifecycle and `DraftChangedEvent` facts; provider payloads never reach the HUD.
- Added persistent Edit/Lock layout controls: module drag, scale, alpha, visibility, reset and independent portrait/landscape profiles.
- Lock mode now applies real `FLAG_NOT_TOUCHABLE` to the full-screen HUD while keeping the edge control dock usable.
- Added an Android-only Draft HUD preview fixture for layout testing. It is explicitly `LOCAL PREVIEW · NOT FACT` and cannot enter Core, repositories or Timeline.
- Kept side-selection and role/matchup uncertainty honest: schedule order is left/right only, and absent role/matchup evidence is not inferred.
- Preserved CI failure history: the first foundation run exposed an obsolete UI-private target-selector test; later the Draft HUD run exposed one incorrect test assertion. Both were fixed without hiding the failed runs.
- PR run `34705512479` at code head `871e1a0ad257c465dc51720df46fb66cceeae7cc` passed Architecture / Core / App Unit / Android build / APK upload and produced artifact `10301324396` (`sha256:ac2953bc1a29ebaead91958a689909e4bae6c3374a795d5ab3956398ec81d424`).
- Real Android system-overlay permission/drag/orientation/touch-through behavior remains `WAITING EXTERNAL TEST`; CI PASS is not device evidence.

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
- Real Android online data remains `WAITING EXTERNAL TEST`; fixture/CI PASS is not online evidence.

#### LNR-016 — Testable Android Global LIVE baseline
- Added first real Global Riot lifecycle source, provider identity discovery, runtime memory-only Riot Key input, on-device LIVE diagnostics and CI debug-APK artifact delivery.
- Final feature/PR Gates passed and PR #8 merged; online Android evidence remains external.

#### LNR-015 — Global POST foundation
- Added strongly separated POST facts, canonical GameId, capability-based Global-first source orchestration, fallback-only archive and provider identity mapping.
- Added verified Awards, Riot global Result/Replay, Riot historical LiveStats Timeline and POST UI/on-demand historical recovery.
- Preserved honest gaps: global per-game winner/stat completeness remains IN PROGRESS without explicit evidence.
- Historical CI failures and permanent regressions remain archived in `docs/TROUBLESHOOTING.md`.

#### LNR-014 — LIVE Android infrastructure
- Added Android local LIVE State/Timeline repositories with schema versioning, atomic replacement, corruption/schema failure and Adapter unit-test Gate.
- Cito online remains externally unverified.

#### LNR-013 — LIVE Core/Application
- Added authoritative LIVE lifecycle reducer, provider arbitration, standardized events and provider-neutral Timeline.
- Locked intermission/new-game/stale-order/terminal-series behavior and semantic Timeline dedupe.

#### LNR-010~012 — PRE migration
- Added global competition catalogue/schedule, PRE roster/starting/staff/form/H2H, Tournament Edition/Standings/Qualification foundations with provenance and honest degradation.

### Migration Foundation
- Locked legacy behavior baseline and `docs/FEATURE_BASELINE.md` migration checklist.
- Froze dependency DAG `:core:domain ← :core:application ← :app` and global competition management.
- Established engineering constitution, immutable development records, PRE/LIVE/POST product axis and `Feature = Persona × Match Phase × User Question`.
