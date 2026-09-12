# Laner Troubleshooting

## 使用方式

历史故障必须形成稳定 ID，并至少记录：

- 现象；
- 影响范围；
- 首查模块；
- 首查接口/服务；
- 日志前缀或错误码；
- 复现步骤；
- 根因；
- 修复任务；
- 永久回归测试。

## 故障 ID

业务错误优先使用稳定错误码：

```text
LNR-<MODULE>-<STAGE>-<NNN>
```

排障记录可另使用文档级故障 ID，但必须映射回稳定错误码/模块。

---

## PRE_MATCH / Riot LoL Esports

### `LNR-SRC-PRE-001` — LoL Esports credential 未配置

**现象**：PRE 页面显示“全球赛前源 · 不可用”，无赛事目录/赛程；错误信息提示 credential 未配置。

**影响范围**：Riot PRE Adapter；不影响 Core、LIVE/POST 壳及其它 Provider。

**首查模块**：`app/data/riot/RiotGlobalPreMatchSource.kt` → `LanerAppGraph` → BuildConfig。

**配置入口**：
- 环境变量 `LOL_ESPORTS_API_KEY`；或
- Gradle Property `lolEsportsApiKey`。

**禁止操作**：不得把 credential 写入 Git、README、日志、截图、测试 Fixture 或聊天留档。

**恢复**：通过受控构建环境注入 credential 后重新构建。缺失时系统按设计显式降级，不允许填假赛程。

**回归**：`GlobalScheduleServiceTest.allSourcesFailReturnsUnavailableWithoutInventingData` + Android 编译 Gate。

### `LNR-SRC-PRE-002` — Riot global schedule 核心请求失败

**现象**：`getSchedule` 中心页不可用，PRE source 状态 `UNAVAILABLE`。

**首查**：`[Laner:SRC]`、网络连通性、HTTP 状态、Provider 可用性、credential 有效性。

**处理原则**：中心页失败意味着当前 Riot PRE source 没有足够事实，不得展示旧猜测为新事实；后续 Cache/Last-good 接入后必须显式标记缓存来源。

### `LNR-SRC-PRE-003` — 全球赛事目录接口降级

**现象**：`getLeagues` 失败，但 `getSchedule` 仍可能返回真实比赛；页面状态为 `DEGRADED`。

**行为**：已取得的真实赛程继续展示；Competition Catalog 可从真实赛程中补出当前出现的赛事，不因目录子接口失败整页清空。

**首查**：`[Laner:SRC]` context 中 `operation=getLeagues`。

### `LNR-SRC-PRE-004` — 赛程 older/newer 分页降级

**现象**：中心页已获得，但某个 older/newer page 请求失败；页面仍有部分真实赛程，状态为 `DEGRADED`。

**行为**：保留已经成功获得的页面，禁止因补页失败丢弃中心页事实。

**首查**：`[Laner:SRC]` context 中 `direction/page`。

### `LNR-SRC-PRE-006` — Riot Team roster credential 未配置

**现象**：某场比赛的 Roster Pool 为空，赛前上下文进入降级状态；赛事目录/赛程本身可能仍可显示。

**首查模块**：`RiotTeamRosterSource` → `LanerAppGraph` → BuildConfig credential。

**行为**：不得使用赛程里的队伍顺序、历史首发或 UI 缓存补造当前名单。

### `LNR-SRC-PRE-007` — Riot Team roster 请求/映射失败

**现象**：赛事存在，但某一队或两队 roster pool 无法解析。

**首查**：`[Laner:SRC]` / `[Laner:PRE]`、Riot `getTeams` 响应、Team identity alias/crosswalk。

**行为**：Roster Pool 保持未知；绝不能因此把旧首发证据反推成当前完整 roster pool。

### `LNR-SRC-PRE-008` — normalized 官方首发 Feed 不可用

**现象**：Roster Pool 可存在，但 Starting Roster 显示“首发未确认”；网络源错误进入 PRE degraded diagnostics。

**首查模块**：`NormalizedStartingRosterSource` → normalized feed endpoint → `PreMatchContextService` evidence validation。

**关键判断**：
- Feed 不可用 ≠ 名单池可以替代首发；
- Team/League social 与 Official Site 均可成为官方证据；
- 日期、对阵、赛事、五位置任一不匹配都必须拒绝；
- 同 Authority 阵容冲突必须显示 `Conflict`，禁止静默选一个。

**永久回归**：`PreMatchContextServiceTest` 中 roster/evidence/conflict 系列用例。

### `LNR-SRC-PRE-009` — 全球 Staff normalized mirror 不可用

**现象**：比赛与 roster 可正常展示，但 Staff 区域为空并显示降级状态。

**首查模块**：`NormalizedTeamStaffSource` → staff mirror → Team identity mapping。

**权威语义**：当前镜像内容主要由 Riot GCD 派生，因此 Adapter 标记为 `VERIFIED_PROVIDER`；不得把镜像配送地址本身伪装成直接官方 API。

---

## PRE UI / Compose

### `LNR-UI-PRE-001` — PRE Context `produceState` 编译失败

**首次发现**：LNR-011 / GitHub Actions run `34688581238`。

**现象**：Core tests PASS，但 `:app:compileDebugKotlin` 失败，提示 `produceState` 不接受 `key4`，随后出现 `value`/suspend 调用级联错误。

**根因**：`PreMatchScreen` 使用 4 个命名 key 调用 `produceState`，与当前 Compose API 的 overload 解析不兼容。

**修复**：稳定的 `PreMatchContextService` 不作为重启 key，只保留 `scheduleSnapshot / selectedMatchId / refreshNonce` 三个 key。

**回归证据**：GitHub Actions run `34688715420` 的 Android debug compile PASS。

---

## LIVE_MATCH

### Legacy 场间识别行为

旧 RiftLab 已于 2026-09-12 经用户实机确认：场间/未开局与真实新局开局可以正确区分。证据：`docs/audits/2026-09-12_legacy_live_intermission_verification.md`。

迁移 LIVE-001 / LIVE-002 时若出现“场间提前进入 IN_GAME”“上一局结束仍卡在 IN_GAME”“新局开始未切换 Game”等现象，必须视为迁移回归，而不是新产品语义。

### `LNR-LIVE-CORE-001` — 新鲜 IN_GAME 心跳后被旧 POST_GAME 推进

**首次发现**：LNR-013 / GitHub Actions run `34691364933`。

**现象**：同一 Game 已在较新时间点收到 `IN_GAME` heartbeat，但随后到达时间更早的延迟 `POST_GAME` observation 时，Reducer 仍可能把权威 lifecycle 推进到 `POST_GAME`。

**影响范围**：LIVE Match State Core；会造成乱序网络/Provider 延迟下的提前结算、场间误判，并进一步污染 Timeline/HUD。

**首查模块**：`core/domain/.../LiveState.kt` → `LiveMatchStateReducer.reduce()`。

**复现**：
1. 当前 G1 = `IN_GAME`；
2. 收到 observation time=20_000 的 G1 `IN_GAME` heartbeat；
3. 再收到 observation time=15_000 的 G1 `POST_GAME`；
4. 正确结果必须保持 `IN_GAME` 且第二条返回 `STALE_OBSERVATION`。

**根因**：最初 stale 判定额外要求 `lifecycleRank(signal) <= lifecycleRank(current)`；因此虽然 `POST_GAME` 的 observation 时间更旧，但因为 lifecycle rank 更高，错误绕过 stale 判断。

**修复**：同一 Game 中，只要 lifecycle-changing observation 的 `observedAtEpochMillis` 早于当前权威 `lastObservedAtEpochMillis`，一律视为 `STALE_OBSERVATION`；“看起来更靠后的 lifecycle”不能覆盖更晚时间的事实。

**修复任务/提交**：LNR-013，commit `22668b37d22be5969ec59c99ac687f57c52a1ad3`。

**永久回归**：`LiveMatchStateReducerTest.delayedPostGameAfterNewerInGameHeartbeatIsIgnored`。

**回归证据**：GitHub Actions run `34691458209`：Architecture Gate / Domain+Application Tests / Android Debug Compile 全 PASS。

**排障路径**：`LIVE lifecycle 异常 → LiveMatchStateService selected provider/freshness → LiveMatchStateReducer → lastObservedAtEpochMillis → 对应 regression test`。

### `LNR-LIVE-INFRA-001` — Android persistence tests 被 JUnit4 拒绝初始化

**首次发现**：LNR-014 / GitHub Actions run `34692037250`。

**现象**：Architecture/Core tests PASS，但新加入的 `:app:testDebugUnitTest` 在执行 `JsonLiveMatchStateRepositoryTest` 时以 `InvalidTestClassError` 失败，Android build 因 Gate 失败被阻止。

**影响范围**：Android Adapter/Persistence test harness；Repository 业务逻辑本身尚未进入测试方法执行阶段。

**根因**：Kotlin expression-body `@Test` 方法将 `runBlocking` 表达式结果作为 JVM 方法返回值，JUnit4 要求测试方法返回 `void`，因此测试类初始化即失败。

**修复**：所有对应 `@Test` 改为标准 block-body，在方法体内调用 `runBlocking`，保证 JVM signature 为 `void`。

**修复提交**：`86ca106cf6372a4f23f4f83faaa997eef3f5bad5`。

**永久门禁**：`.github/workflows/android-build.yml` 保留 `:app:testDebugUnitTest`，禁止为了绕过失败移除 Adapter/Persistence 单测步骤。

**回归证据**：run `34692350405`、`34692588440` 与 final code run `34692936906` 均通过 Android Adapter unit tests。

**排障路径**：`Android unit test class init fail → 检查 @Test JVM signature → 禁止 expression-body 非 Unit test → :app:testDebugUnitTest`。

---

## 当前阶段

项目已进入 M1 Feature Migration。LNR-010~012 与 LNR-014 的外部 Provider/实机验收按各自状态等待补证；LNR-013 为 LIVE Core DONE，LNR-014 的本地 persistence / Composition / LIVE Application-truth UI 已自动验证通过，Cito 在线链按用户要求 DEFERRED。下一开发轮进入 LNR-015 POST。
