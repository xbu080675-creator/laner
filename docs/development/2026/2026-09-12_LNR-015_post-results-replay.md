# LNR-015 — POST Result / Game Archive / Stats / Timeline Archive / Replay Domain

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `a530a8a88d9cc1ee649f0d95732b73e42675510e`
- Working branch: `feature/lnr-015-post-results-replay`
- Current status: `IN PROGRESS`

## Request / Goal

完整迁移旧 RiftLab 的赛后能力，但不复制旧 `MatchDetailRepository` / `MatchTimelineStore` God Store。赛后事实必须强类型拆分、逐来源取证、可独立降级，并保留历史恢复能力。

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
- `RiotLiveStatsHistoryResolver.kt`：使用真实历史 LiveStats window，不插值；上游不再保留则留缺口；
- `GlobalVerifiedAwardsProvider.kt` + `data/global/match_awards.json`：奖项来自 provenance-preserving verified mirror，不从 KDA/伤害推 MVP/POG；
- `OpggMatchSupplementProvider.kt`：第三方 supplement 不冒充官方；部分 stat 缺失时未知不能写成 0；
- `RiotVodRepository.kt`：录像 metadata 与播放实现分离；
- `BilibiliVodRepository.kt`：仅保存 BVID/CID/page/chapter/offset 元数据，不下载/托管视频；
- `MatchTimelineStore.kt`：event-sourced timeline 与保守 delta 行为值得保留，但 Android Store 不复制，复用 Laner canonical `GameTimeline`。

## Locked POST Fact Boundaries

```text
Series Result
!= Completed Game Archive
!= Player Stats
!= Awards
!= Replay Metadata
```

规则：
- FINAL Series 必须有 winner 且与比分一致；
- PARTIAL Series 不得提前声明 winner；
- stat 缺失使用 null，不用 0 表示未知；
- `DataAuthority.DERIVED` 禁止发布 MVP/POG；
- Replay 是 metadata，不是播放器实现；
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
- `PostMatchBundle`。

### Application
- `PostMatchQuery`；
- independent ports: Result / CompletedGame / Award / Replay；
- `PostMatchService`；
- per-capability failures；
- canonical match validation；
- result/game/award arbitration；
- multi-provider Replay coexistence；
- explicit `READY / DEGRADED / CONFLICT / UNAVAILABLE`。

### Android / UI
- `PostMatchScreen` 替代 POST 空壳；
- 只选择 `ScheduleState.COMPLETED` 作为 POST 查看目标；
- no-source 时明确 UNAVAILABLE，不复用 PRE score 冒充 POST result；
- `VerifiedAwardsMirrorSource` 为第一条不需要私钥的真实 POST source；
- Awards UI 展示 verified award 与 provenance provider。

## Design Corrections During Development

### Award Game identity

Verified Awards mirror 的小局奖项只有 game number，没有 canonical GameId。

错误方向（未保留）：用 `${matchId}:game:N` 合成 GameId。

纠正：
- `VerifiedPostAward` 独立增加 `gameNumber`；
- `gameId` 只有真正完成 identity mapping 时才填；
- Application award slot 可按 gameId 或 gameNumber 区分；
- 禁止为了 UI/去重制造 canonical identity。

## Tests

### Domain regressions
- final winner/score consistency；
- tied/winnerless final rejected；
- partial cannot declare winner；
- missing player stats stay null；
- derived authority cannot publish awards；
- replay metadata remains provider-neutral。

### Application regressions
- award source failure does not erase valid result；
- conflicting award winners are visible；
- stronger fact selected without hiding conflict；
- wrong-match fact rejected；
- fresh final can beat stale partial；
- multiple replay providers coexist。

### UI/Adapter regressions
- POST target only comes from COMPLETED schedule rows；
- live/upcoming cannot become POST target；
- Awards mirror matches by canonical teams + date；
- Awards mirror game number does not synthesize GameId。

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

## Security / Secrets

- 不复制旧 `LplHistoricalPostMatchResolver` 内任何 auth/token；
- Awards mirror 无私钥；
- Riot VOD 身份映射未解决前不接 `getEventDetails`，避免把 raw Riot eventId 偷塞进 Domain；
- 不下载/托管第三方 VOD bytes。

## Pending

- latest POST UI + Verified Awards exact-head CI；
- Result Source；
- Completed Game historical source；
- Player/team terminal stats；
- Timeline archive/read integration；
- Replay sources / metadata adapters；
- app README / status / feature baseline / troubleshooting sync；
- final PR + exact-head Gate。
