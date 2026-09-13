# LNR-021 Constitution Closeout — INC-LNR-021-001

- Task: `LNR-021-COMPLIANCE-CLOSEOUT`
- Incident: `INC-LNR-021-001`
- Date: 2026-09-13 Asia/Taipei
- Executor: OpenAI / ChatGPT with GitHub connector
- Baseline: `main@2d7ff40034a8e834f20c02bef41f8c088e98f479`
- Branch: `docs/lnr-021-constitution-closeout`
- Scope: documentation-only compliance certification and Block 2 engineering freeze
- Current delivery state: `DELIVERY INCOMPLETE` until this closeout PR and its post-merge main Gate complete

## 1. Trigger and purpose

User authorized remediation after the independent Block 2 constitution audit recorded `INC-LNR-021-001` with six confirmed deviations. PR #17 has since merged the code/test/document remediation into main. This closeout task does not change production behavior; it certifies the remediation evidence, synchronizes current authority documents, and decides whether Block 2 may be engineering-frozen before Block 3 begins.

Historical records remain immutable. The original incident record keeps its audit-time `OPEN / REMEDIATION REQUIRED` wording; current incident status is established by this closeout record plus current authority status documents.

## 2. Fresh closeout Preflight

Re-read against `main@2d7ff40034a8e834f20c02bef41f8c088e98f479`:
- `docs/ENGINEERING_CONSTITUTION.md` (`1.0.0-laner.1`);
- `docs/PROJECT_SCOPE.md`;
- `docs/DEVELOPMENT_PLAN.md`;
- `docs/IMPLEMENTATION_STATUS.md`;
- `docs/TESTING.md`;
- `docs/TROUBLESHOOTING.md`;
- `docs/COMPATIBILITY.md`;
- `docs/CHANGELOG.md`;
- `docs/development/2026/2026-09-13_LNR-021_违宪事故记录.md`;
- `docs/development/2026/2026-09-13_LNR-021_constitution-remediation.md`;
- current `LiveEventDerivationService` and factual-safety regression tests;
- current `JsonLiveTimelineRepository` and migration/recovery regression tests;
- PR #17 merge and post-merge workflow evidence.

Preflight conclusion: `PASS`.

## 3. Stable remediation anchors

- audit baseline: `main@c73d551925ba273d4caf6259ef621100c105b5de`;
- remediation PR: `#17 — LNR-021: constitution remediation`;
- remediation exact head: `a62df876bf21f6009ea4a7fc1c9227fd48e219b1`;
- exact-head workflow: `34732025919` — Architecture / Domain+Application / Android Adapter Unit / Android debug compile / APK upload all `PASS`;
- exact-head artifact: `10309592107`;
- exact-head artifact digest: `sha256:5d99273cd141ecd3e374022e13daede52e20ee8c30e9865c949deda1618c1610`;
- remediation merge: `2d7ff40034a8e834f20c02bef41f8c088e98f479`;
- post-merge main workflow: `34732179957` — all five Gates `PASS`;
- post-merge artifact: `10309656271`;
- post-merge artifact digest: `sha256:bb185e9822c7e3e559906623e3bc272cbcfbe5bc879112b2988db6f63cb204cb`.

These are automation/build facts only. Real Riot online and Android device items remain `WAITING EXTERNAL TEST` and are not converted to PASS by this closeout.

## 4. Six incident items — closure review

### INC-021-01 — incomplete original Preflight
**Closure result: CLOSED by process correction, historical deviation preserved.**

The original LNR-021 Preflight cannot be retroactively made complete. The remediation and this closeout both restarted from the then-current main, re-read the current constitution, schema/testing/compatibility authorities, affected source/tests, and historical records. The original audit finding remains true; the current process no longer depends on the incomplete original read set.

### INC-021-02 — TeamFightWindow unknown kill delta coerced to zero
**Closure result: CLOSED.**

Current Application rule requires both `blueKillDelta` and `redKillDelta` to be non-null before creating `TeamFightWindowEvent`. Unknown is not converted to zero. A known one-sided aggregate Kill may remain, but no TeamFightWindow is produced from half-known team counters.

Permanent regression: `LiveEventDerivationSafetyRegressionTest.oneSidedUnknownKillDeltaDoesNotBecomeZeroInTeamFightWindow`.

### INC-021-03 — negative/boundary coverage overstated
**Closure result: CLOSED.**

The misleading old test name was corrected. Dedicated permanent regressions now construct and verify:
- one-sided unknown team kill counter;
- true team counter regression;
- player kill counter regression;
- missing current player row.

The latter two conservatively degrade to aggregate unknown-player Kill when team delta is valid; they do not manufacture PlayerId.

### INC-021-04 — stale authority documents
**Closure result: CLOSED.**

The remediation synchronized Project Scope, Architecture, Testing, Compatibility, Development Plan, Implementation Status, Application/App READMEs, Troubleshooting and Changelog. This closeout updates the remaining incident/freeze state from remediation-in-progress to the current certified state.

### INC-021-05 — non-standard status / §15 conclusion tokens
**Closure result: CLOSED.**

Current task tables use constitution §13.1 tokens only. LNR-021 remains `WAITING EXTERNAL TEST`; engineering/compliance facts are prose fields rather than custom status tokens. This closeout's fixed §15 conclusion uses only `DONE / DELIVERY INCOMPLETE / BLOCKED`.

### INC-021-06 — Timeline v1→v2 migration lacked a verifiable recovery point
**Closure result: CLOSED.**

Before overwriting an existing v1 file, current repository code:
1. reads the original v1 text;
2. fully decodes it;
3. verifies canonical GameId;
4. atomically creates the sibling `.schema-v1.bak` if absent;
5. byte/text-compares the recovery copy with the source;
6. fully decodes the recovery copy and re-verifies canonical GameId;
7. only then permits v2 replacement.

Permanent Adapter regressions cover successful backup, stable backup preservation, mismatched pre-existing recovery, wrong-identity v1, and malformed v1. Failed recovery validation leaves the source file unoverwritten and does not create a false recovery point.

## 5. New-debt review

Independent post-remediation read-only review found no new confirmed violation in the changed scope:
- no Domain dependency on Android/network/storage implementation;
- no Presentation re-derivation of Kill/Objective/GoldLead facts;
- no raw Provider payload entering Tactical HUD;
- no Region/LPL/LCK business branch introduced;
- no reintroduction of silent overlay exception swallowing;
- no Tactical Preview path into Core/Repository/Timeline;
- no invented killer/victim, official multi-kill, dragon subtype, HP/CD/position facts;
- no Block 3 Watch Hub/player implementation mixed into remediation;
- no unsupported task status token in current authority task tables.

`AUDIT-NOTE-01` on full-Timeline O(n) re-derivation remains a performance observation, not a confirmed violation. It stays for real-device/real-match measurement and does not justify speculative optimization now.

## 6. Scope and freeze decision

Compliance conclusion for the six-item incident: **PASS / CLOSURE READY**.

Block 2 engineering scope may be frozen once this documentation-only closeout is merged and its post-merge main Gate passes. “Engineering frozen” means no opportunistic Block 2 feature changes while entering Block 3; it does **not** mean LNR-021 becomes `DONE`.

LNR-021 remains `WAITING EXTERNAL TEST` for:
- real Riot online Tactical event triggering;
- Android overlay permission/window/touch-through/orientation/source-loss behavior;
- any future explicit Provider evidence for official killer/victim, official multi-kill classification, or dragon subtype.

No freeze Git Tag is created here because constitution §12 allows stage tags only after Definition of Done, while LNR-021 still has external/device verification outstanding.

## 7. Files changed by this closeout

Planned documentation-only scope:
- add `docs/development/2026/2026-09-13_LNR-021_constitution-closeout.md`;
- update `docs/IMPLEMENTATION_STATUS.md`;
- update `docs/DEVELOPMENT_PLAN.md`;
- update `docs/PROJECT_SCOPE.md`;
- update `docs/TROUBLESHOOTING.md`;
- update `docs/CHANGELOG.md` if milestone closeout wording requires it.

No production source, tests, build scripts, dependency files, persistence schema, UI, or runtime configuration may change in this closeout.

## 8. Rollback

Before merge: close/delete this branch/PR.

After merge: revert the closeout merge commit. This affects documentation/certification only; remediation production code remains at `2d7ff400...` unless separately reverted through its own audited change.

## 9. Post-change Compliance Review

Result for remediation evidence: `PASS`.

This closeout still requires its own documentation exact-head/PR Gate and post-merge main Gate before delivery can be declared complete. A later final addendum may record those transport anchors without rewriting this record.

## 10. Fixed §15 task delivery sheet — current state

1. **Task**: `LNR-021-COMPLIANCE-CLOSEOUT / INC-LNR-021-001`; current final status for this record: `DELIVERY INCOMPLETE`.
2. **Baseline**: `docs/lnr-021-constitution-closeout` from `main@2d7ff40034a8e834f20c02bef41f8c088e98f479`; end commit pending branch freeze.
3. **Constitution Preflight**: `PASS`.
4. **Modules**: docs governance only; production modules are read-only evidence.
5. **Related clauses**: §0.3/0.4, §1/1.1/1.2, §2, §7, §9, §11, §12, §13, §14, §15, L-3/L-5/L-6/L-8.
6. **Files**: planned list in §7; exact final diff must be rechecked before merge.
7. **Implementation/design reason**: certify the six remediation items, sync current authority state, and prevent entering Block 3 with an open compliance incident.
8. **Tests**: positive=`PASS` via remediation regressions; negative=`PASS`; boundary=`PASS`; non-target=`PASS` by scope/diff review; regression=`PASS`; Integration/E2E/device=`WAITING EXTERNAL TEST` where applicable to LNR-021. This docs-only closeout still awaits its own Gate.
9. **Script verification**: `N/A` — no executable script changed.
10. **Logs/fault location**: `LNR-APP-LIVE-004`, `LNR-UI-LIVE-004`, Timeline migration recovery entries remain in Troubleshooting; no new runtime code introduced.
11. **Impact**: documentation/certification only; no runtime/API/schema/UI/security/network change.
12. **Known issues/follow-up**: real Riot/Android external validation, LIVE-009 Herald/Atakhan, LIVE-012 real Draft Provider, and performance audit note remain honest follow-up items.
13. **Rollback**: see §8.
14. **Status sync**: pending this closeout branch updates and merge.
15. **Commit / Push / PR / Release**: branch created and pushed through connector; closeout PR/merge/release not yet complete.
16. **Post-change Compliance Review**: `PASS` for remediation evidence; closeout transport evidence pending.
17. **Final conclusion**: `DELIVERY INCOMPLETE`.

> Do not rewrite this record to pretend the closeout PR was already merged. Final transport evidence must be added in a separate immutable addendum after the closeout merge/main Gate.
