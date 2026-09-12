# Laner Testing Policy

## 原则

测试是交付证据，不是装饰。任何“已支持”“已修复”“已完成”必须能映射到实际执行过的测试。

## 每个任务最低报告

- 正向测试；
- 负向测试；
- 边界测试；
- 非目标对照；
- 历史 Bug 回归（如适用）；
- Integration / E2E / 实机（按影响范围）。

状态必须区分：

- `PASS`
- `FAIL`
- `NOT EXECUTED`
- `WAITING EXTERNAL TEST`
- `N/A`（必须说明原因）

## 架构测试

工程骨架建立后必须逐步自动检查：

- Core/Domain 不得依赖 UI/平台/网络/数据库具体实现；
- 模块依赖不得成环；
- Adapter 不得把外部类型泄漏到领域接口；
- UI 不得直接依赖具体数据源；
- 测试依赖不得泄漏到生产实现。

## 数据源 Contract Test

每个 Source Adapter 必须兑现统一数据契约。声明完整支持时不得通过修改 Contract Test 来适配 Provider 的缺陷；Provider 不足应声明降级并单独测试。

## Match State 测试

统一赛事状态能力建立后必须覆盖：

- 正常状态迁移；
- 重复事件；
- 乱序事件；
- 来源延迟；
- 来源冲突；
- 数据缺失；
- 重连/恢复；
- 系列赛与小局边界；
- 非目标赛事不被误更新。

## Bug 规则

任何关键 Bug 修复必须形成永久回归用例，并在 `TROUBLESHOOTING.md` 登记故障 ID。

## LNR-000

本任务仅新增 Markdown 工程治理文件，无业务代码、脚本或构建系统：

- 文档存在性：通过 GitHub 远端反查验证；
- 业务 Unit/Integration/E2E：`N/A`；
- 实机：`N/A`；
- 脚本 Parser：`N/A`。
