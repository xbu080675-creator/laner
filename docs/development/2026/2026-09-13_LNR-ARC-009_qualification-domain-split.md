# LNR-ARC-009 / 资格领域模型与运行时 Store 物理拆分

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS

## 目标

把 `QualificationCenter.kt` 中的纯资格领域模型从协程运行时 Store 中剥离到 Core；不改变资格规则、积分、队伍状态、UI 或数据源。

## 迁移

- 新增 `core/src/main/kotlin/com/riftlab/app/data/QualificationModels.kt`，承载 `QualificationTeamState`、`QualificationEvidence`、`QualificationRuleRecord`、`TeamQualificationRoute`、`QualificationTournamentSnapshot`、`QualificationCenterState` 等纯模型。
- `QualificationCenterStore` 以及 `CoroutineScope` / `StateFlow` / Store 编排继续留在 App 侧。
- `OfficialLcpChampionshipPoints2026.kt` 迁入 Core；只把顶层 `internal object` 改为跨模块可见的 `object`，规则正文与来源不变。

## 行为影响

无功能变化。此次仅建立 Gradle 物理边界：Core 描述“资格是什么”，App/Adapter 负责“资格中心如何运行与刷新”。

## 验收

- `tools/check_core_boundary.py` PASS。
- `tools/check_repository_links.py` PASS。
- `:core:test` PASS。
- `:app:assembleDebug` PASS。
