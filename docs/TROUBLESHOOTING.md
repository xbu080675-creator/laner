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

**影响范围**：Riot PRE Adapter；不影响 Core、LIVE/POST 壳及其它未来 Provider。

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

---

## LIVE_MATCH 已锁定回归行为

### Legacy 场间识别行为

旧 RiftLab 已于 2026-09-12 经用户实机确认：场间/未开局与真实新局开局可以正确区分。证据：`docs/audits/2026-09-12_legacy_live_intermission_verification.md`。

迁移 LIVE-001 / LIVE-002 时若出现“场间提前进入 IN_GAME”“上一局结束仍卡在 IN_GAME”“新局开始未切换 Game”等现象，必须视为迁移回归，而不是新产品语义。

---

## 当前阶段

项目已进入 M1 Feature Migration。LNR-010 自动化验证通过，真实 Riot 在线拉取/Android 实机展示仍为 `WAITING EXTERNAL TEST`。
