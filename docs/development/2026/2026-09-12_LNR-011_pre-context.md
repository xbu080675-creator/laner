# LNR-011 — PRE Roster / Staff / Form / H2H

- 日期：2026-09-12
- 分支：`feature/lnr-011-pre-context`
- Baseline：`main@be317080b218ab4794984da270efec041319364a`
- 代码验证 Head：`604f4d7c8d426ccfd77d0daa0cf295d6ec83b73f`
- 状态：`WAITING EXTERNAL TEST`

## 1. 目标

在 Laner 新架构中迁移旧 RiftLab 的核心赛前上下文能力，同时彻底拆除“一个 MatchSessionStore 聚合全部赛前职责”的旧结构。

本轮覆盖：

- PRE-006 官方首发阵容；
- PRE-007 名单池与首发严格分离；
- PRE-008 替补领域表达的首批基础；
- PRE-009 教练组 / 管理人员；
- PRE-011 最近正式 Series；
- PRE-012 近期 H2H；
- SH-021 全球 Staff normalized mirror Adapter；
- SH-024 Riot LoL Esports 的 Team/Roster PRE 子链。

不覆盖：

- PRE-010 Rank / 近期英雄池；
- PRE-025 OCR / AI 首发识别；
- Standings / Qualification；
- LIVE/POST 数据。

## 2. Constitution Preflight

结果：`PASS`。

开发前已重新读取/核对：

- `docs/ENGINEERING_CONSTITUTION.md`；
- `docs/ARCHITECTURE_FREEZE.md`；
- `docs/DEVELOPMENT_PLAN.md`；
- `docs/IMPLEMENTATION_STATUS.md`；
- `docs/FEATURE_BASELINE.md`；
- Core/App 模块 README；
- 旧 RiftLab 当前 main 的 `StartingRosterCenter.kt`、`StartingRosterFeed.kt`、`GlobalTeamStaffProvider.kt`、`MatchSessionStore.kt`、`RiftLabApp.kt`；
- 旧 normalized `data/global/starting_rosters.json` 与 `team_staff.json`。

## 3. 迁移确认的旧版正向行为

### 3.1 名单池不等于首发

旧版已有明确保护：Roster Pool 即使可取得，也不得通过顺序或“正好五人”推断官方首发。

Laner 将其升级为 Domain / Application 永久规则，而非 UI 文案。

### 3.2 官方首发证据

可接受的官方 evidence 包括战队官方社媒、联赛官方社媒、官方网站等。官网发布时间不得成为较早官方战队社媒 evidence 的显示门槛。

证据进入 Starting Roster 前必须校验：

- 比赛本地日期；
- Team；
- Opponent；
- Competition/League；
- TOP/JUNGLE/MID/BOT/SUPPORT 五位置完整；
- 五名不同选手。

同 Authority 的不同阵容不得静默覆盖，必须显式 `Conflict`。

### 3.3 Form / H2H

只允许从已验证 `COMPLETED` Series 派生，当前比赛和未完成比赛不参与。W/L 必须带明确 perspective。

## 4. 新架构设计

```text
ScheduledSeries
   ↓
PreMatchContextService
   ├─ TeamRosterSourcePort
   │    └─ RiotTeamRosterSource
   ├─ StartingRosterSourcePort
   │    └─ NormalizedStartingRosterSource
   └─ TeamStaffSourcePort
        └─ NormalizedTeamStaffSource
   ↓
MatchPreContextSnapshot
   ↓
PRE Compose UI
```

业务事实裁决留在 Core/Application；Android Adapter 只做外部 payload 翻译与 IO。

## 5. 实现内容

### Domain

新增 `PreMatchContext.kt`：

- `TeamRosterPool`；
- `OfficialStartingRoster`；
- `StartingRosterResolution.Unknown / Confirmed / Conflict`；
- `RosterEvidenceSource / RosterEvidenceType`；
- `StaffRole / StaffMember / TeamStaffSnapshot`；
- `SeriesOutcome / RecentSeries`；
- `TeamPreMatchContext`。

### Application

新增：

- `PreMatchContextPort.kt`；
- `PreMatchContextService.kt`；
- `PreMatchContextServiceTest.kt`。

`PreMatchContextService` 统一负责：

- 读取并仲裁 Roster Pool；
- 读取 Staff；
- 对 Starting Roster evidence 做二次安全/业务校验；
- 合并相同阵容 evidence；
- 保留同级阵容冲突；
- 从 schedule history 派生 Recent Form / H2H。

### Android / Adapter

新增：

- `RiotTeamRosterSource.kt`；
- `NormalizedStartingRosterSource.kt`；
- `NormalizedTeamStaffSource.kt`。

修改：

- `LanerAppGraph.kt`；
- `MainActivity.kt`；
- `LanerRoot.kt`；
- `PreMatchScreen.kt`。

PRE 页面现在支持：

- 选择一场真实比赛；
- 同页显示官方首发状态；
- 显示 Roster Pool，并明确“不等于首发”；
- 显示 Staff；
- 显示双方近期正式 Series；
- 显示 H2H 且标明左队视角；
- 显示 source/degraded diagnostics；
- 首发 evidence 冲突显式呈现。

## 6. Adapter 权威与安全边界

### Riot Team Roster

- 来源：LoL Esports `getTeams`；
- Adapter Authority：`OFFICIAL`；
- credential 与 LNR-010 相同，只允许构建环境注入；
- 错误码：`LNR-SRC-PRE-006~007`。

### Normalized Starting Roster

当前阶段继续消费旧工程已存在的 normalized delivery feed 作为过渡运输层。该 feed 中的 evidence 不直接成为 Domain 事实，必须再次通过 `PreMatchContextService` 校验。

错误码：`LNR-SRC-PRE-008`。

### Global Staff

当前 normalized Staff 数据主要由 Riot GCD 派生。Laner Adapter 使用 `VERIFIED_PROVIDER`，不会把 mirror transport 自身伪装成直接官方 API。

错误码：`LNR-SRC-PRE-009`。

## 7. 自动测试

`PreMatchContextServiceTest` 覆盖：

1. 五人 Roster Pool 无官方 evidence 时仍为 `StartingRosterResolution.Unknown`；
2. 合法官方五位置 evidence → Confirmed；
3. 错日期 / 错对手 evidence → 拒绝；
4. 重复位置 evidence → 拒绝；
5. 相同阵容多 evidence → crossConfirmed；
6. 同 Authority 不同阵容 → Conflict；
7. Form 仅使用 COMPLETED Series 且排除当前比赛；
8. H2H 要求两队均出现并使用左队 perspective。

## 8. CI 历史

### 8.1 首次 UI 接线失败

GitHub Actions run `34688581238`：

- Architecture boundary gate：`PASS`；
- Domain/Application tests：`PASS`；
- Android debug compile：`FAIL`。

根因：`PreMatchScreen` 的 Context `produceState` 使用 4 个命名 key；当前 Compose API 对应 overload 不支持这种调用，导致 overload resolution 失败并产生 `value`/suspend 调用级联编译错误。

修复：`PreMatchContextService` 生命周期稳定，不需要作为重启 key。改为仅以 `scheduleSnapshot / selectedMatchId / refreshNonce` 三个 key 驱动 Context reload。

该修复没有修改赛事事实、首发证据或 Form/H2H 语义。

### 8.2 修复后验证

GitHub Actions run `34688715420`：

- Architecture boundary gate：`PASS`；
- Domain/Application tests：`PASS`；
- Android debug compile：`PASS`。

## 9. 外部验收

当前仍为 `WAITING EXTERNAL TEST`：

- 真实 LoL Esports credential 下 Riot Team roster pool；
- normalized starting-roster feed 网络配送；
- normalized staff feed 网络配送；
- Android 实机选择比赛与 PRE Context 展示；
- 官方首发 Confirmed / Conflict 实际 UI 样本。

因此：

- PRE-007：`DONE`；
- PRE-006 / PRE-009 / PRE-011 / PRE-012：`WAITING EXTERNAL TEST`；
- PRE-008：`IN PROGRESS`；
- PRE-010 / PRE-025：仍 `TODO`。

## 10. 影响与风险

- 架构：正向。旧 God Store 的 PRE 聚合职责迁入独立 Service/Ports。
- 兼容：未修改 Android applicationId；没有旧持久化格式迁移。
- 数据：新增只读网络消费，不写入关键长期数据。
- 性能：PRE Context 仅在比赛选择/刷新时加载，不引入实时高频轮询。
- 安全：未新增密钥入库；Adapter 不把未验证 payload 直接升级为事实。
- UX：同一个 PRE 页面承载本场上下文；未新建赛区孤岛页面。

## 11. 回滚

任务位于独立 branch。合并前可关闭 PR；合并后按 merge commit 回退。无 schema migration，因此回滚不涉及数据降级。

## 12. Post-change Compliance Review

结果：`PASS WITH EXTERNAL TEST PENDING`。

已确认：

- Core 无 Android/Compose/OkHttp/JSON；
- UI 不直连 Provider；
- Region 未成为业务模块边界；
- Roster Pool 与 Starting Roster 强制分离；
- AI/OCR 未提前越权进入事实路径；
- 冲突 evidence 不静默覆盖；
- 无 Mock 赛前事实；
- credential 未进入 Git；
- 自动化测试与 Android compile 已实际执行；
- 外部/实机未执行项明确标记 WAITING，而非伪造 PASS。

## 【任务交付单】

1. 任务：LNR-011 / PRE Roster / Staff / Form / H2H / `WAITING EXTERNAL TEST`
2. Baseline：`main@be317080b218ab4794984da270efec041319364a`
3. Constitution Preflight：PASS
4. 涉及模块：core-domain / core-application / app / PRE adapters
5. 强相关条款：Core/Adapter 边界、单一业务真相、外部输入不可信、测试、日志错误码、开发留档
6. 文件变更：Domain 1、Application Port/Service/Test 3、Android Adapter 3、Composition/UI 4、治理/状态文档与模块 README；以 PR changed-files 为最终准绳
7. 实现内容与设计原因：见 §3-6
8. 正向/负向/边界/回归：见 §7-8；自动化 PASS，外部验收 WAITING
9. 脚本验证：N/A，本任务无新增脚本语言入口
10. 日志与故障定位：`[Laner:SRC]` / `[Laner:PRE]`；`LNR-SRC-PRE-006~009`；`LNR-UI-PRE-001`
11. 影响评估：见 §10
12. 已知问题与后续：真实网络/实机证据待补；Rank/OCR/AI 不在本轮
13. 回滚：见 §11
14. 状态同步：FEATURE_BASELINE / DEVELOPMENT_PLAN / IMPLEMENTATION_STATUS / TESTING / TROUBLESHOOTING / CHANGELOG / module README 已同步
15. Commit / Push / PR / Release：已 Push；PR 待最终创建/验证；Release N/A
16. Post-change Compliance Review：PASS WITH EXTERNAL TEST PENDING
17. 最终结论：代码交付可进入 PR；功能认证保持 `WAITING EXTERNAL TEST`
