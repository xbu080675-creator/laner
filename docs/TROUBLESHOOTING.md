# Laner Troubleshooting

## 使用方式
历史故障必须形成稳定 ID，并至少记录：现象、影响范围、首查模块/接口、日志或错误码、复现、根因、修复任务、永久回归测试。

业务错误码格式：`LNR-<MODULE>-<STAGE>-<NNN>`。

## PRE_MATCH

### `LNR-SRC-PRE-001~004` — Riot Global Schedule
- `001` credential 未配置；只允许 `LOL_ESPORTS_API_KEY` / `lolEsportsApiKey` 外部注入。
- `002` global schedule 核心请求失败 → UNAVAILABLE，不造数据。
- `003` catalogue 子接口失败 → DEGRADED，保留已取得真实 schedule。
- `004` older/newer pagination 失败 → DEGRADED，保留成功页面。

### `LNR-SRC-PRE-006~009` — Roster / Starting / Staff
Roster Pool 不得替代首发；Starting evidence 必须校验日期、对阵、赛事、五位置；同 Authority 冲突显式保留；Staff mirror 不冒充直接 Riot API。

### `LNR-UI-PRE-001` — PRE Context `produceState` 编译失败
首次发现：LNR-011 / run `34688581238`。四 named keys 与当前 Compose overload 不兼容；修成三个稳定 keys。run `34688715420` PASS。

## LIVE_MATCH

### Legacy 场间识别行为
2026-09-12 用户实机确认旧 RiftLab 可区分场间/未开局与真实新局。迁移后若提前 IN_GAME、上一局卡住或新局不切换，均视为回归。

### `LNR-LIVE-CORE-001` — 新鲜 IN_GAME 被旧 POST_GAME 推进
首次发现：LNR-013 / run `34691364933`。根因是 stale 判定错误依赖 lifecycle rank。修复：同一 Game 中，任何早于当前 freshness 的 lifecycle-changing observation 均为 stale。永久回归：`delayedPostGameAfterNewerInGameHeartbeatIsIgnored`。run `34691458209` PASS。

### `LNR-LIVE-INFRA-001` — JUnit4 拒绝 expression-body @Test
首次发现：LNR-014 / run `34692037250`。Kotlin expression-body 产生非-void JVM test signature。修复 commit `86ca106c...`，永久保留 `:app:testDebugUnitTest` Gate。后续 runs PASS。

## POST_MATCH

### `LNR-POST-TEST-001` — 非法测试 ErrorCode 导致 POST Core 测试提前失败
首次发现：LNR-015 / run `34693494001`。

**现象**：Architecture PASS，Domain/Application FAIL；POST aggregation 尚未进入预期 failure path。

**根因**：fixture 使用非法 `LNR-SRC-POST-TEST`，违反三位数字错误码格式，`ErrorCode` 构造即抛异常。

**修复**：fixture 改为合法 `LNR-SRC-POST-999`，不放宽生产 ErrorCode 规则。fix `ca805e77f89bdb65311c24e5c12e3ed056d7e83a`。

**永久回归/证据**：`PostMatchServiceTest.awardFailureDoesNotEraseValidSeriesResult`；run `34693630753` 全 Gate PASS。

### `LNR-POST-TIMELINE-001` — POST 历史真帧被 Domain source-class 不变量拒绝
首次发现：LNR-015 / run `34695777894`。

**现象**：Application 已允许 `POST_MATCH_SOURCE` historical frame 进入统一 Timeline，但构造 `TimelineSnapshotPoint` 时 Domain 抛 `IllegalArgumentException`。

**根因**：LNR-013 初始 Timeline 不变量只允许 `LIVE_MATCH_SOURCE`；迁移全球历史 Timeline 时 Application 与 Domain contract 不一致。

**修复**：Domain 明确允许 `LIVE_MATCH_SOURCE` 与 `POST_MATCH_SOURCE` 的赛事事实进入 canonical Timeline，同时继续拒绝 PRE/AI；不通过修改测试绕过。

**永久回归**：
- POST 真实帧可与 LIVE 帧进入同一 canonical `GameTimeline`；
- PRE/AI provenance 仍被拒绝；
- wrong MatchId/GameId historical frame 由 `PostTimelineService` 以 `LNR-APP-POST-005` 拒绝。

**回归证据**：run `34695924994` 全 Gate PASS；最终 POST UI/code head run `34696081645` 全 Gate PASS。

**排障路径**：`POST 历史恢复失败 → PostTimelineService identity validation → TimelineSnapshotPoint source class → LiveTimelineService ingest → JsonLiveTimelineRepository`。

## 当前阶段
M1 Feature Migration。LNR-010~012、LNR-014 与 LNR-015 的真实 Provider/实机验收按证据等待补测；LNR-013 Core DONE；Cito 在线链按用户要求 DEFERRED。LNR-015 已完成 Global-first POST 自动化实现，正在进行 PR/merge 收口。
