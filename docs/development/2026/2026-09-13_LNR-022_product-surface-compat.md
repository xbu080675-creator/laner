# LNR-022 — Product Surface Compatibility Remediation

- Date: 2026-09-13
- Branch: `fix/lnr-022-product-surface-compat`
- Baseline: `main@739430d7d8a234bf4ab00ac4004c3745772e26d7`
- Related incident: `INC-LNR-022-001`
- Current conclusion: `DELIVERY INCOMPLETE`
- External state: `WAITING EXTERNAL TEST`

## 1. Requirement / reason

Before Block 3, user device review of the frozen Block 1+2 APK exposed a migration-scope defect: the new architecture had been accompanied by an unauthorized product-surface redesign.

The remediation restores the existing RiftLab user-facing contract while retaining the rebuilt Laner Core/Application/Adapter architecture.

User requirements confirmed for this remediation:

1. Riot may remain an internal Provider, but ordinary users must not be asked to configure a Riot credential as a normal app prerequisite.
2. The product shell must retain the established RiftLab visual/navigation language; the task was “功能不改，底层重做”, not a product redesign.
3. The large floating RiftScreen HUD must not be shrunk or replaced. Its major structure is the legacy baseline; implementation/performance details may improve without changing the product shape.
4. Draft HUD and Tactical HUD are non-targets and must not be opportunistically redesigned.
5. Block 3 remains blocked until real-device visual acceptance of this remediation.

## 2. Constitution Preflight

Read before implementation:

- `main@739430d7d8a234bf4ab00ac4004c3745772e26d7`;
- `docs/ENGINEERING_CONSTITUTION.md`;
- `docs/PROJECT_SCOPE.md`;
- `docs/UX_PRINCIPLES.md`;
- `docs/FEATURE_BASELINE.md`;
- `docs/IMPLEMENTATION_STATUS.md`;
- current `MainActivity`, `LanerRoot`, `LanerTheme`, Riot credential UI, RiftScreen Presentation/View/WindowController;
- legacy `Rlftlab` `RiftLabApp.kt`, `RiftTheme.kt`, `TeamSkin.kt`, `RiftOverlayView.kt`, data-source interfaces and Riot mirror workflow;
- current/legacy Overlay directory inventories.

Preflight result: `PASS TO REMEDIATE`.

Confirmed governing facts:
- Project Scope says `功能不改 / 底层重做` and forbids silent changes to key visible UI/interaction behavior.
- Constitution §4 forbids unauthorized UI/operation changes.
- Constitution §6 requires debug/admin capability to be isolated from ordinary paths.
- UX Principles explicitly say ordinary users should not need to understand internal Provider/cache/OCR/AI pipelines.
- Legacy RiftLab already used Riot among several background sources; Riot itself is not new, but exposing credential configuration as the normal product path was a regression.
- Legacy `RiftOverlayView` is the product baseline for the large HUD: MINI 228dp / COMPACT 308dp / EXPANDED 348dp with team identity, center lead, K/T/D, GOLD/LEAD, event/status and short-lived emphasis.

## 3. Scope

### In scope
- remove Riot credential controls from normal MainActivity/root UI;
- preserve internal credential injection seam for controlled build/dev use;
- restore Rift default visual palette, dense type scale, glow/grid backdrop, cut-corner header/navigation;
- remove the additional root-level CompetitionStructure block from the primary shell without deleting its underlying capability;
- restore large RiftScreen HUD MINI/COMPACT/EXPANDED structure and legacy widths;
- restore legacy signed center gold-difference semantics and GOLD/LEAD row;
- prevent raw Provider identity from appearing in the spectator RiftScreen HUD;
- add automated product-surface guard and RiftScreen Presentation regression tests.

### Explicit non-targets
- Block 3 Watch Hub/player;
- legacy Store/Repository/provider arbitration restoration;
- hardcoded Riot keys;
- Domain/Timeline schema changes;
- Draft HUD redesign;
- Tactical HUD redesign;
- claiming real-device visual equivalence from CI.

## 4. Design

### Provider boundary
Normal user path:

```text
MainActivity
→ LanerApplication.graph()
→ Application services
→ Provider arbitration/adapters
```

There is no normal UI path to edit or save a Riot credential. Controlled build injection remains through `LOL_ESPORTS_API_KEY` environment/Gradle configuration; the existing memory-only internal seam remains available for future isolated developer tooling but is no longer reachable from `MainActivity` / `LanerRoot`.

### Product shell
The rebuilt service boundaries remain unchanged. Only Presentation chrome is restored:

```text
Laner Application services
→ Rift-compatible LanerRoot shell
→ PRE / LIVE / POST screens
```

The product name remains `LANER`, because that rename was explicitly chosen; visual/navigation behavior returns to the existing RiftLab language rather than inventing another shell.

### Large RiftScreen

```text
LiveMatchContextResult
→ RiftScreenPresentationMapper
→ RiftScreenPresentation
→ RiftScreenOverlayView
→ RiftScreenWindowController
→ OverlayWindowHost
```

The View restores the old product shape but does not restore legacy truth stores. `RiftScreenPresentation` remains the only input.

Current TeamRef does not carry logo media URLs. The legacy logo slots are restored spatially using team-code badges for this candidate; this must not be misrepresented as full crest-media restoration. If real-device review requires exact crest rendering, that remains inside this incident before closure and must be solved without putting Provider payload into Domain/UI.

## 5. Files changed in candidate

- `.github/workflows/android-build.yml`
- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- `app/src/main/java/com/laner/app/ui/LanerTheme.kt`
- `app/src/main/java/com/laner/app/ui/RiotCredentialPanel.kt` — removed
- `app/src/main/java/com/laner/app/overlay/RiftScreenOverlayView.kt`
- `app/src/main/java/com/laner/app/overlay/RiftScreenPresentation.kt`
- `app/src/test/java/com/laner/app/overlay/RiftScreenPresentationMapperTest.kt` — added
- `docs/development/2026/2026-09-13_LNR-022_产品表层兼容事故记录.md` — added
- this record

Draft/Tactical source files are intentionally unchanged.

## 6. Automated product-surface guard

CI now blocks:
- re-adding `RiotCredentialPanel.kt`;
- normal MainActivity/LanerRoot references to Riot credential save/clear/runtime editing;
- `presentation.source` leaking Provider identity into spectator RiftScreen View;
- removal of cut-corner root chrome;
- removal of RiftScreen 228/308/348 mode widths;
- removal of MINI/COMPACT/EXPANDED modes.

This gate checks permanent compatibility invariants. It does not claim pixel-perfect device validation.

## 7. Tests

### Permanent unit regression
`RiftScreenPresentationMapperTest` covers:
- waiting placeholders;
- blue-perspective positive signed lead (`+1.0K`);
- red lead as negative signed diff (`-1.5K`);
- old-style `GOLD ... · LEAD ...` semantics;
- no new team-prefixed center-label redesign.

### Failure A — preserved
Workflow run `34735880207`, head `135b084d0ec7626345f47141c73f0e231a601904`:
- Architecture boundary: PASS;
- Product surface compatibility gate: PASS;
- Domain/Application: PASS;
- Android Adapter Unit stage: FAIL during production `:app:compileDebugKotlin`;
- Android debug assemble/APK: skipped.

Root cause: when removing runtime Riot credential Compose state from `MainActivity`, the cleanup also removed `androidx.compose.runtime.getValue/setValue`, while two Activity-level `by mutableStateOf` overlay state properties still required those delegate operators.

Fix: commit `a9a3c4ebea1a05a6b7924255936964f1857413af` restores only `getValue/setValue`; no credential UI or HUD semantics were restored/changed by that fix.

### Green candidate evidence
Workflow run `34736036287`, head `a9a3c4ebea1a05a6b7924255936964f1857413af`:
- Architecture boundary: PASS;
- Product surface compatibility gate: PASS;
- Domain/Application tests: PASS;
- Android Adapter Unit tests: PASS;
- Android debug assemble: PASS;
- APK upload: PASS.

This run proves automated/build integrity only. Real visual/gesture/device behavior remains external validation.

## 8. Risk / compatibility / security

- No Core/Domain/Application source was changed.
- No Timeline/persistence schema changed.
- No Provider raw payload is exposed to UI.
- No secret was added to Git/log/test fixture.
- Removing the normal runtime-key UI means builds without any configured/mirrored provider credential may explicitly degrade instead of asking users for a key; this is intentional product-boundary correction. Source orchestration/fallback work remains separate from UI credential responsibility.
- Large HUD WindowManager ownership remains in `RiftScreenWindowController → OverlayWindowHost`.
- Real Android rendering, drag, permission, orientation and source-loss behavior cannot be proven by JVM/assemble CI.

## 9. Rollback

Revert the LNR-022 branch/PR to return to `main@739430d7d8a234bf4ab00ac4004c3745772e26d7`. This remediation changes no persistent schema, so no data migration rollback is required.

## 10. Current external acceptance checklist

User device validation must confirm:
- ordinary launch contains no Riot key requirement/panel;
- product shell again reads as the established RiftLab/Laner product rather than the replacement generic Material shell;
- large RiftScreen is not shrunk/redesigned and MINI/COMPACT/EXPANDED behave acceptably;
- drag/close/bounds/background visibility remain usable;
- Draft HUD was not degraded;
- Tactical HUD was not degraded.

Until this passes, `INC-LNR-022-001` remains OPEN and Block 3 remains BLOCKED.

## 11. Fixed §15 delivery sheet

1. **Task/title/final status**: `LNR-022 — Product Surface Compatibility Remediation`; `WAITING EXTERNAL TEST`.
2. **Baseline/start/end**: start `main@739430d7d8a234bf4ab00ac4004c3745772e26d7`; current automated candidate `a9a3c4ebea1a05a6b7924255936964f1857413af`; final end commit pending device acceptance/merge.
3. **Preflight**: `PASS TO REMEDIATE`.
4. **Modules**: Android Presentation / Overlay / CI / docs; no Core source changes.
5. **Clauses**: §0.3/0.4, §1/1.1/1.2, §3.6, §4, §6, §9, §10, §11, §13, §15 plus Laner product rules.
6. **Exact files**: listed in §5; authority-status docs still require final sync after candidate acceptance state is known.
7. **Reason/design**: restore unauthorized product-surface drift without reverting rebuilt architecture.
8. **Tests**: positive=`PASS automated`; negative=`PASS automated`; boundary=`PASS automated`; non-target=`PASS by diff + unchanged Draft/Tactical files`; regression=`PASS automated`; integration/build=`PASS`; real device=`WAITING EXTERNAL TEST`.
9. **Scripts**: workflow shell gate syntax/execution proven by CI; no standalone executable script added.
10. **Logs/fault**: Failure A run `34735880207`; stable Troubleshooting entry to be synchronized in final docs closure.
11. **Impact**: normal user product shell, large RiftScreen Presentation/View and CI compatibility policy; no business truth/schema change.
12. **Known issues/follow-up**: device visual acceptance; exact crest media not yet represented by current TeamRef; external provider/device gaps from prior blocks remain.
13. **Rollback**: revert LNR-022 PR; no data migration.
14. **Status sync**: pending final authority-doc sync while incident is OPEN; Block 3 remains blocked by this record.
15. **Commit/push/PR/release**: branch pushed; candidate automated Gate PASS; PR/merge/release intentionally pending user device validation.
16. **Post-change Compliance Review**: automated/static portion `PASS`; final product-surface review pending real-device acceptance.
17. **Final conclusion**: `DELIVERY INCOMPLETE`.

## Final conclusion

Automated remediation candidate exists and passes the full CI chain, but visual/product compatibility is a device-visible contract. `INC-LNR-022-001` remains OPEN, LNR-022 remains `WAITING EXTERNAL TEST`, and Block 3 remains blocked until user acceptance.
