# LNR-020 — Constitution Remediation Closeout / 合规整改闭环

- Date: `2026-09-13` (Asia/Taipei)
- Incident: `INC-LNR-020-001`
- Original functional task: `LNR-020 — RiftScreen / Draft HUD Android Overlay`
- Original functional merge: PR #10 → `967e112d6efcf8e6daa86f0b007cedc39b63b04c`
- Remediation merge: PR #12 → `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`
- Closeout baseline: `main@452ab8f5df3f4536c5c7f39c4024dc51ebc38084`
- Final engineering verdict: `COMPLIANCE PASS / INCIDENT CLOSED`
- Functional device verdict: `WAITING EXTERNAL TEST`

> 本文件只追加最终闭环事实，不修改原 LNR-020 开发记录、违宪事故记录或合规整改记录中的历史状态。原 Preflight 缺失、Failure A/B、事故冻结、整改进行中等记录继续保留为当时真实快照。

## 1. 闭环依据

本次关闭 `INC-LNR-020-001` 的必要条件已经全部满足：

1. 独立整改任务重新执行真实 Constitution Preflight；
2. 7 项确认偏离均有实现或工程治理层整改；
3. PR #12 最终 exact-head 自动 Gate 全 PASS；
4. PR #12 已合并到 `main`；
5. merge 后 `main` 自动 Gate 再次全 PASS；
6. 权威状态文档在本 closeout 分支中同步最终事实；
7. Android 真机事项继续保持 `WAITING EXTERNAL TEST`，没有用 CI 冒充实机验收；
8. `LIVE-012 / LIVE-014 / LIVE-015 / LIVE-030` 等非目标功能状态没有被整改过程偷偷升级。

## 2. 7 项偏离最终结论

| Incident item | Final result | Closure evidence |
|---|---|---|
| `INC-020-01` 原 LNR-020 Preflight 缺失 | CLOSED BY NEW REMEDIATION TASK | 原历史不反写；整改任务在写代码前重新执行完整 Preflight |
| `INC-020-02` 原记录缺固定 17 项交付单 / Compliance Review | CLOSED | 独立整改记录 + 本 closeout 最终 17 项交付单 |
| `INC-020-03` 权威状态文档漂移 | CLOSED | IMPLEMENTATION_STATUS / DEVELOPMENT_PLAN / root README / module README / ARCHITECTURE / PROJECT_SCOPE 已同步真实阶段与事实 |
| `INC-020-04` Presentation 重复 Application 编排 | CLOSED | 唯一 `LiveMatchContextService`；Compose LIVE 与 Overlay 均消费 Application Query |
| `INC-020-05` WindowManager 静默异常 | CLOSED | `OverlayWindowHost` + `[Laner:OVERLAY]` + `LNR-OVR-WINDOW-001~004` + `LNR-OVR-REFRESH-001` |
| `INC-020-06` LNR-020 回归失败未进入 Troubleshooting | CLOSED | Failure A/B 与新诊断路径均已进入 `docs/TROUBLESHOOTING.md` |
| `INC-020-07` `RiftScreenOverlayService` 职责过重 | CLOSED | 拆出 `RiftScreenWindowController / DraftHudWindowController / OverlayWindowHost` |

`DraftHudLayoutStore` 的 future migration/recovery 仍只保留为 audit note；本整改没有修改其 `laner_draft_hud_layout_v1` schema，因此不虚构本次已解决未发生的迁移问题。

## 3. 最终架构事实

Current LIVE Presentation 统一经过：

```text
GlobalScheduleService
→ LiveTargetSelector
→ LiveMatchStateService + LiveSnapshotService
→ canonical GameId selection
→ LiveTimelineService
→ LiveMatchContextService / LiveMatchContextResult
→ Presentation Mapper
→ RiftScreenWindowController / DraftHudWindowController
→ OverlayWindowHost
→ Android WindowManager
```

约束保持：

- `LiveMatchStateService` 仍是 lifecycle 唯一权威；
- `LiveSnapshotService` 不推进 lifecycle；
- UI/Overlay 不再复制 `Schedule → Target → State → Snapshot → Timeline` 调用顺序；
- Provider raw payload 不进入 UI；
- Verified Draft 优先 Preview；Preview 永远 `LOCAL PREVIEW · NOT FACT`；
- WindowManager 平台异常不再静默；
- Service teardown 有 late-callback gate，避免销毁后重新建窗口；
- Region 仍只是 metadata/filter，不成为业务模块边界。

## 4. 自动验证证据

### 4.1 PR #12 最终 exact-head

- Head: `249c42208ab6105ad26b78215b47fbd754d889e9`
- Run: `34708127194`
- Result: `success`
- Architecture boundary gate: PASS
- Domain and application tests: PASS
- Android adapter unit tests: PASS
- Android debug compile: PASS
- Upload debug APK: PASS
- Artifact: `10302087059`
- Artifact name: `laner-debug-050f09553bda50d2f9fe02e2a265f2d2ad7a14bd`
- Digest: `sha256:59eca5214f84e4a231a613b90663fd4d30f613629f3128b4f846716ab56a4509`

### 4.2 PR #12 merge 后 main

- Merge: `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`
- Main run: `34708285172`
- Job: `103592233615`
- Result: `success`
- Architecture boundary gate: PASS
- Domain and application tests: PASS
- Android adapter unit tests: PASS
- Android debug compile: PASS
- Upload debug APK: PASS
- Artifact: `10301779315`
- Artifact name: `laner-debug-452ab8f5df3f4536c5c7f39c4024dc51ebc38084`
- Digest: `sha256:69d61f7bea45a209f56f171e0e9c4f48f24953fdd802b03ade4013851b5a34c4`

## 5. 永久回归

新增并通过：

- `LiveMatchContextServiceTest.readyContextOwnsTargetStateSnapshotAndTimelineOrdering`
- `LiveMatchContextServiceTest.emptyScheduleReturnsTypedNoTargetWithoutCallingLiveSources`
- `LiveMatchContextServiceTest.completedOnlyScheduleReturnsNoEligibleTarget`
- `LiveMatchContextServiceTest.unexpectedApplicationFailureIsDiagnosedWithStableCode`
- `OverlayWindowOperationTest.everyWindowOperationHasUniqueStableErrorCode`
- `OverlayWindowOperationTest.recoveryClassificationDoesNotPretendRemovalCanBeRetriedSafely`

既有 LIVE / Draft HUD regression 随 Core/App 全量 Gate 一并通过。

## 6. 非目标与外部验收边界

本 closeout **不**代表以下事项已经完成：

- `LIVE-012` real Draft Provider：仍 `TODO`；
- `LIVE-014` Kill/MultiKill/TeamFightWindow：仍 `TODO`；
- `LIVE-015` GoldLeadChange：仍 `TODO`；
- `LIVE-030` Tactical HUD：仍 `TODO`；
- Android overlay permission / foreground-background / drag-bounds / orientation profiles / Lock touch-through：仍 `WAITING EXTERNAL TEST`；
- Riot real-event online evidence：按既有状态继续外部补证。

因此关闭的是 **工程合规事故**，不是把所有 LNR-020 真机功能状态改成 `DONE`。

## 7. Post-change Compliance Review

最终复审结论：`PASS`。

复审确认：

- 没有反写原任务 Preflight 为 PASS；
- 没有删除或美化历史失败；
- 没有用 CI PASS 冒充 Android device PASS；
- 没有跨层重新引入 Presentation orchestration；
- 没有无日志 WindowManager exception swallowing；
- 没有把 Tactical HUD / Draft Provider 顺手塞进整改；
- 没有升级非目标 Feature Baseline；
- 当前 authority docs 与 `main@452ab8f5…` 事实一致。

## 8. §15 固定任务交付单

1. **任务编号 / 标题 / 最终状态**：`LNR-020 Constitution Remediation` / `DONE (COMPLIANCE) / FUNCTIONAL DEVICE TESTS STILL WAITING`。
2. **Baseline branch / start commit / end commit**：整改初始 main `967e112d6efcf8e6daa86f0b007cedc39b63b04c`；事故记录 `1b8c6b183ece4b64a4f9ecd8a8c097034d5e37bc`；整改 merge `452ab8f5df3f4536c5c7f39c4024dc51ebc38084`。
3. **Constitution Preflight**：`PASS`，由独立整改记录保存完整读取项；原 LNR-020 历史 Preflight 仍为缺失事实。
4. **涉及模块**：`:core:application`, `:app`, docs authority/status/troubleshooting/development records。
5. **强相关条款**：§0.3, §0.4, §1, §1.1, §1.2, §2.2, §3.2, §3.3, §3.4, §8, §9, §11, §13.2, §13.3, §14, §15, L-3, L-5, L-6, L-8。
6. **精确文件增删改**：以 PR #12 changed-files 与本 closeout PR changed-files 为准；核心新增 `LiveMatchContextService.kt`, `LiveMatchContextServiceTest.kt`, `OverlayWindowHost.kt`, `RiftScreenWindowController.kt`, `DraftHudWindowController.kt`, `OverlayWindowOperationTest.kt`；核心修改 `LanerAppGraph.kt`, `MainActivity.kt`, `LanerRoot.kt`, `LiveMatchScreen.kt`, `RiftScreenOverlayService.kt` 及相关 README/status/troubleshooting/docs。
7. **实现与设计原因**：关闭 `INC-LNR-020-001` 7 项确认偏离，不扩产品功能，不改变 Domain 事实语义。
8. **测试**：正向 PASS；负向 PASS；边界 PASS；非目标 PASS；历史回归 PASS；Integration/Device `WAITING EXTERNAL TEST`。
9. **脚本验证**：Architecture Gate + Gradle Core/App/Android workflow PASS；无本任务专用迁移脚本，其他脚本 `N/A`。
10. **日志 / 故障定位**：`LNR-APP-LIVE-003`, `LNR-OVR-WINDOW-001~004`, `LNR-OVR-REFRESH-001` 已落地并有测试/文档入口。
11. **影响范围**：Current LIVE Query、Compose LIVE、RiftScreen/Draft HUD Android window orchestration、工程文档与诊断；不改变赛事 Domain 事实定义。
12. **已知问题 / Follow-up**：Android 实机 overlay 行为继续 `WAITING EXTERNAL TEST`；Draft Layout future schema migration 仍为 audit note；Tactical HUD/Draft Provider 未开始。
13. **回滚**：整改 merge `452ab8f5…` 可通过独立 revert 回滚；禁止改写历史提交。功能原 merge `967e112d…` 与整改历史保留。
14. **状态同步**：本 closeout 分支同步 `IMPLEMENTATION_STATUS / DEVELOPMENT_PLAN / README / app README`；事故原文保持历史快照，不反向修改。
15. **Commit / Push / PR / Release**：整改 PR #12 已 merge；main `452ab8f5…`；PR exact-head run `34708127194` PASS；post-merge main run `34708285172` PASS；Release `N/A`。
16. **Post-change Compliance Review**：`PASS`，见第 7 节。
17. **最终结论**：`DONE`。`INC-LNR-020-001` 工程合规事故关闭；Block 2 可以在新的 Constitution Preflight 后开始。Android 真机 LNR-020 验收继续独立回填。
