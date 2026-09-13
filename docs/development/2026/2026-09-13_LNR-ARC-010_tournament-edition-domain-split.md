# LNR-ARC-010 / Tournament Edition 档案与赛事研究领域迁入 Core

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS

## 目标

把年度赛事档案的纯领域模型与 Android/文件/JSON/协程运行时 Store 分离，并把只依赖 JDK 与领域模型的赛事研究推导器迁入 Core；不改变归档、研究、补全、持久化、UI 或 Provider 行为。

## 迁移

- 新增 `core/src/main/kotlin/com/riftlab/app/data/TournamentEditionModels.kt`，承载 Tournament Edition/Archive 纯模型。
- `TournamentEditionArchiveStore` 及其 `Context`、`File`、`org.json`、协程、历史 Standings/Event 补全继续留在 App/Adapter 侧。
- `TournamentResearch.kt` 整文件迁入 Core；它仅依赖 `java.time` 与领域模型，研究版本/赛制/覆盖率推导逻辑原样保留。
- `TournamentResearchSchema.kt` 原样迁入 Core。

## 编译依赖闭包

首次 ARC-010 验证的两道架构门禁均 PASS，但 `:core:compileKotlin` 指出 `TournamentEditionDetail` 依赖仍在 App 的 `TournamentResearchSnapshot`。审计确认 `TournamentResearch.kt` 无平台或 IO 依赖，因此将其整体纳入 Core 依赖闭包，而不是复制类型或放宽边界。

## 行为影响

无功能变化。Core 描述 Tournament Edition/Archive 与赛事研究纯规则；App 继续负责持久化、联网补全和运行时编排。

## 验收

- `tools/check_core_boundary.py` PASS。
- `tools/check_repository_links.py` PASS。
- `:core:test` PASS。
- `:app:assembleDebug` PASS。
