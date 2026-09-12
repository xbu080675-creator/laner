# Laner vNext Architecture Freeze

- Task: `LNR-003`
- Status: `FROZEN / M0`
- Applies from: `feature/migration-foundation`
- Legacy behavior baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`

## 1. 冻结结论

Laner 保持 Android 原生技术路线以换取迁移速度，但业务架构彻底重做。

### 技术栈

- Kotlin: `2.3.21`
- Android Gradle Plugin: `9.4.0`
- JDK: `17`
- compileSdk: `36`
- targetSdk: `36`
- minSdk: `28`
- Jetpack Compose BOM: `2026.06.00`
- Coroutines: `1.11.0`
- OkHttp: `4.12.0`
- Media3: `1.11.0`
- Coil: `2.7.0`
- LiteRT-LM / ML Kit 等只在对应 Adapter 迁移任务中加入，不提前污染 Core。

技术栈沿用旧版已验证版本，不在“架构重做”同时引入无必要框架迁移风险。

## 2. 模块 DAG

```text
:core:domain
      ↑
:core:application
      ↑
:app
```

后续按需要增加：

```text
:adapter:network
:adapter:persistence
:adapter:android
:adapter:ai
```

但第一轮不为了“看起来模块很多”提前拆空模块。

### `:core:domain`

纯 Kotlin。禁止 Android / Compose / OkHttp / JSON /数据库 / Provider SDK。

责任：

- Global Competition identity；
- PRE/LIVE/POST phase；
- Match lifecycle；
- Source class / provenance / authority / freshness；
- facts / revisions；
- Match / Game / Team / Player 基础领域模型；
- standardized live events；
- domain invariants。

### `:core:application`

纯 Kotlin，依赖 `:core:domain`。

责任：

- Ports；
- Source registry；
- Source arbitration；
- use cases；
- phase query；
- spectator / coach query shaping contracts；
- no Android types。

### `:app`

Android / Compose composition root。

责任：

- Activity / Service / Overlay；
- Compose UI；
- Android permission / lifecycle；
- concrete Adapters；
- DI wiring（当前手工 composition root，未证明需要 DI framework 前不引入）；
- Media3 / WebView / OTA / Android file/network adapters。

UI 只能消费 Application 的 public query/use-case contract。

## 3. 一级产品轴

```text
PRE_MATCH
LIVE_MATCH
POST_MATCH
```

所有用户可见业务页面必须归属其一。设置/诊断属于共享工具，不构成第四业务阶段。

## 4. Match Lifecycle

冻结基础枚举：

```text
PRE_EVENT
EVENT_LIVE_PRE_GAME
DRAFT
LOADING
IN_GAME
POST_GAME
BETWEEN_GAMES
SERIES_COMPLETE
UNKNOWN
```

映射：

```text
PRE_EVENT                              → PRE_MATCH
EVENT_LIVE_PRE_GAME / DRAFT / LOADING → LIVE_MATCH
IN_GAME / POST_GAME / BETWEEN_GAMES    → LIVE_MATCH
SERIES_COMPLETE                        → POST_MATCH
UNKNOWN                                → 保留当前可信 phase 或 UNKNOWN，不由 UI 猜
```

`赛事开始 != 游戏开始` 是 Domain invariant。

## 5. Source Classes

冻结：

```text
PRE_MATCH_SOURCE
LIVE_MATCH_SOURCE
POST_MATCH_SOURCE
GLOBAL_AI_ASSIST
```

前三类可以提供/验证事实。`GLOBAL_AI_ASSIST` 永远不是赛事事实权威来源。

## 6. Fact / Provenance / Revision

任何跨来源事实必须能够携带：

- internal entity id；
- value；
- provider id；
- source class；
- authority；
- freshness；
- observedAt；
- sourceTimestamp（如有）；
- revision；
- verification state。

首个可信源允许 provisional publish；高权威源后到时允许确认或修订，但禁止静默覆盖历史。

## 7. Source Arbitration

唯一仲裁点位于 Application。

原则优先级：

1. 已验证事实优先于推断；
2. 更高 Authority 优先；
3. 同 Authority 下更符合 Freshness SLA 的数据优先；
4. 同级冲突不得静默选一个，必须记录 conflict；
5. 失去网络时可用 last-good，但 UI 必须知道这是 cache；
6. Provider 不得自行决定全局事实优先级。

## 8. Global Competition

赛区不建业务分支。

统一内部 ID：

- `CompetitionId`
- `EditionId`
- `StageId`
- `TeamId`
- `PlayerId`
- `MatchId`
- `GameId`

Provider external ID 只能放 identity mapping，不作为 Domain 主键。

## 9. Persona Query

同一 Domain Fact 只计算一次。

```text
Domain/Application facts
        ↓
Spectator Query  → 结论 + 中文解释 + 最少必要证据
Coach Query      → 事实 + 变化 + 原因 + 代价 + 下一窗口 + provenance
```

不得复制两套业务逻辑。

## 10. Event Model

首批标准事件：

- MatchStateChanged
- DraftChanged
- Kill
- MultiKill
- TeamFightWindow
- TowerDestroyed
- ObjectiveTaken
- GoldLeadChanged
- ItemSpike
- PlayerStateChanged
- Pause
- Resume

事件必须有：`matchId/gameId/sequence/gameTime/source/evidence`。

事件顺序不得假设网络天然可靠；Application 必须支持重复、乱序、重连后的幂等归并。

## 11. Persistence Boundary

Core 不知道 Room / SQLite / JSON / file path。

后续 Port：

- MatchRepository
- TimelineRepository
- ArchiveRepository
- PreferenceRepository
- CacheRepository

旧 RiftLab 本地数据通过 importer/migration 进入，不直接把旧 JSON / SharedPreferences 结构变成新 Domain。

## 12. Android 平台边界

以下能力只能在 `:app` 或后续 Android Adapter：

- Overlay permission / WindowManager；
- Intent / Uri；
- Media3 / WebView；
- APK installer；
- Notification；
- filesystem；
- LiteRT Android backend；
- ML Kit。

## 13. 第一批错误码

格式：`LNR-<MODULE>-<STAGE>-<NNN>`。

首批模块：

- `DOM` Domain
- `SRC` Source orchestration
- `IDN` Identity
- `MAT` Match lifecycle
- `EVT` Event
- `APP` Android app
- `OVR` Overlay
- `UPD` Update
- `AI` AI assist

示例：`LNR-SRC-LIVE-001` = 实时来源全部不可用。

## 14. 架构 Gate

M0/M1 必须建立自动检查：

- `:core:domain` 不依赖 Android/Compose/OkHttp；
- `:core:application` 不依赖 Android；
- Domain/Application unit tests；
- build compile gate；
- executable scripts syntax gate；
- source set dependency DAG 由 Gradle module dependency 保证。

## 15. 冻结边界

未经新 ADR 不得：

- 改掉 PRE/LIVE/POST 一级轴；
- 重新以赛区拆业务模块；
- 让 UI 直连 Provider；
- 让 AI 覆盖赛事事实；
- 把 Android 类型带进 Core；
- 把旧大型 Store 直接复制进新项目。

这份冻结允许后续增加具体 Adapter 和 Feature module，但依赖方向不得反转。
