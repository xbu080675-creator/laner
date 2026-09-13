# LNR-013 / 主机式 Console UI 视觉系统 V1

- 日期：2026-09-14
- 执行者：OpenAI / ChatGPT
- 状态：WAITING EXTERNAL TEST
- 需求来源：用户明确要求将当前 UI 从偏 iOS / 手机卡片式语言改为主机系统风格
- 起始基线：`main@64b33c7c8dd3a985723d2eeb701b20da2cab6237`
- 工作分支：`ui/console-system-v1`
- 实现提交：`52814bf0c92c2f000ca58319dcf3fd5af08e4771`
- 合并 PR：`#26`
- main 合并提交：`8772aa5586d1785f66f5911cb34710bf90288a20`

## 目标与验收定义

目标不是给现有页面换一套颜色，而是先把共享视觉底座从普通移动 App 语言改成“主机系统 / 游戏设备 UI”语言，同时不碰赛事数据、业务状态与 Core/Adapter 边界。

本阶段通过条件：

1. 共享 UI primitive 统一改为低圆角、矩形、方向性系统线条与明显 Focus 状态；
2. UI 使用 dark-first 主机系统基调，并保留战队皮肤 Accent / Motif；
3. 键盘 / 手柄 Focus 必须具备可见反馈，触屏点击继续可用；
4. 不修改 Core、赛事数据、Provider、比赛状态语义、网络链路和持久化；
5. `:core:test` 与 `:app:assembleDebug` 实际通过；
6. main 固定签名 APK 实际构建并上传；
7. 真机视觉、触屏、不同屏幕尺寸及手柄/键盘 Focus 仍需人工联合验收，完成前不得标记 DONE。

## 实际变更

### `app/src/main/java/com/riftlab/app/ui/RiftVisualSystem.kt`

- `RiftHudPanel` 从大切角移动卡片改为 `3dp` 低圆角系统面板；
- 面板层级改为横向深度渐变、左侧 Focus Rail、顶部/底部结构线；
- 可交互面板新增 Focus 状态及轻量 `1.012x` 聚焦缩放；
- `RiftSectionLabel` 改为 `//` 系统标识 + 延伸导轨，不再使用独立胶囊/卡片感；
- `RiftStatusBadge` 改为低圆角状态标记并增加左侧状态轨；
- 新增 `RiftConsoleAmbientLayer`：弱扫描带、方向性 Accent Glow、顶部/底部系统轨；
- Ambient Layer 不读取、不推断、不修改任何业务状态。

### `app/src/main/java/com/riftlab/app/ui/RiftTheme.kt`

- Console V1 改为 dark-first；
- 战队 Accent / Secondary / Motif 仍由已有 TeamSkin 动态提供；
- 字体层级提高标题重量与 Label Tracking，降低“手机设置页”观感；
- 在 TeamSkin 背景之上叠加共享 Console Ambient Layer；
- 没有修改应用包名、版本号、比赛/赛前/赛后功能与数据语义。

## 明确不做

- 不改 `:core`；
- 不改赛事识别、场间识别、赛中状态机、选边/一抢、赛程、首发、Riot/官方源逻辑；
- 不改 Adapter / Provider / Repository 契约；
- 不删除原功能，不改变页面的数据来源；
- 本轮不进行大规模导航信息架构重写；先建立全局共享主机视觉底座，后续页面结构改造必须建立在该底座上。

## 真实测试证据

### 分支候选

Compile Diagnostics run `34789497885`：PASS。

实际执行并通过：
- Core boundary gate：PASS；
- Laner repository link gate：PASS；
- `:core:test`：PASS；
- `:app:assembleDebug`：PASS；
- diagnostics artifact upload：PASS。

### main 合并树

Compile Diagnostics run `34789638480`：PASS。

Android Build run `34789638341`：PASS。

实际通过：
- Global esports data plane：PASS；
- fixed-signed debug APK：PASS；
- fixed dev signature verification：PASS；
- artifact upload：PASS；
- artifact：`RiftLab-global-debug`；
- artifact id：`10328120735`；
- artifact digest：`sha256:bfa82641618a8ea8b9a41ead26b04c91e836ac995da36c142884ee1b75c123fc`。

当前工作环境无法进行 Android 真机渲染验收，因此没有把“能编译”冒充“视觉已验收”。

## 风险与回退

- V1 强制 dark-first，属于本次视觉方向的明确产品变更；若真机可读性或用户预期不合格，可恢复系统明暗跟随而不影响业务层。
- 新 Ambient Canvas 为纯绘制层，无点击、业务 IO、轮询或持久化；风险集中在视觉密度与 GPU 绘制开销，需真机观察。
- Focus 缩放仅作用于可交互共享面板；需在实体键盘/手柄环境确认焦点顺序与视觉反馈。
- 回退方式：revert PR `#26` / main merge commit `8772aa5586d1785f66f5911cb34710bf90288a20`，即可恢复本任务前共享视觉系统；不会触碰数据或 Core。

## 宪法复核

- Core / Adapter 边界：未改变，门禁 PASS；
- 业务真相来源：未移入 UI；
- 日志 / 错误码 / 数据 Schema：本任务无语义变化；
- 测试：真实 CI 已执行并通过，未宣称未执行测试；
- Git：独立分支、语义 Commit、PR、main 合并均已留档；
- 状态真实性：由于真机视觉与输入方式联合验收尚未执行，本任务保持 `WAITING EXTERNAL TEST`，不得标记 DONE。
