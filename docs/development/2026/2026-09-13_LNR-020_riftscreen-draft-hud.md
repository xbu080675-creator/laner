# LNR-020 — RiftScreen / Draft HUD Android Overlay

- Date: `2026-09-13` (Asia/Taipei)
- Branch: `feature/lnr-020-riftscreen-overlay`
- Base: `main@5546440a8cbcc59571842715dcf02036bd1232b6`
- Legacy behavior baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy current-tree recheck: old overlay/Draft HUD code in current `Rlftlab/main` remains behavior-compatible with the dev.94 migration baseline; later old-repo changes are primarily data/workflow updates.
- Status: `WAITING EXTERNAL TEST`

## 1. 任务目标

迁移第一块平台能力：
1. RiftScreen 悬浮副屏收尾；
2. Draft HUD 全屏悬浮；
3. HUD Edit / Lock；
4. 模块拖动 / Scale / Alpha / Visibility / Reset；
5. 横竖屏独立 HUD Profile；
6. Lock 后真实触摸穿透；
7. 保留旧版 HUD 演示能力，但绝不允许模拟数据污染 Laner 事实链。

本任务不包含 Tactical HUD、Watch Hub、播放器、OTA、本地 AI/OCR，也不宣称真实 Draft Provider 已迁移。

## 2. Legacy Evidence

重新读取旧实现：
- `RiftOverlayService.kt`：系统 overlay、Foreground Service、普通副屏 / Draft HUD / Tactical HUD 窗口切换；
- `RiftOverlayView.kt`：MINI / COMPACT / EXPANDED、拖动、关闭；
- `DraftHudOverlayView.kt`：全屏 HUD、模块拖动、Edit / Lock、Scale / Alpha / Visibility / Reset；
- `DraftHudLayoutStore.kt`：归一化坐标、横竖屏独立 Profile；
- `DraftHudSimulation.kt`：本地 BP 演示；
- old locked flags：`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`。

旧版行为作为验收基线，旧 `MatchSessionStore` 架构不迁移。

## 3. 新架构边界

### 3.1 统一事实链

```text
GlobalScheduleService
→ LiveTargetSelector
→ LiveMatchStateService
→ LiveSnapshotService
→ LiveTimelineService
→ display-only Presentation Mapper
→ Android WindowManager
```

约束：
- Core 不依赖 Android；
- Overlay 不读 Provider raw payload；
- `LiveMatchStateService` 仍是 lifecycle 唯一权威；
- `LiveSnapshotService` 仍只处理 gameplay frame；
- UI / Overlay 不自行仲裁来源；
- Region 不形成独立业务分支；
- 缺失事实保持未知。

### 3.2 Composition Root

旧版悬浮层依赖全局 `MatchSessionStore`。Laner 改为 process-level `LanerApplication / LanerAppGraph`，Activity 与 Foreground Service 共享同一组 Application services。

这保证后台 Overlay 能继续刷新，同时不引入新的全局巨型 Store。

## 4. RiftScreen Delivered

- Android overlay permission / Foreground Service 链；
- host App 前台自动隐藏、退后台自动显示；
- MINI / COMPACT / EXPANDED 三档；
- 拖动与屏幕边界 clamp；
- 显式关闭按钮；
- canonical lifecycle / snapshot / timeline presentation；
- 无 gameplay frame 时显示未知，不填 0；
- Service 与 Activity 共享 Application truth。

## 5. Draft HUD Delivered

### 5.1 Verified Draft Presentation

新增 `DraftHudPresentationMapper`：
- 仅 `MatchLifecycleState.DRAFT` 激活；
- 只读取 canonical `DraftChangedEvent`；
- PICK / LOCK / BAN / UNDO 按事件序列投影；
- unknown teamId 不写入左右侧；
- 没有 SideSelection 事实时只称“左 / 右侧”，不把赛程顺序伪装成蓝 / 红方；
- 没有 role / matchup 事实时不推断对位；
- Provider source label 只来自 canonical provenance。

### 5.2 Local Preview Isolation

新增 `DraftHudPreviewSession`：
- Android-only；
- 用于没有真实比赛时调试 HUD 布局与交互；
- 明确 `LOCAL PREVIEW · NOT FACT`；
- 不进入 Core；
- 不进入 Source Arbitration；
- 不写 Live State / Timeline Repository；
- 不触发 Tactical HUD 自动交接。

优先级：真实 verified Draft 存在时永远覆盖本地 Preview。

### 5.3 Layout / Edit / Lock

新增/迁移：
- STATUS / PROGRESS / LEFT_PICK / RIGHT_PICK / MATCHUP / WATERMARK 模块；
- 模块拖动；
- Scale `0.55..1.45`；
- Alpha `0.30..1.00`；
- Visibility toggle；
- Reset；
- 横屏 / 竖屏独立 SharedPreferences Profile；
- Edit 模式全屏 HUD 可触摸；
- Lock 模式全屏 HUD Window 加 `FLAG_NOT_TOUCHABLE`；
- 边缘控制 Dock 独立 Window，Lock 后仍可操作以重新进入 Edit。

## 6. 主要文件

新增：
- `app/src/main/java/com/laner/app/overlay/DraftHudPresentation.kt`
- `app/src/main/java/com/laner/app/overlay/DraftHudLayoutStore.kt`
- `app/src/main/java/com/laner/app/overlay/DraftHudPreviewSession.kt`
- `app/src/main/java/com/laner/app/overlay/DraftHudOverlayView.kt`
- `app/src/test/java/com/laner/app/overlay/DraftHudPresentationMapperTest.kt`

修改：
- `RiftScreenOverlayService.kt`
- `RiftScreenOverlayView.kt`
- `RiftScreenController.kt`
- `LanerApplication / MainActivity / LanerRoot / LiveMatchScreen`（LNR-020 foundation 中的 process graph / control wiring）
- `AndroidManifest.xml`
- `app/README.md`
- migration/status/changelog docs。

## 7. 自动测试

新增 Draft HUD mapper regressions：
- 非 DRAFT lifecycle 不激活 HUD；
- verified PICK / LOCK / BAN 正确投影；
- 不制造 role / matchup；
- UNDO 仅撤销明确 champion；
- unknown team 不污染任一侧。

现有 Gate 继续执行：
1. Architecture boundary；
2. Domain + Application tests；
3. Android Adapter unit tests；
4. Android debug compile；
5. debug APK artifact upload。

## 8. 失败历史（必须保留）

### Failure A — foundation regression
- initial foundation run `34704014273`：Architecture/Core PASS；App unit compile FAIL；
- 原因：旧 `LiveMatchScreenTest` 仍调用已删除的 UI-private `selectLiveTarget`；
- 结论：测试仍绑旧 Presentation 边界，而生产逻辑已经迁到 Application `LiveTargetSelector`；
- fix: `8b17f94ed75ba417eed017bd4c4d45ba63b5e9a9`；
- push run `34704124799` / PR run `34704127811` 随后全部 PASS。

### Failure B — Draft HUD test assertion
- PR run `34705468018`：Architecture/Core PASS，production `compileDebugKotlin` PASS，App tests 35 个中 1 个失败；
- 原因：新测试错误断言最后一个正常 LEFT UNDO 事件文本不应出现 LEFT；测试意图实际是验证 unknown team 的 Garen 不应污染左右侧；
- 生产代码不是失败面；
- fix: `871e1a0ad257c465dc51720df46fb66cceeae7cc`，改为检查 `Garen` 不存在于任一侧 picks。

## 9. 当前自动证据

- code head: `871e1a0ad257c465dc51720df46fb66cceeae7cc`；
- PR run `34705512479`：Architecture / Core / App Unit / Android build / APK upload 全 PASS；
- artifact: `10301324396`；
- artifact digest: `sha256:ac2953bc1a29ebaead91958a689909e4bae6c3374a795d5ab3956398ec81d424`。

后续文档 closeout 会再触发 exact-head / PR Gate；只有最终头同样绿灯后才合并。

## 10. 真机验收清单

以下不能用 JVM/CI 冒充，因此保持 `WAITING EXTERNAL TEST`：
- 系统悬浮窗权限申请 / 返回；
- Foreground Service 常驻与通知动作；
- Laner 前台隐藏 / 后台显示；
- RiftScreen 关闭；
- RiftScreen 拖动与屏幕边界；
- MINI / COMPACT / EXPANDED；
- HUD Preview 展示；
- HUD Edit / Lock；
- 每个模块拖动；
- Scale / Alpha / Visibility；
- Reset；
- 横屏 Profile 与竖屏 Profile 互不覆盖；
- Lock 后游戏区域真实触摸穿透；
- Edge Dock 在 Lock 后仍可操作；
- 有真实 Draft source 时 Verified Draft 优先 Preview；
- 无结构化 Draft event 时只展示“等待可信事件”，不造 Pick/Ban。

## 11. Feature Baseline 结论

本任务完成后：
- `LIVE-024~029`：实现完成、自动 Gate PASS，状态进入 `WAITING EXTERNAL TEST`；
- `LIVE-012 BP / Draft 实时状态`：继续 `TODO`，因为本任务只完成 Presentation，不代表真实 Draft Provider 已完成；
- `LIVE-030 Tactical HUD`：继续 `TODO`，属于第 2 块。

## 12. Definition of Done for LNR-020 Engineering Slice

工程切片允许收口的条件：
- 新架构边界不回退；
- legacy RiftScreen / Draft HUD 用户行为已映射；
- preview 与事实链物理隔离；
- lock touch-through 是 Window flag 而非文案模拟；
- 自动 Gate / APK PASS；
- 文档和失败历史留档；
- PR 合并主线；
- 真机项诚实保持 `WAITING EXTERNAL TEST`。
