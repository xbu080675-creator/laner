# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay、Media、OTA、权限、具体 Source Adapter、本地持久化 Adapter 与设备 OCR。Core 禁止反向依赖本模块。

## 输入 / 输出
输入仅为 `:core:application` Port/Use Case 与 `:core:domain` 只读模型；输出为 Android UI 与平台副作用。Provider payload 不得直接暴露给 UI。

## Public API / Composition Root
- `MainActivity / LanerAppGraph / LanerRoot`
- `PreMatchScreen / StartingRosterAssistPanel`
- `LiveMatchScreen / PostMatchScreen`
- `RiotGlobalPreMatchSource / RiotGlobalLiveStateSource`
- `RiotTeamRosterSource / NormalizedStartingRosterSource / NormalizedTeamStaffSource`
- `MlKitStartingRosterVisionSource / RosterVisionPipeline`
- LIVE/POST/identity JSON repositories

## PRE / Starting Roster 数据链

```text
normalized collector
  ├─ verified evidence → NormalizedStartingRosterSource → PreMatchContextService → official validation
  └─ announcements    → NormalizedStartingRosterSource
                        → StartingRosterAssistService
                        → StartingRosterVisionPort
                        → MlKitStartingRosterVisionSource
                        → RosterVisionInspection (DERIVED)
                        → StartingRosterAssistPanel
```

关键规则：
- announcement 只是“发现了官方发布”的元数据，不是首发事实；
- OCR 结果永远是 `DERIVED / UNVERIFIED`；
- 完整五位置 OCR 也不得绕过 `PreMatchContextService` 的日期、对阵、赛事和五位置正式 evidence validation；
- `candidateScore` 只影响发现质量理解，不得变成 lineup truth；
- 图片 URL 只接受 HTTPS；
- PRE 面板每 60 秒低频重查，仅在 PRE composition 存活时运行，并提供手动“立即重查”。

## Device OCR
bundled ML Kit dependencies：Latin / Chinese / Japanese / Korean `16.0.1`。Latin 始终运行；LPL/LCP/PCS 加中文，LCK 加韩文，LJL 加日文。bundled 方案避免首次识别临时下载 Play Services OCR model。

`RosterCandidateExtractor` 使用 role anchor + x/y geometry 支持双栏首发图，并过滤 TOP/JUG/MID/BOT/SUP、联赛名、通用赛事词等噪声。成功 OCR 缓存 6h，失败缓存 10m 后允许重试；单轮最多处理匹配公告的前 2 张图。

## LIVE
`RiotGlobalLiveStateSource` 已作为第一条 global LIVE baseline 接入，不再是空 source list。目标通过 canonical teams + scheduled time 定位 provider event identity，raw IDs 只进入 identity mapping。LIVE UI 通过 Application 获得权威状态并显示 `LNR-SRC-LIVE-002~005` 诊断。

## Persistence
LIVE State/Timeline、POST Archive、Provider Identity 使用独立 schema-versioned JSON storage；canonical ID 生成稳定文件名；写入使用 sibling temp + atomic replace；corrupt/unsupported schema 显式失败，不静默清空。

## Credential
Riot credential 只允许环境变量/Gradle Property，或 LNR-016 测试版进程内 runtime key。runtime key 不落盘、不进日志/Git，进程退出即消失。TJStats/Cito 等不得硬编码 secret。

## 日志 / 错误码
模块日志前缀：`[Laner:APP] / [Laner:SRC] / [Laner:PRE] / [Laner:LIVE]`。

Starting Roster Assist：
- `LNR-SRC-PRE-010` announcement found / OCR adapter unavailable；
- `LNR-SRC-PRE-011` no OCR image or partial/ambiguous OCR；
- `LNR-SRC-PRE-012` complete five-role OCR candidate but still unverified；
- `LNR-SRC-PRE-013` image download/OCR engine failure。

LIVE：`LNR-SRC-LIVE-002~005`。详细历史故障见 `docs/TROUBLESHOOTING.md`。

## 测试
- Core：`:core:domain:test :core:application:test`
- Android Adapter：`:app:testDebugUnitTest`
- Android build：`:app:assembleDebug`
- CI：Architecture → Core → App unit → Android build → APK upload。

LNR-017 code head `3879249053832c606f30a6122c31269db9327812` / run `34700457400`：Architecture / Core / App unit / Android build / APK upload 全 PASS；artifact `10300252185`。

真实官方首发发布、图片下载和 Android OCR 仍为 `WAITING EXTERNAL TEST`，不得以 fixture/CI 冒充实机 PASS。

## 故障定位
PRE roster：`StartingRosterAssistPanel → StartingRosterAssistService → NormalizedStartingRosterSource / StartingRosterVisionPort → MlKitStartingRosterVisionSource`。

正式首发：`PreMatchScreen → PreMatchContextService → StartingRosterSourcePort`。

LIVE：`LiveMatchScreen → LiveMatchStateService/LiveTimelineService → Port → Repository/Adapter`。

数据错误不得先在 UI 补丁修正；transport/parser 回 Adapter，事实校验/仲裁回 Application/Domain。
