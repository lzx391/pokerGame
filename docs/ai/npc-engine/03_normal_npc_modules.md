# 规则 NPC 共用模块与配置

> **核对日期**：2026-05-25  
> **权威来源**：`DpNpcEngine`、`DpNpcRuleThinkSampler`、`DpUtilHandEvaluator`、`application.yml`  
> **Status**: maintained

---

## 3.1 `DpNpcEngine`

| 能力 | 说明 |
|------|------|
| `isBotPlayer` / `getBotTypeByNickname` | 规则前缀 + 遗留昵称（`BOT_Shark`→TAG） |
| `decideActionIfReady` | 行动位校验；`dp.npc.rule-think` 两阶段 `nextBotActionTime`；再 `decideBotAction` / `decideCustomBotAction` |
| `decideBotAction` | 翻前 **仅** `DpNpcUnifiedPreflopStrategy`；翻后 `switch(BotType)` |
| `estimateCurrentStrength` | `DpUtilHandEvaluator` → `SimpleStrength` |
| `buildSmartContext` | TAG / MANIAC 等翻后使用 |
| `STYLE_PROFILE_MAP` | `NpcStyle` → `StyleProfile` 数值旋钮 |

全局常量：`NPC_MOOD_ENABLED`（默认 **false**）；`NPC_HAND_SEED_FOR_DECISIONS`（默认 **true**）。概率软噪声通路已移除。

---

## 3.2 思考延时（`dp.npc.rule-think`）

| 组件 | 说明 |
|------|------|
| `DpNpcRuleThinkProperties` | `@ConfigurationProperties`，由 `DpNpcRuleThinkConfig` 注册 |
| `DpNpcRuleThinkSampler.bind` | 启动时绑定；`enabled=false` → 采样 **0ms**（即时决策） |
| `DpPlayer.nextBotActionTime` | 与 LLM 共用字段名；**仅** `DpNpcEngine.decideActionIfReady` 读写规则路径 |

采样：`snapProbability` 秒出；否则快/慢桶加权均匀， capped by `maxMs`。LLM 路径 **不** 调用此采样器。

---

## 3.3 `DpUtilHandEvaluator` / `DpUtilSmartContext`

- **牌力**：`HandStrength`（比牌）与 `SimpleStrength` 四档（WEAK/MEDIUM/STRONG/MONSTER）。
- **SmartContext**：对手 tier、赔率桶、`stackCtx`、multi-way 等；Fish 翻后较少依赖完整包。

---

## 3.4 `DpPlayer` 与统计

| 字段 | 用途 |
|------|------|
| `nextBotActionTime` | 规则思考截止 |
| `mood` | 默认关闭 |
| `npcHandPlan*` | TAG HandPlan（flop 初始化） |
| `DpPlayerStats` | 桌上 VPIP 等；Maniac 翻后用得最多 |

---

## 3.5 房间侧

`DpRoomHeartbeatScheduler`（1s）→ `DpRoomServiceImpl` 行动超时 / NPC → `npcAction` 落地。详见 [01_overview_and_entry.md](01_overview_and_entry.md)。

---

## 3.6 桌边话（可选）

`dp.npc.table-talk` + `DpNpcTableTalkService` / `DpNpcLinePoolLoader`：规则 Bot 与 LLM 的 `table_talk` 字段可触发局内推送（与决策延时独立）。
