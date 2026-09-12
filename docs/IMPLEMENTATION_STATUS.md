# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Working branch: `feature/lnr-011-pre-context`
- Project phase: `M1 / Feature Migration`
- Business implementation: `STARTED`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy source version: `1.0.0-dev.94 / versionCode 94`

## Task Status

| Task | Title | Status |
|---|---|---|
| LNR-000 | 工程立宪与基线初始化 | DONE |
| LNR-001 | 旧工程功能基线提取 | DONE |
| LNR-002 | 旧工程架构与技术债审计 | DONE |
| LNR-003 | 新架构冻结 | DONE |
| LNR-004 | 工程骨架与 CI Gate | DONE |
| LNR-005 | 三阶段一级架构轴确立 | DONE |
| LNR-006 | 用户角色 × 比赛阶段产品矩阵 | DONE |
| LNR-007 | 极简 × 酷炫体验北极星 | DONE |
| LNR-008 | 四类数据/API 源架构 | DONE |
| LNR-009 | 全球赛事统一管理架构 | DONE |
| LNR-010 | 全球赛事目录与赛程中心 | WAITING EXTERNAL TEST |
| LNR-011 | PRE Roster / Staff / Form / H2H | WAITING EXTERNAL TEST |
| LNR-012 | Standings / Qualification / Tournament Edition | TODO |

## Current Truth

Laner 已进入 M1 真实功能迁移。PRE_MATCH 目前已经形成两条连续数据链：

1. 全球赛事目录 / 全球赛程；
2. 以某一场比赛为上下文的 Roster / Starting Roster Evidence / Staff / Recent Form / H2H。

当前已验证存在：

- `:core:domain` 纯 Kotlin Domain；
- `:core:application` 纯 Kotlin Application/Port；
- `:app` Android/Compose Composition Root；
- PRE/LIVE/POST 三阶段 Android 壳；
- Match Lifecycle；
- Global Competition / Team / Player IDs；
- Source Class / Provenance / Authority / Freshness / Revision；
- AI 与事实路径硬隔离；
- `GlobalScheduleService`；
- Riot LoL Esports PRE Schedule Adapter；
- `TeamRosterPool` 与 `OfficialStartingRoster` 分离；
- `StartingRosterResolution.Unknown / Confirmed / Conflict`；
- `PreMatchContextService`；
- Riot Team Roster Adapter；
- normalized official Starting Roster Adapter；
- normalized global Staff Adapter；
- completed-Series-only Form / H2H；
- PRE 页面比赛点选与同页上下文展示；
- 首发证据冲突可视化；
- GitHub Actions Architecture Gate + Core Tests + Android Debug Compile。

## Verification Evidence

### Migration Foundation

- 首轮 CI：FAIL —— CI 错配 Gradle 9.4.0，而 AGP 9.4.0 要求最低 Gradle 9.6.0；失败证据保留。
- 修复后 CI run `34686592578`：PASS。

### LNR-010

GitHub Actions run `34687580424`：PASS。

未执行：
- 带真实 LoL Esports credential 的在线 `getLeagues/getSchedule`；
- Android 实机真实赛事目录/赛程展示。

状态：`WAITING EXTERNAL TEST`。

### LNR-011

首个完整 Core 规则 CI 已通过。UI 接线后 run `34688581238`：

- Architecture boundary gate：PASS；
- Domain/Application tests：PASS；
- Android debug compile：FAIL。

失败根因：`PreMatchScreen` 的 Context `produceState` 使用 4 个命名 key，当前 Compose API 对应重载不接受该调用方式。未修改业务语义，改为 3 个稳定 key。

修复后 GitHub Actions run `34688715420`：PASS。

- Architecture boundary gate：PASS；
- Domain/Application tests：PASS；
- Android debug compile：PASS。

自动化已证明：
- 五人名单池不会自动变成官方首发；
- 错日期/错对手首发证据被拒绝；
- 重复位置首发证据被拒绝；
- 两条相同官方阵容可交叉确认；
- 同 Authority 冲突阵容必须返回 Conflict；
- Form/H2H 只使用已验证结束 Series；
- H2H 的 W/L 视角明确。

未执行 / 外部验收：
- 真实 Riot credential roster pool 在线读取；
- normalized starting-roster/staff 网络配送；
- Android 实机 PRE 上下文展示。

因此 LNR-011 总状态为 `WAITING EXTERNAL TEST`，其中 PRE-007 可凭纯业务不变量与自动化直接标 `DONE`。

旧 RiftLab 现场实测确认的场间/新局开局识别证据仍保留于 `docs/audits/2026-09-12_legacy_live_intermission_verification.md`，迁移 LIVE-001/LIVE-002 时必须永久回归。

## Next

进入 `LNR-012`：Standings / Qualification / Tournament Edition。LNR-010 与 LNR-011 的外部验收在具备真实 credential / Android 实机环境时补证，不阻塞正交迁移。
