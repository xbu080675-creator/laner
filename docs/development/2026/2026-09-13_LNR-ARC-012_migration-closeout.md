# LNR-ARC-012 / 迁移架构总收口

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS
- 验证候选：`863e0add003800a9c048a088b23357cf215e6afa`
- Compile Diagnostics：run `34745478294`

## 目标

完成 Rlftlab -> Laner 架构迁移的最后收口，不新增任何产品功能，不改变 UI/交互/数据语义。

## 变更

1. Gradle `rootProject.name` 从 `RiftLab` 切换为 `Laner`；保留 `applicationId=com.riftlab.app`、包名、版本号和用户可见产品身份。
2. 将 17 个历史一次性 `devXX-*apply*`、`*fix*`、`migrate-*` workflow 原 blob 迁入 `.github/workflow-archive/legacy-one-shot/`，从 GitHub Actions active 目录退出。
3. 保留 Android build、Compile Diagnostics、OTA/selftest、赛事/战队/首发/镜像等持续运行 workflow。
4. 更新架构与实现状态文档；仓库 README 标明 Laner 为迁移后工程，RiftLab 应用兼容身份继续保留。

## 不变量

- 不新增业务功能。
- 不改 UI/布局/交互。
- 不改赛事规则、Provider 语义、排序、过滤或 fallback。
- 不改应用安装身份与版本。
- 不删除历史 workflow 证据，只取消其 active 执行资格。

## 实际验收

GitHub Actions run `34745478294` 已实际完成并通过：
- `tools/check_core_boundary.py`：PASS；
- `tools/check_repository_links.py`：PASS；
- `:core:test`：PASS；
- `:app:assembleDebug`：PASS；
- Compile Diagnostics job：PASS。

本记录的 PASS 仅代表 ARC-012 候选树已通过完整构建。随后仍需将该验证树与接管瞬间 `main` 的最新 `data/` tree 合并并完成主线构建验收。
