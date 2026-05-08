# 1. 总览：普通 NPC 与 Shark、调用链

## 1.1 两类机器人（按实现复杂度分）

| 类型 | 昵称常量 | `BotType` | 说明 |
|------|-----------|-----------|------|
| **普通 NPC** | `BOT_Fish` | `DEMO` | 鱼式演示档，逻辑集中在 `DpNpcEngine` 的 `case DEMO`。 |
| **普通 NPC** | `BOT_Maniac` | `MANIAC` | 松凶、大量加注/全下，逻辑在 `case MANIAC`。 |
| **普通 NPC** | `BOT_Tag` | `TAG` | 紧凶；翻前用参数表 `decidePreflopForTagOrShark`，翻后在 `case TAG`。 |
| **高级 NPC** | `BOT_Shark` | `SHARK` | 翻前委托 `DpNpcSharkPreflopStrategy`，翻后委托 `DpNpcSharkStrategy`，并叠加 HandPlan、剥削剧本、对手学习与持久化。 |

**风格映射（抽象参数，不是第二套决策树）**  
`DpNpcEngine.getStyleByBotType` 把每种 `BotType` 映射到 `NpcStyle`，再通过 `STYLE_PROFILE_MAP` 得到 `StyleProfile`（紧松、凶度、诈唬频率等）。Fish 为 `LOOSE_FUN`，Maniac 与 Shark 为 `LOOSE_AGGRO`，TAG 为 `TIGHT_AGGRO`。Shark 虽与 Maniac 共用同一套风格数字，但 **翻前/翻后走完全不同的代码路径**。

**牌力与赔率**  
规则型 NPC 使用 **`estimateCurrentStrength` 得到的 `SimpleStrength` 真值**（不对牌力档做人为降级噪声）；`buildSmartContext` 里的 **底池赔率**亦为真值。强弱观感主要来自 **`StyleProfile`** 与各 `BotType` 分支。

---

## 1.2 从房间服务到 `BotAction` 的调用链

1. **`DpRoomServiceImpl`** 定时任务发现当前行动者是机器人（`DpNpcEngine.isBotPlayer`），且不是 `BOT_LLM` 时，调用 **`DpNpcEngine.decideActionIfReady(room, bot)`**。
2. **`decideActionIfReady`** 做守卫（房间在玩、行动位是本人、未弃牌未全下等），通过后立即调用决策（不再使用 `nextBotActionTime` 思考窗）。
3. 通过 `getBotTypeByNickname` 得到 `BotType`，进入 **`decideBotAction(room, bot, type)`**。
4. **`decideBotAction`** 统一计算：`callAmount`、`callRatio`、`TablePosition`、`SimpleStrength`（真值）、`BoardDanger`、**`mood`（默认关闭，见 `DpNpcEngine.NPC_MOOD_ENABLED`）**、`StyleProfile` 等，然后 **`switch (type)`** 进入各机器人分支。
5. 返回 **`BotAction`（类型 + 金额）**；房间服务映射为 `fold` / `bet` 等，**不**在引擎里直接改筹码。

---

## 1.3 核心类一览

| 类 | 职责 |
|----|------|
| `DpNpcEngine` | 入口、共用工具（牌力、牌面危险度、位置、`buildSmartContext`、TAG 翻前、HandPlan 初始化与 barrel 消耗等）。 |
| `DpUtilHandEvaluator` | 真实牌型评估与 `SimpleStrength` 粗分档。 |
| `DpUtilSmartContext` | 仅数据容器：进攻者、统计、赔率、筹码深度、multi-way 计数等；由 `DpNpcEngine.buildSmartContext` 填充。 |
| `DpNpcSharkPreflopStrategy` | 仅 Shark 翻前：手牌分组 + `rangeLevel` + 各 spot。 |
| `DpNpcSharkStrategy` | 仅 Shark 翻后：接 `SmartContext`、HandPlan、大量配置驱动下注与弃牌。 |
| `DpNpcSharkExploitHandPlan` | Shark flop 计划在基础 HandPlan 上叠「剥削剧本」。 |
| `DpNpcSharkLearningLab` | 按对手昵称累积旋钮（弃牌倾向、尺度、诈唬频率等）。 |
| `DpNpcSharkOpponentMemoryService` | 桌上有 Shark 时，把统计 + 旋钮持久化到 DB。 |
| `DpRoomServiceImpl` | 执行 NPC 返回的动作；可选结算时调整机器人 `mood`（由 `NPC_MOOD_ENABLED` 控制）。 |

下一篇：[02_normal_npc_implementation.md](02_normal_npc_implementation.md)。
