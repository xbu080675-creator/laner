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

### LNR-015 — Global POST Result / Archive / Historical Timeline / Replay
状态：`TESTING`

本轮已经形成全球 POST 主链，不按 LPL/LCK/LEC/LCP 复制业务系统：
- 强类型拆分 `SeriesResult / CompletedGameRecord / Player Stats / Awards / Replay / Historical Timeline`；
- `PostMatchService` 按 `PostSourceCapability` 选择来源，Application 不写赛区 if/else；
- `GameIdentity.canonical(matchId, gameNumber)` 冻结 canonical GameId，Provider raw IDs 只存在 mapping/Adapter；
- `ProviderMatchIdentityRepository` 保存 canonical MatchId ↔ provider event/match ID 映射；
- `PostMatchArchiveRepository` 保存已经验证的 Result/Game facts，archive 仅在外部事实缺失时补位；
- Riot global schedule 提供跨赛区/国际赛事 Series Result baseline；
- Riot `getEventDetails` 提供跨赛区/国际赛事 Replay metadata baseline；
- Riot LiveStats history 提供按需真实历史过程帧，不插值、上游无历史即留缺口；
- LIVE/POST 真实帧复用同一个 canonical `GameTimeline`，PRE/AI 不得进入 Timeline；
- POST 页面通过 Application 展示 Series Result / Replay / Awards，并只在用户选择 Gx 时触发历史 Timeline 恢复；
- LPL TJStats 历史链仅为区域 supplement，不是 POST 主架构，且 credential 外部注入、旧硬编码值未迁移。

自动验证证据：
- global capability routing commit `e3de01052602d7633368acd003c1ab0fd0f14401` / run `34694930111`：全 PASS；
- global Riot Result/Replay baseline commit `ea9b6e576fe22f4459c0c55c89f805a3d556285e` / run `34695412163`：全 PASS；
- Timeline source-class 根因修复 head `7451c302f106fa1f4d636a8ac77f1580b8dd672c` / run `34695924994`：全 PASS；
- current code/UI head `0681c3b20f2e6cad4cd6fb52bf90a4912e299608` / run `34696081645`：Architecture / Domain+Application / Android Adapter tests / Android debug compile 全 PASS。

尚未认证/尚未完成：
- Riot credentialed online Result / EventDetails / LiveStats fetch 尚未形成本轮真实在线证据，因此对应全球 Provider 能力最终只能进入 `WAITING EXTERNAL TEST`，不能写 DONE；
- global per-game `CompletedGameRecord` 仍要求明确 winner evidence；没有显式赢家字段时不得用经济/击杀推断；
- Bilibili replay supplement、每局 BP/终局装备、Timeline 事件筛选/团战聚合仍未完成；
- Media3/WebView 属后续 Android Adapter，不进入本轮 Core。

### 后续轮次

#### LNR-016 — Android RiftScreen / Watch / Player / OTA
状态：`TODO`

目标：迁移 Watch Hub、平台跳转、RiftScreen/HUD、播放器与更新能力；保持赛事数据面与直播入口解耦。

#### LNR-017 — Local AI / OCR / Roster Assist
状态：`TODO`

目标：迁移本地 AI、OCR、首发图片识别、Insight 与 AI diagnostics；AI 永远不升级为赛事事实权威。

#### LNR-018 — Compatibility Import / Full Regression / Migration Audit
状态：`TODO`

目标：兼容导入、全功能基线销账、真实设备/Provider 回归、迁移审计与发布闭环。

## 第一批真实迁移顺序

1. Global Competition Catalog / Schedule —— `LNR-010 WAITING EXTERNAL TEST`；
2. PRE Roster / Staff / Form / H2H —— `LNR-011 WAITING EXTERNAL TEST`；
3. Standings / Qualification / Tournament Edition —— `LNR-012 WAITING EXTERNAL TEST`；
4. LIVE Core —— `LNR-013 DONE`；
5. LIVE Persistence / Wiring / Degraded UI —— `LNR-014 WAITING EXTERNAL TEST`；
6. Global POST Result / Replay / Historical Timeline / Archive —— `LNR-015 TESTING`；
7. Android RiftScreen / Watch / Player / OTA —— `LNR-016 TODO`；
8. Local AI / OCR / Roster Assist —— `LNR-017 TODO`；
9. Compatibility Import / Full Regression / Migration Audit —— `LNR-018 TODO`。

Cito 在线验收作为 LNR-014 外部补证项独立回填，不阻塞第 6 项及以后迁移。

## 速度原则

允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。

禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、未完成基线条目却宣称“完整迁移”。
