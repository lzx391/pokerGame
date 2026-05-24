# DP 对局 NPC 引擎（规则型）文档索引：`DpNpcEngine` · Fish／Maniac／TAG／Shark

本目录是后端 **规则型 NPC**（`BOT_Fish` / `BOT_Maniac` / `BOT_Tag` / `BOT_Shark`）的实现说明。  
游戏整体协议与房间流程仍以仓库根目录下的 `docs/DPGAME.md` 为准；这里只讲 **AI 决策与相关类**。

**不在本目录展开的内容**

- **大模型机器人 `BOT_LLM`**：走 `DpLlmNpcDecisionService`，与普通 NPC 分流；说明见根目录 `README.md` 的「大模型 NPC」小节。
- **前端如何加机器人**：仍以 `DPGAME.md` 与房主接口为准。

---

## 阅读顺序

| 文件 | 内容 |
|------|------|
| [npc-preflop-unified-decision-flow.md](../npc-preflop-unified-decision-flow.md) | **统一翻前**（`DpNpcUnifiedPreflopStrategy`）数据流向：入口、`Spot`、`lateFactor`、`rangeLevel`、分场景动作与加注尺度 |
| [01_overview_and_entry.md](01_overview_and_entry.md) | 分类（普通 vs Shark）、调用链、核心类一览 |
| [02_normal_npc_implementation.md](02_normal_npc_implementation.md) | 普通 NPC（Fish / Maniac / TAG）从入口到分支的逐步流程 |
| [03_normal_npc_modules.md](03_normal_npc_modules.md) | 普通 NPC 共用模块：`DpUtilHandEvaluator`、`StyleProfile`、规则思考延时（`dp.npc.rule-think`）等 |
| [04_shark_implementation.md](04_shark_implementation.md) | Shark 翻前 → 翻后 HandPlan → 决策委托的逐步流程 |
| [05_shark_modules.md](05_shark_modules.md) | Shark 专属类：`DpNpcSharkPreflopStrategy`、`DpNpcSharkStrategy`、剥削剧本、学习旋钮、持久化 |

---

## 代码主路径（速查）

- **引擎入口**：`com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine`
- **房间调用**：`DpRoomServiceImpl` 定时任务里 `DpNpcEngine.isBotPlayer` → `decideActionIfReady` → 根据 `BotAction` 调 `bet` / `fold`
- **牌力评估**：`com.example.mgdemoplus.utils.dp.DpUtilHandEvaluator`（注意：历史文档里的 `DpHandEvaluator` 名称已统一为该类）
