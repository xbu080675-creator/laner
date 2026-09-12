# Legacy Architecture Audit / RiftLab 旧架构审计

- Task: `LNR-002`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Result: `PASS — migration risks identified`

## 1. 结论

旧 RiftLab 的产品能力已经很丰富，但工程结构已明显进入“大 app 模块 + 大 Store + 大 Provider + Compose 直接订阅全局状态”的阶段。此次 Laner 重构必须保留行为，不保留耦合方式。

旧版值得保留的核心思想：

- PRE / LIVE / POST 生命周期；
- 多 Provider + fallback；
- 缺失保持未知；
- provenance / authority / freshness；
- Timeline 事件溯源；
- Tournament → Series → Game → Team → Player 图；
- 直播入口与赛事数据解耦；
- 本地 AI 作为可选辅助；
- RiftScreen / Draft HUD；
- 全球赛事入口与赛区订阅。

旧版不得原样继承的结构性问题：

- 所有业务、Provider、UI、Android、更新、播放器集中在 `:app`；
- `MatchSessionStore` 等全局 object 聚合过多职责；
- UI 直接依赖 Store 和部分具体数据类；
- Provider 类型和业务模型分层不够清晰；
- LPL 历史专用实现与全球实现并存，存在赛区特例继续扩张风险；
- 本地归档、实时状态、赛后回填、来源诊断的边界容易互相渗透；
- Source arbitration 多处存在，未形成唯一仲裁核心；
- Android Overlay / Stream / OTA 虽已分包，但仍与 app 生命周期直接绑定；
- 旧工程长期通过连续补丁演进，历史行为正确但责任模块不够稳定。

## 2. 旧版模块观察

### UI

`app/src/main/java/com/riftlab/app/ui/` 当前包含：

- `RiftLabApp.kt`
- `ScheduleCenterUi.kt`
- `MatchDetailUi.kt`
- `MatchOperationsContent.kt`
- `MatchTimelineContent.kt`
- `MatchTimelineUi.kt`
- `MatchReplayContent.kt`
- `GlobalReplayContent.kt`
- `MatchVodUi.kt`
- `QualificationPathUi.kt`
- `TeamDetailUi.kt`
- `TournamentEditionArchiveUi.kt`
- `ComprehensiveDataCoverageUi.kt`
- `LeagueSubscriptionUi.kt`
- `LiveBroadcastHub.kt`
- `RealtimeSourceSettingsDialog.kt`
- `CacheSettingsPanel.kt`
- `LocalAiSettingsPanel.kt`
- `UpdateCenterUi.kt`
- Team Skin / Theme / Visual System 等。

风险：UI 大量直接读取 `MatchSessionStore` / `MatchTimelineStore` 等单例状态，页面与数据生命周期之间耦合较强。

### Data / Provider

旧 `data/` 同时承载：

- 领域模型；
- API Client；
- 实时 Provider；
- 赛后 Resolver；
- 全局实体档案；
- Timeline Store；
- Match Session；
- Cache；
- Coverage；
- Qualification；
- Awards；
- Roster；
- Source diagnostics。

风险：同一 package 中混合 Domain / Application / Adapter / Infrastructure。

### AI

旧 `ai/` 包含：

- LiteRT-LM runtime；
- model manager；
- local AI core；
- roster system resolver；
- roster vision pipeline；
- trend engine。

正向：本地 AI 已具备 fallback、benchmark、OCR 辅助的产品价值。

风险：AI 结果进入业务时必须在 Laner 强制区分 `FACT_BACKED / INFERENCE / UNVERIFIED`，且不得成为事实权威。

### Overlay

旧 `overlay/` 已包含：

- `RiftOverlayService`
- `RiftOverlayView`
- `DraftHudOverlayView`
- `DraftHudLayoutStore`
- `TacticalHudOverlayView`
- simulations。

正向：Android 悬浮层已经形成完整产品能力。

迁移要求：Overlay 只能消费 Application 提供的 HUD ViewState，不得读取 Provider 或 Domain 内部缓存。

### Stream / Replay

旧直播入口由 `StreamLauncher` 管理，回放由 Bilibili/Riot/YouTube/Media3/WebView 多链路完成。

迁移要求：

- Watch Link 是平台能力，不是比赛事实；
- Replay metadata 属于 POST_MATCH_SOURCE；
- Media3/WebView 属于 Android Adapter；
- Domain 不包含 Android `Intent`、`Uri`、WebView、Player 类型。

### OTA

旧 `AppUpdateManager` 已包含 GitHub canonical、节点测速、Range、断点续传、换线、SHA/包名/versionCode/签名验证。

迁移要求：OTA 单独成为 `UpdatePort + Android/GitHub Adapter`，不得进入赛事 Core。

## 3. 旧架构中最需要拆掉的耦合

### A. Global Store 耦合

旧路径近似：

```text
Provider
  ↓
MatchSessionStore / TimelineStore / global object
  ↓
Compose UI
  ↓
Overlay / Replay / Details
```

新路径固定为：

```text
Provider Adapter
  ↓
Port
  ↓
Application Use Case / Source Orchestrator
  ↓
Domain State + Repository Port
  ↓
Application Query
  ↓
UI / Overlay / Replay Adapter
```

### B. 赛区特例耦合

旧工程存在 LPL-specific Provider、Historical Resolver、Awards、Draft 等，同时又逐步新增 global Provider。

新架构禁止：

```text
if (region == LPL) { 一套业务 }
else if (region == LCK) { 另一套业务 }
```

允许：

```text
Global Contract
+ Provider Adapter
+ Competition Ruleset / Capability
```

### C. Source arbitration 分散

旧工程多个位置各自做 fallback、cache、source label。

Laner 必须只有一个 `SourceOrchestrator / FactArbiter` 负责：

- source class；
- provider health；
- authority；
- freshness；
- revision；
- provisional publish；
- conflict resolution；
- last-good fallback；
- provenance。

## 4. 数据迁移风险

- 旧缓存/Timeline/归档格式尚未冻结为 Laner Schema；
- 旧本地文件不能直接作为新 Domain Model；
- 需要后续逐类定义 importer/migration；
- 未读取旧 schema 的功能不得承诺无损迁移；
- 事实档案与 Sandbox 必须硬隔离。

## 5. Android 平台风险

必须后续实机验证：

- 悬浮窗权限；
- 前后台生命周期；
- 触摸穿透；
- 横竖屏 HUD profile；
- Media3；
- WebView/YouTube；
- 下载/安装 APK；
- 通知权限；
- LiteRT-LM GPU/OpenCL fallback。

这些不能被 JVM Unit Test 冒充为通过。

## 6. 推荐迁移顺序

```text
M0: Domain + Ports + architecture gates
M1: PRE global catalog / schedule / roster / standings / qualification
M2: LIVE state engine / source orchestration / event stream / timeline
M3: POST result / stats / replay / archive
M4: Android overlay / watch / player / OTA
M5: Local AI / OCR / inference
M6: compatibility import + full regression + migration audit
```

注意：这是工程迁移顺序，不改变产品一级轴；UI 始终保持 PRE/LIVE/POST。

## 7. 最终审计结论

旧 RiftLab 可作为可靠**行为基线**，但不适合作为新工程直接复制的**结构基线**。

Laner 必须保留能力、事实边界和已验证用户行为，同时重新建立清晰的 Core/Application/Port/Adapter DAG。
