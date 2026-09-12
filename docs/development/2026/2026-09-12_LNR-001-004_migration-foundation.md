# LNR-001 ~ LNR-004 — 功能迁移基线与工程骨架

- 日期：2026-09-12
- 分支：`feature/migration-foundation`
- Legacy baseline：`xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy source version：`1.0.0-dev.94 / versionCode 94`
- 状态：`LNR-001 DONE / LNR-002 DONE / LNR-003 DONE / LNR-004 DONE`

## 1. 需求与目标

用户批准开始完整迁移 RiftLab → Laner：功能不缩水，底层架构重做；允许快速推进，但质量、测试、留档和可追溯性不得降低。

## 2. Constitution Preflight

结果：`PASS`

开发前读取并核对：Laner 工程宪法/架构/范围/计划/状态，以及 RiftLab 当前 main、README、开发历史、Gradle、UI/data/ai/overlay/stream/update 目录和关键源码。

审计确认：旧 README 的 dev.66 已滞后，当前源码 `app/build.gradle.kts` 为 `1.0.0-dev.94 / versionCode 94`，故迁移以 Git main 当前源码为权威旧版事实。

## 3. 范围

### 完成
- LNR-001：完整功能迁移基线 `FEATURE_BASELINE.md`；
- LNR-002：旧架构审计 `LEGACY_ARCHITECTURE_AUDIT.md`；
- LNR-003：架构冻结 `ARCHITECTURE_FREEZE.md`；
- LNR-004：真实多模块工程、Core、Tests、Android 壳、错误码/诊断、CI/架构 Gate。

### 明确不做
- 不宣称业务功能已全部迁移；
- 不复制旧大型 Store；
- 不复制旧签名密钥或任何 Secret；
- 不用 Mock 比赛事实填 UI；
- 不把 Android/Compose/网络/Provider SDK 带入 Core。

## 4. 关键设计决策

- 技术栈沿用旧版已验证 Android/Kotlin/Compose 路线，避免无价值换栈；架构重新建立。
- DAG：`:core:domain ← :core:application ← :app`。
- Provider 只产出候选事实；Application `FactArbiter` 唯一仲裁。
- `GLOBAL_AI_ASSIST` 不能进入事实权威路径；`FACT_BACKED` AI 必须引用事实 ID。
- 暂保留 `applicationId = com.riftlab.app` 供未来覆盖升级/数据迁移验证，新 namespace 为 `com.laner.app`。
- Core 使用稳定 `LNR-MODULE-STAGE-NNN` ErrorCode 与平台无关 Diagnostics Port。

## 5. 文件变更

### 新增
- `docs/FEATURE_BASELINE.md`
- `docs/LEGACY_ARCHITECTURE_AUDIT.md`
- `docs/ARCHITECTURE_FREEZE.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `.gitignore`
- `core/domain/build.gradle.kts`
- `core/domain/README.md`
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchLifecycle.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/Identifiers.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/SourceModel.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchModels.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchEvent.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/ErrorCode.kt`
- `core/domain/src/test/kotlin/com/laner/core/domain/MatchLifecycleTest.kt`
- `core/domain/src/test/kotlin/com/laner/core/domain/SourceModelTest.kt`
- `core/application/build.gradle.kts`
- `core/application/README.md`
- `core/application/src/main/kotlin/com/laner/core/application/SourcePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/FactArbiter.kt`
- `core/application/src/main/kotlin/com/laner/core/application/PresentationContracts.kt`
- `core/application/src/main/kotlin/com/laner/core/application/DiagnosticsPort.kt`
- `core/application/src/test/kotlin/com/laner/core/application/FactArbiterTest.kt`
- `app/build.gradle.kts`
- `app/README.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/ui/LanerTheme.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- `.github/workflows/android-build.yml`
- 本开发记录。

### 修改
- `docs/DEVELOPMENT_PLAN.md`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/CHANGELOG.md`
- `FactArbiter.kt` comparator 防御性修正；
- `SourcePort.kt` 改用稳定 `ErrorCode`；
- CI Gradle 由错误的 9.4.0 修正为 9.6.0。

### 删除
- 无。

## 6. Findings / 修复历史

### MGF-001 — `MatchEvent` interface 错误使用 init
第一版 `sealed interface MatchEvent` 误放 `init`，属于 Kotlin 编译错误。立即改为文件级验证函数 + 具体 data class `init`。`FIXED`。

### MGF-002 — Boolean comparator 风险
`FactArbiter` 初版直接用 Boolean descending comparator；改成显式 `1/0` rank，避免泛型/可读性风险。`FIXED`。

### MGF-003 — CI Gradle 版本不满足 AGP 9.4.0
首轮 GitHub Actions 使用 Gradle 9.4.0，真实日志返回：AGP 9.4.0 最低要求 Gradle 9.6.0。Android compile 被阻断。

处理：将 CI Gradle 固定为 9.6.0，重新执行全部 Gate。

结果：`FIXED / REGRESSION PASS`。

历史失败未删除，保留用于回溯。

## 7. 测试与验证

### 覆盖
正向：生命周期映射、官方确认事实优先、freshness 同级选择、revision 修订优先。

负向：AI 不得发布事实候选、Inference 不得进入事实仲裁、FACT_BACKED 无 supporting facts 时拒绝。

边界：REALTIME 15,000ms freshness、UNKNOWN 不猜阶段、同级不同事实显式 Conflict。

架构：Core 禁止 Android/AndroidX/OkHttp/JSON/Retrofit import，禁止反向引用 `com.laner.app`。

### 实际结果
- 首轮 CI：`FAIL` —— MGF-003，Gradle 版本配置错误；
- 修复后 CI run `34686592578`：`PASS`；
  - Architecture boundary gate：PASS
  - Domain and application tests：PASS
  - Android `:app:assembleDebug`：PASS
- 本地执行：`NOT EXECUTED` —— 当前通过 GitHub connector 开发，无本地联网 Gradle 环境；
- 实机：`N/A` —— 本轮尚未迁移需要实机验收的平台功能。

## 8. 脚本验证

`N/A` —— 无新增 `.sh/.py/.ps1` 可执行脚本。GitHub Actions YAML 已通过真实 Actions 运行验证。

## 9. 风险与影响

- 架构：高影响正向；新工程进入可编译、可测试状态。
- 兼容：applicationId 保留，但签名/旧本地数据仍待后续迁移验证。
- 数据：当前无用户数据写入。
- 性能：尚无真实 Provider 链，暂不作运行时结论。
- 网络：真实 Provider Adapter 尚未迁移。
- 安全：未提交 keystore/Token/Secret。
- UI：当前为诚实三阶段壳，不代表旧功能已迁完。

## 10. 回滚

全部改动位于 `feature/migration-foundation` / PR #1。合并前可关闭 PR；合并后按 merge commit 回退。失败记录和本留档不得删除。

## 11. 后续

进入 M1：Global Competition Catalog / Schedule → PRE Roster/Staff/Form/H2H → Standings/Qualification → LIVE → POST → Android 平台能力 → AI/OCR → Compatibility/Migration Audit。

## 12. Post-change Compliance Review

结果：`PASS`。

确认：
- Core 无平台依赖；
- UI 无 Provider 直连；
- Region 不构成业务模块；
- AI 不进入事实权威路径；
- 无假比赛事实；
- 代码、测试、模块文档、开发状态和留档已同步；
- 首轮失败已真实保留；
- 修复后 CI 全绿。

## 【任务交付单】

1. Constitution Preflight：`PASS`
2. 涉及模块：`docs / core-domain / core-application / android-app / ci`
3. 文件变更：见 §5
4. 测试覆盖：见 §7
5. 实际执行：CI `PASS`（run `34686592578`）
6. 脚本验证：`N/A`
7. 日志/诊断：稳定 ErrorCode + Diagnostics Port 已建立；App 前缀 `[Laner:APP]`
8. 故障定位：Domain 状态→`:core:domain`；来源冲突→`FactArbiter`；Android 启动/UI→`:app`
9. 风险：见 §9
10. 状态同步：LNR-001/002/003/004 全部 `DONE`
11. Commit/Push：`PASS`，远端 feature branch 已持久化
12. Compliance Review：`PASS`
13. 最终结论：`DONE`
