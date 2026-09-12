# Laner Testing Policy

## 原则

测试是交付证据，不是装饰。任何“已支持”“已修复”“已完成”必须能映射到实际执行过的测试。

## 每个任务最低报告

- 正向测试；
- 负向测试；
- 边界测试；
- 非目标对照；
- 历史 Bug 回归（如适用）；
- Integration / E2E / 实机（按影响范围）。

状态必须区分：

- `PASS`
- `FAIL`
- `NOT EXECUTED`
- `WAITING EXTERNAL TEST`
- `N/A`（必须说明原因）

## 架构测试

工程骨架建立后必须逐步自动检查：

- Core/Domain 不得依赖 UI/平台/网络/数据库具体实现；
- 模块依赖不得成环；
- Adapter 不得把外部类型泄漏到领域接口；
- UI 不得直接依赖具体数据源；
- 测试依赖不得泄漏到生产实现。

## 数据源 Contract Test

每个 Source Adapter 必须兑现统一数据契约。声明完整支持时不得通过修改 Contract Test 来适配 Provider 的缺陷；Provider 不足应声明降级并单独测试。

Provider 测试必须区分：

1. **Core/Contract 自动测试**：不需要真实 credential，可以验证标准化、仲裁、降级、错误码和边界；
2. **在线 Integration**：必须使用受控 credential，验证真实 API payload；
3. **Android 实机**：验证系统时区、网络、Compose 展示和平台生命周期。

第 1 层通过不能冒充第 2/3 层通过。

## Match State 测试

统一赛事状态能力建立后必须覆盖：

- 正常状态迁移；
- 重复事件；
- 乱序事件；
- 来源延迟；
- 来源冲突；
- 数据缺失；
- 重连/恢复；
- 系列赛与小局边界；
- 非目标赛事不被误更新。

旧 RiftLab 已有用户实机证据证明“场间 vs 新局真实开局”识别可用。未来 LIVE Match State Engine 必须把该行为作为迁移回归测试，而不是重新定义。

## Bug 规则

任何关键 Bug 修复必须形成永久回归用例，并在 `TROUBLESHOOTING.md` 登记故障 ID。

## LNR-000

本任务仅新增 Markdown 工程治理文件，无业务代码、脚本或构建系统：

- 文档存在性：通过 GitHub 远端反查验证；
- 业务 Unit/Integration/E2E：`N/A`；
- 实机：`N/A`；
- 脚本 Parser：`N/A`。

## LNR-010

### 自动化

GitHub Actions run `34687580424`：

- Architecture boundary gate：`PASS`；
- Domain/Application tests：`PASS`；
- Android debug compile：`PASS`。

已锁定的行为：
- provider completed 无 BO 胜场/winner 证据不得结束系列赛；
- 有真实胜场证据才允许 COMPLETED；
- Schedule `EVENT_LIVE` 不具有 `IN_GAME` 语义；
- 重复 Series 由 Authority/Timestamp/Revision 统一裁决；
- `getLeagues` 缺失时可从真实 schedule 事实补目录；
- 全源失败时返回 UNAVAILABLE，不制造数据。

### 未执行 / 外部验收

- 带真实 LoL Esports credential 的 `getLeagues/getSchedule` 在线集成：`WAITING EXTERNAL TEST`；
- Android 实机全球赛事目录/本地时区赛程展示：`WAITING EXTERNAL TEST`。

## LNR-011

### Core / Contract 自动测试

`PreMatchContextServiceTest` 已覆盖：

- 五人 roster pool 没有官方证据时，Starting Roster 必须保持 `Unknown`；
- 官方证据只有日期、对阵、赛事、五位置完整校验通过后才可 `Confirmed`；
- 错日期 / 错对手 evidence 被拒绝；
- 重复位置/不完整五位置 evidence 被拒绝；
- 同阵容多条官方 evidence 可标 `crossConfirmed`；
- 同 Authority 不同阵容不得静默覆盖，必须 `Conflict`；
- Recent Form 只读 `COMPLETED` Series 且排除当前比赛；
- H2H 必须双方同时存在并明确左队视角。

### CI 历史

GitHub Actions run `34688581238`：

- Architecture boundary gate：`PASS`；
- Domain/Application tests：`PASS`；
- Android debug compile：`FAIL`。

根因：Compose `produceState` 使用 4 个命名 key，与项目当前 Compose API 重载不兼容。修复仅改变 UI 状态加载 key 写法，不改变比赛业务语义。

GitHub Actions run `34688715420` 修复后：

- Architecture boundary gate：`PASS`；
- Domain/Application tests：`PASS`；
- Android debug compile：`PASS`。

### 未执行 / 外部验收

- 带真实 LoL Esports credential 的 Riot Team roster pool：`WAITING EXTERNAL TEST`；
- normalized official starting-roster feed 网络配送：`WAITING EXTERNAL TEST`；
- normalized global staff feed 网络配送：`WAITING EXTERNAL TEST`；
- Android 实机比赛点选、首发/冲突、名单池、Staff、Form/H2H 展示：`WAITING EXTERNAL TEST`。

CI 不持有真实 Provider credential，也不是 Android 实机；自动化 PASS 不得冒充上述外部验收 PASS。

## LNR-015 — Global POST

### Domain / Application 自动回归

已覆盖：

- FINAL Series winner/score consistency；
- PARTIAL Series 不得提前声明 winner；
- stat unknown 使用 null，不与真实 0 混淆；
- DERIVED authority 不得发布 MVP/POG；
- canonical GameId 仅由 `(MatchId, gameNumber)` 生成；
- Provider raw game ID 不进入 canonical identity；
- Result / Game / Award / Replay 独立成功/失败；
- 同 Award slot 冲突显式化；
- Replay 多 Provider 可并存；
- archive 仅填外部缺失，不参与和新事实的投票；
- conflict 不覆盖 last-known-good archive；
- `PostSourceCapability` 决定来源适用性，Application 无赛区业务分支；
- Historical POST frame 必须匹配 canonical MatchId/GameId/gameNumber；
- 错 identity historical frame 被拒绝，不能污染 repository；
- LIVE + POST 真实帧可以合并到同一个 canonical GameTimeline；
- Timeline 允许 LIVE/POST source，拒绝 PRE/AI source。

### Android Adapter / Fixture tests

已覆盖：

- `JsonPostMatchArchiveRepository` round-trip / corruption / schema；
- `JsonProviderMatchIdentityRepository` round-trip / newest-observation-wins / schema；
- POST target 只允许 COMPLETED schedule；
- Awards mirror canonical teams + date matching；
- Awards gameNumber 不制造 provider-derived GameId；
- LCK + Worlds 使用同一 Riot global Result parser；
- LCK + Worlds 使用同一 Riot global Replay identity resolver；
- LCK + Worlds 使用同一 Riot historical team mapping；
- EventDetails VOD → canonical ReplayAsset；
- LiveStats frame fixture → canonical POST HistoricalTimelineFrame；
- gold/kills/objectives/player champion 等真实帧字段进入标准模型。

### CI 历史

run `34693494001`：
- Architecture：`PASS`；
- Domain/Application：`FAIL`；
- 根因：测试 fixture 使用非法 `LNR-SRC-POST-TEST` 错误码格式；
- fix `ca805e77f89bdb65311c24e5c12e3ed056d7e83a`；
- run `34693630753`：四道 Gate 全 `PASS`。

run `34694930111`：
- global POST capability routing：四道 Gate 全 `PASS`。

run `34695412163`：
- global Riot Result/Replay/Identity baseline：四道 Gate 全 `PASS`。

run `34695777894`：
- Architecture：`PASS`；
- Domain/Application：`FAIL`；
- 根因：Application 允许 POST historical frame，但 Domain `TimelineSnapshotPoint` 仍只允许 LIVE source；
- fix `f60f87be12e676e3623bac73d586a144f84b3f17`；
- regression commit `7451c302f106fa1f4d636a8ac77f1580b8dd672c` / run `34695924994`：四道 Gate 全 `PASS`。

current code/UI head `0681c3b20f2e6cad4cd6fb52bf90a4912e299608` / run `34696081645`：
- Architecture boundary gate：`PASS`；
- Domain/Application tests：`PASS`；
- Android Adapter unit tests：`PASS`；
- Android debug compile：`PASS`。

### 未执行 / 外部验收

以下不能被 fixture/CI PASS 冒充：

- 真实 `LOL_ESPORTS_API_KEY` 下的 Riot global schedule Result fetch：`WAITING EXTERNAL TEST`；
- Riot `getEventDetails` 真实 VOD payload：`WAITING EXTERNAL TEST`；
- Riot LiveStats 历史 window 保留策略与真实历史回放：`WAITING EXTERNAL TEST`；
- Android 实机 POST Result/Replay/Historical Timeline UI：`WAITING EXTERNAL TEST`；
- Global per-game winner evidence / CompletedGame 完整恢复：`IN PROGRESS`。

因此 LNR-015 自动测试收口后最多进入 `WAITING EXTERNAL TEST`，不得标记 `DONE`。
