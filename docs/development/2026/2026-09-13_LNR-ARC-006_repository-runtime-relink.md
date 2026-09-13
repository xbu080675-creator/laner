# LNR-ARC-006 / 运行时仓库重链接到 Laner

- 日期：2026-09-13
- 执行者：OpenAI / ChatGPT
- 状态：PASS（仓库链接门禁） / BUILD TESTING

## 目标

完成从 `Rlftlab` 到 `laner` 的运行时仓库身份重链接。代码复制完成并不代表迁移完成；客户端 OTA、数据镜像、安装脚本和同步工具如果继续访问旧仓库，会造成“新代码运行、旧数据/旧发布链供给”的隐性分叉。

## Baseline

- 前置架构提交：`c84df2eb735f62d7fee3a4bf53d5b10eb3ee36c6`
- 一次性迁移准备提交：`f71e94ae507952a8d41e7cda4ee3487e499cc78c`
- 重链接结果提交：`49d00848dba88660af50554197eb3cc668195aa0`

## 实际迁移结果

一次性迁移器实际完成：

- 旧 GitHub 仓库引用替换：34 处。
- 未经验证的旧 Gitee 首发镜像移除：2 处。
- 变更文件：16 个。
- `tools/check_repository_links.py` 验证：`REPOSITORY_LINKS_OK owner=xbu080675-creator repo=laner`。

涉及运行链路：

- Android OTA manifest / Release APK 地址。
- 动态战队资料与人员资料。
- 全球教练/管理层镜像。
- 全球核实奖项镜像。
- 国际赛事镜像。
- Riot persisted mirror。
- 全球首发数据 feed / announcement feed。
- 战队历史图谱与 legacy archive。
- RiftClaw Android 安装指引与 bootstrap 下载源。
- 数据同步工具 User-Agent 中的仓库身份。
- 历史 dev28 数据补丁中的仓库数据地址。

## Gitee 处理

旧地址 `xiaobaiaaa1/Rlftlab` 没有已验证的 `laner` 对应镜像，因此没有伪造或猜测新地址。迁移时从 active 首发端点列表中移除了 2 个旧 Gitee 入口，保留已确认存在的 Laner GitHub Raw / jsDelivr 链路。

这是迁移正确性修复：宁可显式少一个未经验证的镜像，也不能让新应用继续静默消费旧仓库或不存在的地址。

## 行为影响

不改变：

- 产品显示名与 UI 文案中的 `RiftLab` 品牌。
- applicationId / package。
- 数据 schema。
- Provider 选择规则。
- 赛事业务判断。
- OTA 安全校验、签名校验与传输策略。

只改变仓库归属相关的 URL / provenance identity。

## 门禁

`tools/check_repository_links.py` 已进入 Compile Diagnostics，持续阻断 active `app/`、`scripts/`、`tools/`、`.github/workflows/` 再次引用旧仓库地址。

迁移审计文档允许保留 `Rlftlab` 字样，因为它是历史来源而非运行时依赖。

## 测试

- 精确重链接脚本：PASS，`repo_replacements=34 stale_gitee_removed=2`。
- active 旧仓库链接扫描：PASS。
- Core boundary：此前连续 PASS；本批未改变 Core。
- Android / Core 全量编译：由后续 Compile Diagnostics 继续验收，未完成前不记 PASS。

## 清理

完成验证后，一次性 `relink-laner-repository` workflow 与迁移脚本从 active 工程删除，避免未来误触；本记录保留完整迁移事实与提交号。

## 回滚

重链接前可回滚到 `c84df2eb735f62d7fee3a4bf53d5b10eb3ee36c6`。精确 Rlftlab 原始基线仍永久保留在 `migration/riftlab-full-reset@3f0b2d6d617b430a819f80eafe673ab44b0f268e`。
