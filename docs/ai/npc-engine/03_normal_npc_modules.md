# 3. 普通 NPC 涉及模块详解

## 3.1 `DpNpcEngine`（主文件）

| 能力 | 说明 |
|------|------|
| `isBotPlayer` | 昵称白名单：`BOT_Fish` / `BOT_Maniac` / `BOT_Tag` / `BOT_Shark`（不含 `BOT_LLM`）。 |
| `decideActionIfReady` | 行动位校验 + 思考延迟 + `decideBotAction`。 |
| `decideBotAction` | 公共前置计算 + `switch(BotType)`。 |
| `estimateCurrentStrength` | 手牌 + 公共牌 → `DpUtilHandEvaluator.evaluateBestHand` → `SimpleStrength`。 |
| `evaluateBoardDanger` | 同花/连张结构 → `DRY` / `WET`。 |
| `getTablePosition` | 结合庄家与座位顺序 → 位置枚举。 |
| （已移除）难度噪声 | 历史上曾有按难度的牌力/赔率噪声；当前规则型 NPC 一律用真值。 |
| （已移除）思考延迟 | 原 `nextBotActionTime` / `BotThinkProfile` 已删；`decideActionIfReady` 校验通过即决策。 |
| `buildSmartContext` | 组装 `DpUtilSmartContext`（见 3.3）。Maniac / TAG / Shark 都会用；Fish 主要在同文件内直接决策，不强制依赖 SmartContext。 |
| `decidePreflopForTagOrShark` | **仅 TAG（以及历史兼容的 SHARK 参数表名）** 使用的翻前表；**当前 Shark 翻前已改为** `DpNpcSharkPreflopStrategy`，不再走此函数。 |
| `initHandPlanIfNeededForPostflop` | **TAG 与 Shark** 在 flop 首次行动时生成整手计划（见 Shark 文档 4/5）。 |
| `STYLE_PROFILE_MAP` / `NpcStyle` / `StyleProfile` | 抽象「紧松凶」的数值参数，供各分支读取。 |

---

## 3.2 `DpUtilHandEvaluator`

- 提供 **`HandStrength`**（真实牌型比较）与 **`SimpleStrength`**（WEAK / MEDIUM / STRONG / MONSTER 四档）。
- NPC 所有「我大概有多强」的判断都基于这里，避免在引擎里重复实现牌型规则。
- 部分 Shark 逻辑会调用诸如 **`isBoardDominantBestHand`**（在 `DpNpcEngine`）：公共牌面是否「看起来已经成很大牌」，用于压低过度自信。

---

## 3.3 `DpUtilSmartContext`

纯 DTO，字段包括：

- **进攻者与统计**：`aggressor`、`aggressorStats`
- **对手画像**：`villainTier`、`credibility`、`showdownBluffiness`
- **数学量**：`potOdds`、`equityEst`（粗桶）
- **筹码**：`stackCtx`（min/max/avg 筹码与 BB）
- **多人桌**：`activeVillains`、`multiwayVillains`、`tightBehindCount` 等
- **反制建议**：`counterStrategy`（`CounterStrategyProfile`）

**普通 NPC 中的使用**：Maniac 大量用；TAG 翻后配合 HandPlan 用；Fish 基本不用完整包。

---

## 3.4 `DpPlayer` 上与 NPC 相关的字段

- **`nextBotActionTime`**：思考延迟截止时刻。
- **`mood`**：默认关闭（`NPC_MOOD_ENABLED`）；开启后结算由房间服务调整。
- **`npcHandPlanType` / `npcHandPlanMaxBarrels` / `npcHandPlanAggression` / `npcHandPlanTargetVillain`**：HandPlan（**TAG 与 Shark**）。

---

## 3.5 `DpPlayerStats`（桌上统计）

- 人类与机器人均可维护；用于 **`estimateVillainRangeTier`**、**`estimateActionCredibility`**、**`estimateShowdownBluffiness`** 等。
- **普通 NPC** 里 Maniac 最依赖；TAG 适度；Fish 几乎不依赖。

---

## 3.6 `DpRoomServiceImpl`

- 定时器里调用 NPC 引擎并执行 `bet`/`fold`。
- 结算 **`autoSettle`** 里可选调整机器人 **`mood`**（同上开关），并触发与 Shark 相关的持久化（仅当桌上有 Shark 时，见 `05_shark_modules.md`）。

---

## 3.7 与 Shark 的边界

- **普通 NPC** 不包含：`DpNpcSharkPreflopStrategy`、`DpNpcSharkStrategy`、`DpNpcSharkExploitHandPlan`、`DpNpcSharkLearningLab`、对手 DB 持久化。
- **TAG** 与 Shark **共用** `HandPlan` 初始化框架，但 Shark 额外在 flop 上叠 **剥削剧本** 与 **LearningLab**。

下一篇（高级 NPC）：[04_shark_implementation.md](04_shark_implementation.md)。
