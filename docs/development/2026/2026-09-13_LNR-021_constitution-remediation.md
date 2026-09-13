# LNR-021 Constitution Remediation — INC-LNR-021-001

- Task: `LNR-021-REMEDIATION`
- Incident: `INC-LNR-021-001`
- Date: 2026-09-13 Asia/Taipei
- Executor: OpenAI / ChatGPT with GitHub connector
- Baseline: `main@c73d551925ba273d4caf6259ef621100c105b5de`
- Repair branch: `fix/lnr-021-constitution-remediation`
- PR: `#17 — LNR-021: constitution remediation`（Draft while this record is being completed）
- Current record status: `DELIVERY INCOMPLETE`

## 1. 需求来源与目标

Block 2 / LNR-021 完成工程交付后，按用户规定执行独立工程宪法复查。复查形成 `INC-LNR-021-001`，确认 6 项偏离。用户随后明确授权“修”。

本整改目标不是扩展 Tactical 功能，而是：

1. 修复赛事事实完整性问题；
2. 补足被原测试名夸大的负向/边界回归；
3. 为 LIVE Timeline v1 → v2 建立升级前可验证恢复点；
4. 同步被审计确认陈旧的权威文档；
5. 把任务状态恢复到宪法允许的标准 token；
6. 新建不可变整改记录，并在最终 Gate 后执行独立 Post-change Compliance Review。

## 2. Fresh Constitution Preflight

本整改没有直接续写任何旧修复草稿；从最新 `main@c73d5519...` 和事故记录重新建立事实基线。

已重新读取/核对：
- `docs/ENGINEERING_CONSTITUTION.md`（当前 1.0.0-laner.1）；
- `main@c73d551925ba273d4caf6259ef621100c105b5de`；
- `docs/PROJECT_SCOPE.md`；
- `docs/ARCHITECTURE.md`；
- `docs/DEVELOPMENT_PLAN.md`；
- `docs/IMPLEMENTATION_STATUS.md`；
- `docs/FEATURE_BASELINE.md` 中 LNR-021 对应 LIVE 状态；
- `docs/TESTING.md`；
- `docs/TROUBLESHOOTING.md`；
- `docs/COMPATIBILITY.md`；
- `docs/PRODUCT_PERSPECTIVE_MATRIX.md`；
- `docs/development/2026/2026-09-13_LNR-021_违宪事故记录.md`；
- 原 LNR-021 delivery / premerge / closeout records；
- `LiveEventDerivationService` 与其 tests；
- `LiveTimelineService`；
- `MatchEvent.kt`；
- `JsonLiveTimelineRepository` 与其 tests；
- `app/README.md` 与 `core/application/README.md`。

Preflight 结论：**PASS for remediation start**。

特别说明：这只表示“本整改任务自己的 Preflight 已完成”。它不追溯修改原 LNR-021 开发时漏读资料的历史事实；`INC-021-01` 仍然成立并被保留。

## 3. 范围 / 不做项

### 本次范围
- `INC-021-01~06` 全部整改；
- 必要的 Application 事实规则、Android persistence migration/recovery、永久测试、模块 README 与权威 docs；
- 新的 remediation record / troubleshooting / changelog / status synchronization。

### 明确不做
- 不新增 Watch Hub / 播放器；
- 不实现 Herald/Atakhan 全局标准化；
- 不实现 real Draft Provider；
- 不新增官方 killer/victim 或 Double/Triple/Quadra/Penta 推断；
- 不把真实 Riot online / Android device 未执行项目写 PASS；
- 不改写旧 LNR-021 delivery/closeout 或事故记录来制造“从未失败”的历史。

## 4. 关键设计决策

### R1 — unknown 不能为了 TeamFight 方便而变成 0
旧实现：

```text
(blueKillDelta ?: 0) + (redKillDelta ?: 0)
```

这会把一侧“不可计算”固化成“确认 0”。整改采用最小安全规则：

- blue/red 两侧 kill delta 都必须非 null；
- 窗口 <=20s；
- 两侧累计 >=3；
- 才允许生成 `TeamFightWindowEvent`。

若只有一侧可确认增长，仍可保留该侧 aggregate `KillEvent`，但不生成 TeamFightWindow。

没有把 Domain `TeamFightWindowEvent` 字段改成 nullable，因为本任务没有真实需求要求表达“半已知团战窗”；引入 nullable 只会扩大 Schema/UI/semanticKey 复杂度。

### R2 — regression / missing player 必须保守退化
- team counter 下降：delta 无效，不产生 Kill/TeamFight；
- player counter 下降：不得绑定该 player；若 team delta 可确认，仅生成 aggregate unknown-player Kill；
- current player row 缺失：同样只允许 aggregate unknown-player Kill。

### R3 — v1 → v2 必须先有恢复点
`JsonLiveTimelineRepository.write()` 在覆盖已有 v1 文件前：

1. 读取原始 v1 文本；
2. 创建 sibling `<timeline>.schema-v1.bak`；
3. 使用同一 atomic-write 机制写 recovery copy；
4. 精确校验 backup 内容与原 v1 文本一致；
5. 只有校验成功才允许写 v2 主文件。

若 recovery copy 已存在但内容与当前 v1 不一致，迁移直接失败，主文件保持 v1。

该策略只承诺“恢复升级前 v1”；新建纯 v2 Timeline 不宣称可无损 downgrade。

### R4 — 状态与工程认证分离
任务表只使用宪法 §13.1 状态。`COMPLIANCE PASS / ENGINEERING DELIVERY MERGED / INCIDENT OPEN` 属于正文工程事实，不再拼进 Status 单元格。

## 5. 文件变更（当前整改 diff）

### 新增
- `app/src/test/java/com/laner/app/data/live/JsonLiveTimelineMigrationRecoveryTest.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveEventDerivationSafetyRegressionTest.kt`
- `docs/development/2026/2026-09-13_LNR-021_违宪事故记录.md`（事故记录，来自独立审计分支并作为整改基线历史）
- `docs/development/2026/2026-09-13_LNR-021_constitution-remediation.md`（本记录）

### 修改
- `core/application/src/main/kotlin/com/laner/core/application/LiveEventDerivationService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/LiveEventDerivationServiceTest.kt`
- `app/src/main/java/com/laner/app/data/live/JsonLiveTimelineRepository.kt`
- `app/README.md`
- `core/application/README.md`
- `docs/ARCHITECTURE.md`
- `docs/CHANGELOG.md`
- `docs/COMPATIBILITY.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/PROJECT_SCOPE.md`
- `docs/TESTING.md`
- `docs/TROUBLESHOOTING.md`

### 删除
无。

## 6. 事故项 → 整改映射

| Incident | Remediation |
|---|---|
| INC-021-01 Preflight 不完整 | 新整改从最新 main/宪法重新完整 Preflight；不篡改原历史 |
| INC-021-02 unknown→0 | TeamFightWindow 两侧 delta 均已知才生成；新增永久回归 |
| INC-021-03 测试名夸大覆盖 | 原测试改名为 missing-only；新增真实 regression/unknown/player-row 回归 |
| INC-021-04 权威文档陈旧 | Scope / Architecture / Testing / Compatibility / Plan / Status / READMEs 全部同步 |
| INC-021-05 非标准状态 | Task status 只使用 §13.1 token；工程认证拆到正文 |
| INC-021-06 migration 无 recovery | v1 覆盖前 exact backup + verification；mismatch 阻断覆盖 |

## 7. 测试设计

### Application factual safety
`LiveEventDerivationSafetyRegressionTest`：
- one-sided unknown → aggregate Kill allowed / no TeamFight；
- team counter regression → no combat events；
- player counter regression → aggregate unknown-player；
- missing player row → aggregate unknown-player。

原 `LiveEventDerivationServiceTest` 中被误命名的测试改为 `missingCountersDoNotManufactureEvents`，不再声称覆盖 regression。

### Adapter migration/recovery
`JsonLiveTimelineMigrationRecoveryTest`：
- first v1→v2 write preserves exact recovery copy；
- subsequent v2 write does not mutate v1 backup；
- mismatched pre-existing recovery blocks overwrite and source remains v1。

既有 `JsonLiveTimelineRepositoryTest` 继续覆盖 round-trip/v1 read/write-forward/corruption/schema。

### Full Gate
最终冻结 head 必须通过：
- Architecture boundary gate；
- Domain/Application tests；
- Android Adapter unit tests；
- Android debug compile；
- APK upload。

当前阶段所有 Gate 仅可作为 intermediate evidence，直到 docs/record 冻结后的 exact-head / PR Gate 通过。

## 8. 中途验证

在文档尚未最终同步时，`fde5c64c27ed0365ed392c58e30a6051fea4af8f` 的 PR workflow `34731621417` 已观察到：
- Architecture boundary: PASS；
- Domain/Application: PASS；
- Android Adapter Unit: running at observation time。

该 run **不是最终交付证据**，因为之后 branch head 继续推进。

## 9. 风险 / 兼容 / 安全 / 性能 / 数据

- 架构：未新增跨层依赖；事实规则仍由 Application 负责。
- 数据：v1→v2 现在增加升级前恢复副本，磁盘占用会为发生升级的旧 Timeline 增加一个 v1 backup；这是换取可恢复性的有意成本。
- 兼容：v1 read 不变；v2 write 不变；新增的是覆盖旧 v1 前的 fail-safe。
- 性能：仅在检测到已有 v1 文件并准备首次写 v2 时多一次原文件读取、backup 原子写和一致性校验；正常 v2 写入无 backup 重写。
- 安全：不新增 credential、网络或敏感输入面。
- UI：不改变 HUD 布局/操作；仅防止不完整事实形成 TeamFightWindow。

## 10. 已知问题 / 外部项目

仍保持：
- real Riot online Tactical trigger：`WAITING EXTERNAL TEST`；
- Android Tactical overlay/touch-through/orientation/source-loss behavior：`WAITING EXTERNAL TEST`；
- LIVE-009 Herald/Atakhan：`IN PROGRESS`；
- LIVE-012 real Draft Provider：`TODO`；
- official killer/victim / official multi-kill / dragon subtype：没有明确 evidence 时不支持。

这些不是本事故整改的完成阻塞项，但不得被 CI 冒充 PASS。

## 11. 回滚

整改未合并前：删除/关闭 `fix/lnr-021-constitution-remediation` / PR #17 即可回到 `main@c73d5519...`。

整改合并后：revert 整改 merge commit。若回退到只认识 Timeline v1 的构建：
- 先备份当前 v2；
- 仅对存在并验证 `.schema-v1.bak` 的升级文件恢复原 v1；
- 纯 v2 新文件不得宣称可无损降级。

## 12. Post-change Compliance Review

当前：`NOT EXECUTED / WAITING FINAL EXACT HEAD`。

最终复查必须明确搜索/验证：
- `blueKillDelta ?: 0` / `redKillDelta ?: 0` 不再存在于 TeamFight derivation；
- 没有其他路径把 unknown kill delta 固化为 0；
- UI 没有恢复事件派生逻辑；
- raw Provider payload 不进入 HUD；
- Region 没有成为业务分支；
- migration recovery 与文档一致；
- task status 全部使用标准 token；
- 事故 6 项全部有证据关闭；
- 整改没有制造新的 cross-layer / silent exception / stale-doc / false-PASS 债务。

## 13. 固定 §15 任务交付单（当前阶段）

1. **任务**：`LNR-021-REMEDIATION / INC-LNR-021-001`；当前 `DELIVERY INCOMPLETE`。
2. **Baseline**：`main@c73d551925ba273d4caf6259ef621100c105b5de` → `fix/lnr-021-constitution-remediation`；结束 commit 待最终冻结。
3. **Constitution Preflight**：PASS（仅指本整改任务）。
4. **涉及模块**：`:core:application`, `:app`, docs governance。
5. **强相关条款**：§0.4、§1/1.1/1.2、§2、§3.2、§7、§9、§11、§13、§14、§15、L-3/L-5/L-8。
6. **文件变更**：见本记录 §5；最终 diff 仍需复核。
7. **实现内容与设计原因**：见 §4；核心是 unknown 保真、真实边界回归、v1 recovery、权威状态同步。
8. **测试覆盖**：正向/负向/边界/非目标/回归已新增或保留；最终执行结果待 exact-head Gate；Integration/E2E/实机=`WAITING EXTERNAL TEST`。
9. **脚本验证**：N/A；本整改没有新增/修改可执行脚本。
10. **日志与故障定位**：`LNR-APP-LIVE-004` 登记事实完整性故障；迁移恢复路径登记于 Troubleshooting/README。
11. **影响评估**：Application TeamFight rule + Android Timeline migration recovery + docs；无 Watch Hub/PRE/POST/AI 功能扩展。
12. **已知问题与后续**：真实 online/device 与 LIVE-009/012，见 §10。
13. **回滚**：见 §11。
14. **状态同步**：Scope/Architecture/Testing/Compatibility/Plan/Status/READMEs/Troubleshooting/Changelog 已进入整改同步；最终需复核。
15. **Commit / Push / PR / Release**：repair commits 已 push；PR #17 Draft；merge/release 未完成。
16. **Post-change Compliance Review**：`NOT EXECUTED`。
17. **最终结论**：`DELIVERY INCOMPLETE`。

> 本记录在当前阶段故意不写 PASS/DONE。最终交付认证必须由冻结 head 的 Gate、PR merge、main Gate 和独立合规复查支持；历史事故记录不改写。
