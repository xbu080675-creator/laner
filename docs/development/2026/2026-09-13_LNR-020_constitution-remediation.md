# LNR-020 — Constitution Remediation / 合规整改

- Date: `2026-09-13` (Asia/Taipei)
- Incident: `INC-LNR-020-001`
- Original functional task: `LNR-020 — RiftScreen / Draft HUD Android Overlay`
- Repair branch: `fix/lnr-020-constitution-remediation`
- Initial repair baseline: `main@967e112d6efcf8e6daa86f0b007cedc39b63b04c`
- Incident record baseline: `docs/lnr-020-constitution-incident@1b8c6b183ece4b64a4f9ecd8a8c097034d5e37bc`
- Current status: `IN PROGRESS / PR #12 DRAFT`

> 本记录是独立整改记录，不修改 LNR-020 原开发记录来伪造历史合规。原任务的 Preflight 缺失、两次测试失败、PR #10、merge 与 CI 证据全部保留。

## 1. 用户授权与整改原则

用户在事故记录完成后明确授权：“先修复这个版本所产生的问题”。同时新增长期原则：

> 不把以前的过错带到这里，同时也不要因为自己的疏忽产生新的过错。

因此本次整改明确：

- 不直接续接旧 `fix/lnr-020-constitution-repair@53e0156…` 草稿；
- 重新从当前仓库事实执行真实 Preflight；
- 只整改事故记录确认的 7 项偏离；
- 不扩展 Tactical HUD；
- 不迁移真实 Draft Provider；
- 不改变 Domain 赛事事实语义；
- Android 真机未验证项继续保持 `WAITING EXTERNAL TEST`。

## 2. Constitution Preflight

本次在代码整改前重新读取并交叉核对：

- `docs/ENGINEERING_CONSTITUTION.md`，版本 `1.0.0-laner.1`；
- `main@967e112d6efcf8e6daa86f0b007cedc39b63b04c`；
- `docs/DEVELOPMENT_PLAN.md`；
- `docs/IMPLEMENTATION_STATUS.md`；
- `docs/ARCHITECTURE.md`；
- `docs/TESTING.md`；
- `docs/TROUBLESHOOTING.md`；
- `docs/FEATURE_BASELINE.md`；
- `core/application/README.md`；
- `app/README.md`；
- 原 LNR-020 开发记录与 `INC-LNR-020-001` 事故记录；
- `GlobalScheduleService / LiveTargetSelector / LiveMatchStateService / LiveSnapshotService / LiveTimelineService / DiagnosticsPort / ErrorCode`；
- `LiveMatchScreen / LanerRoot / MainActivity / LanerAppGraph`；
- `RiftScreenOverlayService / RiftScreenOverlayView / DraftHudOverlayView / DraftHudPreviewSession`；
- 现有 LIVE 与 Draft HUD 测试基线。

本次不引入新的外部 API 或版本敏感 Android API；WindowManager 行为沿用 LNR-020 已存在的平台 API，因此不需要新外部规范作为设计输入。

**Preflight 判定：PASS。**

## 3. 整改范围与验收条件

### 3.1 `INC-020-01` — 原任务 Preflight 缺失

历史事实不可补写。整改方式是：本独立修复任务真实执行 Preflight，并把证据记录在此；原 LNR-020 仍保持“当时未完成 Preflight”的事实。

### 3.2 `INC-020-02` — 固定 17 项任务交付单缺失

不修改原记录伪造旧交付单。本整改记录末尾使用 §15 固定 17 项交付单，最终必须全部真实填写。

### 3.3 `INC-020-03` — 权威状态文件过期

整改完成前更新 `IMPLEMENTATION_STATUS / DEVELOPMENT_PLAN / README` 到真实 merge/CI/事故整改状态；不得提前把整改写成 PASS。

### 3.4 `INC-020-04` — Presentation 重复 Application 编排

新增唯一 `LiveMatchContextService`：

```text
GlobalScheduleService
→ LiveTargetSelector
→ LiveMatchStateService
→ LiveSnapshotService
→ canonical GameId selection
→ LiveTimelineService
→ LiveMatchContextResult
```

Compose LIVE、RiftScreen、后续 Tactical HUD 只允许消费该 Application Query，不再复制调用顺序。

### 3.5 `INC-020-05` — Overlay 静默异常

所有 WindowManager add/update/remove/bounds 统一经过 `OverlayWindowHost`，失败必须输出：

- `[Laner:OVERLAY]`；
- `LNR-OVR-WINDOW-001~004`；
- `window / operation / error_type`；
- 明确 retryability 与可预测 fallback。

Application Current LIVE Context 非业务异常使用 `LNR-APP-LIVE-003`；Overlay mapper 异常使用 `LNR-OVR-REFRESH-001`。

### 3.6 `INC-020-06` — Troubleshooting 未同步

把 LNR-020 Failure A/B 追加为稳定故障入口，并关联永久回归测试；不删除原开发记录中的失败历史。

### 3.7 `INC-020-07` — Overlay Service 职责过重

拆出：

- `OverlayWindowHost`；
- `RiftScreenWindowController`；
- `DraftHudWindowController`。

`RiftScreenOverlayService` 只保留 Foreground Service 生命周期、通知/Action、周期刷新、Preview collection、Application Result → Presentation 连接与组件组装。

## 4. 当前实现

### 4.1 Application Current LIVE Context

新增：
- `LiveMatchContextService.kt`
- `LiveMatchContextServiceTest.kt`

关键约束：
- `LiveMatchStateService` 继续是 lifecycle 唯一权威；
- `LiveSnapshotService` 继续只验证 gameplay frame；
- snapshot GameId 优先于 lifecycle currentGameId 的 Timeline 查询策略只有一个权威实现；
- 无目标使用 typed `NoTarget`；
- 意外失败使用 `LNR-APP-LIVE-003`；
- `CancellationException` 显式继续抛出，不因错误整改而吞掉协程取消。

### 4.2 Android Presentation / Overlay

新增：
- `OverlayWindowHost.kt`
- `RiftScreenWindowController.kt`
- `DraftHudWindowController.kt`
- `OverlayWindowOperationTest.kt`

修改：
- `LanerAppGraph.kt`
- `MainActivity.kt`
- `LanerRoot.kt`
- `LiveMatchScreen.kt`
- `RiftScreenOverlayService.kt`

设计结果：
- LIVE UI 只依赖 `LiveMatchContextService`；
- Overlay 每轮刷新只调用 `graph.liveMatchContextService.load()`；
- WindowManager 不再散落在 Service；
- 原无诊断 `runCatching` 已从 Service 设计中移除；
- Draft Verified presentation 仍优先本地 Preview；
- Lock 仍通过 `FLAG_NOT_TOUCHABLE`；
- RiftScreen MINI/COMPACT/EXPANDED、拖动、clamp 逻辑迁入专属 controller，不改变产品行为。

## 5. 整改过程中主线变化记录

Preflight 开始时 `main = 967e112d…`。

在整改分支推进到 `6720f66073c13c910c778f5ff8df502e5bcfcd16` 后，仓库出现并合并 PR #11：

- PR #11 title: `8docs: record LNR-020 constitution incident`；
- base: `967e112d…`；
- head: `6720f660…`；
- merge: `d4e4f70b9b804d2106b0db7bb335a1f564c60d75`；
- changed files 经 GitHub 复核全部属于本事故记录与本次整改前半段，没有发现 Tactical HUD 或无关业务改动。

该主线变化在打开当前 Draft PR #12 时被立即发现并复核，没有继续假定 main 仍停留在旧 SHA。

当前 PR #12 以新的 `main@d4e4f70…` 为 base，承载剩余整改与最终闭环。此处只记录仓库事实，不推测 PR #11 的触发来源。

## 6. 测试计划与当前证据

必须执行：

- Architecture boundary Gate；
- `:core:domain:test :core:application:test`；
- `:app:testDebugUnitTest`；
- `:app:assembleDebug`；
- APK artifact upload；
- `LiveMatchContextServiceTest` 正向/负向/边界/异常诊断；
- `OverlayWindowOperationTest` 错误码唯一性与恢复分类；
- Draft HUD mapper 既有回归；
- non-target：LIVE-012/LIVE-030 状态不得被升级；
- Android 真机：继续 `WAITING EXTERNAL TEST`。

当前 PR #12 首轮 workflow 已启动；最终结果在 Gate 完成后回填，未完成前不得写 PASS。

## 7. 当前已知事项

- `DraftHudLayoutStore` 仍使用 `laner_draft_hud_layout_v1`，本整改不修改 Schema，因此事故记录中的 migration/recovery 观察项不扩大为本次范围；
- 真机 Window token / ROM / 权限差异不能由 JVM/CI 证明；
- Tactical HUD 明确未开始。

---

# 8. §15 固定任务交付单

1. **任务编号 / 标题 / 最终状态**：`LNR-020 Constitution Remediation` / `IN PROGRESS`。
2. **Baseline branch / start commit / end commit**：`fix/lnr-020-constitution-remediation` / `1b8c6b183ece4b64a4f9ecd8a8c097034d5e37bc` / `NOT FINAL`；Preflight 初始 main `967e112d…`，任务中 main 前进到 `d4e4f70…`，已复核。
3. **Constitution Preflight**：`PASS`；完整读取项见第 2 节。
4. **涉及模块**：`:core:application`, `:app`, docs/development/status/troubleshooting。
5. **强相关条款**：§0.3, §0.4, §1, §1.1, §1.2, §2.2, §3.2, §3.3, §3.4, §8, §9, §11, §13.2, §13.3, §14, §15, L-3, L-5, L-6, L-8。
6. **精确文件增删改**：`IN PROGRESS`；最终以 PR changed-files 反查回填。
7. **实现与设计原因**：修复 `INC-LNR-020-001` 7 项确认偏离，不扩功能；详见第 3~4 节。
8. **测试**：正向 `NOT EXECUTED FINAL`；负向 `NOT EXECUTED FINAL`；边界 `NOT EXECUTED FINAL`；非目标 `NOT EXECUTED FINAL`；历史回归 `NOT EXECUTED FINAL`；Integration/Device `WAITING EXTERNAL TEST`。
9. **脚本验证**：仓库无本任务专用迁移脚本，`N/A`；Gradle/Architecture workflow 结果待回填。
10. **日志 / 故障定位**：已设计 `LNR-APP-LIVE-003`, `LNR-OVR-WINDOW-001~004`, `LNR-OVR-REFRESH-001`；最终代码/测试证据待 Gate。
11. **影响范围**：Current LIVE Query、Compose LIVE 依赖、RiftScreen/Draft HUD Window orchestration；不改变赛事 Domain 事实定义。
12. **已知问题 / Follow-up**：Android 实机 overlay 行为 `WAITING EXTERNAL TEST`；Draft Layout future migration 为 audit note；Tactical HUD 未开始。
13. **回滚**：PR #12 未合并前可关闭 PR；PR #11 已合入的整改前半段如需回滚必须以独立 revert 留痕，禁止改写历史。最终回滚点待 merge 后回填。
14. **状态同步**：`IN PROGRESS`；Troubleshooting 已开始同步，其余 authority docs 待 Gate 结果后更新。
15. **Commit / Push / PR / Release**：PR #12 `DRAFT`；release `N/A`；最终 head/merge/CI 待回填。
16. **Post-change Compliance Review**：`NOT EXECUTED`。
17. **最终结论**：`DELIVERY INCOMPLETE`，因为自动 Gate、状态落账、Post-change Compliance Review 与 PR merge 尚未完成。
