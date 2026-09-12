# Laner Testing Policy

## 原则
测试是交付证据，不是装饰。任何“已支持”“已修复”“已完成”必须能映射到实际执行过的测试。

每个任务最低报告：正向、负向、边界、非目标对照、历史 Bug 回归（如适用）、Integration/E2E/实机（按影响范围）。状态严格区分 `PASS / FAIL / NOT EXECUTED / WAITING EXTERNAL TEST / N/A`。

## 架构测试
- Core/Domain 不得依赖 UI/平台/网络/数据库具体实现；
- 模块依赖不得成环；
- Adapter 不得把外部类型泄漏到领域接口；
- UI 不得直接依赖具体数据源；
- 测试依赖不得泄漏到生产实现。

## Provider / Adapter 证据分层
1. **Core/Contract 自动测试**：标准化、仲裁、降级、错误码、不变量；
2. **在线 Integration**：真实 credential + 真实 Provider payload；
3. **Android 实机**：真实网络、时区、Compose、平台生命周期、图片/OCR。

第 1 层 PASS 不得冒充第 2/3 层 PASS。

## Bug 规则
任何关键 Bug 修复必须形成永久回归用例，并在 `TROUBLESHOOTING.md` 登记。

## 既有里程碑证据摘要
- LNR-010 run `34687580424`: Architecture/Core/Android PASS；online/device `WAITING EXTERNAL TEST`。
- LNR-011 run `34688581238`: Core PASS / Android compile FAIL；fix 后 `34688715420` PASS。
- LNR-012 final branch `34690520095` PASS；PR `34690577554` PASS。
- LNR-013 `34691364933` 暴露 stale-order bug；fix 后 `34691458209` PASS；final `34691766133` / PR `34691843981` PASS。
- LNR-014 `34692037250` 暴露 JUnit4 expression-body test signature；fix 后 `34692350405` / final `34692936906` PASS；Cito online DEFERRED。
- LNR-015 `34693494001` 与 `34695777894` 两次真实失败均已永久归档；final code/UI `34696081645` PASS；PR #7 `34696964926` PASS。

## LNR-016 — Testable Android Platform / Riot Global LIVE

### 自动化
- `34697683655`: Architecture/Core PASS，Android compile FAIL；Compose runtime-key 文案 `when` 语法错误，失败保留。
- fix `2cddeb872d7854829b54750db31f8739e37f0d2a`。
- `34697846793`: Architecture / Domain+Application / App unit / Android build PASS，首次产出可安装 APK。
- final feature head `9ddaab98ee430360865ca36484bc7fede42d7fe6` / run `34698280239`: Gate + APK upload 全 PASS。
- PR #8 run `34698393156`: Gate + APK upload PASS。
- merge `c7cb479175d6e64560d4478418a3ba73c36ddbce`。

### 未执行
BLG vs AL 真实 Riot target/EventDetails/LiveStats Android device 行为仍为 `WAITING EXTERNAL TEST`；fixture 不等于在线证据。

## LNR-017 — Starting Roster Vision / Staged Diagnostics

### Core / Application tests
`StartingRosterAssistServiceTest` 锁定：
- `candidateScore=35` 的官方图片公告仍可进入 OCR 检查，但绝不能自动确认首发；
- 五位置 OCR 完整仍只能是 `OCR_COMPLETE_UNVERIFIED`，并产生 `LNR-SRC-PRE-012`；
- normalized 正式 evidence 存在时跳过 OCR assist，交回既有 `PreMatchContextService` 权威校验；
- 过旧或无关队伍公告不能绑定当前比赛。

### Android Adapter / Parser tests
- schema-v3 `UNPARSED` 官方图片公告被保留给设备端 assist；
- 非 HTTPS 图片 URL 被丢弃；
- 双栏首发海报按 OCR x/y geometry 分离左右两队的 TOP/JUG/MID/BOT/SUP；
- role / league / common noise token 不得成为选手 ID。

### Failure history
run `34700136865`：
- Architecture: `PASS`；
- Domain/Application: `PASS`；
- App compile/test phase: `FAIL`；
- root cause A：Latin ML Kit `TextRecognizerOptions` import package 错误；
- root cause B：Compose 对跨模块 `inspection.error` nullable property 尝试 smart cast；
- fixes：`ad23d8cf493c773b5ff3d6dd6b07b3333a380171` + `a711dce3947b80405af741d69a8c342b191c121c`。

run `34700236839`：
- Architecture/Core: `PASS`；
- production app compile 已越过上面两个根因；
- App unit compile: `FAIL`；
- root cause：两个新 Android tests 使用 `kotlin.test`，但 `:app` test configuration 只声明 JUnit4；
- fixes：`5d763c34301858293ceef6b5257077dc9b866ce3` + `3879249053832c606f30a6122c31269db9327812` 改用 `org.junit.Test / Assert`。

run `34700457400`（code head `3879249053832c606f30a6122c31269db9327812`）：
- Architecture boundary: `PASS`；
- Domain/Application: `PASS`；
- Android Adapter unit tests: `PASS`；
- Android debug build: `PASS`；
- APK upload: `PASS`；
- artifact id `10300252185`，name `laner-debug-3879249053832c606f30a6122c31269db9327812`；
- artifact digest `sha256:91bc80ef7988ee7687b2a0bb5e024317fd702679b5daaa00af41b9edb580f241`。

### External acceptance
真实官方/俱乐部首发发布、真实图片下载、设备 ML Kit OCR、PRE 诊断阶段跳转与最终 normalized evidence 进入正式首发校验仍必须由今晚 Android 实机验证，状态 `WAITING EXTERNAL TEST`。

自动 Gate PASS 只能证明架构、编译、fixture/parser/orchestration；不能声明已经成功抓到今晚的真实首发。
