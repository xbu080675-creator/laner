# LNR-006 — 用户角色 × 比赛阶段产品矩阵

- 日期：2026-09-12
- 状态：DONE
- 类型：Product Architecture / Feature Governance

## 1. 需求与目标

用户提出：功能设计应分别代入教练/分析人员和观众，思考其在赛前、赛中、赛后真正会关注什么，由真实问题反推功能，而不是先堆页面或数据项。

本任务将该原则正式固化为：

```text
Feature = Persona × Match Phase × User Question
```

## 2. Constitution Preflight

结果：PASS

开发前读取并核对：

- `docs/ENGINEERING_CONSTITUTION.md`
- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- 当前 GitHub `main` 真实状态

本任务只修改产品模型与治理文档，无业务代码、Schema、运行时依赖或 UI 实现变更。

## 3. Baseline

- Repository：`xbu080675-creator/laner`
- Branch：`main`
- 前置任务：LNR-000 DONE，LNR-005 DONE
- Business implementation：NOT STARTED

## 4. 核心决策

### D1 — 两类核心 Persona

- `SPECTATOR`：普通观众 / 深度观众
- `COACH_ANALYST`：教练 / 分析人员 / 高阶复盘用户

### D2 — 三阶段继续作为一级时间轴

- `PRE_MATCH`
- `LIVE_MATCH`
- `POST_MATCH`

### D3 — 功能必须来源于真实问题

任何新功能在进入计划前必须回答：Persona、Phase、User Question、Decision Value、Evidence、Presentation、Broadcast Redundancy、Acceptance。

仅因为“这个数据可以获取”不足以构成功能开发理由。

### D4 — 同一底层事实，不复制两套业务逻辑

观众和教练共享 Domain / Event / Match State / Application 能力。

Presentation 层允许不同信息密度：

- 观众：结论优先、中文解释、视觉化；
- 教练：证据优先、结构化、可筛选、可追溯、展示样本与置信度。

### D5 — Laner 不与导播重复劳动

赛中高优先级信息优先满足：

- 导播未展示；
- 导播晚展示；
- 比分板没有；
- 普通观众不易同时观察；
- 组合后才有意义的隐性变化。

## 5. 文件变更

新增：

- `docs/PRODUCT_PERSPECTIVE_MATRIX.md`
- `docs/development/2026/2026-09-12_LNR-006_persona-phase-product-matrix.md`

修改：

- `docs/ARCHITECTURE.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`

删除：无

## 6. 功能 / 架构 / UI / Schema 影响

- 功能：无新增运行时功能；
- 架构：增加 Persona × Phase × Question 产品推导约束；
- UI：未来允许观众/教练不同信息密度，但禁止复制底层业务；
- Schema：N/A；
- 依赖：N/A；
- 配置：N/A。

## 7. 测试与验证

正向：
- 三阶段均覆盖 `SPECTATOR` 和 `COACH_ANALYST` 问题：PASS
- 每阶段均已从用户问题映射到候选产品能力：PASS
- Architecture / Plan / Status / Changelog 一致性：PASS

负向：
- “仅因数据能获取就开发”的路径已明确禁止：PASS

边界：
- Persona 不构成新的业务阶段：PASS
- 两 Persona 不复制 Domain 业务实现：PASS
- 设置/诊断等共享能力仍不构成第四阶段：PASS

回归：
- LNR-005 三阶段一级轴未被改变：PASS
- “功能不改、底层重做”原则未改变：PASS
- Business implementation 仍为 NOT STARTED：PASS

脚本验证：
- N/A —— 本次无脚本变更。

## 8. 风险与影响

- 架构：正向，减少功能堆砌和视图分叉；
- 兼容：无运行时影响；
- 数据：无；
- 性能：无；
- 安全：无；
- UI：未来功能必须声明目标 Persona 和回答的问题。

## 9. 已知问题与后续

- 当前问题矩阵是产品基线，不等于最终功能清单；
- LNR-001 读取旧 RiftLab 时，需为旧功能补齐 Phase、Persona、User Question、Decision Value；
- LNR-003 需冻结观众层 / 教练层的 Application Query 与 ViewModel 边界。

## 10. 回滚

若未来正式撤销该模型，必须新增 ADR/开发记录说明替代原则，并同步 Architecture / Plan / Status；不得删除本历史记录。

## 11. 状态同步

- DEVELOPMENT_PLAN：LNR-006 = DONE
- IMPLEMENTATION_STATUS：LNR-006 = DONE
- CHANGELOG：已同步
- PRODUCT_PERSPECTIVE_MATRIX：已创建

## 12. Post-change Compliance Review

结果：PASS

确认：

- 无业务范围蔓延；
- 无运行时代码或依赖变更；
- 未改变已冻结三阶段原则；
- 产品决策已进入权威仓库；
- 开发留档完整；
- 未虚报运行时测试。

## 【任务交付单】

1. 任务：`LNR-006 / 用户角色 × 比赛阶段产品矩阵 / DONE`
2. Constitution Preflight：PASS
3. 涉及模块：product-model / architecture / planning / status
4. 文件变更：见 §5
5. 测试覆盖：文档一致性、边界与回归 PASS；运行时测试 N/A
6. 脚本验证：N/A
7. 风险：无运行时风险；未来功能开发受产品问题模型约束
8. 状态同步：PASS
9. Post-change Compliance Review：PASS
10. 最终结论：DONE
