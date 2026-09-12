# Laner Development Plan

## 任务编号

正式任务使用 `LNR-###`。

## M0 — Project Foundation / Migration Foundation

### LNR-000 — 工程立宪与基线初始化
状态：`DONE`

### LNR-001 — 旧工程功能基线提取
状态：`DONE`
产物：`docs/FEATURE_BASELINE.md`。

### LNR-002 — 旧工程架构与技术债审计
状态：`DONE`
产物：`docs/LEGACY_ARCHITECTURE_AUDIT.md`。

### LNR-003 — 新架构冻结
状态：`DONE`
产物：`docs/ARCHITECTURE_FREEZE.md`。

### LNR-004 — 工程骨架与 CI Gate
状态：`DONE`

### LNR-005 — 三阶段一级架构轴确立
状态：`DONE`

### LNR-006 — 用户角色 × 比赛阶段产品矩阵
状态：`DONE`

### LNR-007 — 极简 × 酷炫体验北极星
状态：`DONE`

### LNR-008 — 四类数据/API 源架构
状态：`DONE`

### LNR-009 — 全球赛事统一管理架构
状态：`DONE`

## M1 — Feature Migration

完整迁移状态以 `docs/FEATURE_BASELINE.md` 为唯一功能清单。迁移按工程依赖推进，但用户产品结构始终保持赛前 / 赛中 / 赛后。

### LNR-010 — 全球赛事目录与赛程中心
状态：`WAITING EXTERNAL TEST`

已实现 Global Competition Catalog / Schedule、Riot PRE Adapter、PRE 真实赛程 UI、来源状态/降级与安全 credential 注入。自动 Gate 已通过；真实 credential/实机仍待验收。

### LNR-011 — PRE Roster / Staff / Form / H2H
状态：`WAITING EXTERNAL TEST`

已实现 Roster Pool、Official Starting Roster Evidence、Staff、Recent Form、H2H 及 PRE 页面消费。自动 Gate 已通过；真实数据配送与实机仍待验收。

### LNR-012 — Standings / Qualification / Tournament Edition
状态：`WAITING EXTERNAL TEST`

已实现四类事实强类型分离、Riot standings/tournament Adapter、Qualification evidence、Tournament Edition 本地 archive 与 PRE Panel。自动 Gate/PR Gate 已通过；在线与 team-level qualification 证据仍待补齐。

### LNR-013 — LIVE Match State / Provider Arbitration / Unified Event / Timeline
状态：`DONE`

本任务只认证 LIVE Core/Application 基础层，不代表真实 LIVE 产品链已经完成。

已完成：
- `LiveMatchStateReducer` lifecycle authority；
- `赛事开始 != 游戏进入`；
- 场间 / 新局 / stale / future-game / terminal 规则；
- Provider Arbitration；
- 标准生命周期事件；
- provider-neutral Timeline；
- reconnect dedupe / out-of-order replay / provenance arbitration。

最终 exact-head 与 PR Gate 均 PASS，已合并 main。

### LNR-014 — LIVE Source Adapters / Local Persistence / Composition Wiring
状态：`IN PROGRESS`

当前范围：
- Android device-local `LiveMatchStateRepository / LiveTimelineRepository`；
- `schema_version`、原子写入、损坏数据显式失败；
- Android Adapter/Persistence unit-test Gate；
- Composition Root 接线；
- LIVE 页面只消费 Application truth；
- 无 Provider 时明确 `UNAVAILABLE / last-known`，不造数据；
- Provider target query 可获取 canonical MatchId + TeamRef + start time，不从字符串 ID 猜身份。

已完成到当前分支：
- `JsonLiveMatchStateRepository`；
- `JsonLiveTimelineRepository`；
- Snapshot / 标准事件显式 schema 序列化；
- App unit-test CI Gate；
- `LanerAppGraph` 已接本地 LIVE State/Timeline repository 与 Application service；
- 真实源列表为空时保持合法显式降级。

Cito 决策（2026-09-12）：
- 用户当前无法进行 Cito 在线测试；
- Cito 在线验收状态：`WAITING EXTERNAL TEST / DEFERRED`；
- 不删除 Cito 接口/适配设计，不把未测能力描述为 SUPPORTED；
- REST 继续作为未来 bootstrap/reconcile/fallback 基线；
- WSS entitlement 不作假定，只作为在线条件具备后的可选增强；
- Cito 暂缓不得阻塞本地 persistence、Composition、LIVE UI 和其它 Provider 迁移。

LNR-014 当前验收调整：
- 本地 persistence / schema / corruption / atomic write 必须自动 PASS；
- Composition Root / 无源降级必须自动 PASS；
- LIVE UI 不允许直接读取 Provider；
- Cito fixture/contract 可以自动测试，但真实在线能力保持 `WAITING EXTERNAL TEST`；
- LNR-014 是否拆分收口，以后续 Provider 可用性为准，禁止为了 DONE 虚构在线证据。

## 第一批真实迁移顺序

1. Global Competition Catalog / Schedule —— `LNR-010 WAITING EXTERNAL TEST`；
2. PRE Roster / Staff / Form / H2H —— `LNR-011 WAITING EXTERNAL TEST`；
3. Standings / Qualification / Tournament Edition —— `LNR-012 WAITING EXTERNAL TEST`；
4. LIVE Core —— `LNR-013 DONE`；
5. LIVE Persistence / Wiring / Degraded UI —— `LNR-014 IN PROGRESS`；
6. LIVE Provider online verification —— 条件具备后补验收；
7. POST Result / Stats / Replay / Archive；
8. Android RiftScreen / Watch / Player / OTA；
9. Local AI / OCR / Roster Assist；
10. Compatibility Import / Full Regression / Migration Audit。

## 速度原则

允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。

禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、未完成基线条目却宣称“完整迁移”。
