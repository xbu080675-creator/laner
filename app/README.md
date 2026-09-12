# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay、Media、OTA、权限与具体 Adapter 组装。

## 输入
只消费 `:core:application` 公开 Query/Use Case/Port 契约与 `:core:domain` 展示所需只读模型。

## 输出
Android 用户界面与平台副作用。

## 依赖
允许依赖 `:core:application`、`:core:domain` 与 Android/Compose/网络等平台库。Core 禁止反向依赖本模块。

## Public API / Composition Root
- `MainActivity`
- `LanerAppGraph`
- `LanerRoot`
- `PreMatchScreen`
- `RiotGlobalPreMatchSource`（Adapter，实现 Core Port，不向 UI 暴露 Provider payload）

## PRE 数据链

```text
Riot LoL Esports
  → RiotGlobalPreMatchSource
  → GlobalPreMatchSourcePort
  → GlobalScheduleService
  → GlobalScheduleSnapshot
  → PreMatchScreen
```

UI 不允许直接调用 Riot/Cito/微博/OCR/AI。

## Credential
LoL Esports credential 不进入 Git，只允许：
- 环境变量 `LOL_ESPORTS_API_KEY`；
- Gradle Property `lolEsportsApiKey`。

缺失时是合法降级状态 `LNR-SRC-PRE-001`，不得硬编码 fallback key。

## 日志
- App：`[Laner:APP]`
- Source：`[Laner:SRC]`
- PRE：`[Laner:PRE]`
- Overlay/Update/AI 后续各自使用独立模块前缀。

## 失败
平台/Provider 能力不可用时必须明确降级，不得制造赛事事实。PRE 首批错误码：
- `LNR-SRC-PRE-001` credential 未配置；
- `LNR-SRC-PRE-002` global schedule 中心请求失败；
- `LNR-SRC-PRE-003` competition catalogue 降级；
- `LNR-SRC-PRE-004` schedule pagination 降级。

## 测试
- Core：`:core:domain:test :core:application:test`
- 编译：`:app:assembleDebug`
- LNR-010 自动化证据：GitHub Actions run `34687580424` PASS。
- LNR-010 真实在线拉取 + Android 实机展示：`WAITING EXTERNAL TEST`。

## 故障定位
启动/UI 问题先查 `MainActivity` → `LanerRoot` → `PreMatchScreen` → Application Query；Provider/网络问题查 `RiotGlobalPreMatchSource` 和 `[Laner:SRC]`。数据错误不得先在 UI 内补丁修正。
