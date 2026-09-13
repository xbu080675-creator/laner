# Laner 迁移后架构

## 基线

本架构以 `xbu080675-creator/Rlftlab` 的固定提交 `e46e4ea35ff8058ce90af1f79f15de31e14377dd` 为唯一功能基线。迁移基线提交 `3f0b2d6d617b430a819f80eafe673ab44b0f268e` 的根 tree 为 `20ee8cbb258dd9613e1476c30c9e8cd53b633172`，与源仓库固定提交逐字节一致。

旧 Laner 实现仅保存在 `archive/pre-riftlab-reset-2026-09-13` 作为迁移前审计点，不作为新实现的代码来源。

## 不变量

本轮只允许：

1. 代码与资源迁移；
2. 为建立 Core / Port / Adapter 边界所需的机械式重构与重新接线；
3. 对迁移或现存代码中可复现 bug 的修复；
4. 测试、日志、文档和构建门禁的补齐。

禁止新增产品功能、改变 UI/交互、改变既有业务规则、替换数据语义或增加未经需求批准的新 fallback。

## 目标依赖方向

```text
Android UI / Lifecycle / Overlay / Playback
                |
                v
        Adapter implementations
                |
                v
          Core Ports
                |
                v
      Domain models / policies
```

依赖必须单向。Core 不得引用 Android、AndroidX/Compose、OkHttp、Provider SDK、磁盘、`org.json` 或具体平台 API。

## 模块

### `:core`

职责：领域模型、标准化数据契约、Port、纯业务规则和无平台副作用的确定性算法。

已迁入的职责包括：
- 基础模型与数据源 Port：`Models.kt`、`DataSources.kt`、`ComprehensiveData.kt`；
- 赛事治理与纯规则：`TournamentGovernance.kt`、`OfficialHandbookGovernance2026.kt`；
- 本地赛中纯推导：`LocalLiveInsightEngine.kt`；
- 比赛身份规则：`MatchIdentityPolicy`、`LiveMatchTargetRegistry`、`LiveFrameIdentityGate`；
- RiftClaw/OpenClaw 纯协议与安全校验；
- 资格领域模型及 LPL/LCP/Worlds 静态规则；
- `TournamentEdition*` 年度赛事档案模型；
- `TournamentResearch*` 赛事研究模型/确定性推导与 schema；
- `CompletedSeriesSnapshot`、`MatchDetail*`、`TournamentEventHistorySnapshot` 等赛后/历史赛事领域模型。

包名在迁移兼容期继续保持 `com.riftlab.app.data`，避免把包名重写与架构重构混在同一变化里。

### `:app`

职责：Android 组合根、生命周期、Compose UI、Overlay、媒体播放、本地 AI 设备运行时，以及具体 Adapter/Provider/Store。

依赖 Android、网络、OCR、JSON、文件、Provider SDK、StateFlow/Mutex/协程运行时的实现继续留在 Adapter 侧，不得倒灌进 `:core`。

## 仓库身份

Gradle 工程名为 `Laner`。为保持升级、安装和既有用户侧行为兼容，本轮不改 `applicationId=com.riftlab.app`、包名、版本号或用户可见产品文案。

所有运行时 GitHub Raw/jsDelivr/Release/OTA 地址必须指向 `xbu080675-creator/laner`；该要求由 `tools/check_repository_links.py` 强制门禁。

## CI / Workflow 策略

`.github/workflows/` 只保留持续运行能力：Android 构建、Compile Diagnostics、OTA/selftest、赛事/战队/首发/镜像等长期同步。

历史 `devXX-*apply*`、`*fix*`、`migrate-*` 一次性补丁执行器原 blob 移入 `.github/workflow-archive/legacy-one-shot/`，保留审计证据但不再具有 GitHub Actions 执行资格。

## 门禁

- `tools/check_core_boundary.py`：阻止 Core 平台依赖泄漏。
- `tools/check_repository_links.py`：阻止旧 Rlftlab/Gitee 运行时仓库地址回流。
- `gradle :core:test :app:assembleDebug --stacktrace --no-daemon`：每批迁移后的真实编译验收。

## 最终接管原则

架构代码以通过完整门禁的 `refactor/riftlab-architecture` 为准；最终接管 `main` 时，不直接覆盖持续同步产生的数据，而是重新读取当时 `main` 的最新 `data/` tree 并嫁接到已验证架构 tree 后再提交。

## 验收

- 源功能基线可追溯且无旧 Laner 代码混入。
- `:core` 为纯 JVM 模块并通过边界门禁。
- Android/platform Provider 与 Store 留在 `:app` Adapter 侧。
- 旧仓库运行时链接已迁到 Laner。
- 历史一次性 workflow 不再 active。
- Android debug APK 能完整编译。
- 既有数据契约、UI、版本、包名与功能行为不因架构迁移而改变。
