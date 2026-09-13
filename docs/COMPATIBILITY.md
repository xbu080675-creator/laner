# Laner Compatibility

## 当前状态

Laner 已进入 Android 运行时代码与 APK 构建阶段。当前 CI 只能证明自动构建与自动测试；具体 Android 版本、ROM、设备和真实赛事数据源仍需对应层级的外部验证，不能整体宣称为 `SUPPORTED`。

禁止把旧工程曾经运行过的平台、Android 版本、数据源或后端直接继承为 Laner 的正式支持结论。

## 支持等级

后续统一使用：

- `SUPPORTED`
- `DEGRADED`
- `FALLBACK`
- `UNSUPPORTED`
- `NOT VERIFIED`

## 支持声明规则

任何平台、系统版本、设备、数据源、API、数据库 Schema 或后端版本只有存在真实测试证据后才能升级为 `SUPPORTED`。

## 数据源兼容

赛事官网、微博、OCR、第三方 API、AI Provider 等必须分别记录：

- 接口/页面版本；
- 采集方式；
- 时效性；
- 可用字段；
- 限流/认证；
- 失败模式；
- Fallback；
- 最后验证日期。

## Laner 当前持久化兼容

- LIVE Match State 当前 schema 为 v1；
- LIVE Timeline 当前写入 schema 为 v2；
- Timeline v1 读取与 v1 → v2 write-forward 有 Adapter 自动回归；
- 首次覆盖 v1 前必须保留并校验 `.schema-v1.bak` 恢复副本；恢复点失败时不得覆盖；
- 旧 v1-only 构建不能假定读取 v2，只有存在已校验的 v1 恢复副本时才可恢复升级前状态；
- 新建的纯 v2 Timeline 不声明无损 downgrade。

## 旧工程数据

旧 RiftLab 历史数据不得默认直接导入 Laner。兼容导入由 LNR-018 / Migration Audit 单独验收，当前状态：`NOT VERIFIED`。
