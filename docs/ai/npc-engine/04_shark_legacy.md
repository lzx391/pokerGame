# 遗留 `BOT_Shark` 昵称（归档）

> **核对日期**：2026-05-25  
> **权威来源**：`DpNpcEngine#LEGACY_BOT_SHARK_NICKNAME`、`DpRoomServiceImpl#addSharkBotToNextHand`  
> **Status**: maintained（兼容说明，非独立策略）

## 现网行为

| 项 | 说明 |
|----|------|
| REST | `POST /dpRoom/addSharkBot` → `addSharkBotToNextHand` |
| 下一局昵称 | 固定 **`BOT_Shark`**（无 `_<seq>`） |
| 决策类型 | `getBotTypeByNickname` 将 `BOT_Shark` 映射为 **`BotType.TAG`**（与 `BOT_Tag` 遗留昵称相同） |
| 翻前 / 翻后 | 与 **`BOT_TAG_*`** 相同：`DpNpcUnifiedPreflopStrategy` + `DpNpcTagStrategy` |

**不存在** 独立的 `BotType.SHARK`、`DpNpcSharkStrategy` 或 `DpNpcSharkPreflopStrategy` 类路径；旧文档若描述 `case SHARK` 专用策略树，均为历史实现。

## 与 `addTagBot` 的关系

`addTagBotToNextHand` 生成 `BOT_TAG_<seq>`；`addSharkBot` 仅为 **前端/旧接口兼容**，行为等价于加入一名 **TAG 规则 Bot**，昵称不同。

## 数据库与观测

- Flyway **V1** 仍含 `dp_shark_opponent_profile` 等表；**服务层不再读写**（见根 README「对局与房间」Flyway 说明）。
- 牌谱持久化注释中的 `DpNpcSharkObservedHandHistory` 为历史命名，实际写入 `dp_observed_hand_*` 通用表。

## 阅读指引

规则 NPC 主路径见 [01_overview_and_entry.md](01_overview_and_entry.md)、[02_normal_npc_implementation.md](02_normal_npc_implementation.md)（TAG 分支）。
