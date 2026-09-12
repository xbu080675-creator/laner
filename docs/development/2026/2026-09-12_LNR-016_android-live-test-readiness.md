# LNR-016 — Android LIVE Test Readiness / Riot Global LIVE Baseline

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@939567a011a7aef27a6c17a2134a297c1151457b`
- Branch: `feature/lnr-016-android-live-test-readiness`
- Status: `TESTING`

## Request / Goal
用户要求在保证质量的前提下尽快推进剩余阶段，并在今晚 BLG vs AL 再次现场测试“能不能真实拿到数据”。本任务优先解除实测阻断：真实 LIVE Source、真实 gameplay frame、前台可见、失败可诊断、可安装 APK。

## Constitution Preflight
PASS。读取 main、工程宪法、LNR-014/015 状态、LIVE Core/Application contract、Composition Root 与 Android CI。

## Critical Baseline Finding
LNR-014 收口时 `LiveMatchStateService` 的 `sources` 仍为空列表，因此即使 PRE/POST 可用，LIVE 页面也只能 `UNAVAILABLE`。这不是 UI 问题，而是真实 realtime Adapter 尚未接入。

## Scope
### In scope
- Global Riot LIVE state source；
- verified realtime `LiveGameSnapshot` source；
- Application snapshot validation/arbitration/Timeline ingestion；
- LIVE Compose realtime facts；
- public LoL Esports web-client token vs secret classification；
- CI APK artifact；
- BLG–AL manual-refresh field-test path。

### Non-goals
- Cito online（仍 DEFERRED）；
- 高频自动轮询（现场验证前避免请求风暴）；
- RiftScreen/HUD overlay；
- Media3/WebView；
- AI/OCR；
- 无证据事件推断。

## Implemented

### Application
Added `LiveSnapshotQuery / ProviderLiveSnapshot / LiveSnapshotSourcePort / LiveSnapshotService`.

Rules:
- Source raw frame cannot write Repository directly；
- Application revalidates canonical MatchId/GameId/gameNumber；
- only `LIVE_MATCH_SOURCE` accepted；
- wrong canonical identity → `LNR-APP-LIVE-002`；
- authority/timestamp/revision arbitration；
- valid source + failed source => DEGRADED but valid frame preserved；
- accepted snapshot enters canonical Timeline only through `LiveTimelineService.ingest()`。

### Riot Global LIVE Adapter
Added `RiotGlobalLiveSource`, implementing both `TargetAwareLiveStateSourcePort` and `LiveSnapshotSourcePort`.

Global flow:
`Global Schedule → provider identity mapping → EventDetails → LiveStats real frame → LiveMatchStateService / LiveSnapshotService → canonical Timeline → LIVE UI`.

No LPL business branch exists. Any Riot global schedule competition may use the same path.

State rules:
- Event started != game started；
- only a real LiveStats gameplay frame may assert `IN_GAME`；
- completed prior game + active event without a current frame can become BETWEEN_GAMES；
- completed series can become SERIES_COMPLETE；
- provider game id never becomes canonical GameId；`GameIdentity.canonical()` remains authoritative。

Snapshot fields currently normalized conservatively:
- team gold / kills / towers / dragons / barons；
- player champion / level / KDA / CS / gold；
- absent upstream fields stay null。

### Transport credential classification
Initial GitHub artifact run proved repository Secret `LOL_ESPORTS_API_KEY` was unset, so that APK could only exercise the explicit credential-missing degradation path.

Current correction:
- private/controlled `LOL_ESPORTS_API_KEY` or Gradle property remains highest-priority override；
- fallback is the public LoL Esports web-client x-api-key used by lolesports.com and publicly documented as the default shared client key；
- this public token is classified as PUBLIC CLIENT CONFIG, not user/developer secret；
- LiveStats HTTP transport now receives the same `x-api-key` through Adapter composition；
- Core never sees transport authentication。

### Android LIVE UI
LIVE screen now has explicit manual Refresh and renders:
- authoritative lifecycle/source/failure code；
- Gx + elapsed；
- team gold/kills/towers/dragons/barons；
- gold difference；
- player champion/level/KDA/CS/gold；
- canonical local Timeline snapshot/event counts。

Manual refresh is intentional for first field validation; no high-frequency polling before real-device evidence.

### CI / APK
Workflow now uploads `app-debug.apk` after all Gates succeed. Artifact retention: 7 days. Test build version bumped to `2.0.0-dev.4 / versionCode 4` after public-client fallback correction.

## Tests
Application regressions:
- verified live snapshot persists through canonical Timeline；
- wrong canonical GameId is rejected and never persisted；
- failed source does not erase valid snapshot from another source。

Adapter regressions:
- Riot live frame fixture preserves canonical Match/Game identity；
- team gold/kills/objectives and player champion/KDA/CS/gold normalize correctly；
- missing provider-team mapping returns no snapshot rather than inventing identity。

## Failure History

### run `34697555762`
- Architecture PASS；
- Core PASS；
- Android compile FAIL。

Cause: `LanerRoot` passed `liveSnapshotService` before `LiveMatchScreen` signature had been updated. This was a real Composition sequencing error, not hidden or reclassified.

Fix: complete `MainActivity → LanerRoot → LiveMatchScreen → LiveSnapshotService` wiring. Subsequent code head `a2fcae45d7300a22538f040c20f1ccda50de37e2` / run `34697619275` passed all existing Gates.

### Application snapshot contract verification
`933451f3bdf30da48827e9d7625308df581bcc91` / run `34697669902`: full PASS.

### APK artifact / missing repository secret evidence
`a4bff7e4ed5d983e3d2a13502a1a3ae8c7efd9d1` / run `34697849477`: Architecture/Core/App tests/Android compile/APK upload all PASS. Correct run logs show `LOL_ESPORTS_API_KEY` empty, therefore that artifact is not accepted as real-data field-test evidence.

## Security
- no user/private Riot key committed；
- optional secret override is not logged；
- shared public web-client token is explicitly classified as public client configuration, not a secret；
- provider identity remains outside Domain primary keys；
- no raw payload or credential written to Timeline/archive/logs。

## Performance
- first field-test mode is manual refresh；
- LiveStats search probes bounded recent 10-second windows；
- no background high-frequency scan；
- canonical Timeline dedup/persistence reuses existing LNR-013/014 machinery。

## Known External Test
- BLG vs AL real-device online field test still required；
- actual Riot feed delay/window shape must be observed before expanding polling/backoff；
- public client key rotation remains an external dependency and must surface as explicit Source failure, not silent stale data。

## Rollback
Revert LNR-016 branch/merge. Storage schema is unchanged; this task adds source/service/UI wiring and CI distribution only.

## Compliance
Core remains platform/provider-free; Application owns validation/arbitration; Adapter owns Riot transport; UI consumes Application only; missing facts remain unknown; CI failures are preserved; online/device evidence is not fabricated.
