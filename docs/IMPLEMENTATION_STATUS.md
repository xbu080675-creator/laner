# Laner Implementation Status

## Current Baseline
- Repository: `xbu080675-creator/laner`
- Authoritative branch: `main`
- Project phase: `M1 / Feature Migration`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- LNR-020 Block 1: frozen after `INC-LNR-020-001 / CLOSED`；合规结论在正文记录，不混入任务状态 token
- LNR-021 Block 2: `INC-LNR-021-001` 整改中；Block 2 未冻结，Block 3 被该审计门禁阻塞
- Baseline SHA policy: long-lived status docs record stable task/PR merge/Gate anchors, not a self-referential “current main SHA”。

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
- Original functional merge: PR #10 / `967e112d6efcf8e6daa86f0b007cedc39b63b04c`；post-merge main run `34706047380` PASS。
- Compliance remediation merge: PR #12 / `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`；exact-head run `34708127194` PASS；post-merge run `34708285172` PASS。
- Closeout merge: PR #13 / `e92bb65b2469cbeb23e56a531890945880f99eba`；post-merge run `34708701976` PASS。
- Final factual-status correction merge: `87f90a89ad7a35fdb9717ef1003fa984bba4fdab`；main run `34708959768` PASS。
- `INC-LNR-020-001 = CLOSED`，Block 1 compliance review PASS；这属于工程认证事实，不是任务状态枚举。
- `LIVE-024~029` 继续等待 Android 真机证据；`LIVE-012` 仍 TODO。

## Block 2 / LNR-021 Delivered Truth
### Stable delivery anchors
- Baseline before Block 2: `main@87f90a89ad7a35fdb9717ef1003fa984bba4fdab`；
- PR #15 final feature head: `4c1f256daf288bbc835d30f01ce1bcf3b6fa85f5`；
- final push run `34712441374`: Architecture / Domain+Application / Android Adapter unit / Android debug compile / APK upload PASS；
- final PR run `34712444119`: all same Gates PASS；
- PR #15 merge: `6c72a748a23758c975d78a78e80092b16d225d34`；
- post-merge main run `34712560708`: all Gates PASS；
- docs closeout PR #16 merge: `c73d551925ba273d4caf6259ef621100c105b5de`；
- closeout main run `34712899722`: all Gates PASS。

这些是历史工程交付证据，不等于当前独立合规审计 PASS。`INC-LNR-021-001` 在整改完成、重测和独立复查前保持 OPEN。

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

当前规则：
- aggregate Kill delta；没有明确配对证据时 killer/victim 保持 null；
- 当 player kill delta 可完整解释 team delta 时，允许绑定 canonical PlayerId；
- `MultiKillWindowEvent`：<=20s 采样窗内同一选手 kill delta >=2，仅称“采样窗口多杀”，不冒充官方 multi-kill；
- `TeamFightWindowEvent`：只有双方 kill delta 都可比较、窗口 <=20s 且累计 >=3 时生成；任一侧 unknown 时不生成，绝不把 unknown 补成 0；
- team kill counter regression 不制造 Kill/TeamFight；player counter regression 或 player row 缺失不能制造 PlayerId；
- `GoldLeadChangedEvent`：±250g deadband，只有领先方明确易手才生成；
- Tower / Dragon / Baron delta；Dragon 不推断龙种/龙魂/远古龙；Herald/Atakhan 仍未全局标准化；
- `reconcileGeneratedEvents` 只替换 `laner-live-event-derivation` 自己的派生事件，不删除 Provider explicit / lifecycle / Draft facts；
- late/out-of-order/stronger same-second snapshot 后可重新派生，避免陈旧 local-derived event 残留。

### Tactical HUD
- Verified Tactical 只在 canonical lifecycle=`IN_GAME` 激活；
- 用户可见窗口优先级保持旧行为：`Draft > Tactical > RiftScreen`；
- Tactical window 使用 `FLAG_NOT_TOUCHABLE`，不抢底层观赛操作；
- 25s game-time TTL + 30s wall-clock freshness；Provider/Timeline 停更时旧卡不会永久挂屏；
- 不显示现有事实链不存在的 HP、技能/召唤师技能 CD、位置等模拟字段；
- `TacticalHudPreviewSession` 仅用于本地视觉测试，固定 `LOCAL PREVIEW · NOT FACT`，不进入 Core/Repository/Timeline。

### Persistence
- LIVE Timeline 当前写入 schema 为 v2，并继续读取 v1；
- 首次把已有 v1 Timeline 写成 v2 前，必须保存内容完全一致的 `.schema-v1.bak` 并验证一致性；恢复副本异常时迁移失败，主文件不得被覆盖；
- 后续 v2 写入不改写该 v1 recovery copy；
- corrupt / unsupported schema 继续显式失败；
- 不声称任意 v2 可无损降级；只有存在已验证 v1 recovery copy 的升级文件可以恢复升级前状态。

## LNR-021 Failure / Incident History
### Failure A — preserved
- run `34709821178`；
- Architecture boundary PASS；Domain/Application PASS；
- Android Adapter Unit 阶段 production `:app:compileDebugKotlin` FAIL；
- root cause：`LiveMatchScreen.eventLabel()` 未穷举新 `MultiKillWindowEvent / TeamFightWindowEvent`；
- fix：`403bba4e874ad37978179618e84dceaafb9f06f8`；
- Troubleshooting: `LNR-UI-LIVE-004`。

### `INC-LNR-021-001`
- 独立 Block 2 宪法复查确认 6 项偏离；
- 当前整改范围包括事实安全、真实负向/边界回归、Timeline migration recovery、权威文档同步、标准状态 token 与新整改留档；
- 当前状态：OPEN；最终 exact-head / PR / post-merge main Gate 和 Post-change Compliance Review 尚未全部完成。

## Waiting External Test / Honest Gaps
- LNR-021 `LIVE-014 / LIVE-015 / LIVE-030` 自动实现存在，但真实 Riot online 触发仍未证明；
- Android Tactical HUD 的系统 overlay 权限、真实窗口层级、touch-through、横竖屏视觉、断流退场行为需要真机验证；
- 真实官方 multi-kill / killer-victim / dragon subtype 仍依赖未来明确 Provider event evidence，当前不得声称已拥有；
- `LIVE-009` Herald/Atakhan 仍 `IN PROGRESS`；
- `LIVE-012` real Draft Provider 仍 TODO；
- LNR-019 真实赛事 online evidence 继续外部补证；
- Cito remains DEFERRED。

## Next
完成 `INC-LNR-021-001` 整改 → 冻结 exact-head → 完整 Gate → PR Gate → merge → main Gate → 独立 Post-change Compliance Review。只有事故通过新记录正式关闭后，Block 2 才能冻结；随后才允许进入 Block 3 Watch Hub + 播放器。
