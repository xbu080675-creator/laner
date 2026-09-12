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

已完成 LIVE Core/Application 真相层：lifecycle authority、`赛事开始 != 游戏进入`、场间/新局/stale/future-game/terminal、Provider Arbitration、标准生命周期事件、provider-neutral Timeline、reconnect dedupe、out-of-order replay 与 provenance arbitration。最终 exact-head 与 PR Gate 均 PASS，已合并 main。

### LNR-014 — LIVE Source Adapters / Local Persistence / Composition Wiring
状态：`WAITING EXTERNAL TEST`

自动可认证部分已完成：
- Android device-local `LiveMatchStateRepository / LiveTimelineRepository`；
- `schema_version=1`、SHA-256 稳定文件名、原子写入、损坏/未知 schema 显式失败；
- Snapshot / 标准事件显式 schema 序列化；
- Android Adapter/Persistence unit-test Gate；
- target-aware LIVE query；
- Composition Root 接线；
- LIVE 页面只消费 Application truth；
- `EVENT_LIVE` 优先的目标选择规则；COMPLETED 不得冒充当前 LIVE；
- 无 Provider 时明确 `UNAVAILABLE / last-known`；
- Timeline 通过 `LiveTimelineService.load(gameId)` 读取并展示本地标准事件，不允许 UI 直读 Repository。

自动验证：final code head `4d64749c07ce6f91bebad91de91f4fb70ed0bd04` / run `34692936906` 的 Architecture / Domain+Application / Android Adapter tests / Android debug compile 全 PASS。

Cito 决策（2026-09-12）：
- 用户当前无法进行 Cito 在线测试；
- Cito 在线验收：`WAITING EXTERNAL TEST / DEFERRED`；
- 不删除适配设计，不虚构在线证据；
- REST 作为未来 bootstrap/reconcile/fallback 基线；
- WSS entitlement 不作假定；
- Cito 暂缓不阻塞后续迁移。

### LNR-015 — POST Result / Game Archive / Stats / Timeline Archive / Replay Domain
状态：`TODO`

目标：
- 强类型拆分 Series Result、Completed Game Archive、Player Stats、Objective Result、Awards、Replay；
- 建立 `POST_MATCH_SOURCE` Ports 与 Application aggregation；
- 复用 canonical MatchId/GameId，不允许 Provider raw ID 成为 Domain 主键；
- 复用标准 `GameTimeline` 作为赛后归档/查询输入，不复制旧 `MatchTimelineStore`；
- 历史补全来源必须带 provenance / authority / freshness；
- Replay 先建立 provider-neutral Domain/Port；
- Media3/WebView 继续属于 Android Adapter，不进入 Core；
- POST 页面只消费 Application state。

旧版主要行为证据：`CompletedGameArchive`、`MatchDetailRepository`、`LplHistoricalPostMatchResolver`、`RiotLiveStatsHistoryResolver`、`OpggHistoricalFrameResolver`、`GlobalVerifiedAwardsProvider`、`BilibiliVodRepository`、`RiotVodRepository`、`MatchTimelineStore` 以及对应 MatchDetail/Timeline/Replay UI。

## 第一批真实迁移顺序

1. Global Competition Catalog / Schedule —— `LNR-010 WAITING EXTERNAL TEST`；
2. PRE Roster / Staff / Form / H2H —— `LNR-011 WAITING EXTERNAL TEST`；
3. Standings / Qualification / Tournament Edition —— `LNR-012 WAITING EXTERNAL TEST`；
4. LIVE Core —— `LNR-013 DONE`；
5. LIVE Persistence / Wiring / Degraded UI —— `LNR-014 WAITING EXTERNAL TEST`；
6. POST Result / Stats / Replay / Archive —— `LNR-015 TODO`；
7. Android RiftScreen / Watch / Player / OTA；
8. Local AI / OCR / Roster Assist；
9. Compatibility Import / Full Regression / Migration Audit。

Cito 在线验收作为 LNR-014 外部补证项独立回填，不阻塞第 6 项及以后迁移。

## 速度原则

允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。

禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、未完成基线条目却宣称“完整迁移”。
