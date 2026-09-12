# LNR-019 — Global LIVE Snapshot / Timeline / Match HUD

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
- Working branch: `feature/lnr-019-live-snapshot-hud`
- Status: `IN PROGRESS`

## Administrative correction
Initial work was mistakenly started as `LNR-017` on branch `feature/lnr-017-live-snapshot-hud`. Repository planning already reserves:
- `LNR-017` = Local AI / OCR / Roster Assist
- `LNR-018` = Compatibility / Full Regression / Migration Audit

To avoid rewriting history, the original branch/record is preserved. Code head `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5` is continued unchanged here as `LNR-019`. No functional implementation was discarded or rewritten for the renumbering.

Historical CI before correction:
- run `34699837518` on old LNR-017 branch: Architecture PASS, Core test compile FAIL because new test incorrectly used unavailable `kotlinx.coroutines.runBlocking` / JUnit APIs.
- fix `f86fb251bebd5cb8f9b8791f36f5e1809de0c7b5`: test converted to existing Core `kotlin.test` + local coroutine continuation harness.
- run `34699942180`: Architecture / Domain+Application / Android Adapter unit / Android build / APK upload all PASS.

## Goal
Prioritize tonight's real match validation path:
`Global Riot LIVE real frame → canonical LiveGameSnapshot → Application validation → GameTimeline → LIVE visible metrics`.

Global-first remains mandatory; Region is only a data dimension.

## Constitution Preflight
`PASS` after task-ID correction. Latest constitution, main baseline, implementation status, LNR-016 delivery, LIVE Domain/Application/Adapter/UI and Riot historical frame parser were reviewed.

## Acceptance
1. Riot live frame normalizes team gold/kills/towers/dragons/barons and player level/KDA/CS/gold/champion.
2. Provider IDs never become canonical Match/Game IDs.
3. Application revalidates canonical MatchId/GameId/gameNumber/team identity before Timeline persistence.
4. Lifecycle authority remains `LiveMatchStateService`; snapshot ingestion must not mutate lifecycle.
5. Missing values remain unknown and are not rendered as zero.
6. Same implementation is region-neutral.
7. Full CI + APK artifact + PR Gate before merge.
8. Fixture/CI evidence remains distinct from online Android device evidence.

## Implemented so far
- `LiveSnapshotSourcePort / ProviderLiveSnapshot / LiveSnapshotResolution`.
- `LiveSnapshotService` with `LNR-APP-LIVE-002` canonical identity/team validation and Timeline ingestion.
- `RiotGlobalLiveSnapshotSource` with `LNR-SRC-LIVE-006~007`.
- Riot EventDetails provider identity → active game → LiveStats last real frame → canonical `LiveGameSnapshot`.
- Team metrics and Player live rows preserve nullable semantics.
- Composition wiring through `LanerAppGraph → MainActivity → LanerRoot → LiveMatchScreen`.
- Full-width `GAME DATA / 实时真帧` UI with gold difference only when both gold values exist.
- Application tests for valid ingest / wrong GameId / wrong teams.
- Adapter fixture tests for canonical parsing, missing-vs-zero, unknown team rejection.

## Non-goals
AI Insight, full draggable Android overlay editor, unsupported fact inference, Cito online verification.

## Rollback
Current feature branch is isolated from main. Existing Timeline schema is reused; no data migration is introduced.
