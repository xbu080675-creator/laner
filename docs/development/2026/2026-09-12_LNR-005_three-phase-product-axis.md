# LNR-005 — 三阶段一级架构轴确立

- 日期：2026-09-12
- 状态：DONE
- 类型：Architecture / Product Structure

## 1. 需求与目标

用户明确要求：无论哪个页面，全部以赛前、赛中、赛后为基准。

本任务将该要求从 UI 概念提升为 Laner 的一级产品与架构分类轴，避免后续页面和模块继续按功能类别横向生长。

## 2. Constitution Preflight

结果：PASS

开发前读取并核对：

- `docs/ENGINEERING_CONSTITUTION.md`
- `docs/ARCHITECTURE.md`
- `docs/PROJECT_SCOPE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- 当前 GitHub `main` 真实状态

本任务只修改架构与治理文档，无业务代码、Schema、运行时依赖或 UI 实现变更。

## 3. Baseline

- Repository：`xbu080675-creator/laner`
- Branch：`main`
- 任务开始时业务实现：NOT STARTED
- 前置状态：LNR-000 DONE；LNR-001~004 未开始

## 4. 决策

一级业务阶段固定为：

- `PRE_MATCH` / 赛前
- `LIVE_MATCH` / 赛中
- `POST_MATCH` / 赛后

规则：

1. 所有用户可见业务页面必须先归属一个阶段。
2. 主路由、赛事查询、HUD 与赛事信息展示必须遵守同一分类轴。
3. 赛程、首发、积分、Rank、沙盘、复盘等属于二级功能，不得取代三阶段成为一级业务结构。
4. 设置、搜索、数据源、缓存、AI/OCR、日志诊断等属于共享能力，不是第四业务阶段。
5. 比赛阶段由统一 Match State 映射产生，页面不得自行判断。
6. “赛事直播已开始”不等于“游戏已开始”；选手入场、评论席、BP、载入、局内、局间等作为赛中内部细状态处理。

## 5. 文件变更

新增：

- `docs/decisions/ADR-001-three-phase-product-axis.md`
- `docs/development/2026/2026-09-12_LNR-005_three-phase-product-axis.md`

修改：

- `docs/ARCHITECTURE.md`
- `docs/PROJECT_SCOPE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`

删除：无

## 6. 影响

### 功能
无功能删除或新增。

### 架构
新增强制一级分类轴；后续 LNR-001 功能基线和 LNR-003 架构冻结必须验证三阶段归属。

### UI
尚未修改 UI，但未来业务一级导航必须受本规则约束。

### 数据 / Schema / 配置 / 依赖
N/A —— 本次无运行时实现。

## 7. 测试与验证

正向：
- 文档结构检查：PASS
- 三阶段定义在 Architecture / Project Scope / Plan / Status 中一致：PASS

负向：
- N/A —— 无运行时代码。

边界：
- 跨阶段共享能力已明确不得成为第四业务阶段：PASS
- 赛中“直播开始但游戏未开始”已明确保留细状态：PASS

回归：
- 功能冻结原则未被修改：PASS
- 业务实现仍为 NOT STARTED：PASS

脚本验证：
- N/A —— 无脚本变更。

## 8. 风险与影响

- 架构：正向约束增强。
- 兼容：无运行时影响。
- 数据：无。
- 性能：无。
- 安全：无。
- UI：未来一级业务导航受强约束。

## 9. 后续任务

- LNR-001：为旧工程所有用户可见功能增加 Phase 字段。
- LNR-003：冻结细粒度 Match State → PRE_MATCH / LIVE_MATCH / POST_MATCH 的映射。
- LNR-004：后续代码骨架应在 Presentation/Application 边界体现阶段概念，但不得复制三套业务逻辑。

## 10. 回滚

如该决策被正式撤销，必须新增 ADR supersede 本 ADR，并同步 Architecture / Project Scope / Development Plan / Implementation Status。不得直接删除本记录。

## 11. 状态同步

- DEVELOPMENT_PLAN：LNR-005 = DONE
- IMPLEMENTATION_STATUS：LNR-005 = DONE
- CHANGELOG：已同步
- ADR：已创建

## 12. Post-change Compliance Review

结果：PASS

确认：

- 无业务范围蔓延；
- 无代码或依赖变更；
- 未改变“功能不改，底层重做”原则；
- 规则已写入权威仓库文档；
- 开发记录完整；
- 未虚报运行时测试。

## 【任务交付单】

1. 任务：`LNR-005 / 三阶段一级架构轴确立 / DONE`
2. Constitution Preflight：PASS
3. 涉及模块：architecture / project-scope / planning / status / ADR
4. 文件变更：见 §5
5. 测试覆盖：文档一致性 PASS；运行时测试 N/A
6. 脚本验证：N/A
7. 风险：无运行时风险；未来页面结构受强约束
8. 状态同步：PASS
9. Post-change Compliance Review：PASS
10. 最终结论：DONE
