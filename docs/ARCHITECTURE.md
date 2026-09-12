# Laner Architecture

## 状态

- 文档状态：`DRAFT / M0`
- 架构状态：目标骨架已定义，待读取旧工程后冻结细节
- 当前无业务实现

## 核心目标

Laner 的架构目标是：在不改变产品功能的前提下，将旧工程中容易耦合、难追踪、难验证的实现重新拆分为可替换、可测试、可诊断的模块。

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
负责比赛、局、阶段、时间、状态转换和业务不变量。

### Event Domain
负责标准化赛事事件，不关心事件来自官网、微博、OCR、API 还是 AI。

### Data Normalization
负责把不同来源的字段、时间、队伍、选手、赛事 ID 和状态转换为内部统一模型。

### Source Orchestration
负责数据源优先级、健康状态、超时、重试、降级、冲突与验证，不向 UI 暴露具体 Provider。

### Persistence
负责缓存、历史数据、Schema、Migration 和恢复；存储格式不得污染领域模型。

### Analysis
负责统计、规则分析、AI 辅助能力。AI 必须是可选能力，不能成为基础赛事展示的单点依赖。

## 数据路径

目标数据路径：

```text
External Source
→ Source Adapter
→ Validation / Normalization
→ Conflict & Freshness Resolution
→ Domain Event / Match State
→ Repository / Cache
→ Application Query
→ UI / HUD
```

禁止：

```text
UI → 微博
UI → 赛事官网
UI → OCR Provider
UI → AI Provider
```

## 状态权威

同一场比赛只能有一个统一的 Match State 权威视图。来源可以多个，但最终展示给应用层的状态必须经过统一解析。

状态至少要能区分：

- 赛事预告；
- 赛前准备 / 选手入场；
- BP；
- 游戏载入；
- 局内；
- 局间；
- 小局结束；
- 系列赛结束；
- 未知 / 数据冲突 / 数据源降级。

最终枚举与转换规则需在旧工程功能基线和真实数据源读取后冻结。

## 模块约束

1. UI 只能调用 Application 层公开接口。
2. Adapter 不得实现赛事业务规则。
3. Source Adapter 必须可独立替换与测试。
4. 核心模块之间依赖必须形成 DAG。
5. 任何外部源故障只能局部降级。
6. 数据模型变更必须有 Schema/Migration 策略。
7. 高频轮询必须有频率预算、退避和日志限频。
8. 关键状态变化必须可追溯到来源和时间。

## 待旧工程审计后决定

以下内容当前不提前拍脑袋：

- 具体客户端技术栈；
- 数据库实现；
- 网络库；
- DI 框架；
- UI 框架；
- 任务调度实现；
- OCR Provider；
- AI Provider；
- 具体包名/模块名。

这些必须以旧工程现状、目标平台和真实需求为依据后再定。
