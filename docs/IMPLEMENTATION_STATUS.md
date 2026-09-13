# IMPLEMENTATION_STATUS

更新日期：2026-09-13

## 当前迁移主线

- 源功能基线：`Rlftlab@e46e4ea35ff8058ce90af1f79f15de31e14377dd`
- 精确迁移提交：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`
- 源/目标根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- 迁移前旧 Laner：`archive/pre-riftlab-reset-2026-09-13`
- 架构工作分支：`refactor/riftlab-architecture`
- ARC-011 已验证提交：`f811b11c4b9dd6220b261e9b7703b938db42bb0a`

## 状态

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| Rlftlab 全仓逐字节迁移 | DONE | 固定源提交与迁移基线根 tree SHA 完全一致 |
| 旧 Laner 隔离 | DONE | 旧实现只保留归档分支，不作为新代码来源 |
| 通用工程宪法恢复 | DONE | 架构边界、留档、门禁与验收规则已恢复 |
| Core 领域模型/Port 抽取 | DONE | 纯 JVM `:core` 已建立并完成当前迁移范围内的领域抽取 |
| 身份 Policy 抽取 | DONE | Match identity / live frame gate 已迁入 Core |
| Adapter 分层与重新接线 | DONE | Android/网络/OCR/Store/Provider 留 App；纯领域规则进入 Core |
| 仓库/OTA 数据地址重链接到 Laner | DONE | 旧 Rlftlab 运行时地址已清理，仓库链接门禁已建立 |
| 赛后/比赛详情领域拆分 | DONE | ARC-011 完整构建 PASS |
| 历史一次性 workflow 退出 active 区 | TESTING | ARC-012 候选中，原 blob 归档保留 |
| Gradle 工程身份切换为 Laner | TESTING | 仅改 `rootProject.name`，不改 app id/package/UI |
| Android 全量构建验收 | TESTING | ARC-012 候选提交后执行 `:core:test + :app:assembleDebug` |
| main 最终接管 | TODO | 仅在 ARC-012 全绿后执行，并保留 main 最新 `data/` tree |

## 已验证门禁

ARC-011 已通过：
- Core boundary gate；
- Laner repository link gate；
- `:core:test`；
- `:app:assembleDebug`。

ARC-012 未完成前，不将最终迁移状态标记为 COMPLETE。

## 明确不做

- 不新增业务功能。
- 不改 UI/布局/交互。
- 不替换赛事逻辑或数据语义。
- 不从迁移前 Laner 复制实现代码。
- 不改 `applicationId=com.riftlab.app`、现有包名、版本号和用户侧兼容身份。
- 不做与迁移或现存 bug 无关的“顺手优化”。
