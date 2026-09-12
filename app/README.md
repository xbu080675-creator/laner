# `:app`

## 职责
Android 平台适配与 Composition Root。承载 Compose UI、Activity/Service、Overlay、Media、OTA、权限与具体 Adapter 组装。

## 输入
只消费 `:core:application` 公开 Query/Use Case/Port 契约与 `:core:domain` 展示所需只读模型。

## 输出
Android 用户界面与平台副作用。

## 依赖
允许依赖 `:core:application`、`:core:domain` 与 Android/Compose 平台库。Core 禁止反向依赖本模块。

## Public API
当前仅 `MainActivity` / Compose Root。后续平台 Adapter 必须通过 Core Port 接入。

## 日志
稳定前缀预留：`[Laner:APP]`。Overlay/Update/AI 等各自使用独立模块前缀。

## 失败
平台能力不可用时必须明确降级，不得制造赛事事实；权限拒绝、播放器失败、更新失败必须局部化。

## 测试
- 编译：`:app:assembleDebug`
- UI/实机测试将在对应功能迁移任务加入。

## 故障定位
启动/UI 问题先查 `MainActivity` → `LanerRoot` → Application Query；数据错误不得先在 UI 内补丁修正。
