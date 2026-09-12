# LNR-017 — Starting Roster Vision / Staged Diagnostics

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@b4904cae43b9e296e6badf989ef1769263a455fa`
- Branch: `feature/lnr-017-starting-roster-vision`
- Status: `TESTING`

## Request / Goal
在今晚 LPL 官方/俱乐部再次发布首发阵容图片前，交付可安装测试版，使用户能够明确区分：

1. 没发现匹配官宣；
2. 已发现官宣但无可 OCR 图片；
3. 图片下载/OCR 失败；
4. OCR 部分识别；
5. OCR 已形成完整五位置候选但仍未验证；
6. normalized 正式证据已经到达，可进入既有 `PreMatchContextService` 权威校验。

速度要求不得降低工程质量。OCR/AI 不得自行升级为赛事事实。

## Constitution Preflight
`PASS`。

读取：工程宪法、当前 main/branch、Development Plan、Implementation Status、Feature Baseline、现有 PRE Starting Roster Ports/Service/UI、旧 RiftLab `StartingRosterCenter / RosterVisionPipeline / RosterDeviceOcrResolver`。

官方依赖资料已核对：Android ML Kit bundled text recognition，Latin/Chinese/Japanese/Korean `16.0.1`；Laner minSdk 28 满足要求。选择 bundled model，避免今晚首次运行依赖 Play Services 临时下载 OCR 模型。

## Scope
### In scope
- normalized feed `announcements` transport；
- Application roster-assist stage model / Port / orchestration；
- Android bundled multilingual OCR；
- spatial two-column roster candidate extraction；
- low-frequency 60s PRE polling + manual recheck；
- stable PRE diagnostic codes；
- UI stage visibility；
- test APK `2.0.0-dev.4 / versionCode 4`。

### Non-goals
- OCR candidate直接成为 `OfficialStartingRoster`；
- AICore/Gemini Nano/local vision model；
- direct Weibo scraping inside Laner；
- LIVE Insight / HUD / OTA；
- replacing normalized collector.

## Architecture

```text
Legacy normalized collector / official social discovery
                ↓
NormalizedStartingRosterSource
  ├─ verified evidence ───────────────→ existing PreMatchContextService
  └─ raw announcements
                ↓
      StartingRosterAssistService
                ↓
       StartingRosterVisionPort
                ↓
   MlKitStartingRosterVisionSource
                ↓
    RosterVisionInspection (DERIVED)
                ↓
  PRE diagnostic panel / no fact promotion
```

Domain/Application remain free of Android/ML Kit/OkHttp. OCR Adapter returns candidate/diagnostic data only.

## Implemented
- `ProviderStartingRosterAnnouncement` added as discovery metadata, explicitly non-authoritative.
- `ProviderStartingRosterSnapshot` preserves announcements/diagnostics.
- `StartingRosterVisionPort`, `RosterVisionInspection`, `RosterAssistStage`, `StartingRosterAssistService`.
- normalized source now parses schema-v3 `announcements / imageUrls / parseStatus / candidateTeams / candidateScore` instead of dropping them.
- bundled ML Kit Latin always runs; LPL/LCP/PCS adds Chinese, LCK adds Korean, LJL adds Japanese.
- spatial role-anchor parser separates two-column posters by x/y geometry.
- player IDs constrained to Latin-like esports handles; role/league/noise tokens rejected.
- device OCR success cache 6h, failure retry 10m; max 2 images per matched announcement per pass.
- PRE panel polls every 60s only while PRE composition exists and supports manual `立即重查`.
- UI displays target, normalized evidence count, official announcement account/score/parse status, OCR engines/layout/role coverage, and stable diagnostic code.

## Stable diagnostics
- `LNR-SRC-PRE-010`: matching official announcement discovered but OCR adapter unavailable.
- `LNR-SRC-PRE-011`: no OCR-capable image or OCR incomplete/ambiguous.
- `LNR-SRC-PRE-012`: five-role OCR candidate exists but remains unverified pending normalized matchup/date evidence.
- `LNR-SRC-PRE-013`: image download/OCR engine failure.

## Safety / Truth Rules
- `candidateScore` is discovery confidence, not lineup truth. A low-score official image announcement may be inspected but cannot be auto-confirmed.
- OCR authority is `DERIVED`.
- Existing `PreMatchContextService` remains the only path that can create confirmed official starting roster state from structured evidence.
- No direct social login/token is added to Laner.
- image URLs require HTTPS; malformed schemes are dropped by transport parser.

## Tests Added
### Application
- candidateScore=35 official image is still inspectable, but never auto-confirmed;
- complete OCR stays `OCR_COMPLETE_UNVERIFIED` with `LNR-SRC-PRE-012`;
- normalized evidence short-circuits OCR and is surfaced as formal evidence available;
- unrelated/old announcement does not bind to current target.

### Android adapter/parser
- schema-v3 unparsed announcement survives transport parsing;
- non-HTTPS image URL is discarded;
- two-column poster geometry separates both teams across TOP/JUG/MID/BOT/SUP;
- role/league noise tokens are not player IDs.

## Failure History
### Run `34700136865`
- Architecture boundary: PASS
- Domain/Application: PASS
- Android Adapter tests/compile phase: FAIL
- APK: skipped

Root causes at old head:
1. ML Kit Latin `TextRecognizerOptions` imported from wrong package; must use `com.google.mlkit.vision.text.latin.TextRecognizerOptions`.
2. Compose attempted smart cast of public cross-module nullable `inspection.error`; must copy to local value first.

Fixes:
- `ad23d8cf493c773b5ff3d6dd6b07b3333a380171` corrects ML Kit package.
- `a711dce3947b80405af741d69a8c342b191c121c` uses local nullable error value.

Latest exact-head verification: pending.

## Dependency / APK Impact
Adds bundled ML Kit OCR models. APK size will increase intentionally in exchange for deterministic offline-ready text-recognition availability during device tests. No OCR model network download is required for first recognition.

## Rollback
Before merge, delete feature branch. After merge, revert LNR-017 commits; no persistent schema migration is introduced.

## External Test
Tonight's actual official lineup publication and Android device behavior remain `WAITING EXTERNAL TEST` until user tests the APK. Fixture/CI PASS cannot replace that evidence.

## Compliance
Preflight PASS. Post-change review pending exact-head CI, docs/status sync, PR Gate and merge.
