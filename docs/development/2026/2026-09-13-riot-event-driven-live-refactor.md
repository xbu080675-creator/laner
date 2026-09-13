# 2026-09-13 Riot Event-Driven Live Refactor

## 背景

LPL AL vs BLG 实测中，Riot Event ID 已正确锁定为 `117155436343202202`，但应用在 G3 已经开始后仍停留在 `BETWEEN GAMES · COMM · GAME COMM:13505:G3`。公开参考工程 `AndyDanger/live-lol-esports` 在同一 Riot LiveStats 体系下可稳定进入 G3 实时帧。

## 参考工程研究结论

只学习公开协议使用方式和状态机职责，不复制 GPL-3.0 实现代码。

参考工程核心链路：

1. Event ID → `getEventDetails`
2. `match.games[]` 决定当前小局
3. 当前 game 的真实 Riot `gameId`
4. 新 gameId 首次调用 `/window/{gameId}`，不传 `startingTime`，完成 bootstrap
5. 后续持续请求 `/window/{gameId}?startingTime=...`
6. UI 始终消费当前 game 的最新 frame

关键点：历史 LiveStats frame 不参与“当前是哪一局”的选择。

## 根因

旧 `LolEsportsLiveDataSource` 只在 `knownGames` 为空时读取一次 EventDetails。G2 期间读取到的 `knownGames` 会长期保留，因此 G2 的 `inProgress` 状态可能在本地永久陈旧；即使 Riot 已将 G3 切为 `inProgress`，客户端仍可能优先选择旧 G2。之后再叠加 COMM 局间状态，就表现为应用一直停在 BETWEEN_GAMES。

此前用赛程 `gameWins` 推导下一局只能缓解部分场景，因为赛程比分本身也可能滞后。

## 本次架构调整

新增 `RiotEventDrivenLiveDataSource`，职责划分为：

- `WAIT_EVENT`：解析/锁定 Riot Event
- `EVENT_RESOLVED`：Event 已建立
- `GAME_RESOLVED`：仅依据最新 EventDetails 选择当前/下一小局
- `BOOTSTRAPPING`：对新 gameId 调用无 `startingTime` 的 `/window/{gameId}`
- `LIVE`：持续读取当前 game 的 aligned LiveStats window
- `BETWEEN_GAMES`：EventDetails 暂无可绑定小局

`GlobalOfficialLiveDataSource` 的 Riot Provider 已切换到该新 Adapter。Core、UI、MatchIdentityPolicy、其它 Provider 不改。

## 小局选择规则

唯一权威来源：Riot EventDetails `match.games[]`。

顺序：

1. 最新 `inProgress`
2. 第一个 `unstarted`
3. 最后一个 `completed`

不再使用：

- COMM 状态推 Riot gameId
- 赛程比分推下一局
- 历史 LiveStats 是否存在来判断当前小局

## 轮询策略

公开参考工程使用 500 ms 主循环。考虑 Riot LiveStats frame 本身按较粗时间格更新，RiftLab 采用分阶段频率：

- 局间/切局/bootstrap：500 ms
- LIVE window：1000 ms
- EventDetails：1000 ms
- idle/error 重试：1000 ms

这样可把 G2→G3 接管延迟控制在约 0.5–1 秒级，同时避免长期 500 ms 对同一 10 秒窗口做大量重复请求。

## 网络负载预期

稳定 LIVE 时正常路径约为：

- EventDetails：1 req/s
- Live window：通常 1 req/s（首个 aligned candidate 成功时）

窗口 miss 时 cursor 会尝试回退候选，短时请求数会增加；因此不采用长期 500 ms live polling。

## 文件

- `app/src/main/java/com/riftlab/app/data/RiotEventDrivenLiveDataSource.kt`
- `app/src/main/java/com/riftlab/app/data/LplOfficialLiveDataSource.kt`

## 验证要求

在合入 main 前必须通过：

1. Compile Diagnostics
2. Core boundary / repository link gates（由现有 workflow 覆盖）
3. Android Build
4. 实机验证 `G2 completed → G3 inProgress` 时，APP 的 GAME 从 `COMM:*:G3` 切换为 Riot 真 gameId，并出现 `Riot EventDriven Live · G3 · POLL 1s`
