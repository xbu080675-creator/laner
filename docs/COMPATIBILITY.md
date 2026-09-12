# Laner Compatibility

## 当前状态

Laner 尚未进入运行时代码阶段，因此当前**没有任何正式运行环境被声明为 SUPPORTED**。

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

## 旧工程数据

旧数据是否迁移、如何迁移、是否需要兼容旧 Schema，必须在旧工程审计和新数据模型冻结后决定。当前状态：`NOT VERIFIED`。
