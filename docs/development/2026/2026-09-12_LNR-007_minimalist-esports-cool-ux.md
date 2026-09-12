# LNR-007 — 极简 × 酷炫体验北极星

- 日期：2026-09-12
- 状态：DONE
- 类型：Product / UX

## 1. 需求与目标

用户明确提出 Laner 的使用与视觉效果以“极简、酷炫”为准。

本任务将该口头原则固化为可执行 UX 规范，避免后续开发把“电竞风”错误理解为高密度、低可读性、长动画或无意义视觉噪声。

## 2. Constitution Preflight

结果：PASS

开发前读取并核对：

- `docs/ENGINEERING_CONSTITUTION.md`
- `docs/PROJECT_SCOPE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`
- 当前 GitHub `main` 真实状态

本任务只修改产品/UX治理文档，不含运行时代码、依赖、Schema 或脚本变更。

## 3. 决策

体验北极星：

```text
极简 + 酷炫
```

固定优先级：

```text
清晰 / 快速 / 不打扰观赛
>
电竞感 / 动效 / 视觉冲击
```

阶段视觉节奏：

- PRE_MATCH：安静、准备、建立预期；
- LIVE_MATCH：实时、聚焦、临场，允许最高动态强度但必须克制；
- POST_MATCH：沉淀、解释、复盘，主动降低动态强度。

任何正式动效必须能解释其信息价值、持续时长、可中断性和降级策略。

## 4. 文件变更

新增：

- `docs/UX_PRINCIPLES.md`
- `docs/development/2026/2026-09-12_LNR-007_minimalist-esports-cool-ux.md`

修改：

- `docs/PROJECT_SCOPE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`

删除：无

## 5. 测试与验证

正向：
- UX 原则已形成独立权威文档：PASS
- 项目范围、计划、状态、Changelog 已同步：PASS

边界：
- 已明确“酷炫不得压过信息清晰”：PASS
- 已明确赛前/赛中/赛后不同视觉节奏：PASS
- 已明确 Reduced Motion / 性能 / 可读性底线：PASS

运行时测试：N/A —— 无运行时代码。

脚本验证：N/A —— 无脚本变更。

## 6. 风险与影响

- 架构：无运行时影响。
- UI：未来全部 UI/HUD 设计受本原则约束。
- 性能：提前规定动效必须可降级，不得牺牲触控与滚动性能。
- 无障碍：重要状态不能只靠颜色，必须支持减少动画模式。

## 7. 后续

- LNR-001 功能基线提取时，应同时判断旧 UI 哪些交互值得保留、哪些视觉噪声不应迁移。
- LNR-003 架构冻结时，应保证 Presentation 层能够支持阶段化 UI 和信息优先级。
- 未来任何具体页面实现都必须依据 `docs/UX_PRINCIPLES.md` 验收。

## 8. 回滚

如未来调整体验原则，必须新增开发记录并同步本规范；不得删除本记录制造历史缺失。

## 9. Post-change Compliance Review

结果：PASS

- 无业务代码变化；
- 无功能范围变化；
- 无测试结果虚报；
- 状态与 Changelog 已同步；
- 开发记录完整。

## 【任务交付单】

1. 任务：`LNR-007 / 极简 × 酷炫体验北极星 / DONE`
2. Constitution Preflight：PASS
3. 涉及模块：product-scope / UX / planning / status
4. 文件变更：见 §4
5. 测试：文档一致性 PASS；运行时 N/A
6. 脚本验证：N/A
7. 风险：无运行时风险；未来 UI 受强约束
8. 状态同步：PASS
9. Post-change Compliance Review：PASS
10. 最终结论：DONE
