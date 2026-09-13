# IMPLEMENTATION_STATUS

更新日期：2026-09-13

## 当前迁移主线

- 源功能基线：`Rlftlab@e46e4ea35ff8058ce90af1f79f15de31e14377dd`
- 精确迁移提交：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`
- 源/目标根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- 迁移前旧 Laner：`archive/pre-riftlab-reset-2026-09-13`
- 架构工作分支：`refactor/riftlab-architecture`
- ARC-011 已验证提交：`f811b11c4b9dd6220b261e9b7703b938db42bb0a`
- ARC-012 已验证候选：`863e0add003800a9c048a088b23357cf215e6afa`
- ARC-012 Compile Diagnostics：run `34745478294`，PASS

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
| 历史一次性 workflow 退出 active 区 | DONE | 17 个原 blob 已迁入 `.github/workflow-archive/legacy-one-shot/` |
| Gradle 工程身份切换为 Laner | DONE | 仅改 `rootProject.name`；app id/package/UI 不变 |
| ARC-012 Android 全量构建验收 | DONE | Core boundary / repository link / `:core:test` / `:app:assembleDebug` 全绿 |
| main 最终接管 | TESTING | 将验证后的架构 tree 与接管瞬间 main 最新 `data/` tree 合并后提交 |

## ARC-012 验证结果

GitHub Actions run `34745478294`：
- Core boundary gate：PASS；
- Laner repository link gate：PASS；
- `:core:test`：PASS；
- `:app:assembleDebug`：PASS；
- Compile Diagnostics job：PASS。

最终迁移只有在 `main` 接管后的主线构建也实际通过后才标记 COMPLETE。

## 明确不做

- 不新增业务功能。
- 不改 UI/布局/交互。
- 不替换赛事逻辑或数据语义。
- 不从迁移前 Laner 复制实现代码。
- 不改 `applicationId=com.riftlab.app`、现有包名、版本号和用户侧兼容身份。
- 不做与迁移或现存 bug 无关的“顺手优化”。
