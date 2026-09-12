# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay、Media、OTA、权限、具体 Source Adapter 与本地持久化 Adapter 组装。

## 输入
只消费 `:core:application` 公开 Query/Use Case/Port 契约与 `:core:domain` 展示所需只读模型。

## 输出
Android 用户界面与平台副作用。

## 依赖
允许依赖 `:core:application`、`:core:domain` 与 Android/Compose/网络/文件系统等平台库。Core 禁止反向依赖本模块。

## Public API / Composition Root
- `MainActivity`
- `LanerAppGraph`
- `LanerRoot`
- `PreMatchScreen`
- `LiveMatchScreen`
- `RiotGlobalPreMatchSource`
- `RiotTeamRosterSource`
- `NormalizedStartingRosterSource`
- `NormalizedTeamStaffSource`
- `JsonLiveMatchStateRepository`
- `JsonLiveTimelineRepository`

所有 Adapter 只实现 Core Port，不向 UI 暴露 Provider payload。

## PRE 数据链

```text
Riot LoL Esports
  → RiotGlobalPreMatchSource
  → GlobalPreMatchSourcePort
  → GlobalScheduleService
  → GlobalScheduleSnapshot
  → PreMatchScreen

selected ScheduledSeries
  → PreMatchContextService
      ├─ TeamRosterSourcePort → RiotTeamRosterSource
      ├─ StartingRosterSourcePort → NormalizedStartingRosterSource
      └─ TeamStaffSourcePort → NormalizedTeamStaffSource
  → MatchPreContextSnapshot
  → PreMatchScreen
```

PRE 页面按“赛前”一级阶段组织：赛事筛选、比赛焦点、官方首发证据、名单池、Staff、Recent Form、H2H 与全球赛程都属于同一个阶段页面，不拆成赛区孤岛。

## LIVE 数据链

当前基础链：

```text
GlobalScheduleService
  → ScheduledSeries / canonical match target
  → LiveMatchSourceQuery
  → LiveMatchStateService
      → LiveStateSourcePort[]
      → LiveMatchStateRepository
  → LiveStateResolution
  → LiveMatchScreen

LiveGameSnapshot / MatchEvent
  → LiveTimelineService
  → JsonLiveTimelineRepository
```

当前 `LanerAppGraph` 已接真实本地 `JsonLiveMatchStateRepository` 与 `JsonLiveTimelineRepository`。

Cito 在线验证暂缓，因此当前真实 LIVE source list 可以为空。该状态是合法降级：
- `LiveMatchStateService` 返回 `UNAVAILABLE`；
- 若存在 last-known state，则保留本地权威状态；
- 若不存在，则保持 `UNKNOWN`；
- UI 必须明确显示 `NO VERIFIED SOURCE`，不得猜测 IN_GAME/POST_GAME。

LIVE 页面只能通过 Application Service 获得状态，禁止 UI 直接调用 Riot/Cito/微博/OCR/AI。

## LIVE local persistence

- State schema：`schema_version=1`；
- Timeline schema：`schema_version=1`；
- canonical ID 经 SHA-256 生成稳定文件名；
- JSON 内保留完整 canonical ID 并在读入时复核；
- 写入使用 sibling temp file + atomic replace；
- corrupt JSON / unsupported schema 必须显式失败，禁止静默返回空对象；
- Timeline 只保存标准化 Snapshot/Event，不保存 Provider raw payload/free text。

## Credential
LoL Esports credential 不进入 Git，只允许：
- 环境变量 `LOL_ESPORTS_API_KEY`；
- Gradle Property `lolEsportsApiKey`。

缺失时是合法降级状态，不得硬编码 fallback key。

Cito credential 当前未接入 Laner 正式配置；在线验收状态为 `WAITING EXTERNAL TEST / DEFERRED`。不得把 fixture/contract test 描述成真实在线支持。

## 日志
- App：`[Laner:APP]`
- Source：`[Laner:SRC]`
- PRE：`[Laner:PRE]`
- LIVE：`[Laner:LIVE]`
- Overlay/Update/AI 后续各自使用独立模块前缀。

## 失败
平台/Provider 能力不可用时必须明确降级，不得制造赛事事实。

PRE 当前错误码：
- `LNR-SRC-PRE-001` credential 未配置；
- `LNR-SRC-PRE-002` global schedule 中心请求失败；
- `LNR-SRC-PRE-003` competition catalogue 降级；
- `LNR-SRC-PRE-004` schedule pagination 降级；
- `LNR-SRC-PRE-006` Riot Team roster credential 未配置；
- `LNR-SRC-PRE-007` Riot Team roster 请求/映射失败；
- `LNR-SRC-PRE-008` normalized official starting-roster feed 不可用；
- `LNR-SRC-PRE-009` normalized global staff feed 不可用；
- `LNR-UI-PRE-001` PRE context Compose state wiring 编译回归记录。

LIVE 当前原则：
- Provider 缺失 → `UNAVAILABLE`；
- Provider 冲突 → `CONFLICT`；
- 部分失败 → `DEGRADED`；
- local persistence corrupt/unsupported schema → 显式异常并进入故障处理，禁止静默清空；
- Cito 未在线验证时不得标记 `READY/SUPPORTED`。

## 测试
- Core：`:core:domain:test :core:application:test`
- Android Adapter/Persistence：`:app:testDebugUnitTest`
- Android build：`:app:assembleDebug`
- CI 顺序：Architecture Gate → Core Tests → App Unit Tests → Android Build。
- LNR-010 自动化证据：run `34687580424` PASS。
- LNR-011 UI 修复后：run `34688715420` PASS。
- LNR-014 Android unit-test Gate 首次 run `34692037250` FAIL，JUnit4 test signature 问题已留档；修复后 `afa4bf7f...` 对应 run `34692350405` 全 PASS。
- Cito 真实在线 + Android 实机：`WAITING EXTERNAL TEST / DEFERRED`。

## 故障定位

PRE：`MainActivity → LanerRoot → PreMatchScreen → GlobalScheduleService/PreMatchContextService → Port → Adapter`。

LIVE：`MainActivity → LanerRoot → LiveMatchScreen → LiveMatchStateService/LiveTimelineService → Port → Repository/Source Adapter`。

数据错误不得先在 UI 内补丁修正；Provider payload 解析错误必须回到 Adapter，lifecycle/仲裁错误必须回到 Application/Domain。
