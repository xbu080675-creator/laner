# LNR-015 — POST Result / Game Archive / Stats / Timeline Archive / Replay Domain

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `a530a8a88d9cc1ee649f0d95732b73e42675510e`
- Working branch: `feature/lnr-015-post-results-replay`
- Current status: `IN PROGRESS`

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

## Legacy Evidence Audited

- `CompletedGameArchive.kt`：赛后可由独立历史源恢复，不依赖 APP 当时在线；缺失不得合成；
- `MatchDetailRepository.kt`：旧版将历史结果、Awards、Draft、OP.GG、素材、UI loading/cache 混在同一大 Store，本轮禁止复制；
- `LplHistoricalPostMatchResolver.kt`：LPL 历史链通过 schedule/team/date/score 定位 GameList/BMatch/TJStats；旧源码含敏感-looking auth token，本轮禁止复制；
- `RiotLiveStatsHistoryResolver.kt`：跨赛区使用真实 Riot LiveStats 历史 window，不插值；上游不再保留则留缺口；
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
- Provider raw IDs 不成为 canonical Match/Game identity。

## Implemented So Far

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
- ADR-003 冻结 provider game id 不得进入 canonical GameId。

### Application
- `PostMatchQuery`；
- independent ports: Result / CompletedGame / Award / Replay；
- `PostSourceCapability`：所有来源声明自己是否支持当前 canonical match；
- `PostMatchService` 按 capability 选源，不写 LPL/LCK/LEC/LCP 分支；
- `PostMatchArchiveRepository` fallback-only cache；
- `ProviderMatchIdentityRepository`：canonical MatchId ↔ provider event/match id 正式映射；
- per-capability failures；
- canonical match validation；
- result/game/award arbitration；
- multi-provider Replay coexistence；
- explicit `READY / DEGRADED / CONFLICT / UNAVAILABLE`。

### Android / UI
- `JsonPostMatchArchiveRepository`：schema_version + atomic move + corruption failure；
- `JsonProviderMatchIdentityRepository`：provider identity 持久化，新观测覆盖旧映射，旧观测不能回退；
- `PostMatchScreen` 替代 POST 空壳；
- 只选择 `ScheduleState.COMPLETED` 作为 POST 查看目标；
- no-source 时明确 UNAVAILABLE，不复用 PRE score 冒充 POST result；
- `VerifiedAwardsMirrorSource` 提供跨赛事 verified Awards；
- `RiotGlobalResultSource` 提供 Riot global schedule 内所有区域/国际赛事 Series Result baseline；
- `RiotGlobalReplaySource` 提供 Riot getEventDetails 全球 Replay metadata；
- Replay UI 展示 Game / Provider / Locale / media id / provenance metadata。

### Regional supplement
- `LplHistoricalPostMatchSource` 已作为区域补充 Adapter 实现 Result/Game 解析基础；
- 它不构成 POST 主架构，后续只有 Global baseline 缺深度终局 Stats 时才参与补全；
- TJStats credential 改为外部注入，旧硬编码 token 未迁移。

## Design Corrections During Development

### Award Game identity

Verified Awards mirror 的小局奖项只有 game number，没有 canonical GameId。

错误方向（未保留）：用 provider raw ID 或临时拼接规则直接当 Domain GameId。

纠正：
- `VerifiedPostAward` 独立增加 `gameNumber`；
- `gameId` 只有真正完成 identity mapping 时才填；
- Application award slot 可按 gameId 或 gameNumber 区分；
- 后续通过 ADR-003 将 `(canonical MatchId, gameNumber)` 冻结为唯一 canonical GameId 生成规则。

### LPL-first drift → Global-first correction

开发中一度沿旧 `LplHistoricalPostMatchResolver` 深挖，形成“先把 LPL POST 做满”的趋势。这与 ADR-002 的 Global Competition 原则冲突。

纠正：
- POST 主流程回到 Global baseline；
- Application 增加 `PostSourceCapability`；
- 所有区域差异限制在 Adapter；
- 首先迁移 Riot global Series Result + Replay；
- `RiotLiveStatsHistoryResolver` 作为下一条全球 Timeline/Stats 数据面；
- LPL TJStats / 后续 LCK 或其他赛区专属源只做 supplement；
- UI/Domain/Application 不出现赛区业务分支。

## Tests

### Domain regressions
- final winner/score consistency；
- tied/winnerless final rejected；
- partial cannot declare winner；
- missing player stats stay null；
- derived authority cannot publish awards；
- replay metadata remains provider-neutral；
- canonical GameId stable for same match/game number and isolated across matches。

### Application regressions
- award source failure does not erase valid result；
- conflicting award winners are visible；
- stronger fact selected without hiding conflict；
- wrong-match fact rejected；
- fresh final can beat stale partial；
- multiple replay providers coexist；
- unsupported regional source is not called；
- archive only fills missing facts and does not vote against fresh providers；
- conflict does not overwrite last-known-good archive。

### UI/Adapter regressions
- POST target only comes from COMPLETED schedule rows；
- live/upcoming cannot become POST target；
- Awards mirror matches by canonical teams + date；
- Awards mirror game number does not synthesize provider-derived GameId；
- LCK + Worlds fixtures use the same Riot global Result parser；
- LCK + Worlds fixtures use the same Riot global Replay identity resolver；
- Riot provider game id does not leak into canonical GameId；
- provider identity repository round-trip + newest-observation-wins；
- unsupported identity schema fails loudly。

## Failure History

### CI run `34693494001`

- Architecture Gate: `PASS`
- Domain/Application tests: `FAIL`
- App tests / Android compile: skipped by Gate

Failure:
`PostMatchServiceTest.awardFailureDoesNotEraseValidSeriesResult`

Root cause:
测试 fixture 使用非法错误码 `LNR-SRC-POST-TEST`，违反 `LNR-MODULE-STAGE-NNN` 三位数字格式，因此 `ErrorCode` 在 fixture 构造阶段抛异常；POST aggregation 业务逻辑没有进入失败路径。

Fix:
`ca805e77f89bdb65311c24e5c12e3ed056d7e83a` 将 fixture 改为合法 `LNR-SRC-POST-999`，不放宽生产 ErrorCode 规则。

Verification:
run `34693630753`：Architecture / Domain+Application / Android Adapter unit tests / Android compile 全 PASS。

### Global capability routing verification

- commit `e3de01052602d7633368acd003c1ab0fd0f14401`
- run `34694930111`: Architecture / Domain+Application / Android Adapter unit tests / Android compile 全 PASS。

### Global identity/result/replay verification

- commit `ea9b6e576fe22f4459c0c55c89f805a3d556285e`
- run `34695412163`: Architecture / Domain+Application / Android Adapter unit tests / Android compile 全 PASS。
- later Replay UI head must still pass its own exact-head Gate before delivery。

## Security / Secrets

- 不复制旧 `LplHistoricalPostMatchResolver` 内任何 auth/token；
- TJStats 使用 `LPL_TJSTATS_AUTH` / `lplTjstatsAuth` 外部注入；
- Riot 使用既有 `LOL_ESPORTS_API_KEY` 外部注入；
- provider event/match/game ID 只放 identity mapping / provenance / adapter-local metadata；
- 不下载/托管第三方 VOD bytes。

## Pending

- latest exact-head CI after Replay UI；
- Global Riot LiveStats historical Timeline/Stats source；
- per-game winner evidence for global `CompletedGameRecord`（没有明确赢家字段前不发布）；
- regional deep Stats supplements as needed；
- Bilibili / other Replay metadata supplements；
- app README / status / feature baseline / troubleshooting sync；
- final PR + exact-head Gate。
