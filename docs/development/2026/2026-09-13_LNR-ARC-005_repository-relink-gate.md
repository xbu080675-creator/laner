# LNR-ARC-005 / 仓库身份重链接门禁

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：TESTING

## 目标

迁移后的 Laner 不能继续在运行时代码、数据同步脚本或 CI 发布链路中指向旧 `xbu080675-creator/Rlftlab`。本批先建立可重复扫描，随后逐项把真实运行时地址重链接到 `xbu080675-creator/laner`。

## 扫描范围

- `app/`
- `scripts/`
- `tools/`
- `.github/workflows/`

文档中的迁移基线说明不纳入阻断，因为它们需要保留旧仓库名用于审计。

## 检查项

阻断以下旧运行时地址：
- `github.com/xbu080675-creator/Rlftlab`
- `raw.githubusercontent.com/xbu080675-creator/Rlftlab`
- `cdn.jsdelivr.net/gh/xbu080675-creator/Rlftlab`
- `gitee.com/xiaobaiaaa1/Rlftlab`

## 行为范围

本门禁本身不改变产品功能，只把“仓库迁了但客户端/脚本仍访问老仓库”从隐性迁移错误变成 CI 硬失败。

## 测试

首次运行预期会列出现有旧链接并失败；逐项重链接完成后才允许 PASS。不得为了过门禁而把旧链接加入白名单。
