# Laner Implementation Status

## Current Baseline

- Repository: `xbu080675-creator/laner`
- Working branch: `feature/migration-foundation`
- Project phase: `M0 / Migration Foundation COMPLETE`
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

## Current Truth

Laner 已进入真实代码开发。目前完成的是**可编译、可测试、受架构 Gate 保护的迁移地基**，不是完整功能迁移。

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
- Domain/Application Unit Tests；
- GitHub Actions Architecture Gate + Core Tests + Android Debug Compile。

## Verification Evidence

- 首轮 CI：FAIL —— CI 错配 Gradle 9.4.0，而 AGP 9.4.0 要求最低 Gradle 9.6.0；已保留失败证据。
- 修复后 CI run `34686592578`：PASS。
  - Architecture boundary gate: PASS
  - Domain and application tests: PASS
  - Android debug compile: PASS

当前 UI 不展示假比赛数据；旧功能只有在 `docs/FEATURE_BASELINE.md` 对应条目通过真实迁移验收后才可标记 DONE。

## Next

立即进入 `M1` 第一批真实功能迁移：Global Competition Catalog / Schedule / PRE data。
