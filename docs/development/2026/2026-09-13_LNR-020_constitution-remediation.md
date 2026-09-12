# LNR-020 — Constitution Remediation / 合规整改

- Date: `2026-09-13` (Asia/Taipei)
- Incident: `INC-LNR-020-001`
- Original functional task: `LNR-020 — RiftScreen / Draft HUD Android Overlay`
- Repair branch: `fix/lnr-020-constitution-remediation`
- Initial repair baseline: `main@967e112d6efcf8e6daa86f0b007cedc39b63b04c`
- Incident record baseline: `docs/lnr-020-constitution-incident@1b8c6b183ece4b64a4f9ecd8a8c097034d5e37bc`
- Remediation review head: `098d6a6896fd443cd39340f8094c7511251f569d`
- Current status: `CODE/DOC REMEDIATION REVIEW PASS / DELIVERY INCOMPLETE UNTIL MERGE + MAIN VERIFY`

> 本记录是独立整改记录，不修改 LNR-020 原开发记录来伪造历史合规。原任务的 Preflight 缺失、两次测试失败、PR #10、merge 与 CI 证据全部保留。

## 1. 用户授权与整改原则

用户在事故记录完成后明确授权：“先修复这个版本所产生的问题”。同时新增长期原则：

> 不把以前的过错带到这里，同时也不要因为自己的疏忽产生新的过错。

因此本次整改明确：

- 不直接续接旧 `fix/lnr-020-constitution-repair@53e0156…` 草稿；
- 重新从当前仓库事实执行真实 Preflight；
- 只整改事故记录确认的 7 项偏离及其同类状态漂移；
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

不修改原记录伪造旧交付单。本整改记录使用 §15 固定 17 项交付单；PR merge 与 main 复核完成前，最终字段保持未闭环状态。

### 3.3 `INC-020-03` — 权威状态文件过期

已同步：

- `docs/IMPLEMENTATION_STATUS.md`：补原 PR #10 merge `967e112d…`、main run `34706047380`、当前事故与 PR #12 状态；
- `docs/DEVELOPMENT_PLAN.md`：Block 2 明确受本整改 Gate 阻塞；
- 根 `README.md`：从错误的 `M0 / 尚未迁移 / 下一步 LNR-001` 修正为当前 `M1 / Feature Migration`；
- `app/README.md` / `core/application/README.md`：同步真实架构与故障定位链。

这些状态文件当前仍如实写“整改中”，不会在 PR #12 merge 前提前写成 CLOSED。

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

当前静态复查：
- `LiveMatchScreen` 只依赖 `LiveMatchContextService`；
- `RiftScreenOverlayService` 每轮只调用 `graph.liveMatchContextService.load(context)`；
- Presentation 不再决定 snapshot/lifecycle GameId 优先级或服务调用顺序。

### 3.5 `INC-020-05` — Overlay 静默异常

所有 WindowManager add/update/remove/bounds 统一经过 `OverlayWindowHost`，失败输出：

- `[Laner:OVERLAY]`；
- `LNR-OVR-WINDOW-001~004`；
- `window / operation / error_type`；
- 明确 retryability 与可预测 fallback。

Application Current LIVE Context 非业务异常使用 `LNR-APP-LIVE-003`；Overlay mapper 异常使用 `LNR-OVR-REFRESH-001`。

静态复查确认：整改后的 `RiftScreenOverlayService / RiftScreenWindowController / DraftHudWindowController` 不再使用无诊断 WindowManager `runCatching`；平台 catch 由 `OverlayWindowHost` 统一发出 diagnostics。

### 3.6 `INC-020-06` — Troubleshooting 未同步

`docs/TROUBLESHOOTING.md` 已追加：

- `LNR-UI-LIVE-003`：run `34704014273` / stale UI-private target selector test；
- `LNR-LIVE-TEST-003`：run `34705468018` / Draft HUD 错误断言；
- `LNR-APP-LIVE-003`；
- `LNR-OVR-WINDOW-001~004`；
- `LNR-OVR-REFRESH-001`。

原开发记录中的 Failure A/B 没有删除或改写。

### 3.7 `INC-020-07` — Overlay Service 职责过重

已拆出：

- `OverlayWindowHost`；
- `RiftScreenWindowController`；
- `DraftHudWindowController`。

`RiftScreenOverlayService` 现在只保留 Foreground Service 生命周期、通知/Action、周期刷新、Preview collection、Application Result → Presentation 连接与组件组装；Window 参数、Rift 拖拽/clamp、Draft Edit/Lock/Dock 均已移出。

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
- 原无诊断 `runCatching` 已从整改路径移除；
- Draft Verified presentation 仍优先本地 Preview；
- Lock 仍通过 `FLAG_NOT_TOUCHABLE`；
- RiftScreen MINI/COMPACT/EXPANDED、拖动、clamp 逻辑迁入专属 controller，不改变产品行为；
- Service teardown 增加 `destroyed` gate 与 `mainHandler.removeCallbacksAndMessages(null)`，防止网络/Flow 的晚到主线程 callback 在 `onDestroy` 后重建窗口；
- Draft controller teardown 不再为了销毁先更新一次 Edit/Lock flags，避免无意义的窗口更新失败。

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

## 6. 自动 Gate 与测试证据

### 6.1 PR #12 exact-head Gate

整改 review head：`098d6a6896fd443cd39340f8094c7511251f569d`

GitHub Actions：
- run: `34707759041`
- job: `103590804282`
- workflow: `Android Build`
- conclusion: `success`

逐 Gate 结果：
- Architecture boundary gate：PASS；
- Domain and application tests：PASS；
- Android adapter unit tests：PASS；
- Android debug compile：PASS；
- Upload debug APK：PASS。

Artifact：
- id `10302302373`；
- name `laner-debug-edb6620cb59582b35ed7f8ca7517fc77f470e81b`；
- size `11,451,563` bytes；
- digest `sha256:7eea41054c3b9525913b3106617ee269b16845f4fd0a042987983f9ec04dbc67`；
- workflow head SHA 仍明确为 `098d6a…`；artifact name 使用 GitHub PR merge-ref SHA，不冒充 branch head。

### 6.2 新增永久回归

`LiveMatchContextServiceTest`：
- 正向：`readyContextOwnsTargetStateSnapshotAndTimelineOrdering`；
- 负向：`emptyScheduleReturnsTypedNoTargetWithoutCallingLiveSources`；
- 边界：`completedOnlyScheduleReturnsNoEligibleTarget`；
- 异常诊断：`unexpectedApplicationFailureIsDiagnosedWithStableCode`。

`OverlayWindowOperationTest`：
- `everyWindowOperationHasUniqueStableErrorCode`；
- `recoveryClassificationDoesNotPretendRemovalCanBeRetriedSafely`。

既有 Draft HUD mapper / LIVE / Application tests 随 `:app:testDebugUnitTest` 与 `:core:application:test` 全量回归通过。

### 6.3 非目标对照

从原 `967e112…` 到 review head `098d6a…` 的 compare 共 19 个文件，均属于事故记录、Current LIVE Context、Overlay 拆分、测试与状态文档；没有 Tactical HUD / Draft Provider / Domain 赛事事实实现文件。

`docs/FEATURE_BASELINE.md` 未被本整改修改，并重新读取确认：
- `LIVE-012 BP / Draft 实时状态 = TODO`；
- `LIVE-024~029 = WAITING EXTERNAL TEST`；
- `LIVE-030 Tactical HUD = TODO`；
- `LIVE-014 Kill/MultiKill/TeamFightWindow = TODO`；
- `LIVE-015 GoldLeadChange = TODO`。

因此没有通过整改偷偷升级非目标功能状态。

### 6.4 实机边界

以下仍不能由 CI 证明，继续 `WAITING EXTERNAL TEST`：
- 系统 overlay permission；
- 前台隐藏 / 后台显示；
- RiftScreen drag/bounds/三档尺寸/close；
- Draft HUD Edit/Lock、模块拖动、Scale/Alpha/Visibility/Reset；
- 横竖屏 Profile；
- LOCK 触摸穿透与 edge Dock；
- Android ROM / Window token 差异。

## 7. Post-change Compliance Review

以 `098d6a…` 为代码/文档 review head，逐项复审：

| Incident | Review result | Evidence |
|---|---|---|
| `INC-020-01` Preflight 缺失 | REMEDIATED FOR REPAIR TASK | 本整改在写代码前真实执行完整 Preflight；不反写原任务为 PASS |
| `INC-020-02` 17 项交付单缺失 | REMEDIATED IN THIS RECORD | 本文件按 §15 固定结构留档；merge/main 最终事实待 closeout |
| `INC-020-03` 状态漂移 | REMEDIATED PRE-MERGE | IMPLEMENTATION_STATUS / DEVELOPMENT_PLAN / root README / module README 已同步真实当前状态 |
| `INC-020-04` Presentation 重复业务编排 | PASS | 唯一 `LiveMatchContextService` + Core tests + Architecture Gate |
| `INC-020-05` 静默异常 | PASS | `OverlayWindowHost` + `LNR-OVR-*` + no silent WindowManager runCatching + App tests |
| `INC-020-06` Troubleshooting 缺口 | PASS | Failure A/B 与新 diagnostics 已登记统一故障入口 |
| `INC-020-07` Service God Class | PASS | Window host + Rift/Draft controller 拆分；Service 只保留 lifecycle/scheduling/composition |

额外防新错检查：
- 旧 `53e0156…` 草稿未直接续接；
- `CancellationException` 不被通用异常兜底吞掉；
- Service late callback teardown race 增加 destroyed gate；
- Draft teardown 不额外制造 Window update；
- `DraftHudLayoutStore` schema 未改，不借整改触发无迁移的数据变化；
- Region 仍只是数据维度，没有新增赛区业务分支；
- raw Provider payload 未进入 UI；
- Android 实机项没有因 CI PASS 被升级成 DONE。

**Post-change Compliance Review（pre-merge code/doc scope）：PASS。**

该 PASS 不等价于任务最终 CLOSED：本次记录更新会形成新的 docs-only head，仍需 exact-head Gate；随后必须 PR #12 merge，并对 merge 后 main 再做状态落账与 Gate 复核。

## 8. 当前已知事项

- `DraftHudLayoutStore` 仍使用 `laner_draft_hud_layout_v1`，本整改不修改 Schema，因此事故记录中的 migration/recovery 观察项不扩大为本次范围；
- 真机 Window token / ROM / 权限差异不能由 JVM/CI 证明；
- Tactical HUD 明确未开始；
- PR #12 merge/main closeout 尚未完成。

---

# 9. §15 固定任务交付单

1. **任务编号 / 标题 / 最终状态**：`LNR-020 Constitution Remediation` / `DELIVERY INCOMPLETE — PRE-MERGE REMEDIATION PASS`。
2. **Baseline branch / start commit / end commit**：branch `fix/lnr-020-constitution-remediation`；start `1b8c6b183ece4b64a4f9ecd8a8c097034d5e37bc`；review head `098d6a6896fd443cd39340f8094c7511251f569d`；最终 end/merge commit 待 post-merge closeout。
3. **Constitution Preflight**：`PASS`；完整读取项见第 2 节；没有把原 LNR-020 事后补写成 PASS。
4. **涉及模块**：`:core:application`, `:app`, root/module README, docs/development/status/troubleshooting。
5. **强相关条款**：§0.3, §0.4, §1, §1.1, §1.2, §2.2, §3.2, §3.3, §3.4, §8, §9, §11, §13.2, §13.3, §14, §15, L-3, L-5, L-6, L-8。
6. **精确文件增删改（相对原 LNR-020 main `967e112…`）**：新增 `app/src/main/java/com/laner/app/overlay/DraftHudWindowController.kt`, `OverlayWindowHost.kt`, `RiftScreenWindowController.kt`, `app/src/test/java/com/laner/app/overlay/OverlayWindowOperationTest.kt`, `core/application/src/main/kotlin/com/laner/core/application/LiveMatchContextService.kt`, `core/application/src/test/kotlin/com/laner/core/application/LiveMatchContextServiceTest.kt`, `docs/development/2026/2026-09-13_LNR-020_constitution-remediation.md`, `docs/development/2026/2026-09-13_LNR-020_违宪事故记录.md`；修改 `README.md`, `app/README.md`, `app/src/main/java/com/laner/app/LanerAppGraph.kt`, `MainActivity.kt`, `overlay/RiftScreenOverlayService.kt`, `ui/LanerRoot.kt`, `ui/LiveMatchScreen.kt`, `core/application/README.md`, `docs/DEVELOPMENT_PLAN.md`, `docs/IMPLEMENTATION_STATUS.md`, `docs/TROUBLESHOOTING.md`；删除：无。
7. **实现与设计原因**：只修复 `INC-LNR-020-001` 7 项确认偏离及同类状态漂移；不扩功能；详见第 3~4 节。
8. **测试**：正向 PASS；负向 PASS；边界 PASS；异常诊断 PASS；非目标 PASS（FEATURE_BASELINE 状态不变）；历史回归 PASS；Architecture PASS；Integration/Device `WAITING EXTERNAL TEST`。自动证据 run `34707759041`。
9. **脚本验证**：无本任务专用迁移脚本，`N/A`；Architecture/Gradle workflow run `34707759041` 全 Gate PASS。
10. **日志 / 故障定位**：`LNR-APP-LIVE-003`, `LNR-OVR-WINDOW-001~004`, `LNR-OVR-REFRESH-001` 已实现并写入 README/TROUBLESHOOTING；统一 `[Laner:LIVE] / [Laner:OVERLAY]`。
11. **影响范围**：Current LIVE Query、Compose LIVE 依赖、RiftScreen/Draft HUD Window orchestration、状态/故障文档；不改变赛事 Domain 事实定义、Provider payload contract 或 Feature Baseline 功能状态。
12. **已知问题 / Follow-up**：Android 实机 overlay 行为 `WAITING EXTERNAL TEST`；Draft Layout future migration 为 audit note；Tactical HUD 未开始；最终 merge/main verification 待执行。
13. **回滚**：PR #12 未合并部分可关闭 PR；PR #11 已进入 main 的前半段如需撤销必须用独立 revert 保留历史；PR #12 merge 后以其 merge commit 为整体回滚点，禁止改写历史。
14. **状态同步**：pre-merge authority docs 已同步真实状态；原 incident/原 LNR-020 记录保持历史原貌；merge 后还需最终 CLOSED 落账。
15. **Commit / Push / PR / Release**：PR #12 `DRAFT`；review head `098d6a…`；run `34707759041` PASS；artifact `10302302373`, digest `sha256:7eea41054c3b9525913b3106617ee269b16845f4fd0a042987983f9ec04dbc67`；release `N/A`；merge/main run 待回填。
16. **Post-change Compliance Review**：`PASS` for pre-merge code/doc scope at review head `098d6a…`；本记录 docs-only 更新及 merge/main verification 仍是最终交付门禁。
17. **最终结论**：`DELIVERY INCOMPLETE`。7 项偏离在 pre-merge review 范围已整改通过，但在 PR #12 merge、merge 后 main 状态落账和 main Gate 完成前，不得标记事故 CLOSED，也不得进入 Block 2。
