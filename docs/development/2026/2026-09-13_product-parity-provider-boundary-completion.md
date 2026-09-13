# Product parity / Provider boundary completion

- 日期：2026-09-13
- 执行者：OpenAI ChatGPT
- 状态：IMPLEMENTED / CI PENDING / DEVICE TEST PENDING
- 基线：`main@2b9cf6ba39d3306ec89557451207aa30d29a7cc9`
- 前序：PR #21 已合入产品壳与 credential UI 的第一阶段纠偏；旧 PR #22 因并发 main 前进已关闭，不作为最终证据。

## 目标

补齐 PR #21 未覆盖的 Block 1/2 迁移偏差，继续遵守用户明确边界：

- 功能、UI、交互保持 legacy RiftLab 产品契约；
- 只重构底层；
- 全球赛事使用一套 Core/Application 流程；
- Region 仅为数据维度；
- Riot 只是可插拔 Provider，不是产品运行前提。

## 本次补齐

1. `LanerAppGraph` 按 capability 注册 Provider：无 Riot credential 时不注册 Riot PRE/LIVE/POST Provider，Application 以明确降级/不可用语义运行，不崩溃、不伪造数据。
2. 已有 `LplHistoricalPostMatchSource` 作为区域 Adapter supplement 接入统一 `PostMatchService`；区域判断仍留在 Adapter，不进入 Core。
3. `RiftScreenOverlayView` 恢复 MINI / COMPACT / EXPANDED 的 legacy 信息密度、228/308/348dp 宽度与 accent 动画；仍只消费 `RiftScreenPresentation`。
4. `TacticalHudOverlayView` 恢复 legacy 的 92%×52% 稀疏边缘 X-ray 布局；仍只消费 canonical `TacticalHudPresentation`。
5. `LanerTheme` 恢复 legacy RiftLab palette 与紧凑 typography。
6. `LanerRoot` 补齐 `LEAGUE ESPORTS COMPANION`、版本号与旧式 header/tab 视觉契约。

## 明确不做

- 不恢复旧全局 Store；
- 不允许 UI 直连 Provider；
- 不在 Core 按 LPL/LCK/LEC/LCP 分支；
- 不宣称 Android 实机视觉验收已完成。

## 验证门禁

- Architecture boundary gate：PENDING
- Core Domain/Application tests：PENDING
- Android adapter unit tests：PENDING
- Android debug compile：PENDING
- Android APK artifact：PENDING
- Android device / overlay visual validation：WAITING EXTERNAL TEST

CI 失败必须记录并修复后重新跑 exact-head，不得把旧 run 作为新提交证据。

## Block 3 门禁

本补齐 PR exact-head CI 全绿后，才从最新 `main` 创建 Block 3 fresh branch。Block 3 迁移 Watch Hub + player，并继续保持直播入口与赛事数据完全解耦。
