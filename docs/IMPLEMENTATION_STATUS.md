# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Working branch: `feature/lnr-010-global-schedule`
- Project phase: `M1 / Feature Migration`
- Business implementation: `STARTED`
- Functional migration: `IN PROGRESS`
- Legacy baseline: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy source version: `1.0.0-dev.94 / versionCode 94`

## Task Status

| Task | Title | Status |
|---|---|---|
| LNR-000 | 工程立宪与基线初始化 | DONE |
| LNR-001 | 旧工程功能基线提取 | DONE |
| LNR-002 | 旧工程架构与技术债审计 | DONE |
| LNR-003 | 新架构冻结 | DONE |
| LNR-004 | 工程骨架与 CI Gate | DONE |
| LNR-005 | 三阶段一级架构轴确立 | DONE |
| LNR-006 | 用户角色 × 比赛阶段产品矩阵 | DONE |
| LNR-007 | 极简 × 酷炫体验北极星 | DONE |
| LNR-008 | 四类数据/API 源架构 | DONE |
| LNR-009 | 全球赛事统一管理架构 | DONE |
| LNR-010 | 全球赛事目录与赛程中心 | WAITING EXTERNAL TEST |
| LNR-011 | PRE Roster / Staff / Form / H2H | TODO |

## Current Truth

Laner 已进入 M1 真实功能迁移。当前已经不仅是工程骨架：**第一条 PRE_MATCH 真实数据链已经实现并通过自动化构建/单测，但真实在线 Provider 与实机展示尚待外部验收。**

当前已验证存在：

- `:core:domain` 纯 Kotlin Domain；
- `:core:application` 纯 Kotlin Application/Port；
- `:app` Android/Compose Composition Root；
- PRE/LIVE/POST 三阶段 Android 壳；
- Match Lifecycle；
- Global Competition IDs；
- Source Class / Provenance / Authority / Freshness / Revision；
- AI 与事实路径硬隔离；
- Standard Match Event 初版；
- Application FactArbiter；
- 稳定 ErrorCode / Diagnostics Port；
- Global Schedule Domain / Port / Service；
- Riot LoL Esports PRE Adapter；
- 全球赛事目录筛选 + 本地时区赛程 UI；
- Schedule `EVENT_LIVE` 与 Match `IN_GAME` 分离；
- PRE source 明确 `READY / DEGRADED / UNAVAILABLE`；
- credential 缺失时不制造数据，返回 `LNR-SRC-PRE-001`；
- Domain/Application Unit Tests；
- GitHub Actions Architecture Gate + Core Tests + Android Debug Compile。

旧 RiftLab 现场实测还确认了一条必须保留的正向行为：场间/新局开局识别可用。该证据已留档于 `docs/audits/2026-09-12_legacy_live_intermission_verification.md`，后续迁移 LIVE-001/LIVE-002 时必须做永久回归。

## Verification Evidence

### Migration Foundation

- 首轮 CI：FAIL —— CI 错配 Gradle 9.4.0，而 AGP 9.4.0 要求最低 Gradle 9.6.0；失败证据保留。
- 修复后 CI run `34686592578`：PASS。
  - Architecture boundary gate: PASS
  - Domain and application tests: PASS
  - Android debug compile: PASS

### LNR-010

GitHub Actions run `34687580424`：PASS。

- Architecture boundary gate：PASS；
- Domain and application tests：PASS；
- Android debug compile：PASS。

未执行：

- 带真实 LoL Esports credential 的在线 `getLeagues/getSchedule` 集成测试；
- Android 实机真实赛事目录/赛程展示验收。

原因：CI 不持有真实 Provider credential。以上两项状态为 `WAITING EXTERNAL TEST`，因此 LNR-010、PRE-001、PRE-004 暂不标 DONE。

当前 UI 不展示假比赛数据；旧功能只有在 `docs/FEATURE_BASELINE.md` 对应条目通过真实迁移验收后才可标记 DONE。

## Next

- 将 LNR-010 可编译、可测试实现合并到 `main`，保留 `WAITING EXTERNAL TEST` 认证状态；
- 立即启动 `LNR-011`：PRE Roster / Staff / Form / H2H；
- 后续获取真实 credential/实机环境后补 LNR-010 外部验收证据，不阻塞其它正交功能迁移。
