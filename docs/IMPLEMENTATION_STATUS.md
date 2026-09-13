# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- Project phase: `M1 / Feature Migration`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- LNR-020 Block 1: engineering-frozen after `INC-LNR-020-001 / CLOSED`; compliance facts are prose, not task status tokens
- LNR-021 Block 2: engineering-frozen after `INC-LNR-021-001 / CLOSED`; LNR-021 product status remains `WAITING EXTERNAL TEST`
- Baseline SHA policy: long-lived status docs record stable task/PR merge/Gate anchors, not a self-referential “current main SHA”.

## Task Status
| Task | Title | Status |
|---|---|---|
| LNR-000~009 | Constitution / baseline / architecture / product foundation | DONE |
| LNR-010 | Global catalogue / schedule | WAITING EXTERNAL TEST |
| LNR-011 | PRE Roster / Staff / Form / H2H | WAITING EXTERNAL TEST |
| LNR-012 | Standings / Qualification / Tournament Edition | WAITING EXTERNAL TEST |
| LNR-013 | LIVE Match State / Arbitration / Event / Timeline Core | DONE |
| LNR-014 | LIVE Android persistence / composition / UI | WAITING EXTERNAL TEST |
| LNR-015 | Global POST Result / Archive / Historical Timeline / Replay | WAITING EXTERNAL TEST |
| LNR-016 | Testable Android Platform / Riot Global LIVE / APK Delivery | WAITING EXTERNAL TEST |
| LNR-017 | Local AI / OCR / Roster Assist | TODO |
| LNR-018 | Compatibility / Full Regression / Migration Audit | TODO |
| LNR-019 | Global LIVE Snapshot / Timeline / Match HUD | WAITING EXTERNAL TEST |
| LNR-020 | RiftScreen / Draft HUD Android Overlay | WAITING EXTERNAL TEST |
| LNR-021 | Tactical HUD / LIVE Event Derivation | WAITING EXTERNAL TEST |

## Block 1 / LNR-020 Frozen Truth
- Original functional merge: PR #10 / `967e112d6efcf8e6daa86f0b007cedc39b63b04c`; post-merge main run `34706047380` PASS.
- Compliance remediation merge: PR #12 / `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`; exact-head run `34708127194` PASS; post-merge run `34708285172` PASS.
- Closeout merge: PR #13 / `e92bb65b2469cbeb23e56a531890945880f99eba`; post-merge run `34708701976` PASS.
- Final factual-status correction: `87f90a89ad7a35fdb9717ef1003fa984bba4fdab`; main run `34708959768` PASS.
- `INC-LNR-020-001 = CLOSED`; Block 1 engineering scope is frozen.
- `LIVE-024~029` continue `WAITING EXTERNAL TEST`; `LIVE-012` remains TODO.

## Block 2 / LNR-021 Frozen Truth

### Original delivery anchors
- baseline before Block 2: `main@87f90a89ad7a35fdb9717ef1003fa984bba4fdab`;
- PR #15 final feature head: `4c1f256daf288bbc835d30f01ce1bcf3b6fa85f5`;
- final push run `34712441374`: Architecture / Domain+Application / Android Adapter Unit / Android debug compile / APK upload PASS;
- final PR run `34712444119`: same Gates PASS;
- PR #15 merge: `6c72a748a23758c975d78a78e80092b16d225d34`;
- post-merge main run `34712560708`: same Gates PASS;
- docs closeout PR #16 merge: `c73d551925ba273d4caf6259ef621100c105b5de`;
- closeout main run `34712899722`: same Gates PASS.

### Constitution incident / remediation / closeout anchors
- independent audit: `INC-LNR-021-001` confirmed 6 deviations;
- remediation PR #17 exact head: `a62df876bf21f6009ea4a7fc1c9227fd48e219b1`;
- remediation exact-head run `34732025919`: all five Gates PASS;
- remediation exact-head artifact `10309592107`, digest `sha256:5d99273cd141ecd3e374022e13daede52e20ee8c30e9865c949deda1618c1610`;
- remediation merge: `2d7ff40034a8e834f20c02bef41f8c088e98f479`;
- remediation post-merge main run `34732179957`: all five Gates PASS;
- remediation main artifact `10309656271`, digest `sha256:bb185e9822c7e3e559906623e3bc272cbcfbe5bc879112b2988db6f63cb204cb`;
- independent post-remediation read-only review: PASS for the six confirmed incident items and no new confirmed violation in remediation scope;
- closeout PR #18 exact head: `df58f5651fd241dc95137e0d40aa05e2e29850ee`;
- closeout exact-head run `34732993654`: all five Gates PASS;
- closeout exact-head artifact `10309788301`, digest `sha256:8adfed1f4683f71886a3684397b423645113279763417fc2569da98391da83b0`;
- closeout merge: `38161578e04a523c9247c64c762f4dd983561a9d`;
- closeout post-merge main run `34733073857`: `completed / success`, all five Gates PASS;
- closeout main artifact `10309823348`, digest `sha256:ce9f7744a331587b869d46a13261019adeb2a4377615e67a676d73b4cff93926`;
- final certification record: `docs/development/2026/2026-09-13_LNR-021_constitution-closeout-final.md`;
- `INC-LNR-021-001 = CLOSED`;
- Block 2 engineering scope is frozen. No freeze Tag is created because LNR-021 external/device Definition of Done is incomplete.

### Canonical event derivation
```text
Verified LiveGameSnapshot
→ LiveTimelineService ingest
→ canonical GameTimeline snapshots
→ LiveEventDerivationService
→ LiveTimelineService.reconcileGeneratedEvents
→ canonical MatchEvent
→ LiveMatchContextResult
→ TacticalHudPresentationMapper
→ TacticalHudWindowController
→ OverlayWindowHost
```

Current rules:
- aggregate Kill delta; killer/victim stay null without explicit evidence;
- canonical PlayerId may bind only when player kill deltas fully explain team delta;
- `MultiKillWindowEvent` is a <=20s sampling-window fact, not an official Double/Triple/Quadra/Penta claim;
- `TeamFightWindowEvent` requires both team kill deltas known, window <=20s, combined delta >=3; unknown is never coerced to zero;
- team kill counter regression manufactures no combat event;
- player counter regression or missing player row cannot manufacture PlayerId and may only degrade to aggregate unknown-player Kill when team delta is valid;
- `GoldLeadChangedEvent` uses ±250g deadband and only emits on clear leader-side change;
- Tower / Dragon / Baron deltas are supported from explicit counters; Dragon does not infer subtype/soul/Elder; Herald/Atakhan remain not globally normalized;
- generated-event reconciliation replaces only `laner-live-event-derivation` output and preserves Provider explicit / Draft / lifecycle events;
- late/out-of-order/stronger same-second snapshot can be re-derived to prevent stale local-derived events.

### Tactical HUD
- Verified Tactical activates only for canonical lifecycle=`IN_GAME`;
- visible priority remains `Draft > Tactical > RiftScreen`;
- Tactical window uses `FLAG_NOT_TOUCHABLE`;
- 25s game-time TTL + 30s wall-clock freshness prevents stale cards from hanging after provider loss;
- no HP/CD/position fact is fabricated from unavailable data;
- `TacticalHudPreviewSession` remains Android-only `LOCAL PREVIEW · NOT FACT` and never enters Core/Repository/Timeline.

### Persistence
- LIVE Timeline writes schema v2 and reads v1/v2;
- before first overwrite of an existing v1 Timeline, source v1 is fully decoded and canonical GameId checked;
- exact sibling `.schema-v1.bak` is created atomically, compared with source, fully decoded and identity-checked before v2 replacement is allowed;
- wrong-identity, malformed, or mismatched recovery input blocks replacement;
- later v2 writes do not mutate the original v1 recovery copy;
- pure v2 files do not claim lossless downgrade.

## LNR-021 Failure / Incident History
### Failure A — preserved
- run `34709821178`: Architecture and Domain/Application PASS; Android production compile FAIL because `LiveMatchScreen.eventLabel()` did not exhaust new sealed events;
- fix `403bba4e874ad37978179618e84dceaafb9f06f8`;
- Troubleshooting: `LNR-UI-LIVE-004`.

### `INC-LNR-021-001`
- independent Block 2 audit confirmed 6 deviations;
- remediation code/tests/docs merged and passed exact-head + post-merge main Gates;
- docs-only closeout PR #18 merged and passed exact-head + post-merge main Gates;
- immutable final evidence: `2026-09-13_LNR-021_constitution-closeout-final.md`;
- status: `CLOSED`.

## Waiting External Test / Honest Gaps
- `LIVE-014 / LIVE-015 / LIVE-030` automated implementation exists, but real Riot online triggering remains unproven;
- Android Tactical HUD overlay permission/window/touch-through/orientation/source-loss behavior still needs device evidence;
- official multi-kill / killer-victim / dragon subtype require future explicit Provider evidence;
- `LIVE-009` Herald/Atakhan remains `IN PROGRESS`;
- `LIVE-012` real Draft Provider remains `TODO`;
- LNR-019 real online evidence remains external;
- Cito remains externally unverified.

## Next
The governance gate for Block 3 is open. The next permitted engineering block is **Block 3 — Watch Hub + player**. It must start with its own fresh Constitution Preflight from then-current main; no Block 3 implementation is included in the LNR-021 closeout.

External/device evidence continues independently and must never be represented as CI PASS.
