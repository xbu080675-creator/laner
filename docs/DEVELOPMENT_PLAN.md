# Laner Development Plan

## 任务编号
正式任务使用 `LNR-###`。

## M0 — Project / Product Foundation
`LNR-000~009`：`DONE`。

已冻结：工程宪法、旧功能基线、依赖 DAG、PRE/LIVE/POST 一级轴、Persona × Phase × Question、极简×酷炫 UX、四类 Source、全球赛事统一管理。

## M1 — Feature Migration
完整迁移状态以 `docs/FEATURE_BASELINE.md` 为唯一功能清单。

### LNR-010 — 全球赛事目录与赛程中心
状态：`WAITING EXTERNAL TEST`

### LNR-011 — PRE Roster / Staff / Form / H2H
状态：`WAITING EXTERNAL TEST`

### LNR-012 — Standings / Qualification / Tournament Edition
状态：`WAITING EXTERNAL TEST`

### LNR-013 — LIVE Match State / Arbitration / Unified Event / Timeline Core
状态：`DONE`

### LNR-014 — LIVE Android Persistence / Composition / Degraded UI
状态：`WAITING EXTERNAL TEST`

Cito online：`DEFERRED / WAITING EXTERNAL TEST`，不阻塞主线。

### LNR-015 — Global POST Result / Archive / Historical Timeline / Replay
状态：`WAITING EXTERNAL TEST`

PR #7 已合并。Global-first POST 自动化链、文档与 PR Gate 已通过；真实 Riot online / Android device 证据待补。

### LNR-016 — Android LIVE Field-Test Readiness / Platform Migration
状态：`TESTING`

当前优先切片用于今晚 BLG vs AL 真实数据验收，不以赶时间跳过 Gate。

已实现/正在收口：
- `RiotGlobalLiveSource`：Global Schedule → provider identity → EventDetails → LiveStats；
- 只有真实 LiveStats gameplay frame 才能证明 `IN_GAME`；event started 仍与 game started 分离；
- `LiveSnapshotService`：canonical Match/Game validation、来源降级、Timeline ingest；
- 实时 team gold/kills/towers/dragons/barons；
- player champion/level/KDA/CS/gold；
- LIVE 页面手动刷新、权威 lifecycle、来源/错误码、实时帧、经济差、选手状态与 Timeline；
- LoL Esports public web-client token 作为公开 client config fallback，受控 `LOL_ESPORTS_API_KEY` override 仍优先；
- persisted gateway 与 LiveStats transport 均携带 `x-api-key`；
- CI 成功后上传可安装 debug APK artifact；
- `2.0.0-dev.4 / versionCode 4` 为本轮真实联网测试构建。

自动证据：
- run `34697555762`：Architecture/Core PASS，Android compile FAIL；真实暴露 UI Composition 接线顺序遗漏；
- run `34697619275`：接线修复后全 Gate PASS；
- run `34697669902`：`LiveSnapshotService` canonical capture 回归全 PASS；
- run `34697849477`：四道 Gate + APK artifact PASS，但仓库 Secret 为空，因此该 dev.3 artifact 只可验证降级路径；
- dev.4 public-client fallback、LiveStats auth、Adapter fixtures 与 warning cleanup 的 final exact-head Gate：进行中。

今晚 field-test 验收：
1. PRE 能定位 BLG vs AL；
2. EVENT_LIVE 不提前变 IN_GAME；
3. 真 gameplay frame 出现后进入正确 Gx/IN_GAME；
4. 经济/击杀/塔/龙/男爵和玩家 KDA/CS/等级/英雄至少按上游实际字段显示；
5. 场间不误报下一局；
6. Provider/identity/window 失败有稳定错误码，不显示旧缓存为新数据；
7. 实机结果必须回填开发记录，才能把相关 `WAITING EXTERNAL TEST` 条目升级。

LNR-016 后续平台能力仍包括 Watch Hub、RiftScreen/HUD、Media3/WebView、OTA；今晚 field-test slice 合并后继续拆小步推进，不做风险巨大的 mega-commit。

### LNR-017 — Local AI / OCR / Roster Assist
状态：`TODO`

### LNR-018 — Compatibility / Full Regression / Migration Audit
状态：`TODO`

## 速度原则
允许并行读取/分析、同责任域成组实现、自动化减少重复；禁止跨层乱改、跳过测试/留档、把 fixture 当在线证据、把未完成条目写 DONE。
