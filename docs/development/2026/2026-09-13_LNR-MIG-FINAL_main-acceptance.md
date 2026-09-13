# LNR-MIG-FINAL / main 联合验收前收口记录

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS / READY FOR ACCEPTANCE

## 1. 功能基线与精确迁移

- 唯一功能基线：`xbu080675-creator/Rlftlab@e46e4ea35ff8058ce90af1f79f15de31e14377dd`
- 源根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- Laner 精确迁移提交：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`
- 迁移根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`

因此迁移起点在 Git tree 级别与源固定提交完全一致。迁移前旧 Laner 仅保留于 `archive/pre-riftlab-reset-2026-09-13`。

## 2. 架构完成点

- 最终架构留档：`refactor/riftlab-architecture@43c083b8066a281df2cf2eb100c750902ba7876d`
- `:core`：纯 JVM 领域模型、Port 与无平台副作用规则。
- `:app`：Android UI/组合根及网络、OCR、JSON、文件、Provider、Store 等 Adapter。
- Core 平台依赖由 `tools/check_core_boundary.py` 阻断。
- 旧 Rlftlab 运行时仓库链接由 `tools/check_repository_links.py` 阻断。
- Gradle `rootProject.name` 已切为 `Laner`。
- `applicationId=com.riftlab.app`、包名、版本和用户可见产品行为保持不变。

## 3. Workflow 收口

17 个历史一次性 apply/fix/migrate workflow 已按原 blob 归档到：

`.github/workflow-archive/legacy-one-shot/`

它们不再拥有 GitHub Actions 执行资格，但完整保留审计证据。active `.github/workflows/` 仅保留 11 个长期 build、Compile Diagnostics、selftest、OTA 与数据同步能力。

## 4. main 接管与数据并发保护

- 接管前 main：`75a2d64cf9049799e6964dc4c740c7b1a8c3275c`
- 接管提交：`6b454bc7a20433811b37fe6a142e200e212b0048`
- 接管 tree：`66f21ac174feef7e461202d0b8a2a01101bf04af`
- 接管采用普通 fast-forward 更新，未使用 force。
- 接管时原 main 最新 `data/` tree `a3a4c7cd3df37f5fbe54ad5571c3f11c8b6c7afb` 被显式保留，没有用迁移基线旧数据覆盖。
- 接管后持续同步继续正常提交。`d932e020...` 快照中的 `data/` tree 已推进为 `2ecefe97425a60c7bf079727f7352a2b6d22588e`。
- 在最终验收留档前，Starting Roster bot 又正常追加 `53fed17246ecb96e8bf3605d046432c2b02b31ed`，当前数据 tree 继续推进为 `cfc4e12ccbf472c06880bec45a27138206380389`。
- 最终验收文档以该 bot 提交为父提交生成，不覆盖任何并发数据更新。

## 5. 主线真实构建验收

### ARC-012 架构候选

GitHub Actions Compile Diagnostics run `34745478294`：PASS。

- Core boundary：PASS
- repository link：PASS
- `:core:test`：PASS
- `:app:assembleDebug`：PASS

### main 接管提交

Compile Diagnostics run `34745713282`：PASS。

Android Build run `34745713299`：PASS。
- global esports data plane：PASS
- fixed-signed debug APK：PASS
- fixed dev signature：PASS
- artifact upload：PASS
- artifact `RiftLab-global-debug`
- artifact digest `sha256:5a6dd1d6c7945203863a0d0c9c8bca32403e142f65506f2530c6e52d301cef72`

### 接管后的 selftest 修复

第一次 RiftClaw Selftest 发现迁入 Core 后 workflow 仍读取旧 App 路径；修复提交：
`e1232024a2302505f22e25b68a2343ca74eca351`

第二次运行继续发现源固定基线本身已有的陈旧断言：selftest 要求 Store 中出现 `RiftClaw bridge pairing token`，但 `Rlftlab@e46e4ea...` 的 `ProviderCredentialStore.kt` 从来不存在该文本。该断言改为验证真实凭据键 `KEY_RIFTCLAW_BRIDGE_TOKEN = "riftclaw_bridge_token_v1"`；修复提交：
`d932e0200c91b3f3085ec755e405e180bb698b14`

这两次修复只修改 selftest 路径/断言，RiftClaw 协议、安全策略与 App 运行时代码均未修改。

最终 RiftClaw Selftest run `34745871662`：PASS。

`d932e020...` Android Build run `34745871640`：PASS。
- fixed-signed debug APK：PASS
- fixed dev signature：PASS
- artifact upload：PASS
- artifact digest `sha256:d74d4caf9cf9e6f7509a6c1143b3d7b9114f90171f0649edf48a65b02e9c0cf0`

## 6. 持续能力验收

main 接管提交触发的长期 workflow：

- Team Data Sync `34745713291`：PASS
- Starting Roster Sync `34745713239`：PASS
- International Event Mirror `34745713273`：PASS
- Riot Persisted Mirror `34745713232`：PASS
- DEV OTA Direct `34745713306`：PASS，canonical GitHub `dev-latest` 已发布

Android Build 和 Compile Diagnostics 亦均 PASS。接管后数据同步已经连续产生新的 `data/` tree，证明持续链路没有因迁移被切断。

## 7. 行为约束验收

本迁移未引入新产品功能，未重做 UI，未改变产品需求、赛事规则、数据语义、排序/过滤逻辑或 Android 应用兼容身份。所有新代码/改动均限定于迁移、架构分层、重新链接、门禁与可复现已有 bug 修复范围。

## 8. 结论

仓库迁移、架构重构、运行时仓库重链接、workflow 收口、main 接管、主线编译、签名 APK、RiftClaw selftest、OTA 与主要持续数据同步均已通过真实验证。

状态：**READY FOR USER ACCEPTANCE**。
