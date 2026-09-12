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

已完成并验证：
- Gradle 多模块工程；
- `:core:domain` / `:core:application` / Android `:app`；
- 三阶段 Compose 壳；
- Match Lifecycle / Global IDs / Source Provenance / Fact Candidate / Standard Events；
- FactArbiter；
- ErrorCode / Diagnostics Port；
- Domain/Application Unit Tests；
- GitHub Actions Architecture Boundary Gate；
- Android `assembleDebug` Gate；
- 模块 README。

验证证据：CI run `34686592578` 全部 PASS。首轮因 Gradle 版本低于 AGP 9.4.0 最低要求而失败，已修正为 Gradle 9.6.0并保留失败记录。

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

已实现：
- Global Competition Catalog / Schedule Domain；
- `GlobalPreMatchSourcePort`；
- `GlobalScheduleService` 统一标准化、状态校验、跨源去重与降级；
- Riot LoL Esports PRE Adapter；
- PRE 页面真实赛事目录筛选与本地时区赛程列表；
- Schedule `EVENT_LIVE` 与 Match `IN_GAME` 硬隔离；
- API credential 仅允许环境变量/Gradle Property 注入，不写入 Git；
- `[Laner:SRC] / [Laner:PRE]` 诊断与 `LNR-SRC-PRE-001~004` 错误码。

自动验证：GitHub Actions run `34687580424` 全部 PASS。

未完成的外部验收：真实在线 `getLeagues/getSchedule` 与 Android 实机展示仍为 `WAITING EXTERNAL TEST`。

### LNR-011 — PRE Roster / Staff / Form / H2H
状态：`WAITING EXTERNAL TEST`

已实现：
- `TeamRosterPool` 与 `OfficialStartingRoster` 独立领域事实；
- `StartingRosterResolution = Unknown / Confirmed / Conflict`；
- TOP/JUNGLE/MID/BOT/SUPPORT 五位置与五名不同选手硬不变量；
- 名单池永远不得自动升级为官方首发；
- `PreMatchContextService` 统一组合 Roster Pool / Starting Roster Evidence / Staff / Form / H2H；
- Riot `getTeams` Roster Pool Adapter；
- normalized official roster evidence Adapter；
- normalized global staff Adapter；
- Recent Form / H2H 仅从已验证 `COMPLETED` Series 派生且明确 W/L 视角；
- PRE 页面点选比赛后同页展示首发证据、名单池、Staff、近期 Series、H2H；
- 首发证据冲突显式展示，不静默覆盖；
- `LNR-SRC-PRE-006~009` 错误码。

自动验证：GitHub Actions run `34688715420` 全部 PASS。

已保留失败历史：run `34688581238` 中 Core PASS、Android compile FAIL，根因为 Compose `produceState` 使用 4 个命名 key 与当前版本重载不兼容；修为 3-key 后回归通过。

仍需外部验收：真实 Riot roster pool、normalized roster/staff 配送与 Android 实机 PRE 展示。

`PRE-007` 已凭 Domain 不变量与自动化测试标记 `DONE`；其余真实数据展示能力在外部证据前不冒充 DONE。

### LNR-012 — Standings / Qualification / Tournament Edition
状态：`WAITING EXTERNAL TEST`

已实现：
- Standings / Championship Points / Qualification / Tournament Edition 四套事实强类型分离；
- tied standings ordinal 合法，同一 section team identity 唯一；
- `CompetitionStructureService` 与四类独立 Source Port；
- Riot `getTournamentsForLeague/getStandings` Adapter；
- Provider raw tournament id 不进入 canonical Domain identity；
- Riot standings 胜场不再伪装成 points，只有显式 provider points 才映射 `STAGE_POINTS`；
- Tournament Edition additive archive；
- `schema_version=1` + `ATOMIC_MOVE/REPLACE_EXISTING` 写入；
- Riot 2026 Handbook qualification mechanism Source；
- Qualification 无可信证据时 `UNKNOWN/PENDING`；
- PRE `CompetitionStructurePanel` 展示届次、Standings、年度积分状态与晋级机制；
- 旧 RiftLab `2026-09-08` 年度积分快照因已过时，没有迁移为当前值。

自动验证：
- run `34689400211`：Core semantics PASS；
- run `34689473333`：Riot structure Adapter PASS；
- run `34690235942`：Architecture/Core PASS，Android compile FAIL，根因为 `Files.move()` 返回 `Path` 导致 Repository `save(): Unit` 返回类型推断错误；已仅补显式 `Unit`，业务/原子写入语义不变；
- final exact-head CI 在文档收口后重新验证。

仍需外部验收 / 数据补全：
- credentialed Riot Tournament/Standings 在线读取；
- Android 实机 archive 与 Panel；
- 2026-09-12 新鲜可信 Championship Points 总分 Source；
- 完整 team-level qualification status 证据。

其中“Championship Points 与 Standings 严格分离”和 Qualification Evidence Model 可由 Domain/自动化直接认证；team-level qualification status 仍保持 `IN PROGRESS`。

### LNR-013 — LIVE Match State / Provider Arbitration / Unified Event / Timeline
状态：`TODO`

目标：
- 建立 LIVE 权威 Match State；
- 保证 `赛事开始 != 游戏进入`；
- 统一 Provider Arbitration；
- 统一事实事件模型；
- 处理 duplicate / out-of-order / reconnect 幂等；
- 建立 Timeline append/replay contract；
- 将旧 RiftLab 已实机通过的“场间未开局 vs 新局真实开局”固化成永久回归；
- 本轮先做 Core/Application，不抢跑 RiftScreen/HUD。

## 第一批真实迁移顺序

1. Global Competition Catalog / Schedule —— `LNR-010 WAITING EXTERNAL TEST`；
2. PRE Roster / Staff / Form / H2H —— `LNR-011 WAITING EXTERNAL TEST`；
3. Standings / Qualification / Tournament Edition —— `LNR-012 WAITING EXTERNAL TEST`；
4. LIVE Match State / Provider Arbitration / Unified Event / Timeline —— `LNR-013 TODO`；
5. POST Result / Stats / Replay / Archive；
6. Android RiftScreen / Watch / Player / OTA；
7. Local AI / OCR / Roster Assist；
8. Compatibility Import / Full Regression / Migration Audit。

## 速度原则

允许并行读取与分析、同责任域成组实现、自动化减少重复、需求明确后直接开发。

禁止跨层乱改、跳过测试/留档、临时代码进入主线、把未执行测试写 PASS、未完成基线条目却宣称“完整迁移”。