# LNR-012 — Standings / Qualification / Tournament Edition

- Date: 2026-09-12
- Executor: OpenAI / ChatGPT
- Baseline branch: `main`
- Baseline commit: `64eb14fc7b847f2ec0f937b871cf18b9844b8c4b`
- Working branch: `feature/lnr-012-standings-qualification`
- Final status: `WAITING EXTERNAL TEST`

## 1. Request / Goal

迁移旧 RiftLab 的赛事排名、年度 Championship Points、晋级路径/证据等级和 Tournament Edition 年度档案，同时从底层纠正旧工程中容易混淆的概念边界。

必须保持：

- Standings 是单届/单阶段赛事排名；
- Championship Points 是跨赛段年度资格积分；
- Qualification 是到目标赛事的路径、机制与状态；
- Tournament Edition 是可长期保存的某年某届赛事身份；
- 四者可以关联，但任何一者都不得被另一者隐式替代。

## 2. Constitution Preflight

结果：`PASS`。

读取并遵循：

- `docs/ENGINEERING_CONSTITUTION.md`；
- `docs/ARCHITECTURE_FREEZE.md`；
- `docs/FEATURE_BASELINE.md`；
- `docs/LEGACY_ARCHITECTURE_AUDIT.md`；
- 旧 RiftLab 的 `LolEsportsStandingsClient.kt`、`QualificationCenter.kt`、`TournamentEditionArchive.kt`、`LplChampionshipPoints.kt`、`OfficialHandbookGovernance2026.kt`。

本任务只迁移 PRE_MATCH 赛事结构域，不进入 LIVE/POST。

## 3. Scope

### In scope

- pure Kotlin Standings / Championship Points / Qualification / Tournament Edition Domain；
- 四类独立 Application Port；
- `CompetitionStructureService`；
- Riot Tournament Edition / Standings Adapter；
- Tournament Edition 持久档案；
- Riot 2026 Handbook 的已核实资格机制 Source；
- PRE 页面赛事结构 Panel；
- 语义隔离回归测试；
- provenance / error / degraded / pending 行为。

### Explicit non-goals

- 不从 Standings 重算或猜测 Championship Points；
- 不把旧 RiftLab 2026-09-08 静态年度积分快照冒充 2026-09-12 当前值；
- 不从参赛名单顺序反推 Seed / Qualification Origin；
- 不在规则有冲突时自行补写结论；
- 不迁移 LIVE Match State / Timeline；
- 不迁移 POST Match。

## 4. Design Decisions

### D1 — 四套事实强类型分离

Domain 分成 `TournamentStandingsSnapshot`、`ChampionshipPointsSnapshot`、`QualificationSnapshot`、`TournamentEdition`。Qualification 输入使用 typed `QualificationInput`，避免任意整数 points 被误当年度积分。

### D2 — Provider raw tournament id 不进入 Domain identity

Riot raw tournament id 只保留在 Adapter 内临时映射。Domain 使用稳定 canonical `EditionId`，避免 Provider ID 成为跨源长期主键。

### D3 — Standings ordinal 允许并列

取消 ordinal 唯一约束；`1,2,2,4` 属于合法排名。仍要求一个 standings section 内同一 team 只出现一次。

### D4 — Riot Standings 不再 `points = wins`

旧 RiftLab 曾为展示把 standings points 映射成 wins。Laner 只有在 Riot payload 明确存在独立 `points` 字段时才发布 `STAGE_POINTS`；series wins 永远保持 series wins。

### D5 — Qualification 缺证据即 PENDING

没有可信 Qualification Source 时，Application 返回 `UNKNOWN + PENDING`，UI 显式说明不推断。

### D6 — 官方规则与队伍状态分离

`Official2026QualificationSource` 只发布 Riot 2026 Handbook 明确说明的机制。它不根据排名自动发布 `LOCKED / ELIMINATED`。LEC 2026 官方页面存在“前三资格卡”与“Playoffs 文本前二”冲突，因此保持 PENDING。

### D7 — 不迁移过时 Championship Points 快照

旧 RiftLab 的 LPL 年度积分静态快照更新到 `2026-09-08 赛后`。当前日期为 2026-09-12，赛事状态已继续变化，因此本轮拒绝将旧快照作为当前值。UI 会显示“暂无独立可信 Championship Points 来源”，而不是静默回退到旧数据。

### D8 — Tournament Edition Archive 只增不删

上游一次缺页不能删除历史届次。Application 将已有 archive 与本次 incoming editions 合并。

### D9 — schema_version + atomic replace

本地档案使用 `schema_version = 1`。写入先生成 sibling temporary file，再使用 `ATOMIC_MOVE + REPLACE_EXISTING` 替换；若文件系统不能提供原子替换，写入失败，不先删除 last-known-good archive。

## 5. Files Changed

### Added

- `core/domain/src/main/kotlin/com/laner/core/domain/StandingsQualification.kt`
- `core/domain/src/test/kotlin/com/laner/core/domain/StandingsQualificationTest.kt`
- `core/application/src/main/kotlin/com/laner/core/application/CompetitionStructurePort.kt`
- `core/application/src/main/kotlin/com/laner/core/application/CompetitionStructureService.kt`
- `core/application/src/test/kotlin/com/laner/core/application/CompetitionStructureServiceTest.kt`
- `app/src/main/java/com/laner/app/data/riot/RiotCompetitionStructureSource.kt`
- `app/src/main/java/com/laner/app/data/archive/JsonTournamentEditionArchiveRepository.kt`
- `app/src/main/java/com/laner/app/data/qualification/Official2026QualificationSource.kt`
- `app/src/main/java/com/laner/app/ui/CompetitionStructurePanel.kt`
- `docs/development/2026/2026-09-12_LNR-012_standings-qualification-editions.md`

### Modified

- `app/src/main/java/com/laner/app/LanerAppGraph.kt`
- `app/src/main/java/com/laner/app/MainActivity.kt`
- `app/src/main/java/com/laner/app/ui/LanerRoot.kt`
- `docs/CHANGELOG.md`
- `docs/DEVELOPMENT_PLAN.md`
- `docs/FEATURE_BASELINE.md`
- `docs/IMPLEMENTATION_STATUS.md`

### Deleted

- None.

## 6. Functional / Architecture / API / Schema / UI Changes

- 新增四类赛事结构 Domain 与 Ports；
- 新增 `CompetitionStructureService` 作为唯一 Application orchestration entry；
- Riot Adapter 只负责 Tournament Edition + Standings；
- Riot Handbook Seed 只负责已核实 Qualification mechanism；
- Championship Points Port 已建立，但当前没有接入过时/不可信总分源；
- 新增 `schema_version=1` 的 Tournament Edition device archive；
- PRE UI 新增独立 `CompetitionStructurePanel`，不直接调用 Riot 或文件系统；
- Structure Panel 与 Schedule/Match Context 相互独立，任一结构源降级不清空赛程。

## 7. Tests / Verification

### Automated regression semantics

已加入测试证明：

- Standings points 不会成为 Championship Points；
- standings 第一名不会自动成为 Qualification LOCKED；
- Participant Origin 不得携带虚构 Championship Points；
- archive merge 不因当前 API 缺页删除历史届次；
- stronger/fresher provenance 可替换同一 canonical edition；
- Qualification 无可信来源时保持 `UNKNOWN / PENDING`。

### CI evidence

- run `34689400211`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS（Core 语义第一轮）。
- run `34689473333`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS（Riot Tournament/Standings Adapter）。
- run `34690235942`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile **FAIL**。
  - 根因：`Files.move(...)` 返回 `Path`，使 `withContext` 推断的 `save()` 返回类型不再满足 `TournamentEditionArchiveRepository.save(): Unit`。
  - 修复：原子写入逻辑不变，仅在 block 尾部显式返回 `Unit`。
- branch final-head run `34690520095`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS。
- PR exact-head run `34690577554`: Architecture Gate PASS / Domain+Application Tests PASS / Android Compile PASS。

### Not executed / external evidence still required

- 带真实 `LOL_ESPORTS_API_KEY` 的 Riot `getTournamentsForLeague/getStandings` 在线数据验证；
- Android 实机 Edition Archive 写入/重启恢复；
- Android 实机 Competition Structure Panel 展示；
- 新鲜、独立、可核验的 Championship Points 总分 Source；
- 完整队伍级 Qualification LOCKED/CONTENDING/ELIMINATED 实时证据。

## 8. Risks / Compatibility / Security / Performance / Data

- Riot API credential 继续只通过环境/Gradle property 注入，不进入 Git。
- Tournament scan 对 leagues 使用并发上限 4，避免无界并发。
- Edition archive schema 目前仅 v1；未来 schema 变更必须显式 migration。
- Android 文件系统若拒绝 `ATOMIC_MOVE`，本轮设计选择报错并保留旧档，不静默降级为 delete+rename。
- 官方 qualification mechanism 是规则快照，不等同实时 team qualification state。

## 9. Known Issues / Blockers / Follow-ups

- Championship Points 当前缺少 2026-09-12 新鲜可信总分源；故 UI 正确显示缺失。
- PRE-019 team-level qualification state 仍需要正式赛果/席位证据继续填充。
- Riot standings ranking row 若未来出现完全空 team identity，应由 Adapter 丢弃；建议后续增加 provider payload contract test。
- LEC 2026 Worlds 第三席机制在当前 Riot Handbook 表述中存在冲突，保持 PENDING。

## 10. Rollback

- Revert PR #4 / merge commit `07d2b4a3d5094b81a72316bc8f0eba6486d4adb0` 即可移除本任务全部结构域功能。
- 本地 `tournament_editions_v1.json` 属缓存档案；回滚版本不依赖该文件。
- 不涉及远程数据库 migration。

## 11. Final Repository Evidence

- Branch: `feature/lnr-012-standings-qualification`
- Final branch head: `7680b8d640bc57006838bbcf2b5998166f82643a`
- PR: `#4`
- Merge commit: `07d2b4a3d5094b81a72316bc8f0eba6486d4adb0`
- Final branch CI: `34690520095` PASS
- Final PR CI: `34690577554` PASS

## 12. Status Sync

- `PRE-016 Standings`: `WAITING EXTERNAL TEST`
- `PRE-017 Championship Points 与 Standings 分离`: `DONE`
- `PRE-018 Qualification Center`: `WAITING EXTERNAL TEST`
- `PRE-019 Team Qualification State`: `IN PROGRESS`
- `PRE-020 Evidence Model`: `DONE`
- `PRE-021 Tournament Edition`: `WAITING EXTERNAL TEST`

LNR-012 task status 以“本轮架构/数据链已形成，但真实 Provider/队伍级状态仍需外部证据”为 `WAITING EXTERNAL TEST`；PRE-019 作为后续数据充实项保留 `IN PROGRESS`，不得被 task status 掩盖。

## 13. Post-change Compliance Review

- Core 无 Android/OkHttp/JSON/platform import：`PASS`（Architecture Gate）。
- UI 不直接调用 Riot / storage：`PASS`。
- Provider raw id 未进入 canonical Domain identity：`PASS`。
- Standings / Championship Points / Qualification / Edition 分离：`PASS`。
- 不迁移过时静态积分冒充当前值：`PASS`。
- Persistence 有 schema version：`PASS`。
- Persistence 写入要求原子替换：`PASS`（compile PASS；runtime real-device 仍待外部证据）。
- FAIL 历史已记录：`PASS`。
- Final exact-head CI：`PASS`。
- PR exact-head CI：`PASS`。
- Commit + Push + PR + Merge：`PASS`。

结论：`WAITING EXTERNAL TEST`。本任务工程闭环完成；真实 Provider/Android 实机与未获得的新鲜 Championship Points / team-level qualification facts 继续按功能条目跟踪，不冒充 DONE。
