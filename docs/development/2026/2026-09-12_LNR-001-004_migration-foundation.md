# LNR-001 ~ LNR-004 — 功能迁移基线与工程骨架

- 日期：2026-09-12
- 分支：`feature/migration-foundation`
- Legacy baseline：`xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy source version：`1.0.0-dev.94 / versionCode 94`
- 状态：`LNR-001 DONE / LNR-002 DONE / LNR-003 DONE / LNR-004 TESTING`

## 1. 需求与目标

用户正式批准开始开发，并要求将 RiftLab 功能完整迁移到 Laner：功能不缩水，底层架构全部重做；允许快速推进，但质量、测试、留档和可追溯性不得降低。

## 2. Constitution Preflight

结果：`PASS`

开发前读取：

- Laner 最新工程宪法、架构、项目范围、开发计划、实现状态；
- `xbu080675-creator/Rlftlab` 当前 main；
- 旧 README、开发历史、当前 changelog、Gradle 配置；
- 旧 UI / data / ai / overlay / stream / update 目录；
- 旧 `DataSources.kt`、`ComprehensiveData.kt`、`RiftLabApp.kt` 等关键源码。

确认旧 README 所写 dev.66 已滞后；真实源码 `app/build.gradle.kts` 为 dev.94，因此迁移以 Git main 源码而不是 README 版本描述为权威事实。

## 3. 本次范围

### 完成

- LNR-001：提取完整功能基线；
- LNR-002：审计旧架构；
- LNR-003：冻结新架构与技术栈；
- LNR-004：建立真实多模块项目、第一批 Core、Tests、Android 壳、CI Gate。

### 明确不做

- 本轮不宣称 PRE/LIVE/POST 业务功能已全部迁移；
- 不复制旧 `MatchSessionStore` 等大型 Store；
- 不复制旧签名密钥；
- 不用 Mock 比赛数据填 UI；
- 不在 Core 中加入 Android / Compose / OkHttp / Media3 / AI SDK。

## 4. 设计决策

### D1 — 技术栈沿用，架构重做

继续使用旧版已验证 Android/Kotlin/Compose 主栈，避免同时承担框架迁移风险；把工程速度用在业务迁移而不是无价值换栈。

### D2 — 多模块 DAG

```text
:core:domain ← :core:application ← :app
```

Core 两层纯 Kotlin，Android 只存在于 `:app`。

### D3 — 唯一事实仲裁

Provider 只生成候选事实；Application `FactArbiter` 统一处理 verification / authority / freshness / timestamp / revision。无法可靠裁决的同级冲突返回 `Conflict`，禁止静默覆盖。

### D4 — AI 与事实硬隔离

`GLOBAL_AI_ASSIST` 不能创建 `FactCandidate`，`INFERENCE` 不能进入事实仲裁；`FACT_BACKED` AI 结果必须引用 supporting fact IDs。

### D5 — 保留旧 applicationId

暂保留 `com.riftlab.app` 以支持未来覆盖升级和数据兼容评估；新代码 namespace 使用 `com.laner.app`。签名兼容单独在 OTA/发布任务验证。

## 5. 文件变更

### 新增

- `docs/FEATURE_BASELINE.md`
- `docs/LEGACY_ARCHITECTURE_AUDIT.md`
- `docs/ARCHITECTURE_FREEZE.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `core/domain/build.gradle.kts`
- `core/domain/README.md`
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchLifecycle.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/Identifiers.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/SourceModel.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchModels.kt`
- `core/domain/src/main/kotlin/com/laner/core/domain/MatchEvent.kt`
- `core/domain/src/test/kotlin/com/laner/core/domain/MatchLifecycleTest.kt`
- `core/domain/src/test/kotlin/com/laner/core/domain/SourceModelTest.kt`
- `core/application/build.gradle.kts`
- `core/application/README.md`
- `core/application/src/main/kotlin/com/laner/core/application/SourcePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/FactArbiter.kt`
- `core/application/src/main/kotlin/com/laner/core/application/PresentationContracts.kt`
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
- 开发过程中 `FactArbiter.kt` 做过一次防御性 comparator 修正。

### 删除

- 无。

## 6. 过程中发现并修复的问题

### Finding MGF-001 — `MatchEvent` interface 错误使用 init

第一版 `sealed interface MatchEvent` 中误放 `init` 块，这是 Kotlin 编译错误。

处理：立即停止扩展该文件，把公共验证函数移到文件级，并在每个具体 data class 的 `init` 中执行。

状态：`FIXED`。

### Finding MGF-002 — Boolean comparator 可读性/兼容风险

`FactArbiter` 初版直接对 Boolean 做 descending comparator。为避免 Kotlin Comparator 泛型/可读性风险，改成显式 `1/0` rank。

状态：`FIXED`。

## 7. 测试覆盖

### 已编写

正向：
- 生命周期映射；
- 官方确认事实优先；
- freshness 同级选择；
- revision 修订优先。

负向：
- AI 不得发布事实候选；
- inference 不得进入事实仲裁；
- FACT_BACKED AI 无 supporting facts 时拒绝。

边界：
- REALTIME freshness 15,000ms 边界；
- UNKNOWN 生命周期不猜阶段；
- 同级不同事实显式 Conflict。

回归：
- `赛事开始 != 游戏开始` 通过生命周期测试永久化；
- MGF-001 由后续真实编译 Gate 覆盖。

### 实际执行状态

- 本地 Unit Tests：`NOT EXECUTED` —— 当前通过 GitHub connector 直接开发，无本地 Gradle runtime。
- Android compile：`NOT EXECUTED` —— 等待 GitHub Actions。
- GitHub CI：`PENDING`。
- 实机：`N/A` —— 本轮只建立骨架，无平台功能迁移验收。

不得在 CI 通过前把 LNR-004 标记 DONE。

## 8. 脚本验证

`N/A` —— 本轮未新增 `.sh/.py/.ps1` 可执行脚本。GitHub Actions YAML 不属于本宪法的本地可执行脚本解析项，但其真实有效性由 Actions 运行验证。

## 9. 风险与影响

- 架构：高影响、正向；新工程从文档阶段进入真实模块化实现。
- 兼容：保留旧 applicationId，但签名/旧本地数据尚未验证迁移。
- 数据：无用户数据写入；旧数据 importer 尚未实现。
- 性能：无真实数据链，暂无运行时结论。
- 网络：无 Provider Adapter 落地。
- 安全：未复制任何旧 keystore/Token/Secret。
- UI：只有诚实的三阶段迁移壳，不代表功能完成。

## 10. 回滚

本轮所有实现位于 `feature/migration-foundation`。在合并前可直接关闭 PR；合并后可按 merge commit 回退。不得删除本记录以伪造历史。

## 11. 后续

CI 通过后：

1. LNR-004 → DONE；
2. 开始 M1 Global Competition Catalog / Schedule；
3. 逐项推进 `docs/FEATURE_BASELINE.md`；
4. 每个迁移切片独立留档与测试。

## 12. Post-change Compliance Review

当前：`PENDING CI`。

已确认：

- Core 无 Android 类型；
- UI 无 Provider 直连；
- 未按赛区复制业务逻辑；
- AI 不能进入事实权威路径；
- 无 Mock 赛事事实；
- 代码/测试/文档同步存在；
- 未虚报未执行测试。

## 【任务交付单】

1. Constitution Preflight：`PASS`
2. 涉及模块：`docs / core-domain / core-application / android-app / ci`
3. 强相关规则：架构边界、DAG、测试、留档、Git 权威、AI/外部源边界
4. 文件变更：见 §5
5. 测试覆盖：已编写；实际运行等待 CI
6. 日志前缀：运行时日志尚未引入，`N/A`；App 预留 `[Laner:APP]`
7. 故障定位：Core 状态→domain；来源冲突→FactArbiter；启动/UI→app composition root
8. 风险：见 §9
9. 任务状态同步：LNR-001/002/003 DONE；LNR-004 TESTING
10. Commit/Push：`PASS`，变更已在远端 feature branch
11. Compliance Review：`PENDING CI`
12. 最终结论：`DELIVERY INCOMPLETE`，直到 CI PASS
