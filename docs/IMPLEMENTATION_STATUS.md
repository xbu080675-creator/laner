# IMPLEMENTATION_STATUS

更新日期：2026-09-13

## 迁移结论

**状态：COMPLETE / READY FOR ACCEPTANCE**

Laner 已完成从固定 Rlftlab 功能基线的全仓迁移、架构重构、仓库重新链接、历史 workflow 收口和 main 接管。当前没有在本迁移任务范围内已知的阻断项。

## 可追溯基线

- 源功能基线：`Rlftlab@e46e4ea35ff8058ce90af1f79f15de31e14377dd`
- 源根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- 精确迁移提交：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`
- 精确迁移根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- 迁移前旧 Laner：`archive/pre-riftlab-reset-2026-09-13`
- 架构工作分支最终留档：`refactor/riftlab-architecture@43c083b8066a281df2cf2eb100c750902ba7876d`
- main 架构接管提交：`6b454bc7a20433811b37fe6a142e200e212b0048`
- RiftClaw selftest Core 路径修复：`e1232024a2302505f22e25b68a2343ca74eca351`
- 源基线已有 selftest 陈旧断言修复：`d932e0200c91b3f3085ec755e405e180bb698b14`
- 验收留档前最新数据提交：`53fed17246ecb96e8bf3605d046432c2b02b31ed`

## 架构状态

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| Rlftlab 全仓逐字节迁移 | DONE | 固定源提交与迁移基线根 tree SHA 完全一致 |
| 旧 Laner 隔离 | DONE | 旧实现只保留归档分支，不作为当前代码来源 |
| 工程宪法/留档规则 | DONE | Core/Adapter 边界、真实测试、开发记录、门禁均已恢复 |
| 纯 JVM `:core` | DONE | 领域模型、Port、身份/资格/赛事治理/研究/赛后等纯规则已迁入 |
| Android `:app` Adapter | DONE | Android、网络、OCR、JSON、文件、Provider、Store、UI/组合根留 App |
| 仓库重新链接 | DONE | 运行时 GitHub/Raw/jsDelivr/Release/OTA 已指向 `xbu080675-creator/laner` |
| Core boundary gate | DONE | `tools/check_core_boundary.py` 已进入 Compile Diagnostics |
| Repository link gate | DONE | `tools/check_repository_links.py` 已进入 Compile Diagnostics |
| 历史一次性 workflow 收口 | DONE | 17 个原 blob 归档到 `.github/workflow-archive/legacy-one-shot/`，不再 active |
| 长期 workflow | DONE | active 区保留 11 个 build/selftest/OTA/数据同步能力 |
| Gradle 工程身份 | DONE | `rootProject.name = "Laner"` |
| App 兼容身份 | UNCHANGED | `applicationId=com.riftlab.app`、包名、版本、UI/用户侧产品行为不改 |
| main 接管 | DONE | 普通 fast-forward 提交，无 force |
| main 最新数据保留 | DONE | 接管时保留 `data@a3a4c7cd...`；同步持续推进，验收留档前为 `data@cfc4e12ccbf472c06880bec45a27138206380389` |

## 最终真实门禁

### ARC-012 候选

Compile Diagnostics run `34745478294`：PASS。
- Core boundary：PASS
- Laner repository links：PASS
- `:core:test`：PASS
- `:app:assembleDebug`：PASS

### main 接管提交 `6b454bc7...`

Compile Diagnostics run `34745713282`：PASS。
- Core boundary：PASS
- repository link：PASS
- compile：PASS

Android Build run `34745713299`：PASS。
- Global esports data plane：PASS
- fixed-signed debug APK：PASS
- fixed dev signature：PASS
- artifact upload：PASS
- artifact：`RiftLab-global-debug`
- artifact digest：`sha256:5a6dd1d6c7945203863a0d0c9c8bca32403e142f65506f2530c6e52d301cef72`

### selftest 修复后当前代码树

RiftClaw Selftest run `34745871662`：PASS。

Android Build run `34745871640`（`d932e020...`）：PASS。
- fixed-signed debug APK：PASS
- signature verification：PASS
- artifact upload：PASS
- artifact digest：`sha256:d74d4caf9cf9e6f7509a6c1143b3d7b9114f90171f0649edf48a65b02e9c0cf0`

## 持续能力验收

main 接管触发的长期工作流：
- Team Data Sync `34745713291`：PASS
- Starting Roster Sync `34745713239`：PASS
- International Event Mirror `34745713273`：PASS
- Riot Persisted Mirror `34745713232`：PASS
- DEV OTA Direct `34745713306`：PASS，并完成 canonical GitHub `dev-latest` 发布

RiftClaw Selftest 在接管后先后暴露两个真实测试问题：迁入 Core 后的旧路径、以及源基线自带的陈旧 credential 文本断言。两项均只修 selftest 接线/断言，没有修改 RiftClaw 运行时协议或安全策略，最终 run `34745871662` PASS。

## 明确未改变

- 不新增业务功能。
- 不改 UI/布局/交互。
- 不替换赛事逻辑或数据语义。
- 不从迁移前 Laner 复制实现代码。
- 不改 `applicationId=com.riftlab.app`、现有包名、版本号和用户侧兼容身份。
- 不做与迁移或现存 bug 无关的“顺手优化”。

当前状态可进入人工联合验收。
