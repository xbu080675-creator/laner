# LNR-010 — 全球赛事目录与赛程中心

- 日期：2026-09-12
- 分支：`feature/lnr-010-global-schedule`
- Baseline：`main@6fbec881f80744cf4eda35ad080474c0ae658641`
- 状态：`TESTING`

## 1. 目标

将旧 RiftLab 的全球赛事目录与赛程中心能力迁移到 Laner 新架构，保持全球统一管理，不按赛区复制业务逻辑。

本轮只覆盖：

- PRE-001 全球/赛区赛事目录；
- PRE-004 赛程中心与本地时区显示；
- PRE-005 的“赛程侧状态展示”子集；
- 真实 Riot LoL Esports 赛程 Provider；
- 状态校验、跨源去重、来源降级与 Provenance。

## 2. 明确不做

- 不迁移首发、Rank、Standings、Qualification；
- 不迁移 LIVE 实时经济/击杀/资源；
- 不把 Schedule 的 EVENT_LIVE 当作 IN_GAME；
- 不复制旧 `MatchSessionStore`；
- 不把旧仓库中的 LoL Esports API key 复制到新仓库；
- 不使用 Mock 比赛数据填 UI。

## 3. Constitution Preflight

结果：`PASS`。

开发前已读取：

- 最新工程宪法；
- `ARCHITECTURE_FREEZE.md`；
- `DEVELOPMENT_PLAN.md`；
- `IMPLEMENTATION_STATUS.md`；
- `FEATURE_BASELINE.md`；
- `:core:domain` / `:core:application` / `:app` 模块 README；
- 旧 RiftLab `DataSources.kt`、`LolEsportsApiClient.kt`、`ScheduleCenterUi.kt`。

## 4. 设计决策

### D1 — ScheduleState 与 MatchLifecycle 分离

赛程源只允许输出：

`UPCOMING / EVENT_LIVE / COMPLETED / UNKNOWN`

其中 `EVENT_LIVE` 只表示赛事层面已开始，**不能证明游戏已开局**。真正 `IN_GAME` 只能由 LIVE source / Match State Engine 证明。

### D2 — Application 统一标准化与仲裁

Riot Adapter 只负责请求和翻译 Provider payload；内部 ID、状态校验、赛事分类、去重和来源优先级都由 `GlobalScheduleService` 统一执行。

### D3 — 完成状态必须有证据

如果 Provider 提前报告 completed，但 BO 所需胜场和 winner outcome 都不能证明系列赛结束，则降级为 UPCOMING，保留旧版已经验证有效的防误判行为。

### D4 — 子接口失败允许降级

`getLeagues` 或 older/newer 补页失败不会抹掉已经取得的真实赛程；通过 warning 进入 `DEGRADED`。

### D5 — 密钥不入 Git

API credential 只允许通过：

- `LOL_ESPORTS_API_KEY` 环境变量；或
- `lolEsportsApiKey` Gradle property

注入 BuildConfig。未配置时返回 `LNR-SRC-PRE-001`，UI 明确显示源不可用。

## 5. 实现内容

新增：

- `core/domain/.../ScheduleModels.kt`
- `core/application/.../GlobalSchedulePort.kt`
- `core/application/.../GlobalScheduleService.kt`
- `core/application/.../GlobalScheduleServiceTest.kt`
- `app/.../data/riot/RiotGlobalPreMatchSource.kt`
- `app/.../AndroidDiagnosticsPort.kt`
- `app/.../LanerAppGraph.kt`
- `app/.../ui/PreMatchScreen.kt`
- 本开发记录

修改：

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`

## 6. 测试设计

已编写：

- completed 无胜场证据不得结束；
- completed 有 BO 胜场证据可以结束；
- EVENT_LIVE 不存在 IN_GAME 语义；
- 重复 Series 高 Authority 源优先；
- 目录缺失时从真实赛程补全 competition；
- 全源失败不得发明数据。

当前实际运行状态：`PENDING CI`。

## 7. 已知风险

- CI 没有真实 LoL Esports API credential，因此只能验证无 credential 的编译/降级路径；真实在线数据仍需要带 credential 的实机/集成测试。
- 当前只接 Riot 官方 PRE source；多源仲裁逻辑已存在，但 Cito/微博等 Adapter 尚未迁移。
- 全球赛程内部 ID 当前根据规范化赛事、时间、队伍和 BO 生成；后续 Global Identity Graph 会进一步统一跨源 identity mapping。

## 8. 回滚

本轮位于独立 feature branch。合并前关闭 PR 即可完全回滚；合并后按最终 merge commit 回退。

## 9. Post-change Compliance Review

当前：`PENDING CI`。

已确认：

- Core 无 Android/OkHttp/JSON；
- UI 不直连 Riot；
- Region 未成为业务模块边界；
- 无 Mock 比赛事实；
- API credential 未写入 Git；
- Schedule EVENT_LIVE 未升级成 IN_GAME。

## 【任务交付单】

1. 任务：LNR-010 / 全球赛事目录与赛程中心 / TESTING
2. Baseline：main@6fbec881 → 当前 feature branch
3. Constitution Preflight：PASS
4. 涉及模块：core-domain / core-application / app / riot-pre-adapter
5. 强相关条款：架构边界、数据源可替换、UI 不直连 Provider、测试、密钥、留档
6. 文件变更：见 §5
7. 实现内容与设计原因：见 §4-5
8. 测试覆盖：已编写，实际执行 `PENDING CI`
9. 脚本验证：N/A
10. 日志与故障定位：`[Laner:SRC]` / `[Laner:PRE]`；错误码 `LNR-SRC-PRE-001~004`
11. 影响评估：新增真实网络 PRE 数据链；无旧数据写入；无 LIVE 行为变更
12. 已知问题与后续：见 §7
13. 回滚：feature branch / 后续 merge commit
14. 状态同步：待 CI 后同步
15. Commit / Push / PR / Release：已 Push，PR 待创建
16. Post-change Compliance Review：PENDING CI
17. 最终结论：DELIVERY INCOMPLETE
