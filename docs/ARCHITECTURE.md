# Laner Architecture

## 状态

- 文档状态：`DRAFT / M0`
- 架构状态：目标骨架已定义，待读取旧工程后冻结细节
- 当前无业务实现

## 核心目标

Laner 的架构目标是：在不改变产品功能的前提下，将旧工程中容易耦合、难追踪、难验证的实现重新拆分为可替换、可测试、可诊断的模块。

## 第一产品与页面分类轴：赛前 / 赛中 / 赛后

Laner 所有用户可见页面、路由、HUD、功能入口、业务查询和赛事信息展示，必须首先归属到以下三阶段之一：

```text
PRE_MATCH   / 赛前
LIVE_MATCH  / 赛中
POST_MATCH  / 赛后
```

这不是单纯的 UI Tab，而是全工程的一级业务分类轴。

任何新页面或新功能在设计前必须先回答：

1. 它主要服务于赛前、赛中还是赛后？
2. 如果跨阶段复用，它的领域数据是否与展示入口分离？
3. 当前比赛状态变化时，它是否需要自动切换、预取、刷新、冻结或归档？
4. 它是否错误地把“功能类别”提升成与赛前/赛中/赛后平级的一级导航？

无法明确归属的页面或功能，不得直接加入主产品结构，必须先完成架构评审。

## 第二产品设计轴：用户角色 × 当下问题

三阶段只回答“现在处于什么时候”，还必须继续回答“谁正在看、他现在最想知道什么”。

Laner 的功能来源统一定义为：

```text
Feature = Persona × Match Phase × User Question
```

当前核心 Persona：

- `SPECTATOR`：普通观众 / 深度观众；
- `COACH_ANALYST`：教练 / 分析人员 / 高阶复盘用户。

完整问题矩阵见 `docs/PRODUCT_PERSPECTIVE_MATRIX.md`。

任何新功能在进入开发计划前，必须明确：Persona、Phase、User Question、Decision Value、Evidence、Presentation、Broadcast Redundancy、Acceptance。

无法说明“用户此刻为什么需要它”的功能，不得仅因为“这个数据能拿到”就进入正式开发。

### 同一事实，两种展示密度

不得为观众和教练复制两套底层业务逻辑。标准结构：

```text
Domain Fact / Event / Match State
              ↓
        Application Query
          ↙           ↘
 Spectator View   Coach/Analyst View
```

观众层默认：结论优先、中文解释、低认知负担、视觉化。

教练层默认：证据优先、结构化、可筛选、可追溯、展示样本与置信度。

## 第三数据源分类轴：四大 Source Class

所有外部 API / 数据来源统一归入：

```text
PRE_MATCH_SOURCE   / 赛前源
LIVE_MATCH_SOURCE  / 赛中源
POST_MATCH_SOURCE  / 赛后源
GLOBAL_AI_ASSIST   / 全局 AI 辅助源
```

详细规则见 `docs/SOURCE_ARCHITECTURE.md`。

关键边界：

1. 前三类负责提供或验证赛事事实；
2. `GLOBAL_AI_ASSIST` 只做解释、归纳、推断、翻译、提示和辅助分析；
3. AI 不得凭模型输出覆盖已确认事实；
4. AI 推断必须标记为推断，不能伪装成赛事实体；
5. 来源类别与页面阶段相关但不等价，赛中可以读取赛前背景，赛后可以复用赛中事件；
6. UI 永远不能直接连接任何 Provider。

## PRE_MATCH / 赛前

面向比赛正式进入进行态之前的全部观赛准备与判断，包括但不限于：赛事预告与倒计时、首发与阵容、Rank / 近期状态、转会与人员信息、历史交手、积分/排名/晋级路径、赛制、BP 前背景情报、直播入口。

## LIVE_MATCH / 赛中

面向赛事已经开始但系列赛尚未正式结束的全部实时能力。赛中内部仍必须区分：

```text
赛事直播已开始但游戏未开始
→ 选手入场 / 评论席 / 赛前节目
→ BP
→ 游戏载入
→ IN_GAME
→ 小局结束
→ 局间
→ 下一局
```

因此“赛事开始”绝不等于“游戏已进入”。

赛中能力包括实时赛事状态、BP、事件流、导播未展示或未及时展示的信息、等级/技能/装备/经济/资源/节奏衍生观察、HUD、沙盘和关键节点提示。

## POST_MATCH / 赛后

面向小局或系列赛已经结束后的结果沉淀与复盘，包括比赛结果、数据面板、关键事件时间线、统计与对比、复盘与 AI 总结、积分排名晋级变化、历史归档、可回放事件与状态快照。

## 跨阶段公共能力

Team / Player / Competition 基础资料、数据源与标准化、缓存与持久化、搜索、设置、日志诊断、AI/OCR、通知、账户与平台能力可以跨阶段复用，但不是第四个业务阶段。

## 目标分层

```text
Presentation / UI / HUD
        ↓
Application / Use Cases / Queries
        ↓
Domain / Core / Match State
        ↓
Ports / Contracts
        ↓
Adapters / Infrastructure
        ↓
External Sources / Platform APIs / Storage
```

依赖方向只允许向内指向抽象和领域，不允许 Core 反向依赖外部平台。

## 计划中的核心域

### Match Domain
负责比赛、局、阶段、时间、状态转换和业务不变量，并为 Application 层提供明确的 PRE_MATCH / LIVE_MATCH / POST_MATCH 阶段映射。

### Event Domain
负责标准化赛事事件，不关心事件来自官网、微博、OCR、API 还是 AI。

### Data Normalization
负责把不同来源的字段、时间、队伍、选手、赛事 ID 和状态转换为内部统一模型。

### Source Orchestration
负责 Source Class、Provider 注册、优先级、权威度、健康状态、超时、重试、降级、冲突、时效性与 provenance，不向 UI 暴露具体 Provider。

必须把“快”与“权威”分开：首个可信源可以先 provisional 发布，后续高权威源可以确认/修正，但所有修订必须留 provenance/revision。

### Persistence
负责缓存、历史数据、Schema、Migration 和恢复；存储格式不得污染领域模型。

### Analysis
负责统计、规则分析、AI 辅助能力。AI 必须是可选能力，不能成为基础赛事展示的单点依赖，也不能成为事实权威层。

## 数据路径

```text
External Provider
→ Source Adapter
→ Validation / Normalization
→ Provenance
→ Conflict & Freshness Resolution
→ Domain Event / Match State
→ Repository / Cache
→ Application Query
→ PRE_MATCH / LIVE_MATCH / POST_MATCH Presentation
```

禁止：

```text
UI → 微博
UI → 赛事官网
UI → OCR Provider
UI → AI Provider
```

## 状态权威

同一场比赛只能有一个统一 Match State 权威视图。来源可以多个，但最终展示状态必须经过统一解析。状态至少区分赛事预告、赛前准备/选手入场、BP、载入、局内、局间、小局结束、系列赛结束、未知/冲突/降级，并稳定映射至 PRE_MATCH / LIVE_MATCH / POST_MATCH。

## 页面与路由强约束

1. 一级产品路由必须以赛前 / 赛中 / 赛后组织。
2. 二级页面可以按赛程、首发、积分、HUD、沙盘、复盘等拆分，但不得脱离所属阶段。
3. 同一能力跨阶段出现时复用 Domain/Application 能力，不复制三套业务逻辑。
4. 比赛阶段由 Match State 驱动，不允许页面各自判断。
5. 页面只能消费阶段 ViewModel / Query，不得直读 Provider 或数据库细节。
6. 设置、诊断等工具型页面不得与三阶段争夺业务一级结构。
7. 页面和功能必须说明目标 Persona 与 User Question。

## 模块约束

1. UI 只能调用 Application 层公开接口。
2. Adapter 不得实现赛事业务规则。
3. Source Adapter 必须可独立替换与测试。
4. 核心模块之间依赖必须形成 DAG。
5. 外部源故障只能局部降级。
6. 数据模型变更必须有 Schema/Migration 策略。
7. 高频轮询必须有频率预算、退避和日志限频。
8. 关键状态变化必须可追溯到来源和时间。
9. 所有业务页面必须通过三阶段归类审查。
10. 同一领域事实不得因 Persona 不同而复制业务实现。
11. 所有 Provider 必须声明 Source Class；不得绕过 Source Orchestration。
12. AI 输出必须能够区分事实支撑、推断与未验证内容。

## 待旧工程审计后决定

具体客户端技术栈、数据库、网络库、DI、UI 框架、任务调度、OCR Provider、AI Provider、具体包名/模块名均不提前拍脑袋，必须以旧工程现状、目标平台和真实需求为依据后再定。
