# Legacy LIVE 场间状态识别实测证据

- 日期：2026-09-12
- Evidence type：`EXTERNAL / REAL-DEVICE USER VERIFICATION`
- Legacy project：`xbu080675-creator/Rlftlab`
- Laner working branch：`feature/lnr-010-global-schedule`
- Related baseline items：`LIVE-001 / LIVE-002`
- Result：`PASS WITH SCOPE LIMIT`

## 1. 实测结论

旧 RiftLab 的赛中部分仍存在其它已知问题，但**场间状态识别本身已实测可用**。

确认能力：

- 能识别当前仍处于场间 / 新一局尚未真正开局；
- 能识别新一局已经真正开局；
- 因此旧版已经具备可用的“场间 ↔ 开局”状态边界判断能力；
- 该能力应视为旧版行为基线的一部分，而不是在重构时重新定义或缩水。

## 2. 明确不代表

本证据只确认“场间 / 是否开局”的识别能力。

**不代表**：

- 旧版全部 LIVE 数据链均正确；
- 实时经济、击杀、资源、BP、Timeline、HUD 等能力已经全部通过；
- 旧版其它赛中问题可以忽略；
- Laner 可以直接复制旧实现。

## 3. Laner 迁移要求

未来 Match State Engine 迁移 `LIVE-001 / LIVE-002` 时，必须把此行为设为回归要求：

```text
EVENT_LIVE_PRE_GAME / BETWEEN_GAMES
        ↓
不得误判为 IN_GAME

真实新局开始
        ↓
必须能够进入 LOADING / IN_GAME 对应状态
```

并继续保持既定领域不变量：

> 赛事开始 != 游戏开始。

## 4. 最低回归场景

后续自动/实机测试至少覆盖：

1. 上一局结束进入场间，系统不得继续显示为当前局仍在进行；
2. 场间选手/导播准备阶段，不得提前判定新局已开；
3. 新局实际开始后，应从场间状态切入新 Game；
4. Series 未结束时，小局结束不得误判整个 Series 完成；
5. 状态切换重复事件/延迟事件不得造成来回抖动。

## 5. 迁移决策

- `LIVE-001`：仍为 `TODO`，但已有明确旧版 PASS 行为证据；
- `LIVE-002`：仍为 `TODO`，但“赛事开始 != 游戏开始”已有旧版实机证据；
- 后续新架构必须**保留并加强**此能力，而不是把整个旧 LIVE 模块当作不可用后全部推倒行为语义。

## 6. 证据状态

这是用户现场实测反馈形成的仓库证据。由于当前未保存原始录屏/日志，本记录不替代后续 Laner 自动测试与实机验收；其作用是锁定旧版已经证明有效的行为边界，防止迁移回归。
