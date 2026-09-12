# LNR-017 — Starting Roster Vision / Staged Diagnostics

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
- Branch: `feature/lnr-017-starting-roster-vision`
- Status: `TESTING`

## Request / Goal
在今晚 LPL 官方/俱乐部再次发布图片型首发阵容前交付可安装测试版，并使设备能明确区分：没有官宣、发现官宣、图片/OCR 失败、OCR 部分、OCR 完整但未验证、normalized 正式证据已到。速度要求不得降低工程质量；OCR/AI 永远不能自行升级为赛事事实。

## Constitution Preflight
`PASS`。读取工程宪法、main/branch、Development Plan、Implementation Status、Feature Baseline、PRE Starting Roster Ports/Service/UI 与旧 RiftLab `StartingRosterCenter / RosterVisionPipeline / RosterDeviceOcrResolver`。

ML Kit bundled Latin/Chinese/Japanese/Korean `16.0.1` 已按官方依赖面核对；选择 bundled model 避免今晚首次运行临时下载 OCR model。

## Scope / Non-goals
In scope：normalized announcements、Application assist stage/Port/orchestration、bundled multilingual OCR、双栏 geometry extraction、60s PRE polling + manual recheck、stable diagnostics、可见 UI、`2.0.0-dev.4 / versionCode 4` APK。

Non-goals：OCR 直接确认首发、Local AI runtime、direct Weibo scraping in Laner、LIVE Insight/HUD/OTA、替换现有 normalized collector。

## Architecture
```text
normalized collector / official social discovery
        ↓
NormalizedStartingRosterSource
 ├─ verified evidence → PreMatchContextService → official validation
 └─ raw announcement → StartingRosterAssistService
                       → StartingRosterVisionPort
                       → MlKitStartingRosterVisionSource
                       → RosterVisionInspection (DERIVED)
                       → PRE diagnostic panel
```

Core/Application 无 Android/ML Kit/OkHttp。UI 不直接读 Provider。OCR 只输出 candidate/diagnostic。

## Implemented
- `ProviderStartingRosterAnnouncement`：官方发布 discovery metadata，明确非首发事实；
- `ProviderStartingRosterSnapshot` 保留 announcements/diagnostics；
- `StartingRosterVisionPort / RosterVisionInspection / RosterAssistStage / StartingRosterAssistService`；
- normalized schema-v3 `announcements / imageUrls / parseStatus / candidateTeams / candidateScore`；
- Latin 常驻，LPL/LCP/PCS + 中文、LCK + 韩文、LJL + 日文；
- role-anchor + x/y geometry 双栏分队；
- esports handle whitelist-style shape + role/league/noise rejection；
- OCR success cache 6h、failure retry 10m、单轮最多 2 张图；
- PRE 60s refresh + `立即重查`；
- UI 显示 target、正式 evidence 数、announcement account/score/status、OCR engines/layout/roles/text preview、stable failure code。

## Stable diagnostics
- `LNR-SRC-PRE-010`: matching official announcement / OCR adapter unavailable；
- `LNR-SRC-PRE-011`: no OCR-capable image or partial/ambiguous OCR；
- `LNR-SRC-PRE-012`: complete five-role OCR candidate but still unverified；
- `LNR-SRC-PRE-013`: image download/OCR engine failure。

## Truth / Security Rules
- `candidateScore` 是 discovery confidence，不是 lineup truth；低分官方图片可检查但不能自动确认；
- OCR authority = `DERIVED`；
- confirmed official lineup 仍只能走 `PreMatchContextService` structured evidence validation；
- 不新增 social login/token；
- image URL 仅 HTTPS；
- bundled OCR 不依赖首次运行下载模型。

## Tests
Application：低分官方图片可 inspect 但不确认；完整 OCR 仍 unverified；正式 evidence 短路 assist；过旧/无关 announcement 不绑定。

Android Adapter：UNPARSED announcement transport；bad scheme drop；5×2 双栏 geometry；role/league noise rejection。

## Failure History
### `34700136865`
Architecture/Core `PASS`，App phase `FAIL`，APK skipped。

根因：
1. ML Kit Latin `TextRecognizerOptions` package 错；
2. Compose 对跨模块 public nullable `inspection.error` smart cast。

Fix：`ad23d8cf493c773b5ff3d6dd6b07b3333a380171` + `a711dce3947b80405af741d69a8c342b191c121c`。

### `34700236839`
Architecture/Core `PASS`，生产 App compile 已越过上述错误；Android unit test compile `FAIL`。

根因：两个新 `:app` tests 使用 `kotlin.test`，但项目只声明 JUnit4。

Fix：`5d763c34301858293ceef6b5257077dc9b866ce3` + `3879249053832c606f30a6122c31269db9327812`，统一 `org.junit.Test / Assert`，不添加冗余 test dependency。

### Code head verification
`3879249053832c606f30a6122c31269db9327812` / run `34700457400`：
- Architecture `PASS`；
- Domain/Application `PASS`；
- Android Adapter unit tests `PASS`；
- Android debug build `PASS`；
- APK upload `PASS`。

Artifact：
- id `10300252185`；
- name `laner-debug-3879249053832c606f30a6122c31269db9327812`；
- ZIP size `33,640,087` bytes；
- digest `sha256:91bc80ef7988ee7687b2a0bb5e024317fd702679b5daaa00af41b9edb580f241`；
- expires 2026-09-15。

## Docs / Status Sync
已同步：`DEVELOPMENT_PLAN / IMPLEMENTATION_STATUS / FEATURE_BASELINE / TESTING / TROUBLESHOOTING / CHANGELOG / app README / core application README`。

`core/domain/README.md`: `N/A`，本任务未改变 Domain model 或 Domain invariant；Roster Assist 位于 Application + Android Adapter。

## Performance / APK Impact
bundled OCR 使 APK 体积明显增加，换取今晚设备测试的确定性。轮询仅 PRE composition 存活时每 60s；OCR 有成功/失败缓存；每轮图像数有上限，不做高频暴力下载/OCR。

## Rollback
未合并前删除 feature branch；合并后回滚 LNR-017 commits。无 persistent schema migration。

## External Test
今晚真实官方发布、真实图片下载、Android ML Kit OCR、stage transition、最终 normalized evidence → official lineup validation 仍为 `WAITING EXTERNAL TEST`。CI/fixture PASS 不冒充真实首发抓取 PASS。

## Compliance Review
- Core platform boundary：PASS；
- UI→Provider：不存在；
- OCR authority：DERIVED only；
- low-confidence auto-confirm：禁止并有 regression；
- external URLs：HTTPS filter；
- secrets：无新增；
- performance：60s PRE-only + bounded images/cache；
- failures：已留档并有永久回归；
- code head Gate：PASS；
- final docs exact-head / PR Gate / merge：pending。

当前结论：`TESTING`。final exact-head + PR Gate 通过并 merge 后进入 `WAITING EXTERNAL TEST`。
