# LNR-015 — Global POST Result / Game Archive / Stats / Historical Timeline / Replay

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@a530a8a88d9cc1ee649f0d95732b73e42675510e`
- Feature branch final head: `19002ae01b1b6ebaf604638406fd3081e941f156`
- PR: `#7`
- Merge commit: `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca`
- Final status: `WAITING EXTERNAL TEST`

## Goal / Constitution
Global-first POST migration. Region is a data dimension only; Domain/Application must not fork by LPL/LCK/LEC/LCP. Constitution Preflight and Post-change Compliance Review: `PASS`.

## Delivered
- Strongly separated `SeriesResult / CompletedGameRecord / PlayerStats / Awards / Replay / HistoricalTimeline` facts.
- canonical GameId from `(canonical MatchId, gameNumber)`; ADR-003; provider IDs remain mapping/provenance/Adapter metadata.
- capability-based `PostMatchService`; global sources first, regional sources supplement only.
- fallback-only local POST archive with schema versioning, SHA-256 filenames, atomic replacement and loud corruption/schema failure.
- persistent `ProviderMatchIdentityRepository`.
- verified Awards mirror; no KDA/damage-derived MVP.
- Riot global Series Result and Replay metadata shared across regional/international competitions.
- Riot global historical LiveStats backfill with no interpolation; finished real frames always retained.
- `PostTimelineService` canonical validation and unified LIVE/POST `GameTimeline`; PRE/AI Timeline writes remain forbidden.
- POST Compose: completed-match target only, Result/Awards/Replay state, on-demand G1/G2/... historical Timeline recovery.
- LPL TJStats kept only as optional regional supplement; old embedded auth not migrated.

## Critical invariants
Unknown stat != 0. FINAL winner must agree with score. PARTIAL cannot declare winner. Replay metadata != player implementation. No explicit per-game winner → no `CompletedGameRecord` winner inference from gold/kills/objectives. UI never calls Provider/Repository directly.

## Failure history preserved
- run `34693494001`: Core FAIL because test fixture used illegal `LNR-SRC-POST-TEST`; fixed to legal three-digit ErrorCode without weakening validation. run `34693630753` PASS.
- run `34695777894`: Core FAIL because Domain Timeline still rejected POST factual frames while Application allowed them; fixed Domain contract to allow LIVE/POST and reject PRE/AI. run `34695924994` PASS.

Both failures are permanently documented in `docs/TROUBLESHOOTING.md` and covered by regression tests.

## Verification evidence
- `34693630753` — PASS after ErrorCode fixture correction.
- `34694930111` — Global capability routing PASS.
- `34695412163` — Global identity / Result / Replay PASS.
- `34695924994` — Timeline source-class root-cause fix PASS.
- `34696081645` — final POST code/UI head PASS.
- `34696404045` — governance/test-evidence head PASS.
- `34696868207` — final documentation exact-head PASS.
- PR #7 exact-head run `34696964926` — Architecture / Domain+Application / Android Adapter unit tests / Android debug compile all PASS.
- PR #7 merged as `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca`.

## External tests still required
- credentialed Riot Result/EventDetails/LiveStats online integration;
- Android real-device POST Result/Replay/Timeline verification.

These remain `WAITING EXTERNAL TEST`; automated fixture PASS is not presented as online/device evidence.

## Known follow-ups
Global per-game CompletedGame winner/stat completeness remains `IN PROGRESS` where upstream lacks explicit evidence. Bilibili supplement, BP/items, Timeline event filtering, TeamFight aggregation, Media3/WebView and AI summary remain separate Feature Baseline items.

## Security / performance
Credentials only via external injection; provider IDs do not become canonical IDs; no VOD bytes downloaded/hosted. Historical Timeline is user-triggered per game, serial, bounded by empty-window and 3h caps; opening POST never scans a full series automatically.

## Rollback
Revert merge `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca` plus this documentation closeout if full task rollback is required. POST archive/identity storage is isolated and PRE/LIVE schemas are not migrated by this task.

## Compliance conclusion
Architecture, tests, docs, status, troubleshooting and remote Git evidence are consistent. Automated delivery is complete; external Provider/device certification is pending by design.

**Final: `WAITING EXTERNAL TEST` — merge complete, no unverified online/device claim.**
