# LNR-016 — Testable Android Platform / Riot LIVE Baseline / APK Delivery

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline: `main@939567a011a7aef27a6c17a2134a297c1151457b`
- Feature branch final head: `9ddaab98ee430360865ca36484bc7fede42d7fe6`
- PR: `#8`
- Merge commit: `c7cb479175d6e64560d4478418a3ba73c36ddbce`
- Status: `WAITING EXTERNAL TEST`

## Request / Goal
在 BLG vs AL 再次测试前交付可安装、可现场配置 Riot Key、可验证 Global Riot LIVE 生命周期的数据测试包；不等待 Cito。

## Constitution / Architecture
Preflight + post-change compliance：`PASS`。

LIVE flow：
`LiveMatchSourceQuery → Riot global schedule unique event → ProviderMatchIdentityRepository → getEventDetails → current provider game → LiveStats real frame probe → ProviderLiveObservation → LiveMatchStateService → UI`。

- Region 仍只是数据维度；BLG/AL 与 LCK fixture 共用同一 discovery/parser。
- provider raw event/match/game ID 不成为 canonical Domain ID。
- canonical GameId 仍由 `GameIdentity.canonical(matchId, gameNumber)` 生成。
- lifecycle authority 仍在 Application，不在 Adapter/UI。

## Delivered
- `RiotGlobalLiveStateSource`：全球目标定位、EventDetails、LiveStats frame probe；
- `LNR-SRC-LIVE-002~005` 稳定错误码；
- AppGraph 首次接入真实 Global Riot LIVE source，不再 `sources = emptyList()`；
- Android runtime Riot Key 临时输入；修改后立即重建 Composition graph；
- runtime Key 仅在当前进程内存，退出进程即消失；
- LIVE UI 直接显示 `SOURCE DIAGNOSTICS` 错误码/原因，实机不接 adb 也可定位数据链断点；
- CI 成功后上传 `app-debug.apk`；
- build `2.0.0-dev.3 / versionCode 3`。

## Security correction
早期实现曾短暂加入 app-private plaintext credential 文件。工程宪法复核后判定普通私有文件不是正式 Secret Store，因此在交付前删除文件实现与测试。最终测试包只保留内存临时 Key，不写磁盘、日志、Git、provenance。

## Failure history
### run `34697683655`
- Architecture PASS
- Domain/Application PASS
- Android compile FAIL
- APK upload skipped

根因：`RiotCredentialPanel.kt` 的 `when` 文案 `else` expression 后存在错误尾逗号，导致 Compose `Text` 收到非法/`Any` expression。

修复：`2cddeb872d7854829b54750db31f8739e37f0d2a`。

### first installable green build
run `34697846793` / head `2cddeb872d7854829b54750db31f8739e37f0d2a`：Architecture / Core / App unit / Android build PASS。

### final preferred build
head `9ddaab98ee430360865ca36484bc7fede42d7fe6`。
run `34698280239`：Architecture / Domain+Application / Android Adapter unit / Android debug build / APK upload 全 PASS。

Preferred artifact：
- id `10299341967`
- name `laner-debug-9ddaab98ee430360865ca36484bc7fede42d7fe6`
- artifact digest `sha256:9c9b883ef32bf08d0a8e841789807d0b421f32052032a21471d972ac3e1a81aa`
- extracted APK SHA-256 `e6dc6bdf5e802a1d006de8ac8e3b134b5779e436ef7f9e0b4990350540204bf7`

### PR Gate / merge
PR #8 run `34698393156`：Architecture / Domain+Application / Android Adapter unit / Android debug build / APK upload 全 PASS。

Merged main as `c7cb479175d6e64560d4478418a3ba73c36ddbce`。

## External test boundary
仍未宣称 BLG vs AL online PASS。真实设备需要确认：
1. Riot Key 能否建立 PRE schedule；
2. 是否正确选中 BLG vs AL；
3. getEventDetails 是否定位当前 Gx；
4. LiveStats 是否返回真实 frame；
5. Application lifecycle 是否得到 PRE_EVENT / EVENT_LIVE_PRE_GAME / IN_GAME / BETWEEN_GAMES 等正确状态。

失败时以 UI 的 `LNR-SRC-LIVE-002~005` 为排障入口。Cito 仍 DEFERRED。

## Rollback
完整回滚：revert merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`。runtime credential 无持久化迁移，无需数据清理。

## Compliance conclusion
Code / tests / security correction / docs / failure history / APK artifact / PR Gate / merge evidence 完整。自动交付完成，真实 Provider + Android device 认证等待 BLG vs AL 测试。

**Final: `WAITING EXTERNAL TEST`。**
