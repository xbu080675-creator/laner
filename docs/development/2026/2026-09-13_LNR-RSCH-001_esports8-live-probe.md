# LNR-RSCH-001 · Esports8 Live Probe

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：RESEARCH IN PROGRESS

## 需求来源与目标

用户提供公开比赛页 `https://m.esports8.com/lol/match/l70s9sjtm011ytx`，要求确认极速电竞当前网页真实使用的实时赛事接口、刷新机制与数据结构，并评估如何通过 Laner 既有 Port/Adapter 架构接入。

## Baseline

- 基线分支：`main`
- 基线提交：`b7edde5102cabfa443d7cc27f1758b9f2da9a9c7`
- 基线 tree：`e0dcb71d24a60f16891144902816a440465b50f1`
- 研究分支：`research/esports8-live-probe-2026-09-13`

## Constitution Preflight

已读取：

- `docs/ENGINEERING_CONSTITUTION.md`（1.0.0-laner.1）
- `docs/IMPLEMENTATION_STATUS.md`
- `core/.../DataSources.kt`
- `core/.../Models.kt`
- `app/.../CitoRealtimeLiveDataSource.kt`
- `app/.../LplOfficialLiveDataSource.kt`

结论：

- Core 已存在 `LiveMatchDataSource` Port 与标准 `LiveSnapshot`；
- 网络、JSON、浏览器/HTTP 属于 App/Adapter 或研究工具责任，不进入 Core；
- 当前任务只研究公开页面自然产生的公开网络请求，不尝试登录、鉴权绕过、暴力枚举或保护机制规避；
- 若未来接入，第三方源必须可替换、可降级，并继续接受 `LiveMatchTargetRegistry` / `MatchIdentityPolicy` 身份约束；
- `main` 不在本研究步骤中修改。

## 范围

### 本次包含

1. 公开比赛页普通 HTTP 获取；
2. Headless Chromium 打开公开页面并记录其自然产生的 request/response/WebSocket URL；
3. 下载页面自然引用的 Esports8 JS 静态资源并提取 endpoint 字符串；
4. 保存脱敏后的诊断证据；
5. 基于真实 schema 给出 Laner Adapter 接入方案。

### 明确不做

- 不绕过账号、付费、验证码、鉴权或访问控制；
- 不高频扫描、不枚举未知资源；
- 不修改 `main`；
- 未确认接口前不写生产 Adapter；
- 本研究不改变 UI/赛事语义。

## 设计

研究探针位于 `tools/esports8_live_probe.py`，通过独立 GitHub Actions workflow 在研究分支运行。URL query 中 token/key/sign/auth 等敏感键自动脱敏；响应体只保存 Esports8 自身公开流量，单响应上限 2 MB。

## 文件变化

- 新增 `.github/workflows/esports8-live-probe.yml`
- 新增 `tools/esports8_live_probe.py`
- 新增本记录

## 测试/证据

- Python 语法与真实外网探针：等待 GitHub Actions run `34748608957` 完成。
- 接口/schema 结论：PENDING。

## 风险与回滚

该 workflow 仅监听研究分支指定路径，不影响 `main` 长期 workflow。回滚可直接删除研究分支；生产树不受影响。

## Commit / 交付

- workflow commit：`18ede48c9f7ad3ed027e228043c6578e05a1b72f`
- probe script commit：`4f14d265e3dfd92d41e3868cd3c144abb0b6aa88`
- 最终研究提交、run 结果与接入决策待本任务完成后补充。

## 宪法合规结论

当前状态：`DELIVERY INCOMPLETE / RESEARCH RUNNING`。不得在真实 run 完成前宣称接口已确认或测试 PASS。
