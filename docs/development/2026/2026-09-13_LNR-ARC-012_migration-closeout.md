# LNR-ARC-012 / 迁移架构总收口

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：TESTING

## 目标

完成 Rlftlab -> Laner 架构迁移的最后收口，不新增任何产品功能，不改变 UI/交互/数据语义。

## 变更

1. Gradle `rootProject.name` 从 `RiftLab` 切换为 `Laner`；保留 `applicationId=com.riftlab.app`、包名、版本号和用户可见产品身份。
2. 将历史一次性 `devXX-*apply*`、`*fix*`、`migrate-*` workflow 原 blob 迁入 `.github/workflow-archive/legacy-one-shot/`，从 GitHub Actions active 目录退出。
3. 保留 Android build、Compile Diagnostics、OTA/selftest、赛事/战队/首发/镜像等持续运行 workflow。
4. 更新 `docs/ARCHITECTURE.md` 与 `docs/IMPLEMENTATION_STATUS.md`，记录已完成 Core/Adapter 边界与最终 main 接管规则。

## 不变量

- 不新增业务功能。
- 不改 UI/布局/交互。
- 不改赛事规则、Provider 语义、排序、过滤或 fallback。
- 不改应用安装身份与版本。
- 不删除历史 workflow 证据，只取消其 active 执行资格。

## 验收

候选提交必须通过：
- `tools/check_core_boundary.py`；
- `tools/check_repository_links.py`；
- `gradle :core:test :app:assembleDebug --stacktrace --no-daemon`。

在上述门禁实际通过前，本记录保持 `TESTING`，不得改写为 PASS。
