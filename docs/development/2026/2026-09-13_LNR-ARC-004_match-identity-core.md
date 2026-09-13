# LNR-ARC-004 / 比赛身份链迁入 Core

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：TESTING

## 目标

将比赛身份、目标注册与实时帧归属校验从 Android 应用模块迁入纯 JVM `:core`，让所有 Provider、archive 与 UI 继续通过同一套 fail-closed 规则判断“这帧数据是否属于当前比赛”。

## Baseline

- 分支：`refactor/riftlab-architecture`
- 前置提交：`70bb0490d2af0ea1cdc270307003695f0f987ae3`
- 精确 Rlftlab 迁移基线：`3f0b2d6d617b430a819f80eafe673ab44b0f268e`

## 迁移文件

从 `app/src/main/java/com/riftlab/app/data/` 迁入 `core/src/main/kotlin/com/riftlab/app/data/`：

- `MatchIdentityPolicy.kt`
- `LiveMatchTargetRegistry.kt`
- `LiveFrameIdentityGate.kt`

## 代码变化

算法正文、时间窗口、team alias 匹配、eventId/matchId 优先级、frame fail-closed 原则全部保持不变。

唯一 API 变化：三个原 `internal object` 改为 public `object`，原因是 Kotlin 的 `internal` 以 Gradle module 为边界；迁入 `:core` 后，`:app` 中的现有 Provider/Store 仍需要调用这些规则。该变化仅扩大编译可见性，不改变运行逻辑或数据契约。

## 行为影响

- UI：无。
- Provider 路由：无。
- 比赛身份判断：无。
- Schema：无。
- 网络：无。
- fallback：无。

## 测试

- Core boundary：PENDING。
- `:core:test`：PENDING。
- `:app:assembleDebug`：PENDING。

任何失败只按真实跨模块依赖修复，不放宽身份规则。

## 合规

本批仅进行既有纯业务规则迁移和跨模块可见性调整，无新增功能。
