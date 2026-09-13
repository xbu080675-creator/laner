# Legacy one-shot workflow archive

这些文件来自 Rlftlab 精确迁移基线，曾用于一次性 apply/fix/migrate 操作。

ARC-012 将它们从 `.github/workflows/` 移到本目录，目的仅是取消 GitHub Actions 的主动执行资格，避免迁移后的 Laner 在普通 push/分支创建时再次运行历史补丁。文件内容与 blob SHA 保持不变，作为审计证据继续保留。

持续运行的 Android build、Compile Diagnostics、OTA/selftest、赛事/战队/首发/镜像同步 workflow 仍留在 `.github/workflows/`。

归档批次：LNR-ARC-012 / 2026-09-13。
