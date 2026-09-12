# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### M1 Feature Migration

#### LNR-015 — Global POST foundation
- Added strongly separated POST facts: Series Result, Completed Game, Player Stats, Awards, Replay Metadata and Historical Timeline; none may silently infer another.
- Added global canonical GameId rule `(canonical MatchId, gameNumber)` and ADR-003; provider event/match/game IDs remain mapping/provenance metadata.
- Added `PostMatchService` with capability-based Global-first source orchestration. Application contains no LPL/LCK/LEC/LCP business branches; regional sources are supplements only.
- Added fallback-only `PostMatchArchiveRepository` and Android atomic JSON persistence. Archive fills missing facts but does not vote against fresh providers or overwrite last-good on conflict.
- Added `ProviderMatchIdentityRepository` for canonical↔provider identity mapping.
- Added verified Awards mirror; MVP/POG cannot be derived from KDA/damage.
- Added Riot global Series Result baseline and Riot global Replay metadata shared by regional and international competitions; LCK and Worlds fixtures exercise the same parsers.
- Added Riot global historical LiveStats Timeline recovery. Only real Riot frames are retained; no interpolation. Finished frames are preserved even outside normal sampling cadence.
- Reused canonical `GameTimeline` for LIVE and POST facts through `PostTimelineService`; POST wrong Match/Game identity is rejected before persistence.
- Added POST UI for completed-match selection, verified Awards, Replay metadata and explicit per-game historical Timeline recovery. Opening POST does not automatically scan all historical windows.
- Kept LPL TJStats as an optional regional supplement with externally injected credential; legacy hard-coded auth was not migrated.
- Preserved honest gaps: global per-game CompletedGame winner/stat completeness remains IN PROGRESS where no explicit winner evidence exists; economic/kills/towers never infer winner.
- CI failure `34693494001` exposed an invalid test ErrorCode fixture; fixed without weakening ErrorCode validation, run `34693630753` PASS.
- CI failure `34695777894` exposed a Domain/Application Timeline source-class mismatch; Domain now accepts LIVE/POST factual Timeline sources while rejecting PRE/AI. run `34695924994` PASS.
- Global capability routing run `34694930111` PASS; global identity/result/replay run `34695412163` PASS; final POST code/UI head run `34696081645` PASS.
- Real Riot credentialed online fetch and Android device POST verification remain `WAITING EXTERNAL TEST`; automated fixtures are not reported as online evidence.

#### LNR-014 — LIVE Android infrastructure
- Added Android local LIVE State/Timeline repositories with schema versioning, atomic replacement, corruption/unsupported-schema failure and full event round-trip tests.
- Added `:app:testDebugUnitTest` as a permanent CI Gate.
- Wired LIVE Application truth and Timeline read UI; no realtime source is a valid explicit `UNAVAILABLE` state.
- Cito online REST/WSS validation remains `DEFERRED / WAITING EXTERNAL TEST` by current external test constraints.

#### LNR-013 — LIVE Core/Application
- Added authoritative LIVE lifecycle reducer, provider arbitration, standardized state events and provider-neutral Timeline.
- Locked intermission/new-game/stale-order/terminal-series behavior and semantic Timeline dedupe.
- Historical failure and regression evidence is retained in `docs/TROUBLESHOOTING.md` and the immutable task record.

#### LNR-010~012 — PRE migration
- Added global competition catalogue/schedule, PRE roster/starting/staff/form/H2H, Tournament Edition/Standings/Qualification foundations with provenance, evidence and honest degradation.

### Migration Foundation
- Locked legacy behavior baseline and established `docs/FEATURE_BASELINE.md` as the complete migration checklist.
- Froze dependency DAG `:core:domain ← :core:application ← :app` and global competition management.
- Established engineering constitution, immutable development records, PRE/LIVE/POST product axis and `Feature = Persona × Match Phase × User Question`.
