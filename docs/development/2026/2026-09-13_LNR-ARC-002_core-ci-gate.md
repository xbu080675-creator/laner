# LNR-ARC-002 / Core 架构门禁与编译链路

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：TESTING

## 目标

为迁移后的 `:core` 建立自动化硬门禁，确保 Core 不因后续迁移重新引入 Android/UI/网络/Provider 依赖，并让 CI 实际编译 `:core` 与 `:app` 的接线结果。

## Baseline

- 分支：`refactor/riftlab-architecture`
- 前置提交：`e2209bd3e888ad9945381333c66c4d63ac49c935`
- 迁移基线：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`

## 范围

新增 `tools/check_core_boundary.py`，只检查 `core/src/**/*.kt` 的 import，不参与业务运行。

修改 `.github/workflows/compile-diagnostics.yml`：
- Core、根 Gradle 配置和边界脚本变化时触发；
- 先执行 Core 边界检查；
- 再执行 `:core:test` 与 `:app:assembleDebug`；
- 保留完整 compile log 与 exit code artifact。

## 明确不做

- 不修改业务源码。
- 不修改 UI、数据源、fallback 或运行时行为。
- 不增加生产依赖。

## 架构门禁

当前禁止 Core import：Android、AndroidX、OkHttp、Coil、MLKit、LiteRT Android、`org.json`。如未来确有领域层需要新增依赖，必须先按工程宪法审查，不得通过放宽脚本静默绕过。

## 测试

- Core boundary：PENDING（由本提交触发 CI）。
- `:core:test`：PENDING。
- `:app:assembleDebug`：PENDING。

失败必须保留并按真实错误修复，不得将未执行或失败写成 PASS。

## 回滚

回滚到 `e2209bd3e888ad9945381333c66c4d63ac49c935` 可撤销本次门禁变化，不影响已完成的 Core 文件迁移。

## 合规

仅新增构建/验证代码，无产品功能范围扩张。状态在 CI 验证结束前保持 TESTING。
