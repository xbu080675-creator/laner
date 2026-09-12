# Laner Source Architecture

## 一级分类

Laner 的外部数据/API 来源统一分为四大类：

1. `PRE_MATCH_SOURCE` / 赛前源
2. `LIVE_MATCH_SOURCE` / 赛中源
3. `POST_MATCH_SOURCE` / 赛后源
4. `GLOBAL_AI_ASSIST` / 全局 AI 辅助源

前三类负责提供或验证赛事事实；第四类负责解释、归纳、推断、翻译、提示和辅助分析，不得凭模型生成内容覆盖已确认赛事事实。

## PRE_MATCH_SOURCE / 赛前源

主要服务赛前判断与观赛准备，例如：

- 赛程、赛事时间与场次状态；
- 首发与阵容；
- 选手 Rank / 近期状态；
- 转会、人员变更与队伍信息；
- 历史交手；
- 积分、排名、晋级路径；
- 赛制与赛事公告；
- 赛前社交媒体官方信息；
- 首发图、海报等需要 OCR 的图片型信息。

## LIVE_MATCH_SOURCE / 赛中源

主要服务赛事开始后的实时事实与状态变化，例如：

- 直播/赛事状态；
- BP；
- 阵容与英雄信息；
- 游戏事件流；
- 等级、技能、装备、经济与资源；
- 击杀、塔、龙、先锋、大龙、地图资源；
- 实时比分与小局状态；
- 局间与下一局状态；
- 可用于 HUD、沙盘、异常检测的实时数据。

赛中源必须优先考虑延迟、连续性、事件顺序、重复事件和断流恢复。

## POST_MATCH_SOURCE / 赛后源

主要服务结果确认、历史沉淀和复盘，例如：

- 官方赛果；
- 小局/系列赛最终数据；
- 赛后统计；
- 选手与队伍数据面板；
- 完整事件时间线；
- 积分、排名与晋级状态变更；
- 历史赛事归档；
- 可用于复盘和模型分析的稳定数据集。

赛后源允许比赛中源更高延迟，但要求更高完整性、稳定性和可追溯性。

## GLOBAL_AI_ASSIST / 全局 AI 辅助源

AI 跨越赛前、赛中、赛后三阶段，但属于辅助层，不属于事实权威层。

允许能力：

- 中文解释与术语转换；
- 长文本/公告/新闻摘要；
- OCR 后结构化与纠错建议；
- 多事实归纳；
- 趋势、异常和模式辅助识别；
- 观众版与教练版信息压缩；
- 赛中提示与赛后复盘辅助；
- 对已有证据进行自然语言解释。

禁止行为：

- 无证据生成比分、首发、赛果或事件；
- 用模型输出覆盖更高权威赛事源；
- 将推断伪装成事实；
- AI 服务故障导致核心赛事数据不可用。

AI 输出必须区分至少：

- `FACT_BACKED`：有事实源支撑；
- `INFERENCE`：基于事实的推断；
- `UNVERIFIED`：暂未验证，不得作为权威事实展示。

## Source Adapter 统一契约方向

所有来源必须通过 Source Adapter 进入系统，不允许 UI 或业务页面直连 Provider。

```text
Provider
→ Source Adapter
→ Validation
→ Normalization
→ Provenance
→ Conflict/Freshness Resolution
→ Domain Event / Match State / Repository
```

每条标准化数据至少应保留：

- `source_id`
- `source_class`
- `source_timestamp`
- `fetched_at`
- `confidence`
- `authority_level`
- `revision`
- `match_id / game_id / event_id`（适用时）

## 权威与速度分离

“更快”不等于“更权威”。

规则：

1. 首个可信来源可以先发布 provisional 状态；
2. 更高权威来源可以后续确认、修正或覆盖；
3. 不得为了等待最慢的官方源而阻塞整个 UI；
4. 修正必须有 revision/provenance，不允许静默篡改历史；
5. 每类源都必须有健康状态、超时、重试、降级和冲突处理策略。

## 与三阶段的关系

页面阶段和来源类别相关，但不要求一一绑定。

例如：

- 赛前页面主要消费 `PRE_MATCH_SOURCE`；
- 赛中页面主要消费 `LIVE_MATCH_SOURCE`，必要时读取赛前背景；
- 赛后页面主要消费 `POST_MATCH_SOURCE`，并可回放赛中事件；
- `GLOBAL_AI_ASSIST` 可服务全部三阶段。

来源分类解决“数据从哪里、在什么时候最有价值”；页面阶段解决“用户此刻要看什么”。两者不得混为同一概念。
