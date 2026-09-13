# LNR-022 Watch Hub 产品一致性修复记录

> Incident: `INC-LNR-022-002`
> 状态：`TESTING`
> 规则：本记录创建后不回写历史结论；最终 Gate / merge / post-merge 证据写入独立 closeout 记录。

## 1. 任务 / 标题 / 最终状态

- Task: `LNR-022`
- 标题：恢复 RiftLab Watch Hub 需求资产并修复继承/新生缺陷
- 当前状态：`TESTING`
- 本记录结论：`DELIVERY INCOMPLETE`

## 2. baseline / start / end commit

- 最新主线基线：`09ea83708461f64754f4dcd21b473babf94ff839`
- 分支：`fix/lnr-022-watch-hub-parity`
- 开始 commit：`09ea83708461f64754f4dcd21b473babf94ff839`
- 本记录创建时分支 head：以本文件提交后的 branch head 为准；最终 head 另写 closeout。

## 3. Constitution Preflight

`PASS`

已重新读取/核对：
- `docs/ENGINEERING_CONSTITUTION.md`
- 最新 `main@09ea83708461f64754f4dcd21b473babf94ff839`
- `FEATURE_BASELINE / IMPLEMENTATION_STATUS / DEVELOPMENT_PLAN`
- 当前 `LNR-022` Watch Port / Android Adapter / UI / tests
- 旧 RiftLab `StreamLauncher.kt`
- 旧 RiftLab `LiveBroadcastHub.kt`
- 旧 RiftLab `BilibiliNativePlayback.kt / MatchVodUi.kt`

## 4. 涉及模块

- `:core:application` Watch Port contract
- `:app` Android Watch Adapter
- `:app` Presentation / Watch Hub UI
- `:app:test` Watch Hub regressions
- 文档 / Troubleshooting / status authority

## 5. 涉及宪法条款

- 需求资产完整迁移；不得借重构改变 UI / 操作习惯
- `Core <- Application <- App` 单向依赖
- Platform 行为留在 Adapter
- UI 只消费 canonical Application/domain 状态
- Region 只能作为 metadata/filter
- 禁止静默吞异常
- 稳定日志前缀 `[Laner:<Module>]`
- 稳定错误码 `LNR-MODULE-STAGE-NNN`
- 历史 bug 必须进入永久回归
- 自动化证据与真机/在线证据严格分离

## 6. 精确文件

已改 / 新增：
- `core/application/src/main/kotlin/com/laner/core/application/WatchPort.kt`
- `app/src/main/java/com/laner/app/watch/AndroidWatchCatalog.kt`
- `app/src/main/java/com/laner/app/watch/AndroidWatchPort.kt`
- `app/src/main/java/com/laner/app/ui/WatchHubUi.kt`
- `app/src/main/java/com/laner/app/ui/LiveMatchScreen.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- `app/src/test/java/com/laner/app/ui/WatchHubPresentationMapperTest.kt`
- `app/src/test/java/com/laner/app/watch/AndroidWatchCatalogTest.kt`
- 本记录及后续 authority/troubleshooting 文档

## 7. 原因 / 设计

### 发现 A：产品需求资产漂移

旧 RiftLab 的 Watch Hub 是全局左下角浮动入口：
- 小局进行中显示脉冲 `LIVE / 观赛`；
- 赛事进行、场间等状态显示 `ON AIR · 观赛`；
- 其他状态显示 `直播入口`；
- 小局 LIVE 时显示双方对阵；
- 点击后进入独立平台选择弹窗，分“中国大陆 / 海外 GLOBAL”；
- 六个平台入口保持 Bilibili / Huya / LoL Esports / YouTube / Twitch / X。

已合入的 LNR-022 把它重设计成 RiftScreen 控制卡内部按钮组。这改变了旧产品的入口位置、生命周期反馈、对阵信息和交互方式，违反“需求资产保留、只重铸架构”。

修复：恢复旧产品交互面，但状态来源改为新架构 canonical `ScheduledSeries + MatchLifecycleState`；不得恢复旧 Store 或 Provider 直连。

### 发现 B：权限回跳存在假成功

`resumePendingIfReady()` 在 180ms 后才真正执行 `open(spec)`，此前却直接返回 `OPENED`。若延迟 launch 失败，系统已经向上层声称成功。

修复：新增 `WatchLaunchStatus.RESUMING`，只在同步 `open(spec)` 成功后返回 `OPENED`；权限回跳只声明“正在恢复”。

### 发现 C：旧异常吞噬被继承

`ActivityNotFoundException / SecurityException` 原先直接转 `false`，无稳定日志和错误码；权限页 launch 也缺少安全处理。

修复：保留 fallback 行为，但增加 `[Laner:Watch]` 日志及：
- `LNR-WATCH-LAUNCH-001`
- `LNR-WATCH-LAUNCH-002`
- `LNR-WATCH-PERMISSION-001`
- `LNR-WATCH-PERMISSION-002`
- `LNR-WATCH-RESUME-001`

### POST 播放器资产边界

旧 RiftLab `MatchVodUi / BilibiliNativePlayback` 的 Media3/WebView/VOD 能力属于 POST replay 需求资产，不属于 LIVE Watch Hub handoff。`FEATURE_BASELINE` 仍保留 `POST-013~018`，其中 `POST-016 Media3` / `POST-017 WebView` 为 `TODO`。本修复不得把这些资产删除，也不得为了“补 Watch Hub”错误混入 LIVE 入口；后续按 POST 迁移块实现。

## 8. 测试

### Positive
- IN_GAME -> `LIVE` + matchup
- BETWEEN_GAMES -> `ON AIR` 且不能伪装为 game LIVE
- 六平台 catalog 与 legacy contract 一致
- 原生 App 提示 metadata 保留

### Negative
- null match 即使收到 IN_GAME 参数也必须中性显示，不伪造 LIVE
- UPCOMING + PRE_EVENT 不得显示 ON AIR
- UNKNOWN lifecycle + schedule EVENT_LIVE 只能表明赛事活动，不能表明小局 LIVE

### Boundary
- Region 仅用于弹窗展示分组
- Watch launch 不参与赛事事实仲裁
- Watch UI 不读取 raw provider payload

### Non-target
- RiftScreen/Draft/Tactical 既有能力不重写
- PRE/POST 事实链不改变
- POST Media3/WebView 本次不实现、不删除

### Regression
- 新增 `WatchHubPresentationMapperTest`
- 加强 `AndroidWatchCatalogTest`

### Integration / device
- 尚未执行 Android 真机六平台跳转、悬浮窗权限往返，因此 `LIVE-031~034` 必须继续 `WAITING EXTERNAL TEST`。

## 9. 脚本 / Gate

本记录创建时尚未完成 PR Gate；不得写 PASS。最终证据写入独立 closeout。

## 10. 日志 / 故障定位

- Tag: `[Laner:Watch]`
- Launch: `LNR-WATCH-LAUNCH-001/002`
- Permission: `LNR-WATCH-PERMISSION-001/002`
- Resume: `LNR-WATCH-RESUME-001`

## 11. 影响

- 恢复旧 Watch Hub 产品习惯；
- 保持全局 Provider/Region 新架构不变；
- 不增加赛事事实源依赖；
- 不改变六个平台目的地；
- 不把直播平台当作事实源。

## 12. 已知问题 / follow-up

- Android 真机 handoff / permission round-trip 仍未补证；
- POST Media3/WebView/VOD resolver 按 `POST-013~018` 后续迁移；
- `LIVE-031~034` 不得因自动化 Gate 变绿而虚报真机 DONE。

## 13. 回滚

回滚本修复分支/PR 即可恢复 `main@09ea837...` 行为；不涉及 schema / persistent data migration。

## 14. 状态同步

本记录创建时 authority docs 尚未完成最终同步；最终 PR 前必须同步 `FEATURE_BASELINE / IMPLEMENTATION_STATUS / DEVELOPMENT_PLAN / TROUBLESHOOTING` 或明确说明无需改状态值。

## 15. commit / push / PR / release

- Branch 已推：`fix/lnr-022-watch-hub-parity`
- PR：尚未创建
- Release：不涉及

## 16. Post-change Compliance Review

尚未执行最终 PCR。当前只完成 Preflight + 修复实现阶段，因此不得填写 PASS。

## 17. 最终结论

`DELIVERY INCOMPLETE`
