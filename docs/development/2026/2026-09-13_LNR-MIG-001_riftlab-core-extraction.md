# LNR-MIG-001 / Rlftlab 全量迁移与 Core 第一阶段抽取

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：TESTING

## 需求来源与目标

用户要求清空 Laner 当前实现，以 `xbu080675-creator/Rlftlab` 为唯一功能基线完整迁入；随后按照既定工程宪法重新构筑架构、重新接线，并只修已有 bug。禁止新增功能和改变既有需求。

## 开发前 Baseline

- 源仓库：`xbu080675-creator/Rlftlab`
- 源提交：`e46e4ea35ff8058ce90af1f79f15de31e14377dd`
- 源根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`
- 目标旧 main：`09ea83708461f64754f4dcd21b473babf94ff839`
- 旧实现归档：`archive/pre-riftlab-reset-2026-09-13`
- 精确迁移提交：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`
- 精确迁移根 tree：`20ee8cbb258dd9613e1476c30c9e8cd53b633172`

## 本次范围

1. 保存旧 Laner 审计点。
2. 将 Rlftlab 固定提交完整迁入目标仓库并做 tree SHA 硬校验。
3. 恢复用户此前已批准的通用工程宪法作为当前工程规则源。
4. 建立纯 JVM `:core` 模块。
5. 第一批机械迁移领域模型、Port、综合数据契约、赛事治理契约和本地确定性分析。
6. 让 `:app` 依赖 `:core`，不改变上述源码正文和包名。

## 明确不做

- 不新增产品功能。
- 不修改 UI、布局或交互。
- 不改赛事判断、数据格式和 Provider 策略。
- 不从旧 Laner 归档分支复制功能实现。
- 不对与迁移无关的代码做清理或风格化重写。

## 设计决策

### 精确基线

迁移验收不采用人工抽查，而采用 Git tree SHA。最终目标迁移 tree 与源 tree 完全相同，因此后续所有架构变化都可从该提交独立审计。

### 包名兼容

第一阶段只改变 Gradle 模块归属，不改变 `com.riftlab.app.data` 包名。原因是包名重写会造成大面积无业务价值 diff，并把架构迁移与 API 重命名混成一个风险面。

### Core 依赖

Core 只允许 Kotlin/JDK 和 `kotlinx-coroutines-core`。Android、Compose、OkHttp、MLKit、媒体、平台存储与 Provider SDK 均禁止进入 Core。

## 文件变化

新增：
- `core/build.gradle.kts`
- `core/README.md`
- `core/src/main/kotlin/com/riftlab/app/data/Models.kt`
- `core/src/main/kotlin/com/riftlab/app/data/DataSources.kt`
- `core/src/main/kotlin/com/riftlab/app/data/ComprehensiveData.kt`
- `core/src/main/kotlin/com/riftlab/app/data/TournamentGovernance.kt`
- `core/src/main/kotlin/com/riftlab/app/data/LocalLiveInsightEngine.kt`
- `docs/ENGINEERING_CONSTITUTION.md`
- `docs/ARCHITECTURE.md`
- `docs/IMPLEMENTATION_STATUS.md`
- 本记录

修改：
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`

删除（内容迁移至 Core，非功能删除）：
- `app/src/main/java/com/riftlab/app/data/Models.kt`
- `app/src/main/java/com/riftlab/app/data/DataSources.kt`
- `app/src/main/java/com/riftlab/app/data/ComprehensiveData.kt`
- `app/src/main/java/com/riftlab/app/data/TournamentGovernance.kt`
- `app/src/main/java/com/riftlab/app/data/LocalLiveInsightEngine.kt`

## 行为/API/Schema/UI 影响

- 业务行为：无计划变化。
- Kotlin 包名：不变。
- 领域 Public API：不变。
- JSON/数据 Schema：不变。
- UI：不变。
- Android applicationId/version：不变。
- 构建结构：从单 `:app` 模块变为 `:core` + `:app`。

## 测试与真实结果

- Rlftlab → Laner 全仓 tree SHA：PASS，`20ee8cbb258dd9613e1476c30c9e8cd53b633172 == 20ee8cbb258dd9613e1476c30c9e8cd53b633172`。
- Core/Android 编译：PENDING，本记录随实现提交进入 CI 后更新状态以新增记录为准，不伪造 PASS。

## 风险与回滚

主要风险是 Gradle/Kotlin 跨模块可见性和隐藏的同模块 `internal` 依赖。本阶段只移动 public 契约，身份 Policy 的 `internal` 迁移留到下一独立批次。

回滚节点：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`。

## 合规结论

当前为 TESTING。已满足范围控制、精确基线、Core 平台隔离设计和开发留档要求；需等待编译/测试结果后再进入下一批。
