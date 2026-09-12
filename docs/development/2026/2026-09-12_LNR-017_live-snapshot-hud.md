# LNR-017 — Global LIVE Snapshot / Timeline / Match HUD

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
- Working branch: `feature/lnr-017-live-snapshot-hud`
- Status: `IN PROGRESS`

## Request / Goal
为今晚真实赛事测试优先打通：Global Riot LIVE 真帧 → canonical `LiveGameSnapshot` → Application validation/Timeline ingest → 赛中可视化。保持 Global-first，不按 LPL/LCK 复制业务。

## Constitution Preflight
`PASS`。已读取最新工程宪法、main、IMPLEMENTATION_STATUS、LNR-016 交付事实、LIVE Source/Application/Domain/UI 与 Riot historical frame parser。

## Acceptance
1. Riot live frame 可标准化出双方经济/击杀/塔/龙/男爵与选手 level/KDA/CS/gold/champion；
2. raw provider game/team/player id 不成为 canonical Game/Match identity；
3. Application 二次校验 MatchId/GameId/gameNumber/team identity 后才写 Timeline；
4. UI 只读 Application state，展示真实快照及领先差，不制造缺失值；
5. BLG/AL 与其他赛区共用同一 parser/Port；
6. Architecture/Core/App unit/Android build Gate 必须通过；
7. 真实在线/实机证据与 fixture/CI 严格分离。

## Non-goals
- 不在本任务实现 AI Insight；
- 不实现完整可拖拽系统 Overlay/RiftScreen 编辑器；
- 不从经济/击杀推断不存在的赛事事实；
- Cito 继续 DEFERRED。

## Planned design
`LiveSnapshotSourcePort → LiveSnapshotService → canonical validation → LiveTimelineService → Live Snapshot View`。

生命周期权威仍由 `LiveMatchStateService` 负责；Snapshot Service 不自行推进 Match Lifecycle。

## Tests planned
- positive real-frame fixture parse；
- null/missing metrics preserved；
- wrong match/game/team rejected；
- duplicate same-second frame idempotent via Timeline；
- LCK/LPL/global fixture same parser；
- UI projection does not turn null into zero。

## Rollback
Feature branch isolated. No schema migration planned; Timeline continues existing schema. Before merge, exact-head + PR Gate required.
