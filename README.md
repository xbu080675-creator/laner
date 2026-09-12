# Laner

Laner 是电竞观赛助手的下一代重构工程。

## 核心原则

- **功能不改，底层重做。**
- **可以快，但不能以跳过质量门禁换速度。**
- **聊天不是工程记忆，仓库才是。**
- **每一次开发都必须留档。**
- **历史错误只作为证据与回归输入；修复旧错不能制造新错。**

## 当前状态

项目目前处于 `M1 / Feature Migration`。

LNR-010~020 已推进到不同程度的自动验证 / 外部实机待验收状态；完整事实以 `docs/IMPLEMENTATION_STATUS.md` 与 `docs/FEATURE_BASELINE.md` 为准，不在 README 复制一份会漂移的详细表格。

`INC-LNR-020-001` 已完成整改并关闭：PR #12 合入 `main@452ab8f5df3f4536c5c7f39c4024dc51ebc38084`，整改 exact-head run `34708127194` 与 post-merge main run `34708285172` 均全 Gate PASS。历史违宪事故记录和原失败记录继续保留，不反向改写。

Android 系统悬浮窗、触摸穿透、横竖屏 Profile 等真实设备行为仍保持 `WAITING EXTERNAL TEST`；CI/fixture 不冒充实机 PASS。

## 权威文档

- `docs/ENGINEERING_CONSTITUTION.md` — 工程唯一规则源
- `docs/PROJECT_SCOPE.md` — 项目范围与冻结边界
- `docs/ARCHITECTURE.md` — 目标架构
- `docs/DEVELOPMENT_PLAN.md` — 开发计划
- `docs/IMPLEMENTATION_STATUS.md` — 当前真实状态
- `docs/FEATURE_BASELINE.md` — 旧功能 1:1 迁移验收基线
- `docs/TESTING.md` — 测试策略
- `docs/TROUBLESHOOTING.md` — 故障知识库
- `docs/COMPATIBILITY.md` — 支持与兼容边界
- `docs/development/` — 每次开发不可变留档

## 下一步

第 2 块 `Tactical HUD + 赛中事件层` 已解除合规阻塞，可以在新的 Constitution Preflight 后开始。后续每个大版本继续执行“开发 → 宪法复查 → 事故/偏离留档 → 先整改 → 再进入下一版本”的固定节奏，直到第 8 块 Migration Audit / release closure。
