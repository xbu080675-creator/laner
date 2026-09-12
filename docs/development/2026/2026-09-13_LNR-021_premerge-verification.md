# LNR-021 Pre-merge Verification / Delivery Addendum

- Date: 2026-09-13 Asia/Taipei
- Parent task: `LNR-021 — Tactical HUD / LIVE Event Derivation`
- Baseline: `main@87f90a89ad7a35fdb9717ef1003fa984bba4fdab`
- PR: `#15`
- Verified pre-addendum head: `49c318c5d39c301fb94bcab32a2032b046a913a9`
- Status: `PRE-MERGE VERIFIED / FINAL HEAD GATE REQUIRED AFTER THIS RECORD`

## Why this addendum exists

The primary LNR-021 development record intentionally ended as `DELIVERY INCOMPLETE` because it was written before exact-head push/PR Gates. It is not rewritten into retroactive PASS. This addendum preserves the next verification step and deliberately acknowledges that adding this file creates a new branch head which must itself be re-verified before merge.

## Verified evidence on `49c318c5...`

### Push Gate
- run: `34712292230`
- Architecture boundary: PASS
- Domain/Application tests: PASS
- Android Adapter unit tests: PASS
- Android debug compile: PASS
- APK upload: PASS
- artifact: `10303328840`
- artifact name: `laner-debug-49c318c5d39c301fb94bcab32a2032b046a913a9`
- digest: `sha256:cbf90980552eafa7199730a74fedb60230975a4da8b5a472a27755a9349a497a`

### PR Gate
- PR: `#15 — LNR-021: Tactical HUD and LIVE event derivation`
- run: `34712306844`
- exact head: `49c318c5d39c301fb94bcab32a2032b046a913a9`
- Architecture boundary: PASS
- Domain/Application tests: PASS
- Android Adapter unit tests: PASS
- Android debug compile: PASS
- APK upload: PASS

## Historical failure remains preserved

Failure A is still run `34709821178`: Core passed; Android production compile failed because the existing `LiveMatchScreen.eventLabel()` exhaustive sealed `when` did not include the newly-added `MultiKillWindowEvent` / `TeamFightWindowEvent`. Fix: `403bba4e874ad37978179618e84dceaafb9f06f8`. Troubleshooting entry: `LNR-UI-LIVE-004`.

No failed evidence is rewritten or removed by this addendum.

## Post-change compliance review at verified code/doc state

- dependency DAG `:core:domain ← :core:application ← :app`: PASS
- Core platform/network imports: PASS via Architecture Gate
- LIVE event business derivation located in Application, not Presentation: PASS
- Presentation consumes canonical MatchEvent/LiveMatchContext truth: PASS
- raw Provider payload reaches Tactical HUD: NO
- Region-specific business branch added: NO
- killer/victim invented from team count delta: NO
- official multi-kill classification invented: NO
- dragon subtype/soul/elder inferred from total count: NO
- local Preview enters Core/Repository/Timeline: NO
- WindowManager direct/silent exception path added: NO; Tactical uses `OverlayWindowHost`
- Timeline schema migration present: PASS (`v1` read compatibility, `v2` writes)
- generated-event reconciliation preserves non-generator facts: PASS by tests
- stale Tactical card protection: PASS in JVM logic (25s game-time + 30s wall-clock); device evidence remains external
- Feature Baseline non-target status integrity: PASS (`LIVE-009` stays IN PROGRESS, `LIVE-012` stays TODO)
- historical failure synchronized to Troubleshooting: PASS
- Android real-device/real-Riot claims kept `WAITING EXTERNAL TEST`: PASS

## Final-head rule

This addendum itself advances the feature branch. Therefore `49c318c5...` is no longer the final merge candidate once this commit lands. The new exact head MUST pass both:
1. branch push Gate;
2. PR #15 Gate.

No further branch file changes are allowed after those two final-head Gates pass, except a failure-specific repair which would invalidate the head and require new Gates again.

## Fixed delivery continuation

1. **Task / status**: LNR-021; functional target `WAITING EXTERNAL TEST`; pre-merge verification PASS.
2. **Baseline / verified head**: `main@87f90a89...` / `49c318c5...`; final addendum head pending Gate.
3. **Preflight**: PASS, recorded in parent task.
4. **Modules**: Domain / Application / App / docs.
5. **Constitution clauses**: §0, §1/1.1, §2, §3, §7, §8, §11, §13, §15.
6. **Files**: parent task exact list + this addendum; no deletions.
7. **Design**: canonical snapshot → Application derivation → canonical events → display-only Tactical HUD.
8. **Tests**: Core/Application, Adapter persistence migration, Presentation boundaries/freshness; device external.
9. **Gate evidence**: push `34712292230` PASS; PR `34712306844` PASS; final-head Gates pending due to this addendum.
10. **Fault evidence**: Failure A / `LNR-UI-LIVE-004` preserved.
11. **Impact**: LIVE derived event layer + Timeline v2 + Tactical HUD only.
12. **Known gaps**: real Riot online / Android device, Herald/Atakhan, explicit official kill classifications.
13. **Rollback**: revert task merge; v2 timeline cache requires compatibility handling if downgrading to v1-only APK.
14. **Status sync**: Baseline/Plan/Status/Troubleshooting/Changelog/module READMEs synced.
15. **PR**: #15 Draft; not merged.
16. **Post-change Compliance Review**: PASS for `49c318c5...`; final-head re-verification mandatory.
17. **Conclusion**: `READY FOR FINAL-HEAD GATE / NOT YET MERGE-COMPLETE`.
