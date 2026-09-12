# LNR-010 — 全球赛事目录与赛程中心

- 日期：2026-09-12
- 分支：`feature/lnr-010-global-schedule`
- Baseline：`main@6fbec881f80744cf4eda35ad080474c0ae658641`
- PR：`#2`
- 状态：`WAITING EXTERNAL TEST`

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

该设计同时保护 2026-09-12 旧 RiftLab 实机确认通过的“场间/新局开局识别”行为，不允许 PRE Schedule 粗粒度状态覆盖 LIVE 细粒度状态。

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

旧 RiftLab 源码中存在历史硬编码 credential；本次迁移没有复制该值，也没有将其写入任何新文件、日志或测试。

## 5. 实现内容

新增：

- `core/domain/src/main/kotlin/com/laner/core/domain/ScheduleModels.kt`
- `core/application/src/main/kotlin/com/laner/core/application/GlobalSchedulePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/GlobalScheduleService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/GlobalScheduleServiceTest.kt`
- `app/src/main/java/com/laner/app/data/riot/RiotGlobalPreMatchSource.kt`
- `app/src/main/java/com/laner/app/AndroidDiagnosticsPort.kt`
- `app/src/main/java/com/laner/app/LanerAppGraph.kt`
- `app/src/main/java/com/laner/app/ui/PreMatchScreen.kt`
- `docs/audits/2026-09-12_legacy_live_intermission_verification.md`
- 本开发记录

修改：

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- `docs/FEATURE_BASELINE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/TROUBLESHOOTING.md`
- `docs/TESTING.md`
- `docs/CHANGELOG.md`
- `core/domain/README.md`
- `core/application/README.md`
- `app/README.md`

删除：无。

## 6. 测试与证据

### 自动化测试已编写

- completed 无胜场证据不得结束；
- completed 有 BO 胜场证据可以结束；
- EVENT_LIVE 不存在 IN_GAME 语义；
- 重复 Series 高 Authority 源优先；
- 目录缺失时从真实赛程补全 competition；
- 全源失败不得发明数据。

### GitHub Actions

Run：`34687580424`

结果：`PASS`

- Architecture boundary gate：`PASS`；
- Domain and application tests：`PASS`；
- Android debug compile：`PASS`。

### 外部/实机测试

- 带真实 LoL Esports credential 的 `getLeagues/getSchedule` 在线集成：`WAITING EXTERNAL TEST`；
- Android 实机真实赛事目录/本地时区赛程展示：`WAITING EXTERNAL TEST`。

原因：CI 不持有真实 Provider credential。自动化 PASS 不得冒充在线/实机 PASS。

## 7. 开发过程中发现并修复的问题

### LNR010-F01 — Adapter Kotlin 空值写法

初版出现 `ifBlank { null }` 类型不合法风险。

处理：改为 `takeIf { it.isNotBlank() }`。

状态：`FIXED`；Android compile PASS。

### LNR010-F02 — 分页异常控制流

初版在 `runCatching/getOrElse` 中使用 `break`，编译/可读性风险高。

处理：改为普通 `try/catch`，补页失败生成 `LNR-SRC-PRE-004` warning 并安全终止该方向分页。

状态：`FIXED`；Android compile PASS。

### LNR010-F03 — 非拉丁 Region 可能导出空 code

规范化只保留 `[a-z0-9]` 时，纯中文 Region 名可能生成空代码并触发 Domain 不变量。

处理：Region code 无法从 provider/名称推导时显式 fallback `GLOBAL`，Region 仍只是 metadata，不成为业务边界。

状态：`FIXED`；Core tests / Android compile PASS。

## 8. 已知风险与后续

- 真实在线 Provider 拉取尚未验证，所以 PRE-001 / PRE-004 保持 `WAITING EXTERNAL TEST`。
- 当前只接 Riot 官方 PRE source；多源仲裁逻辑已存在，但 Cito/微博等 Adapter 尚未迁移。
- 全球赛程内部 ID 当前根据规范化赛事、时间、队伍和 BO 生成；后续 Global Identity Graph 会进一步统一跨源 identity mapping。
- PRE-005 只完成了赛程侧状态展示，不含完整倒计时/Match Lifecycle 联动，因此仍为 TODO。
- `SH-024 Riot Persisted Gateway / LoL Esports` 只完成 PRE 子集，整体保持 IN PROGRESS。

## 9. 回滚

本轮位于独立 feature branch。合并前关闭 PR #2 即可完全回滚；合并后按 PR #2 最终 merge commit 回退。credential 不在 Git，因此回滚不涉及 secret 清理。

## 10. Post-change Compliance Review

结果：`PASS FOR CODE / WAITING EXTERNAL TEST FOR FEATURE CERTIFICATION`。

已确认：

- Core 无 Android/OkHttp/JSON；
- UI 不直连 Riot；
- Region 未成为业务模块边界；
- 无 Mock 比赛事实；
- API credential 未写入 Git；
- Schedule EVENT_LIVE 未升级成 IN_GAME；
- Provider 子接口失败可局部降级；
- 自动化测试和 Android 编译真实通过；
- 未把未执行的真实在线/实机测试写成 PASS；
- 文档、排障、状态、Changelog、模块 README 已同步。

## 【任务交付单】

1. 任务：LNR-010 / 全球赛事目录与赛程中心 / `WAITING EXTERNAL TEST`
2. Baseline：`main@6fbec881f80744cf4eda35ad080474c0ae658641` → PR #2 final head（以合并前最新 commit 为准）
3. Constitution Preflight：`PASS`
4. 涉及模块：`core-domain / core-application / app / riot-pre-adapter / docs`
5. 强相关条款：架构边界、数据源可替换、UI 不直连 Provider、测试、密钥、留档、状态同步
6. 文件变更：见 §5
7. 实现内容与设计原因：见 §4-5
8. 测试覆盖：
   - 正向：`PASS`
   - 负向：`PASS`
   - 边界：`PASS`
   - 非目标对照：`PASS`（EVENT_LIVE 不触发 IN_GAME）
   - 回归：`PASS`（premature completed 防误判）
   - Integration 在线：`WAITING EXTERNAL TEST`
   - Android 实机：`WAITING EXTERNAL TEST`
9. 脚本验证：`N/A`（本轮无新增可执行脚本）
10. 日志与故障定位：`[Laner:SRC] / [Laner:PRE]`；错误码 `LNR-SRC-PRE-001~004`；见 `TROUBLESHOOTING.md`
11. 影响评估：新增真实网络 PRE 数据链；无旧数据写入；不改变 LIVE 权威状态；credential 不入 Git
12. 已知问题与后续：见 §8
13. 回滚：PR #2 / 最终 merge commit
14. 状态同步：`FEATURE_BASELINE / DEVELOPMENT_PLAN / IMPLEMENTATION_STATUS / TESTING / TROUBLESHOOTING / CHANGELOG / module README` 已同步
15. Commit / Push / PR / Release：代码与文档已 Push；PR #2 已建立；待最终 head CI 后合并
16. Post-change Compliance Review：`PASS FOR CODE / WAITING EXTERNAL TEST FOR CERTIFICATION`
17. 最终结论：`DELIVERY INCOMPLETE — WAITING EXTERNAL TEST`；代码可合并，不得宣称 PRE-001/PRE-004 已 DONE
