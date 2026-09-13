# LNR-ARC-007 / RiftClaw 纯协议与安全规则迁入 Core

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：BUILD TESTING

## 目标

继续执行 Laner 的 Core / Adapter 边界重构：只迁移纯 Kotlin/JDK 规则，不改变现有功能、UI、协议语义或联网实现。

## Baseline

- 前置提交：`6d81058651f0ee68e77ee293e0433bf553aae78a`
- `OpenClawSecurityPolicy.kt` 原位置：`app/src/main/java/com/riftlab/app/data/`
- `RiftClawContract.kt` 原位置：`app/src/main/java/com/riftlab/app/data/`

## 边界审计

`OpenClawSecurityPolicy` 仅依赖 JDK `java.net.URI` 进行 endpoint 解析，不发起网络请求。

`RiftClawContract` 与 `RiftClawInjectionGuard` 不依赖 Android、OkHttp、JSON、文件系统、Keystore、OCR 或具体 Provider。

真正具有平台/IO 副作用的 `RiftClawClient`、`RiftClawProbe` 继续留在 app/Adapter 侧。

## 迁移

迁入：
- `core/src/main/kotlin/com/riftlab/app/data/OpenClawSecurityPolicy.kt`
- `core/src/main/kotlin/com/riftlab/app/data/RiftClawContract.kt`

移除原 app 副本，避免同包同名实现双份存在。

为允许 app 模块通过 `implementation(project(":core"))` 使用这些规则，仅把顶层 `internal object` 改为模块外可见的 `object`；校验条件、正则、返回类型、错误 reason、默认 endpoint、协议版本与查询构造逻辑均保持不变。

## 行为影响

无功能变化：
- RiftClaw endpoint 不变。
- protocolVersion 不变。
- WEIBO_SEARCH 能力范围不变。
- 注入过滤规则不变。
- UI 不变。
- HTTP/JSON/协程实现不迁入 Core。

## 验收

- `tools/check_core_boundary.py` 必须 PASS。
- `tools/check_repository_links.py` 必须 PASS。
- `:core:compileKotlin` / `:core:test` 必须 PASS。
- `:app:assembleDebug` 必须 PASS，确认 app 到 Core 的二进制接线真实成立。

未取得 CI 结果前，本记录保持 `BUILD TESTING`。
