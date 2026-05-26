# 规则型 NPC 引擎文档索引

> **核对日期**：2026-05-25  
> **权威来源**：`com.example.mgdemoplus.npc.engine.DpNpcEngine`、`npc/strategy/*`、`application.yml` 之 `dp.npc`  
> **Status**: maintained

本目录说明 **规则型 Bot**（`BOT_FISH` / `BOT_TAG` / `BOT_LAG` / `BOT_NIT` / `BOT_CALL` / `BOT_MANIAC` / `BOT_CUSTOM` 及旧昵称兼容）。对局协议与房间流程见 [docs/DPGAME.md](../../DPGAME.md)。

**不在此目录展开**

- **大模型 Bot**：`BOT_LLM` / `BOT_LLM_GLOBAL` → [docs/ai/npc-llm/part01_类间关系以及调用.md](../npc-llm/part01_类间关系以及调用.md)
- **翻前统一策略专文**：[npc-preflop-unified-decision-flow.md](../npc-preflop-unified-decision-flow.md)

---

## 阅读顺序

| 文件 | 内容 |
|------|------|
| [npc-preflop-unified-decision-flow.md](../npc-preflop-unified-decision-flow.md) | `DpNpcUnifiedPreflopStrategy`：`Spot`、`lateFactor`、`rangeLevel`、13×13 查表 |
| [01_overview_and_entry.md](01_overview_and_entry.md) | 六档 archetype、调用链、`BotType`、与 LLM 分流 |
| [02_normal_npc_implementation.md](02_normal_npc_implementation.md) | 翻后各策略类在 `decideBotAction` 中的分派 |
| [03_normal_npc_modules.md](03_normal_npc_modules.md) | 共用模块、**`dp.npc.rule-think` 思考延时**、`StyleProfile` |
| [04_shark_legacy.md](04_shark_legacy.md) | 遗留 `BOT_Shark` 昵称 → **TAG** 行为（REST 兼容） |
| [05_shark_modules_legacy.md](05_shark_modules_legacy.md) | `dp_shark_opponent_profile` 等表留存、无运行时写路径 |

---

## 代码主路径

| 角色 | 类 / 配置 |
|------|-----------|
| 引擎入口 | `npc.engine.DpNpcEngine`（静态方法，非 Spring Bean） |
| 翻前 | `npc.strategy.DpNpcUnifiedPreflopStrategy` |
| 翻后 | `DpNpcFishStrategy`、`DpNpcCallStrategy`、`DpNpcLagStrategy`、`DpNpcManiacStrategy`、`DpNpcTagStrategy`、`DpNpcNitStrategy` |
| 思考延时 | `npc.rulethink.DpNpcRuleThinkProperties`（`dp.npc.rule-think`）、`DpNpcRuleThinkSampler` |
| 房间调度 | `room.support.DpRoomHeartbeatScheduler`（1s tick）→ `DpRoomServiceImpl` → `decideActionIfReady` / `npcAction` |
| 牌力 | `utils.dp.DpUtilHandEvaluator` |

---

## 昵称与前缀（速查）

| 前缀 | `BotType` | REST 示例 |
|------|-----------|-----------|
| `BOT_FISH` | FISH | `POST /dpRoom/addFishBot` |
| `BOT_CALL` | CALL | `addCallBot` |
| `BOT_LAG` | LAG | `addLagBot` |
| `BOT_TAG` | TAG | `addTagBot` |
| `BOT_NIT` | NIT | `addNitBot` |
| `BOT_MANIAC` | MANIAC | `addManiacBot` |
| `BOT_CUSTOM` | 自定义 YAML 档 | `addCustomBot` |
| `BOT_Shark`（遗留） | 映射 **TAG** | `addSharkBot`（兼容旧前端） |

多实例：`{PREFIX}_<seq>`，序号由 `DpRoomBO.allocateBotNicknameSeqBatch` 分配。

### `npc/` 包结构（速查）

```text
npc/
├── engine/DpNpcEngine.java          # 规则入口（静态）
├── strategy/DpNpc*Strategy.java     # 翻后分 archetype；翻前 DpNpcUnifiedPreflopStrategy
├── llm/DpLlmNpcDecisionService.java # BOT_LLM / BOT_LLM_GLOBAL
├── rulethink/                       # dp.npc.rule-think 采样
├── tabletalk/                       # 桌边话池与推送
├── entity/DpSharkOpponentProfile    # 遗留表映射，无写路径
└── CustomNpcStyleProfileDto         # BOT_CUSTOM 调参
```

`room` / `quickmatch` **无** NPC Mapper；Bot 状态在 `DpRoomBO` / `DpPlayer` 内存字段。

---

## 与 LLM 的分流

- `DpNpcEngine.isLlmBotNickname` / `isGlobalLlmBotNickname` → 心跳走 `DpLlmNpcDecisionService`，**不**进入 `decideActionIfReady`。
- 规则 Bot 与 LLM 共用 `DpPlayer.nextBotActionTime`，但 **仅** `DpNpcEngine.decideActionIfReady` 写入规则思考排期；LLM 用 in-flight ticket 控制两阶段。

---

## 配置（`application.yml`）

```yaml
dp:
  npc:
    rule-think:
      enabled: true          # DP_NPC_RULE_THINK_ENABLED
      snap-probability: 0.18 # 本 tick 秒出
      max-ms: 4000
      # fast/slow 桶见 DpNpcRuleThinkProperties
    table-talk:
      enabled: true          # 桌边话（与 LLM table_talk 字段配合）
```

`enabled=false` 时规则 Bot **即时** 决策（无 `nextBotActionTime` 等待）。**已实现**，非「已移除思考延迟」。
