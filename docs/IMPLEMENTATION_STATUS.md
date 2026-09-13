# IMPLEMENTATION_STATUS

更新日期：2026-09-13

## 当前迁移主线

- 源功能基线：`Rlftlab@e46e4ea35ff8058ce90af1f79f15de31e14377dd`
- 精确迁移提交：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`
- 源/目标根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- 迁移前旧 Laner：`archive/pre-riftlab-reset-2026-09-13`
- 架构工作分支：`refactor/riftlab-architecture`

## 状态

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| Rlftlab 全仓逐字节迁移 | DONE | 固定源提交与迁移基线根 tree SHA 完全一致 |
| 旧 Laner 隔离 | DONE | 旧实现只保留归档分支，不作为新代码来源 |
| 通用工程宪法恢复 | IN PROGRESS | 仅恢复已约定的工程规则，不恢复旧 Laner 功能实现 |
| Core 领域模型/Port 抽取 | IN PROGRESS | 纯 JVM `:core` 第一批迁移中 |
| 身份 Policy 抽取 | TODO | 需处理跨模块可见性后迁入 Core |
| Adapter 分层与重新接线 | TODO | 不改变 Provider 行为，仅切断跨层依赖 |
| 仓库/OTA 数据地址重链接到 Laner | TODO | 只处理硬编码仓库身份，不改数据协议 |
| 已有 bug 修复 | TESTING | 仅修构建/迁移或可复现现存问题 |
| Android 全量构建验收 | TESTING | 每批架构迁移后执行 |

## 明确不做

- 不新增业务功能。
- 不改 UI/布局/交互。
- 不替换赛事逻辑或数据语义。
- 不从迁移前 Laner 复制实现代码。
- 不做与迁移或现存 bug 无关的“顺手优化”。
