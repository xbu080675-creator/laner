# LNR-ARC-008 / 资格静态快照迁入 Core

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS

## 目标

继续执行 Laner 的 Core / Adapter 边界重构，将不含 Android、网络、存储、协程或 Provider 副作用的资格静态快照迁入 Core；功能、数据、UI 与既有调用语义保持不变。

## Baseline

- 前置提交：`f1e831d79b5329fc9c64d34071e94dd17eb8dc0b`
- 迁移提交：`abc409b351c46613e5d667a6b5ad750845ef69cd`
- `LplChampionshipPoints.kt` 原位置：`app/src/main/java/com/riftlab/app/data/`
- `Worlds2026QualifiedTeams.kt` 原位置：`app/src/main/java/com/riftlab/app/data/`

## 边界审计

两份文件均为纯 Kotlin 静态领域快照，无 import，无 Android API、网络请求、文件系统、Keystore、OCR、JSON 或具体 Provider 依赖。

`LplChampionshipPoints2026` 记录年度 Championship Points 与资格状态；它与 Riot Tournament Standings 保持原有语义隔离。

`Worlds2026QualifiedTeams` 是带检查日期和来源信息的官方 Worlds 资格快照，不推断种子与未确认资格。

## 迁移

迁入：
- `core/src/main/kotlin/com/riftlab/app/data/LplChampionshipPoints.kt`
- `core/src/main/kotlin/com/riftlab/app/data/Worlds2026QualifiedTeams.kt`

移除 app 原副本，避免同包同名实现双份存在。

`LplChampionshipPoints.kt` 的公开 API 原样保留。`Worlds2026QualifiedTeams.kt` 原顶层 `internal data class` / `internal object` 仅改为模块外可见，以允许现有 App/UI/Store 通过 Core 模块继续使用；队伍列表、来源、检查日期、查找逻辑均不变。

## 行为影响

无功能变化：
- Championship Points 数值不变。
- Worlds 已确认队伍列表不变。
- 来源与检查日期不变。
- 资格状态与查找逻辑不变。
- UI 不变。
- 资格运行时 Store 不迁入 Core。

## 验收

正式 `Compile Diagnostics` run `34742857973` 已完成并 PASS：
- Core boundary PASS。
- Laner repository links PASS。
- `:core:test` PASS。
- `:app:assembleDebug` PASS。
- 最终 `Fail when compile failed` PASS，确认 Gradle 实际退出码为 0。
