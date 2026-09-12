# ADR-003 — Canonical Game Identity 由 MatchId × Game Number 唯一确定

- 日期：2026-09-12
- 状态：ACCEPTED
- 任务：LNR-015

## Context

POST 历史源会暴露不同的外部小局标识，例如 Riot gameId、LPL bMatchId/TJStats game 序号、OP.GG game id。ADR-002 已冻结“Provider external ID 不得作为 Domain 主键”，但此前没有明确 Laner `GameId` 的统一生成规则。

如果每个 Adapter 自行生成 GameId，会造成同一系列赛同一小局在 LIVE / POST / Replay / Timeline 中出现多套内部身份，破坏跨来源归并和历史回放。

## Decision

Laner 对已解析到 canonical `MatchId` 的系列赛采用统一规则：

```text
GameId = canonical(MatchId, gameNumber)
       = "<matchId>:game:<positive gameNumber>"
```

规则由 `core/domain/GameIdentity.kt` 唯一实现。

### 强约束

- `gameNumber` 必须 > 0；
- Provider Riot/TJStats/OP.GG/Bilibili 等 external game id 不得进入 canonical GameId 字符串；
- Adapter 必须先把外部比赛解析到 canonical MatchId，再根据明确的小局序号生成 GameId；
- 外部 ID 只属于 Identity Mapping / Adapter metadata / provenance，不是 Domain 主键；
- LIVE / POST / Timeline / Replay 对同一 `(MatchId, gameNumber)` 必须得到同一个 GameId；
- 来源无法确认 game number 时，必须保持未映射，不得猜测小局身份。

## Consequences

正向：
- 跨 Provider 小局身份稳定；
- POST 历史恢复可以与 LIVE Timeline 对齐；
- Replay chapter、Awards、Completed Game 可以共享同一内部 GameId；
- 不需要把任何 Provider raw ID 泄漏进 Domain 主键。

代价：
- Adapter 必须可靠解析 series + game number；
- 仅提供 opaque game id、无法定位 series/game number 的来源不能直接发布 Game Domain fact。

## Compatibility

此规则是对 ADR-002 Identity Resolution 的具体化，不 supersede ADR-002。后续如需改变 canonical GameId 算法，必须新增 ADR 并提供迁移策略，避免已持久化 Timeline/Archive 身份断裂。
