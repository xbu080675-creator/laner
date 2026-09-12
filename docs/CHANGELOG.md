# Laner Changelog

All notable project changes must be recorded here at release or milestone level. Per-task detail lives in `docs/development/`.

## Unreleased

### M1 Feature Migration
- Implemented the first real PRE_MATCH data slice (`LNR-010`): global competition catalogue and schedule.
- Added pure-domain `CompetitionKind`, `ScheduleState`, `CompetitionCatalogEntry`, `ScheduledTeam`, and `ScheduledSeries` models.
- Added `GlobalPreMatchSourcePort` and `GlobalScheduleService`; Provider payloads are normalized and arbitrated in Application instead of UI/Adapter-owned global stores.
- Preserved the legacy safeguard that a premature provider `completed` flag cannot finish a series without BO-winning score or winner evidence.
- Kept schedule-level `EVENT_LIVE` separate from Match Lifecycle `IN_GAME`; PRE UI explicitly says “赛事已开始 · 不代表游戏开局”.
- Added cross-source schedule deduplication using global competition/team identity, time tolerance, authority, timestamp and revision ordering.
- Added explicit PRE source states `READY / DEGRADED / UNAVAILABLE`; catalogue/pagination sub-failures preserve already acquired real schedule facts.
- Added Riot LoL Esports PRE Adapter for `getLeagues` and global `getSchedule` pagination.
- Removed the legacy pattern of repository-embedded LoL Esports credential from the migration path. Laner accepts only `LOL_ESPORTS_API_KEY` environment injection or `lolEsportsApiKey` Gradle property; missing configuration returns `LNR-SRC-PRE-001` rather than inventing data.
- Added `[Laner:SRC] / [Laner:PRE]` diagnostics and `LNR-SRC-PRE-001~004` failure mapping.
- Added real PRE Compose UI for global competition filtering, local-time schedule rendering, source status/provenance and manual resync; no mock match data is used.
- Added schedule normalization regression tests. GitHub Actions run `34687580424` passed architecture boundary, Domain/Application tests and Android debug compilation.
- LNR-010 remains `WAITING EXTERNAL TEST` until a credentialed online fetch and Android real-device display are verified.
- Archived 2026-09-12 legacy real-device evidence that intermission vs actual new-game start recognition is already good behavior and must survive LIVE migration.

### Migration Foundation
- Locked the legacy behavior baseline to `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43` and verified that current source is `1.0.0-dev.94`, newer than the stale README baseline.
- Added `docs/FEATURE_BASELINE.md` as the complete migration checklist for PRE / LIVE / POST / shared platform capabilities and future Sandbox commitments.
- Added `docs/LEGACY_ARCHITECTURE_AUDIT.md`; old RiftLab remains the behavior reference but its large `:app` / global Store / mixed Provider structure is not copied.
- Added `docs/ARCHITECTURE_FREEZE.md` and froze `:core:domain ← :core:application ← :app` as the initial dependency DAG.
- Initialized the real Kotlin/Android multi-module project.
- Added pure Kotlin Match Lifecycle, global IDs, source provenance/authority/freshness/revision, factual candidate and AI evidence contracts.
- Added match/game/live state models and first standardized match events.
- Added Application source/repository Ports and the single global `FactArbiter` with explicit conflict handling.
- Added Unit Tests for lifecycle mapping, AI/fact isolation, freshness and source arbitration.
- Added the first Android Compose shell organized by PRE_MATCH / LIVE_MATCH / POST_MATCH. It intentionally shows honest migration/empty states instead of mock match facts.
- Preserved `com.riftlab.app` as the Android applicationId for compatibility planning while using `com.laner.app` as the new namespace.
- Added module READMEs and GitHub Actions gates for Core tests and Android debug compilation.

### Product Foundation
- Established mandatory engineering constitution and immutable per-task development records.
- Defined the project as a function-preserving, bottom-up architectural rewrite.
- Defined `PRE_MATCH / LIVE_MATCH / POST_MATCH` as the first-level product axis.
- Defined `Feature = Persona × Match Phase × User Question` for `SPECTATOR` and `COACH_ANALYST`.
- Defined `Minimalist + Esports-Cool` UX principles with clarity and non-interference taking precedence over decoration.
- Defined source classes `PRE_MATCH_SOURCE`, `LIVE_MATCH_SOURCE`, `POST_MATCH_SOURCE`, `GLOBAL_AI_ASSIST` and separated factual authority from AI assistance.
- Replaced region-silo architecture with global competition management; Region is a domain attribute/filter, not a business-module boundary.
