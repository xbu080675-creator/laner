# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- Current main closeout: `824c19aea6ad5cd00612bb2af2ed6c44b69cda67`
- Project phase: `M1 / Feature Migration`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`

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

## Current Truth
Laner 已形成连续 PRE → LIVE → POST 基础链。Global POST 已并入 main：强类型 Result/Game/Stats/Awards/Replay/Timeline；Global-first capability routing；canonical GameId/provider identity mapping；fallback-only archive；verified Awards；Riot global Series Result/Replay；Riot historical LiveStats Timeline；POST Compose 与按局 backfill。

Region 仍只作数据维度。Application 无 LPL/LCK/LEC/LCP 业务分支；LPL TJStats 只作为区域 supplement。

## LNR-015 Verification
- `34693494001` FAIL：非法测试 ErrorCode fixture；失败保留。
- `34693630753` PASS：修复后全 Gate。
- `34694930111` PASS：Global capability routing。
- `34695412163` PASS：Global identity/result/replay。
- `34695777894` FAIL：POST historical Timeline 被旧 Domain source-class invariant 拒绝；失败保留。
- `34695924994` PASS：根因修复后全 Gate。
- `34696081645` PASS：final POST code/UI head。
- `34696404045` PASS：governance/test-evidence head。
- `34696868207` PASS：final documentation exact-head。
- PR #7 run `34696964926` PASS：Architecture / Domain+Application / Android Adapter tests / Android debug compile。
- PR #7 merge: `0ed5cdfa882b74cdb1b7a6e87dd6ee60856f40ca`。
- delivery record closeout: `824c19aea6ad5cd00612bb2af2ed6c44b69cda67`。

## Waiting External Test / Honest Gaps
- credentialed Riot POST Result/EventDetails/LiveStats online integration；
- Android real-device POST Result/Replay/Timeline；
- Cito online remains DEFERRED；
- global per-game CompletedGame winner/stat completeness remains IN PROGRESS without explicit winner evidence；
- BP/items/objective archive completion, Timeline filtering/TeamFight aggregation, Bilibili supplement, Media3/WebView and VOD↔Timeline sync remain separate baseline work。

Automated fixture PASS is not online/device evidence. Missing facts remain unknown; no winner is inferred from gold/kills/objectives.

## Next
LNR-015 automated delivery is closed. Next migration task may proceed from current `main`; external POST/device certification can be added later without reopening the architecture task.
