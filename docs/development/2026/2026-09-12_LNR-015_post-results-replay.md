# LNR-015 — Global POST Result / Game Archive / Stats / Historical Timeline / Replay

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `a530a8a88d9cc1ee649f0d95732b73e42675510e`
- Working branch: `feature/lnr-015-post-results-replay`
- Current status: `TESTING`

## Request / Goal

完整迁移旧 RiftLab 的赛后能力，但不复制旧 `MatchDetailRepository` / `MatchTimelineStore` God Store。赛后事实必须强类型拆分、逐来源取证、可独立降级，并保留历史恢复能力。

全局原则：
> 全局管理、全局接管；赛区只是数据维度，不是架构边界。

POST 必须先提供 Global baseline；LPL/LCK/LEC/LCP 等区域来源只能作为 Adapter supplement，不得成为独立业务架构。

## Constitution Preflight

`PASS`。

已重新读取：
- `docs/ENGINEERING_CONSTITUTION.md`；
- `docs/DEVELOPMENT_PLAN.md`；
- 当前 main/branch baseline；
- 旧 RiftLab POST 相关源码与数据文件。

## Scope / Non-goals

### In scope
- Global Series Result；
- Completed Game / Player Stats / Objective facts 的强类型 Domain 边界；
- Awards；
- Replay metadata；
- provider identity mapping；
- device-local POST archive；
- Global historical process backfill；
- POST Application orchestration + Compose presentation；
- 区域来源 capability/supplement 边界。

### Non-goals
- Media3 / WebView 播放器；
- RiftScreen / HUD；
- AI 赛后总结；
- Bilibili replay supplement；
- 没有证据时推断 per-game winner；
- 为每个赛区复制一套 Domain/Application。

## Legacy Evidence Audited

- `CompletedGameArchive.kt`：赛后可由独立历史源恢复，不依赖 APP 当时在线；缺失不得合成；
- `MatchDetailRepository.kt`：旧版将历史结果、Awards、Draft、OP.GG、素材、UI loading/cache 混在同一大 Store，本轮禁止复制；
- `LplHistoricalPostMatchResolver.kt`：LPL 历史链通过 schedule/team/date/score 定位 GameList/BMatch/TJStats；旧源码含敏感-looking auth token，本轮禁止复制；
- `RiotLiveStatsHistoryResolver.kt`：跨赛区使用真实 Riot LiveStats 历史 window，不插值；上游不再保留则留缺口；finished frame 即使落在采样间隔内也必须保留；
- `GlobalVerifiedAwardsProvider.kt` + `data/global/match_awards.json`：奖项来自 provenance-preserving verified mirror，不从 KDA/伤害推 MVP/POG；
- `OpggMatchSupplementProvider.kt`：第三方 supplement 不冒充官方；部分 stat 缺失时未知不能写成 0；
- `RiotVodRepository.kt`：全球 Riot getEventDetails VOD metadata，与播放实现分离；
- `BilibiliVodRepository.kt`：仅保存 BVID/CID/page/chapter/offset 元数据，不下载/托管视频；
- `MatchTimelineStore.kt`：event-sourced timeline 与保守 delta 行为值得保留，但 Android Store 不复制，复用 Laner canonical `GameTimeline`。

## Locked POST Fact Boundaries

```text
Series Result
!= Completed Game Archive
!= Player Stats
!= Awards
!= Replay Metadata
!= Historical Timeline
```

规则：
- FINAL Series 必须有 winner 且与比分一致；
- PARTIAL Series 不得提前声明 winner；
- stat 缺失使用 null，不用 0 表示未知；
- `DataAuthority.DERIVED` 禁止发布 MVP/POG；
- Replay 是 metadata，不是播放器实现；
- Historical Timeline 只接受真实 provider frames/events，不插值；
- 每类 POST Source 可独立成功/失败；
- 某个 Award/VOD 源失败不得清空已验证 Series Result；
- Provider raw IDs 不成为 canonical Match/Game identity；
- 未获得明确 per-game winner 时不得通过 gold/kills/objectives 推断胜方。

## Architecture / Design

```text
Global Schedule / Canonical Match
              ↓
        PostMatchQuery
              ↓
     PostSourceCapability
       ↙      ↓       ↘
 Global    Regional    Cache
 Source   Supplement  Fallback
       ↘      ↓       ↙
        PostMatchService
              ↓
     Canonical POST facts
              ↓
           POST UI
```

Historical process:

```text
Provider historical frames
          ↓
 PostTimelineSourcePort
          ↓
  PostTimelineService
    canonical validation
          ↓
 LiveTimelineService
          ↓
 canonical GameTimeline
```

UI 不直接访问 Provider / Repository。

## Implemented

### Domain
- `SeriesResult / SeriesResultState`；
- `PostTeamStats`；
- `PostPlayerStats` nullable stat semantics；
- `PostDraftSide`；
- `CompletedGameRecord`；
- `VerifiedPostAward`；
- `ReplayAsset / ReplayProvider`；
- `PostMatchBundle`；
- `GameIdentity.canonical(matchId, gameNumber)` 全局 canonical GameId 规则；
- ADR-003 冻结 provider game id 不得进入 canonical GameId；
- canonical Timeline snapshot 允许 `LIVE_MATCH_SOURCE` 与 `POST_MATCH_SOURCE`，继续拒绝 PRE/AI。

### Application
- `PostMatchQuery`；
- independent ports: Result / CompletedGame / Award / Replay；
- `PostSourceCapability`：所有来源声明自己是否支持当前 canonical match；
- `PostMatchService` 按 capability 选源，不写 LPL/LCK/LEC/LCP 分支；
- `PostMatchArchiveRepository` fallback-only cache；
- `ProviderMatchIdentityRepository`：canonical MatchId ↔ provider event/match id 正式映射；
- `PostTimelineSourcePort`；
- `PostTimelineService`；
- Historical frame 的 matchId/gameId/gameNumber/sourceClass 二次校验；
- 错误 canonical identity → `LNR-APP-POST-005`，不得污染 Timeline；
- LIVE/POST 真实帧复用同一个 canonical `GameTimeline`；
- per-capability failures；
- result/game/award arbitration；
- multi-provider Replay coexistence；
- explicit `READY / DEGRADED / CONFLICT / UNAVAILABLE`。

### Android / UI
- `JsonPostMatchArchiveRepository`：schema_version + SHA-256 filename + atomic move + corruption failure；
- `JsonProviderMatchIdentityRepository`：provider identity 持久化，新观测覆盖旧映射，旧观测不能回退；
- `PostMatchScreen` 替代 POST 空壳；
- 只选择 `ScheduleState.COMPLETED` 作为 POST 查看目标；
- no-source 时明确 UNAVAILABLE，不复用 PRE score 冒充 POST result；
- `VerifiedAwardsMirrorSource` 提供跨赛事 verified Awards；
- `RiotGlobalResultSource` 提供 Riot global schedule 内所有区域/国际赛事 Series Result baseline；
- `RiotGlobalReplaySource` 提供 Riot getEventDetails 全球 Replay metadata；
- `RiotGlobalHistoricalTimelineSource` 提供 Riot LiveStats 全球真实历史过程帧；
- historical adapter 不插值，kickoff 使用真实 `/window/{gameId}`，之后按 10s cursor window 顺序恢复；
- 正常帧按“距离上次已存帧 ≥2s”采样；`finished` 真实帧无条件保留；
- Riot 不再保留历史窗口时返回 unavailable/null，不合成；
- `PostTimelineCard` 只有用户选择 G1/G2/... 后才发起 backfill，不在页面打开时扫整场；
- Replay UI 展示 Game / Provider / Locale / media id / provenance metadata；
- historical UI 展示真实帧数、时间范围、complete/degraded 与 failures。

### Regional supplement
- `LplHistoricalPostMatchSource` 已作为区域补充 Adapter 实现 Result/Game 解析基础；
- 它不构成 POST 主架构，也未作为默认全球 baseline wiring；
- 只有 Global baseline 缺深度终局 Stats 时才可参与补全；
- TJStats credential 改为 `LPL_TJSTATS_AUTH / lplTjstatsAuth` 外部注入，旧硬编码 token 未迁移；
- 无 explicit winner evidence 时不发布 `CompletedGameRecord`。

## Design Corrections During Development

### Award Game identity

Verified Awards mirror 的小局奖项只有 game number，没有 canonical GameId。

错误方向（未保留）：用 provider raw ID 或临时拼接规则直接当 Domain GameId。

纠正：
- `VerifiedPostAward` 独立增加 `gameNumber`；
- `gameId` 只有真正完成 identity mapping 时才填；
- Application award slot 可按 gameId 或 gameNumber 区分；
- ADR-003 将 `(canonical MatchId, gameNumber)` 冻结为 canonical GameId 生成规则。

### LPL-first drift → Global-first correction

开发中一度沿旧 `LplHistoricalPostMatchResolver` 深挖，形成“先把 LPL POST 做满”的趋势。这与 ADR-002 Global Competition 原则冲突。

纠正：
- POST 主流程回到 Global baseline；
- Application 增加 `PostSourceCapability`；
- 所有区域差异限制在 Adapter；
- Riot global Series Result + Replay 作为跨赛区 baseline；
- Riot LiveStats History 作为跨赛区 Timeline/Stats baseline；
- LPL TJStats / 后续任何 LCK/LEC/LCP 专属源只做 supplement；
- UI/Domain/Application 不出现赛区业务分支。

### Timeline source-class boundary

第一版 Application 已允许 POST 历史帧，但 Domain `TimelineSnapshotPoint` 仍只允许 `LIVE_MATCH_SOURCE`，导致真实 POST backfill 在 Domain 落点失败。

纠正：
- Domain Timeline 只允许 `LIVE_MATCH_SOURCE / POST_MATCH_SOURCE`；
- 继续拒绝 `PRE_MATCH_SOURCE / GLOBAL_AI_ASSIST`；
- 增加 `TimelineSourceClassTest`；
- run `34695924994` 证明根因修复后全 Gate PASS。

### Historical sampling semantics

第一版 global Riot history 使用 `elapsed % 2 == 0`，可能漏掉落在奇数秒的真实 finished frame。

纠正：
- 对齐旧版已验证行为，改为 `elapsed - lastStoredSecond >= 2`；
- `gameState=finished` 永远允许进入解析；
- 只在真实解析成功后推进 `lastStoredSecond`；
- 不引入任何插值。

## Tests

### Domain regressions
- final winner/score consistency；
- tied/winnerless final rejected；
- partial cannot declare winner；
- missing player stats stay null；
- derived authority cannot publish awards；
- replay metadata remains provider-neutral；
- canonical GameId stable for same match/game number and isolated across matches；
- Timeline accepts LIVE/POST fact sources；
- Timeline rejects PRE/AI sources。

### Application regressions
- award source failure does not erase valid result；
- conflicting award winners are visible；
- stronger fact selected without hiding conflict；
- wrong-match fact rejected；
- fresh final can beat stale partial；
- multiple replay providers coexist；
- unsupported regional source is not called；
- archive only fills missing facts and does not vote against fresh providers；
- conflict does not overwrite last-known-good archive；
- POST historical frames merge into canonical GameTimeline；
- LIVE frame + POST historical frame coexist in same GameTimeline；
- wrong historical canonical GameId is rejected and cannot poison repository。

### UI / Adapter regressions
- POST target only comes from COMPLETED schedule rows；
- live/upcoming cannot become POST target；
- Awards mirror matches by canonical teams + date；
- Awards mirror game number does not synthesize provider-derived GameId；
- LCK + Worlds fixtures use the same Riot global Result parser；
- LCK + Worlds fixtures use the same Riot global Replay resolver；
- LCK + Worlds fixtures use the same Riot historical team mapping contract；
- Riot provider game id does not leak into canonical GameId；
- provider identity repository round-trip + newest-observation-wins；
- unsupported identity schema fails loudly；
- Riot real-frame fixture becomes canonical POST historical snapshot with gold/objectives/player champion data。

## Failure History

### CI run `34693494001`

- Architecture Gate: `PASS`
- Domain/Application tests: `FAIL`
- App tests / Android compile: skipped by Gate

Root cause:
测试 fixture 使用非法错误码 `LNR-SRC-POST-TEST`，违反 `LNR-MODULE-STAGE-NNN` 三位数字格式，因此 `ErrorCode` 在 fixture 构造阶段抛异常；POST aggregation 业务逻辑没有进入失败路径。

Fix:
`ca805e77f89bdb65311c24e5c12e3ed056d7e83a` → 合法 `LNR-SRC-POST-999`，不放宽生产 ErrorCode 规则。

Verification:
run `34693630753`：Architecture / Domain+Application / Android Adapter unit tests / Android compile 全 PASS。

### Global capability routing

- commit `e3de01052602d7633368acd003c1ab0fd0f14401`
- run `34694930111`: Architecture / Domain+Application / Android Adapter unit tests / Android compile 全 PASS。

### Global identity / result / replay

- commit `ea9b6e576fe22f4459c0c55c89f805a3d556285e`
- run `34695412163`: Architecture / Domain+Application / Android Adapter unit tests / Android compile 全 PASS。

### CI run `34695777894` — historical Timeline Domain boundary failure

- Architecture Gate: `PASS`
- Domain/Application tests: `FAIL`
- downstream gates skipped。

Failing regressions:
- `PostTimelineServiceTest.historicalPostFramesMergeIntoCanonicalTimelineAndMarkComplete`
- `PostTimelineServiceTest.existingLiveFrameAndHistoricalPostFrameShareOneTimeline`

Root cause:
Application 已允许 POST historical facts，但 Domain `TimelineSnapshotPoint` 仍要求 `LIVE_MATCH_SOURCE`，产生跨层 contract 不一致。

Fix:
- commit `f60f87be12e676e3623bac73d586a144f84b3f17`；
- Domain Timeline 允许 LIVE/POST，继续拒绝 PRE/AI；
- `TimelineSourceClassTest` 固化边界。

Verification:
- commit `7451c302f106fa1f4d636a8ac77f1580b8dd672c`；
- run `34695924994`：Architecture / Domain+Application / Android Adapter tests / Android debug compile 全 PASS。

### Current code/UI exact-head

- commit `0681c3b20f2e6cad4cd6fb52bf90a4912e299608`；
- run `34696081645`：Architecture / Domain+Application / Android Adapter unit tests / Android debug compile 全 PASS。

该 head 已包含：
- global Result / Replay；
- provider identity repository；
- Global Riot historical Timeline source；
- LIVE/POST canonical Timeline integration；
- on-demand POST Timeline UI wiring。

## Security / Secrets

- 不复制旧 `LplHistoricalPostMatchResolver` 内任何 auth/token；
- TJStats 使用 `LPL_TJSTATS_AUTH` / `lplTjstatsAuth` 外部注入；
- Riot 使用既有 `LOL_ESPORTS_API_KEY` 外部注入；
- provider event/match/game ID 只放 identity mapping / provenance / adapter-local metadata；
- 不下载/托管第三方 VOD bytes；
- UI 不接触 credential 或 Provider client。

## Performance / Network Impact

- POST 页面打开只读取 Result/Replay/Awards；
- 历史 Timeline 必须由用户选择具体 Gx 后按需 backfill；
- LiveStats windows 串行读取，禁止并发扫全场；
- 连续 3 个空 window 即停止；
- 3 小时 hard cap 防止无限循环；
- 已获得本地 GameTimeline 后可复用本地持久化，不要求 UI 直接重新读取 Provider。

## Known Issues / Waiting External Test

- Riot credentialed online Result / EventDetails / LiveStats fetch 尚未在 LNR-015 形成真实在线证据；
- Android real-device POST Result/Replay/Timeline UI 尚未验收；
- Global per-game CompletedGame winner evidence 尚未建立统一可靠字段映射；
- Riot EventDetails team identity shape 若在真实响应中缺 code/name，需要扩展 provider team identity mapping；
- canonical PlayerId 当前部分来源仍由 handle token 生成，跨更名 identity 仍需后续 Global Player Mapping；
- Bilibili Replay supplement / BP / final items / Timeline event filtering / TeamFight aggregation 未完成。

## Rollback

- 本任务全部位于 `feature/lnr-015-post-results-replay`；未合并前可直接删除分支；
- POST archive / provider identity storage 使用独立目录，不修改 PRE/LIVE schema；
- Timeline Domain source-class 变更只扩大为 LIVE+POST 事实源，PRE/AI 仍拒绝；如需回退可回滚本任务 commits，不需要迁移已有 LIVE schema。

## Status Sync

- `docs/DEVELOPMENT_PLAN.md`：LNR-015 → `TESTING`；
- `docs/IMPLEMENTATION_STATUS.md`：已同步 global POST truth；
- `docs/FEATURE_BASELINE.md`：POST 条目按真实证据更新，未完成项不提前 DONE；
- 仍需同步 TESTING / TROUBLESHOOTING / CHANGELOG / module README 后再开 PR。

## Compliance Conclusion

当前代码实现边界符合：
- Core 无 Android/Provider API；
- Application 统一编排，不复制赛区业务；
- Adapter 保持 provider-specific ID/transport；
- UI 只消费 Application；
- 缺失事实保持未知；
- tests written/run/passed 与 waiting external 明确分离；
- failures 不抹除，并计划进入 TROUBLESHOOTING。

当前任务状态：`TESTING`。最终文档 exact-head + PR Gate 通过后，应进入 `WAITING EXTERNAL TEST`，而不是 `DONE`。
