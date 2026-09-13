# Product parity / Provider boundary remediation

- 日期：2026-09-13
- 执行者：OpenAI ChatGPT
- 状态：IMPLEMENTED / CI PENDING / DEVICE TEST PENDING

## 需求来源与目标

用户重申迁移契约：RiftLab 功能、UI、操作习惯必须 1:1 迁移到 Laner；允许重构的是底层。底层必须从按赛区复制业务流程改为全球统一管理，Region 只能作为数据维度。用户同时指出两个回归：普通产品 UI 暴露 Riot API Key，并且 Laner 主壳偏离旧 RiftLab UI。

## Baseline

- 目标仓库：`xbu080675-creator/laner`
- 基线分支：`main`
- 基线 Commit：`739430d7d8a234bf4ab00ac4004c3745772e26d7`
- 产品对照仓库：`xbu080675-creator/Rlftlab`
- 固定产品基线 Commit：`6eeeda0051177f56bb3bf4d4b5492b12f3271bbc`

## Constitution Preflight

已读取最新 `docs/ENGINEERING_CONSTITUTION.md`、`docs/DEVELOPMENT_PLAN.md`、涉及的 Android composition root、主 UI、RiftScreen presentation 与旧产品基线。适用条款：

- 3.6 外部能力必须可替换、可降级；
- 4 未经授权不得改变 UI/操作习惯；
- 6 密钥不得进入普通产品路径或日志；
- 10 UI 不得成为业务真相来源；
- Laner 附录：Region 只是数据维度，不是业务模块边界。

## 本次范围

1. 从普通产品 UI 移除 Riot Credential 面板。
2. 删除 Activity/Application 的运行时 Riot Key 产品状态；Riot Key 仅保留为部署/构建配置，Provider 无凭证时必须按现有 SourceResult 语义降级。
3. 主壳恢复旧 RiftLab 的布局语言：默认进入赛中、Header 尺寸/间距、切角导航、Tab 指示条、180/120ms 页面切换节奏。
4. RiftScreen 文本展示语义恢复：中间值只表示领先差，不再把队伍代码拼进领先值；指标和 GOLD/LEAD 文案回归旧版结构。

## 明确不做

- 不删除 Riot Adapter；旧 RiftLab 本身存在 LolEsports/Riot 数据源，因此 Riot 仍可作为 Provider。
- 不把任何赛区重新做成独立业务模块。
- 不在本整改中开始 Block 3。
- Android 真机悬浮窗、触摸穿透和旋转证据仍需外部实机测试。

## 修改文件

- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/LanerApplication.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- 删除 `app/src/main/java/com/laner/app/ui/RiotCredentialPanel.kt`
- `app/src/main/java/com/laner/app/overlay/RiftScreenPresentation.kt`
- 本记录

## 架构决策

Riot/Cito/LPL 官方/微博/OCR/其他来源都属于 Adapter/Provider。Application/Core 只消费统一 Port 与 Domain Model。普通用户界面不得把任何单一 Provider 提升成产品运行前提。Provider 缺失凭据或不可用时应返回可诊断的 DEGRADED/UNAVAILABLE 结果，由应用层做来源选择或降级。

## 测试与验证

计划由 PR 触发 `.github/workflows/android-build.yml`：

- Core architecture boundary gate
- `:core:domain:test`
- `:core:application:test`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`

当前记录创建时：CI `NOT EXECUTED`；Android 真机 `NOT EXECUTED`。不得把二者写成 PASS。

## 风险与回滚

主要风险是 Compose 参数签名移除后存在遗漏调用点，或旧视觉骨架与当前页面内边距产生布局差异。CI 编译用于发现前者；后者需要截图/实机验收。回滚点为基线 Commit `739430d7d8a234bf4ab00ac4004c3745772e26d7`。

## 后续门禁

本整改 CI 通过并完成 Post-change Compliance Review 后，才允许从 `main` 新开 Block 3（Watch Hub + 播放器）的独立开发分支。Block 3 之后仍按“开发 → CI → 宪法复查 → 留档 → 合并 → 下一块”顺序推进。
