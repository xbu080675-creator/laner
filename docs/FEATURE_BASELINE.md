# Laner Feature Baseline / RiftLab 功能迁移基线

- Task: `LNR-001`
- Legacy source of truth: `xbu080675-creator/Rlftlab@0c5dcaad47853bedbf5f4abcff2ead41b81ffa43`
- Legacy app version from source: `1.0.0-dev.94 / versionCode 94`
- Migration rule: **功能完整迁移；旧实现仅作证据与行为基准，不机械复制。**
- Status: `DONE`

> 本文件是功能迁移验收清单。任何旧版用户可见能力、后台能力、数据能力、播放器、悬浮层、更新机制，只要当前 main 中仍然存在并具有产品价值，都必须在 Laner 中被迁移、替代或明确经过批准后废弃。未在本清单逐项关闭前，不得宣称“完整迁移完成”。

## 字段说明

- Phase: `PRE_MATCH / LIVE_MATCH / POST_MATCH / SHARED`
- Persona: `SPECTATOR / COACH_ANALYST / BOTH / SYSTEM`
- Source: `PRE_MATCH_SOURCE / LIVE_MATCH_SOURCE / POST_MATCH_SOURCE / GLOBAL_AI_ASSIST / PLATFORM`
- Migration: `TODO / IN PROGRESS / TESTING / WAITING EXTERNAL TEST / DONE / APPROVED_REPLACEMENT`

---

# A. PRE_MATCH / 赛前

| ID | 功能 | Persona | Source | Legacy evidence | Laner 归属 | Migration |
|---|---|---|---|---|---|---|
| PRE-001 | 全球/赛区赛事目录 | BOTH | PRE_MATCH_SOURCE | README / ScheduleCenter | Competition Catalog Query | WAITING EXTERNAL TEST |
| PRE-002 | 联赛 / 国际赛事双入口 | SPECTATOR | PRE_MATCH_SOURCE | dev.47 | Competition Navigation | TODO |
| PRE-003 | 赛区订阅与首页目标控制 | SPECTATOR | PRE_MATCH_SOURCE | dev.47-48 | User Preference + Competition Filter | TODO |
| PRE-004 | 赛程中心与本地时区显示 | BOTH | PRE_MATCH_SOURCE | ScheduleCenterUi / dev.47 | Schedule Query | WAITING EXTERNAL TEST |
| PRE-005 | 赛事倒计时与状态 | SPECTATOR | PRE_MATCH_SOURCE | RiftLabApp PreScreen | Match Lifecycle Query | TODO |
| PRE-006 | 首发阵容 | BOTH | PRE_MATCH_SOURCE | StartingRosterCenter / PreScreen | Roster Query | TODO |
| PRE-007 | 名单池与首发严格分离 | BOTH | PRE_MATCH_SOURCE | dev.71 / PreScreen | Roster Evidence Policy | TODO |
| PRE-008 | 替补 | BOTH | PRE_MATCH_SOURCE | README / roster model | Roster Query | TODO |
| PRE-009 | 教练组 / 管理人员 | BOTH | PRE_MATCH_SOURCE | GlobalTeamStaff / PreScreen | Staff Query | TODO |
| PRE-010 | Rank / 近期英雄池（有可靠源时） | BOTH | PRE_MATCH_SOURCE | dev.71 roadmap | Player Form Query | TODO |
| PRE-011 | 最近正式 Series 状态 | BOTH | PRE_MATCH_SOURCE | PreScreen | Form Query | TODO |
| PRE-012 | 近期 H2H | BOTH | PRE_MATCH_SOURCE | PreScreen | H2H Query | TODO |
| PRE-013 | 战队档案 / 运营关系 / 谱系 | BOTH | PRE_MATCH_SOURCE | TeamDetail / dev.30-31 | Global Identity Graph | TODO |
| PRE-014 | 人物履历 / 电竞图谱 | COACH_ANALYST | PRE_MATCH_SOURCE | EntityDetail / esports graph | Global Identity Graph | TODO |
| PRE-015 | 战队历史 Honors / Results 分离 | BOTH | PRE_MATCH_SOURCE | dev.31 | Team Archive | TODO |
| PRE-016 | Standings / 排名 | BOTH | PRE_MATCH_SOURCE | Standings client | Standings Query | TODO |
| PRE-017 | Championship Points 与联赛排名分离 | BOTH | PRE_MATCH_SOURCE | dev.70 | Qualification Domain | TODO |
| PRE-018 | 晋级路径 / Qualification Center | BOTH | PRE_MATCH_SOURCE | QualificationPathUi / dev.70 | Qualification Query | TODO |
| PRE-019 | 已锁定/可争夺/淘汰/待确认状态 | BOTH | PRE_MATCH_SOURCE | dev.70 | Qualification State | TODO |
| PRE-020 | 资格证据等级 OFFICIAL/PROVIDER/DERIVED/PENDING | COACH_ANALYST | PRE_MATCH_SOURCE | dev.70 | Evidence Model | TODO |
| PRE-021 | Tournament Edition 年度届次档案 | BOTH | PRE_MATCH_SOURCE | dev.69 | Competition Edition | TODO |
| PRE-022 | Patch / 规则 / 抽签 / 签位 / 赛制 | BOTH | PRE_MATCH_SOURCE | Tournament Research / Governance | Competition Research | TODO |
| PRE-023 | 赛前选边 / 当前小局选边 | BOTH | PRE_MATCH_SOURCE | SideSelectionPrePanel | Match Setup | TODO |
| PRE-024 | 内容覆盖度 / 缺失字段显式展示 | COACH_ANALYST | PRE_MATCH_SOURCE | ComprehensiveDataCoverage | Coverage Query | TODO |
| PRE-025 | 官方首发图片 / OCR / AI 辅助识别 | BOTH | GLOBAL_AI_ASSIST | RosterVisionPipeline | Roster Assist Pipeline | TODO |

## PRE 验收原则

- 不允许名单池顺序被伪装成官方首发。
- 不允许无可靠来源时填造 Rank、伤病、转会、首发变化。
- 所有数据必须带 provenance / authority / freshness。
- 用户必须能从“这场比赛”继续下钻到队伍、选手、赛事、资格路径与历史事实。
- `WAITING EXTERNAL TEST` 表示实现与自动化已具备，但真实 Provider/实机证据尚未完成；不得等价为 DONE。

---

# B. LIVE_MATCH / 赛中

| ID | 功能 | Persona | Source | Legacy evidence | Laner 归属 | Migration |
|---|---|---|---|---|---|---|
| LIVE-001 | EVENT_LIVE / GAME_LIVE / BETWEEN_GAMES 生命周期 | BOTH | LIVE_MATCH_SOURCE | README / dev.60 / 2026-09-12 legacy real-device intermission PASS | Match State Engine | TODO |
| LIVE-002 | 赛事开始 != 游戏开始 | BOTH | LIVE_MATCH_SOURCE | dev.60 / RiftLabApp / 2026-09-12 legacy real-device intermission PASS | Match State Engine | TODO |
| LIVE-003 | Riot/LPL/Cito 等多实时源 | SYSTEM | LIVE_MATCH_SOURCE | data providers | Source Orchestration | TODO |
| LIVE-004 | 来源优先级、降级、fallback | SYSTEM | LIVE_MATCH_SOURCE | MatchSessionStore/providers | Source Arbitration | TODO |
| LIVE-005 | 实时经济 | BOTH | LIVE_MATCH_SOURCE | LiveSnapshot UI | Live Domain | TODO |
| LIVE-006 | 实时击杀 | BOTH | LIVE_MATCH_SOURCE | LiveSnapshot UI | Live Domain | TODO |
| LIVE-007 | 防御塔 | BOTH | LIVE_MATCH_SOURCE | LiveSnapshot UI | Live Domain | TODO |
| LIVE-008 | 小龙 / 龙魂 / 远古龙 | BOTH | LIVE_MATCH_SOURCE | LiveSnapshot / roadmap | Objective Domain | TODO |
| LIVE-009 | 男爵 / Herald / Atakhan 等版本资源 | BOTH | LIVE_MATCH_SOURCE | dev.72 roadmap | Objective Domain | TODO |
| LIVE-010 | 选手等级 / CS / KDA | BOTH | LIVE_MATCH_SOURCE | Live player rows | Player Live State | TODO |
| LIVE-011 | 终局前装备/Item Spike | BOTH | LIVE_MATCH_SOURCE | dev.72 roadmap | Player Live State | TODO |
| LIVE-012 | BP / Draft 实时状态 | BOTH | LIVE_MATCH_SOURCE | OfficialDraftProvider | Draft Domain | TODO |
| LIVE-013 | 统一事件模型 | BOTH | LIVE_MATCH_SOURCE | dev.72 / RiftLabApp | Event Domain | TODO |
| LIVE-014 | Kill / MultiKill / TeamFightWindow | BOTH | LIVE_MATCH_SOURCE | Timeline model | Event Domain | TODO |
| LIVE-015 | GoldLeadChange | BOTH | LIVE_MATCH_SOURCE | Timeline | Event Domain | TODO |
| LIVE-016 | Pause / Resume（可确认时） | BOTH | LIVE_MATCH_SOURCE | dev.72 roadmap | Match State | TODO |
| LIVE-017 | 本地 Timeline 持续采集 | BOTH | LIVE_MATCH_SOURCE | MatchTimelineStore/Capture | Timeline Repository | TODO |
| LIVE-018 | 状态周期快照 + 关键事件额外落点 | COACH_ANALYST | LIVE_MATCH_SOURCE | dev.34 | Timeline Capture | TODO |
| LIVE-019 | Timeline 证据等级 / 不猜无法确认配对 | BOTH | LIVE_MATCH_SOURCE | dev.34 / RiftLabApp | Evidence Model | TODO |
| LIVE-020 | 本地局势解释 / Insight | SPECTATOR | GLOBAL_AI_ASSIST | LocalLiveInsightEngine | AI Assist | TODO |
| LIVE-021 | 本地 AI 模型运行时 | SYSTEM | GLOBAL_AI_ASSIST | LocalAiCore/LiteRT-LM | AI Runtime Adapter | TODO |
| LIVE-022 | GPU/OpenCL 优先 + CPU fallback | SYSTEM | GLOBAL_AI_ASSIST | dev.90 | AI Runtime Capability | TODO |
| LIVE-023 | AI 性能基准 COLD/WARM/MEDIAN/P90/THERMAL | COACH_ANALYST | GLOBAL_AI_ASSIST | dev.90 | AI Diagnostics | TODO |
| LIVE-024 | RiftScreen 悬浮副屏 | SPECTATOR | PLATFORM | overlay | Android Overlay Adapter | TODO |
| LIVE-025 | Draft HUD 全屏悬浮 | BOTH | PLATFORM | dev.62 | HUD Presentation | TODO |
| LIVE-026 | HUD Edit / Lock | BOTH | PLATFORM | dev.63 | HUD Layout | TODO |
| LIVE-027 | HUD 拖动 / Scale / Alpha / Visibility / Reset | BOTH | PLATFORM | dev.63 | HUD Layout | TODO |
| LIVE-028 | 横竖屏独立 HUD Profile | BOTH | PLATFORM | dev.63 | HUD Layout Persistence | TODO |
| LIVE-029 | LOCK 后触摸穿透 | SPECTATOR | PLATFORM | dev.63 | Android Overlay Adapter | TODO |
| LIVE-030 | Tactical HUD / 战术副屏 | COACH_ANALYST | PLATFORM | TacticalHudOverlay | HUD Presentation | TODO |
| LIVE-031 | Bilibili 观赛入口 | SPECTATOR | PLATFORM | StreamLauncher | Watch Hub | TODO |
| LIVE-032 | 虎牙观赛入口 | SPECTATOR | PLATFORM | StreamLauncher | Watch Hub | TODO |
| LIVE-033 | LoL Esports / YouTube / Twitch / X 观赛入口 | SPECTATOR | PLATFORM | dev.60 | Watch Hub | TODO |
| LIVE-034 | 直播入口与赛事数据完全解耦 | SYSTEM | PLATFORM | RiftLabApp | Watch Port | TODO |

## LIVE 验收原则

- 导播已明确展示且 Laner 无额外解释价值的信息，不应抢占 HUD 高优先级区域。
- 统一事件必须可追溯来源，不允许 Provider 自由文本直接成为赛事事实。
- 事件重复、乱序、重连必须幂等处理。
- 赛中 UI 必须自动跟随 Match State，不由页面自行猜状态。
- 旧版已实机通过的“场间未开局 vs 新局真实开局”是必须保留的行为基线，证据见 `docs/audits/2026-09-12_legacy_live_intermission_verification.md`。

---

# C. POST_MATCH / 赛后

| ID | 功能 | Persona | Source | Legacy evidence | Laner 归属 | Migration |
|---|---|---|---|---|---|---|
| POST-001 | Series 总比分 | BOTH | POST_MATCH_SOURCE | PostScreen / MatchDetail | Result Domain | TODO |
| POST-002 | G1/G2/... 历史小局 | BOTH | POST_MATCH_SOURCE | MatchDetail | Game Archive | TODO |
| POST-003 | 每局 BP | BOTH | POST_MATCH_SOURCE | dev.73 roadmap | Draft Archive | TODO |
| POST-004 | 每局阵容/英雄/召唤师技能 | BOTH | POST_MATCH_SOURCE | MatchDetail | Game Archive | TODO |
| POST-005 | K/D/A、CS、经济、伤害等统计 | BOTH | POST_MATCH_SOURCE | dev.73 roadmap | Player Stats | TODO |
| POST-006 | 终局装备 | BOTH | POST_MATCH_SOURCE | dev.73 roadmap | Player Stats | TODO |
| POST-007 | 龙/塔/男爵等资源结果 | BOTH | POST_MATCH_SOURCE | MatchDetail | Objective Archive | TODO |
| POST-008 | MVP / POG / Awards | BOTH | POST_MATCH_SOURCE | Award providers | Awards Query | TODO |
| POST-009 | Timeline 本地归档 | BOTH | POST_MATCH_SOURCE | MatchTimeline | Timeline Repository | TODO |
| POST-010 | Timeline 事件筛选 | COACH_ANALYST | POST_MATCH_SOURCE | dev.34 | Timeline Query | TODO |
| POST-011 | 团战窗口聚合 | COACH_ANALYST | POST_MATCH_SOURCE | dev.34 | Timeline Analysis | TODO |
| POST-012 | 历史赛后补全 | SYSTEM | POST_MATCH_SOURCE | OP.GG/Cito/Riot/LPL resolvers | Post Source Orchestration | TODO |
| POST-013 | Bilibili VOD | SPECTATOR | POST_MATCH_SOURCE | BilibiliVodRepository | Replay Domain | TODO |
| POST-014 | Riot / LoL Esports VOD | SPECTATOR | POST_MATCH_SOURCE | GlobalReplay | Replay Domain | TODO |
| POST-015 | YouTube VOD | SPECTATOR | POST_MATCH_SOURCE | GlobalReplay | Replay Domain | TODO |
| POST-016 | APP 内 Media3 播放 | SPECTATOR | PLATFORM | MatchVodUi | Media Adapter | TODO |
| POST-017 | APP 内 WebView 播放 | SPECTATOR | PLATFORM | MatchVodUi | Media Adapter | TODO |
| POST-018 | VOD 来源按赛区/赛事智能选择 | SYSTEM | POST_MATCH_SOURCE | dev.47-54 | Replay Resolver | TODO |
| POST-019 | Tournament Research / 年度档案 | BOTH | POST_MATCH_SOURCE | TournamentEditionArchiveUi | Research Query | TODO |
| POST-020 | Team Archive / 历史成绩 | BOTH | POST_MATCH_SOURCE | TeamDetailUi | Archive Query | TODO |
| POST-021 | 赛后 AI 总结 / 复盘辅助 | BOTH | GLOBAL_AI_ASSIST | product roadmap | AI Assist | TODO |
| POST-022 | 未来 VOD ↔ Timeline 对齐 | COACH_ANALYST | POST_MATCH_SOURCE | dev.74 roadmap | Replay Timeline Sync | TODO |

---

# D. SHARED / 全局共享能力

| ID | 功能 | Persona | Source | Legacy evidence | Laner 归属 | Migration |
|---|---|---|---|---|---|---|
| SH-001 | Global Competition Graph | SYSTEM | PRE_MATCH_SOURCE | dev.31 / ComprehensiveData | Domain Identity | IN PROGRESS |
| SH-002 | Team / Player / Competition 稳定 ID | SYSTEM | PRE_MATCH_SOURCE | dev.31 / dev.68 | Domain Identity | IN PROGRESS |
| SH-003 | Region 仅作维度，不作业务模块边界 | SYSTEM | ALL | Laner LNR-009 | Global Domain | IN PROGRESS |
| SH-004 | Source provenance | SYSTEM | ALL | dev.68 | Source Domain | IN PROGRESS |
| SH-005 | Authority | SYSTEM | ALL | dev.68 | Source Domain | IN PROGRESS |
| SH-006 | Freshness | SYSTEM | ALL | dev.68 | Source Domain | IN PROGRESS |
| SH-007 | Coverage 12 域 | COACH_ANALYST | ALL | dev.68 | Coverage Domain | TODO |
| SH-008 | 缓存 / Last-good | SYSTEM | ALL | multiple providers | Persistence Port | TODO |
| SH-009 | APK Seed fallback | SYSTEM | ALL | dev.30/71 | Seed Adapter | TODO |
| SH-010 | League subscription persistence | SPECTATOR | PLATFORM | dev.47 | Preference Port | TODO |
| SH-011 | Team Skin Registry | SPECTATOR | PLATFORM | dev.32-33 | Presentation Theme | TODO |
| SH-012 | 浅色 / 深色模式 | SPECTATOR | PLATFORM | dev.33 | Theme | TODO |
| SH-013 | 数据来源设置 / Provider diagnostics | COACH_ANALYST | PLATFORM | RealtimeSourceSettingsDialog | Diagnostics UI | TODO |
| SH-014 | 缓存设置 | COACH_ANALYST | PLATFORM | CacheSettingsPanel | Settings UI | TODO |
| SH-015 | 本地 AI 设置 | COACH_ANALYST | GLOBAL_AI_ASSIST | LocalAiSettingsPanel | AI Settings | TODO |
| SH-016 | APP 内 OTA 更新中心 | SYSTEM | PLATFORM | AppUpdateManager / UpdateCenterUi | Update Port/Adapter | TODO |
| SH-017 | GitHub canonical Release | SYSTEM | PLATFORM | dev.59-66 | Update Adapter | TODO |
| SH-018 | GitHub 直连 + GitHub-only 节点测速 | SYSTEM | PLATFORM | dev.65 | Update Transport | TODO |
| SH-019 | HTTP Range 断点续传 / 自动换线 | SYSTEM | PLATFORM | dev.59-65 | Update Transport | TODO |
| SH-020 | APK SHA-256 / 包名 / versionCode / 签名校验 | SYSTEM | PLATFORM | README | Update Security | TODO |
| SH-021 | 全球人员 / Staff 镜像 | SYSTEM | PRE_MATCH_SOURCE | GlobalTeamStaff | Provider Adapter | TODO |
| SH-022 | 全球 Awards 镜像 | SYSTEM | POST_MATCH_SOURCE | GlobalVerifiedAwards | Provider Adapter | TODO |
| SH-023 | International Event Mirror | SYSTEM | PRE_MATCH_SOURCE | InternationalEventMirrorProvider | Provider Adapter | TODO |
| SH-024 | Riot Persisted Gateway / LoL Esports | SYSTEM | ALL | LolEsportsApiClient | Provider Adapter | IN PROGRESS |
| SH-025 | Cito REST/WebSocket optional provider | SYSTEM | ALL | CitoDataPlane | Provider Adapter | TODO |
| SH-026 | Bilibili VOD resolver | SYSTEM | POST_MATCH_SOURCE | BilibiliVodRepository | Provider Adapter | TODO |
| SH-027 | OCR 多语言（中/日/韩） | SYSTEM | GLOBAL_AI_ASSIST | app dependencies | OCR Adapter | TODO |
| SH-028 | AI 输出 FACT_BACKED / INFERENCE / UNVERIFIED | BOTH | GLOBAL_AI_ASSIST | Laner LNR-008 | AI Evidence | IN PROGRESS |
| SH-029 | 错误/来源/降级状态可视化 | BOTH | ALL | old UI source labels | Diagnostics Contract | IN PROGRESS |
| SH-030 | 不制造假事实 / 缺失保持未知 | BOTH | ALL | legacy rules | Domain Invariant | IN PROGRESS |

---

# E. 旧路线中已定义、尚未完整落地但必须保留的产品方向

这些不计入“旧版已完整实现”的迁移完成率，但因为已是明确产品路线，Laner 不得因重构丢失：

- Sandbox Core / 从真实比赛或 Timeline 创建只读事实快照；
- 召唤师峡谷战术地图；
- BP / Draft Sandbox；
- 阵容与对位 Sandbox；
- 兵线、塔、资源状态；
- Timeline Branch / 多分支“What-if”；
- 打野动线、辅助游走、TP/回城窗口、资源交换、交叉地图、视野路径、团战角度、兵线代价标注；
- Explainable Evaluation Engine；
- Sandbox Compare；
- VOD ↔ Timeline 时间对齐。

这些能力进入 `FUTURE_PRODUCT_COMMITMENTS`，后续单独立项，不允许污染事实档案。

---

# F. 完整迁移 Definition of Done

只有同时满足以下条件，才允许宣称 RiftLab → Laner 功能完整迁移：

1. 本文件 A/B/C/D 中所有非废弃条目为 `DONE` 或经过用户批准的 `APPROVED_REPLACEMENT`；
2. 每项均有自动化测试或明确外部/实机验收证据；
3. PRE/LIVE/POST 三阶段行为与旧版关键行为对照通过；
4. 旧版 dev.94 当前源码存在的后台能力已映射到新模块；
5. Provider 故障、缓存、无网、数据缺失、乱序、重复帧、赛局切换有回归测试；
6. RiftScreen / 播放器 / OTA 等 Android 平台能力完成实机测试；
7. 不存在 UI 直接读取 Provider、Core 直接依赖 Android、赛区复制业务规则；
8. Migration Audit = PASS。
