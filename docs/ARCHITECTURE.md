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

依赖必须单向。Core 不得引用 Android、Compose、OkHttp、Provider SDK、磁盘或具体平台 API。

## 模块

### `:core`

职责：领域模型、标准化数据契约、Port、纯业务规则和无平台副作用的确定性算法。

第一批从原 `app/data` 原样迁入：
- `Models.kt`
- `DataSources.kt`
- `ComprehensiveData.kt`
- `TournamentGovernance.kt`
- `LocalLiveInsightEngine.kt`

包名在迁移兼容期保持 `com.riftlab.app.data`，避免把包名重写与架构重构混在同一变化里。

### `:app`

职责：Android 组合根、生命周期、Compose UI、Overlay、媒体播放、本地 AI 设备运行时，以及现阶段的具体数据 Adapter。

原 `app/data` 中依赖 Android/网络/OCR/存储/Provider 的实现继续留在 Adapter 侧，不得倒灌进 `:core`。

## 迁移策略

采用可编译的渐进抽取：每一批只移动一个职责集合，先恢复构建和验证，再继续下一批。每一批都必须留档；发现循环依赖或 Core 平台泄漏时立即停止扩展并修复。

## 验收

- 源功能基线可追溯且无旧 Laner 代码混入。
- `:core` 可作为纯 JVM 模块独立编译/测试。
- `:app` 只能通过 Core 的 public model/Port 使用领域核心。
- 迁移后 Android debug APK 可编译。
- 既有数据契约、UI、版本和功能行为不因架构迁移而改变。
