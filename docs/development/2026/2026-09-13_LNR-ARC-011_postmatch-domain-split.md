# LNR-ARC-011 / 赛后与比赛详情领域模型迁入 Core

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS

## 目标

把赛后系列赛、比赛详情/MVP/投票/BP、历史赛事补全结果的纯领域模型与 StateFlow/Mutex/Provider/网络 JSON 实现分离；不改变任何比赛详情、赛后归档或历史数据行为。

## 迁移

- `CompletedSeriesSnapshot` 迁入 Core；`CompletedGameArchive` StateFlow 运行时留 App。
- `MatchDetailKey`、MVP/投票/BP 记录、`MatchDetailState` 迁入 Core；`MatchDetailRepository` 及 Provider/Mutex/协程留 App。
- `TournamentEventHistorySnapshot` 迁入 Core并仅解除顶层 `internal`；Riot Completed Events/LiveStats/JSON Provider 留 App。

## 首次编译修复

第一次 ARC-011 完整构建通过了两道架构门禁，但 `LplOfficialAwardsProvider` 对迁入 Core 的 `OfficialMvpRecord.game` 使用了跨模块 smart cast。修复仅把 `record.game` 读取到局部 `val game` 后再判空/比较；MVP 筛选条件、排序和业务语义不变。

## 行为影响

无功能、UI、数据源、排序、过滤或历史补全逻辑变化。

## 验收

- Core boundary PASS。
- Laner repository links PASS。
- `:core:test` PASS。
- `:app:assembleDebug` PASS。
