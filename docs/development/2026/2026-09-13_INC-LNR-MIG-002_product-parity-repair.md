# INC-LNR-MIG-002 — Block 1/2 产品一致性与数据源边界纠偏

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：IN PROGRESS — WAITING CI / EXTERNAL DEVICE TEST

## 1. 需求来源与目标

用户明确要求：旧 RiftLab 的功能、UI 与用户操作体验 1:1 迁移到 Laner；允许重构的是底层架构。新架构必须统一管理全球各赛区，Region 只能作为数据维度，不能成为业务模块边界。Riot 只能是可插拔 Provider，不能成为产品级必选依赖。

本次发现 LNR-020 / LNR-021 冻结后仍存在两类范围偏离：

1. 产品主界面新增 Riot Credential Panel，并把运行时 Riot Credential 变成 Activity/Application 产品状态；
2. RiftScreen / Tactical HUD 和主壳视觉相对 legacy baseline 被重新设计，违反“产品层 1:1、底层重构”的迁移边界；
3. Android composition root 在多个能力面只注册 Riot Provider，使一个外部 Provider 实际成为默认唯一来源。

根据工程宪法 §0.3、§3.6、§4、§10，本次按 ARCHITECTURE BREAK + 未批准 UI 行为变化打破 Block 1/2 工程冻结并进行纠偏。

## 2. Baseline

- 目标仓库：`xbu080675-creator/laner`
- 基线分支：`main`
- 基线 Commit：`739430d7d8a234bf4ab00ac4004c3745772e26d7`
- Legacy 产品基线：`xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- 修复分支：`fix/block1-2-parity-global-sources`

## 3. 范围

### 本次做

- 删除产品级 Riot Credential Panel；
- 删除 Activity/Application 中运行时 Riot Credential 产品状态；
- Riot Provider 仅在部署配置可用时注册；无 Riot key 时允许应用进入明确降级路径；
- 接回已存在的 LPL historical POST Adapter 作为区域 supplement，不在 Core 写 LPL 分支；
- 恢复 legacy RiftLab 的主壳视觉语言、默认进入赛中页的交互；
- 恢复 RiftScreen 的 MINI / COMPACT / EXPANDED 信息密度和尺寸行为；
- 恢复 Tactical HUD 的边缘稀疏 X-ray 布局，继续只消费新的 canonical Presentation。

### 明确不做

- 不回退新的 Core / Application / Port / Adapter 分层；
- 不把旧 `MatchSessionStore`、旧全局 Store 或 Provider payload 搬回 UI；
- 不新增按 LPL/LCK/LEC/LCP 分支的业务流程；
- 不在本修复中开始 Block 3 Watch Hub；Block 3 必须在本修复 Gate 完成后单独 Preflight。

## 4. 设计决策

### 4.1 产品层保留，底层替换

UI 与 Overlay 继续使用新 Application service / Presentation model，但视觉结构和操作语义回到 legacy baseline。这样避免为了 1:1 复刻重新引入旧 Store 与跨层依赖。

### 4.2 Provider 不是产品依赖

`LanerAppGraph` 负责按 capability 注册 Provider：

- Riot key 可用：注册 Riot global Provider；
- Riot key 不可用：不注册 Riot Provider，Application 进入可解释的 DEGRADED / UNAVAILABLE；
- LPL TJStats auth 可用：仅作为 POST regional supplement 注册；
- Core/Application 不出现赛区业务分支。

## 5. 文件变化

- `app/src/main/java/com/laner/app/MainActivity.kt` — 移除 Riot credential UI 状态与 graph rebuild UI 逻辑。
- `app/src/main/java/com/laner/app/LanerApplication.kt` — 移除 runtime Riot credential product state。
- `app/src/main/java/com/laner/app/LanerAppGraph.kt` — Provider capability 条件注册；LPL POST supplement 接回统一 PostMatchService。
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt` — 恢复 legacy 主壳视觉与默认 LIVE 行为；删除 Riot panel 参数。
- `app/src/main/java/com/laner/app/ui/LanerTheme.kt` — 恢复 legacy 密度、字体和主色系。
- `app/src/main/java/com/laner/app/ui/RiotCredentialPanel.kt` — 删除。
- `app/src/main/java/com/laner/app/overlay/RiftScreenOverlayView.kt` — 恢复 legacy 三档信息布局。
- `app/src/main/java/com/laner/app/overlay/TacticalHudOverlayView.kt` — 恢复 legacy 稀疏边缘布局。

## 6. 测试与验证

当前记录创建时：

- 本地 Gradle / Android 编译：`NOT EXECUTED` — 当前执行环境未挂载 Android SDK / 仓库工作树；不得虚报 PASS。
- GitHub CI：`PENDING` — 将通过 PR 触发并记录真实结果。
- Android 实机 overlay：`WAITING EXTERNAL TEST`。
- 视觉 1:1：已按 legacy baseline 源码逐项对照主壳、RiftScreen、Tactical HUD；最终仍需设备截图验收。

## 7. 风险与兼容性

- 没有任何可用 PRE/LIVE Provider 时，对应能力应明确 UNAVAILABLE，而不是伪造数据；这是预期降级，不是崩溃路径。
- legacy RiftScreen 的队标资源字段尚未存在于新 Core `TeamRef`，本修复不把图片 URL 塞回 Domain；队标资产恢复应通过后续 display asset port 完成，避免污染 Core。
- Block 1/2 的外部 Android 验收状态仍不能写 PASS。

## 8. 回滚

- 基线回滚点：`739430d7d8a234bf4ab00ac4004c3745772e26d7`
- 本修复独立分支，可整体关闭 PR 回滚，不重写此前 LNR-020 / LNR-021 历史记录。

## 9. 后续门禁

1. PR CI 必须通过；
2. Post-change Compliance Review 必须确认无 Region 业务分支、无 raw Provider payload 进入 UI；
3. Gate 通过后才开始 Block 3：Watch Hub + 播放器；
4. Block 3 完成后再次独立宪法复查，再进入 Block 4。
