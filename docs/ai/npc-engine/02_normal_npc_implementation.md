# 2. 普通 NPC（Fish / Maniac / TAG）逐步实现说明

下文按 **`decideBotAction` 里实际执行顺序** 描述（文件：`DpNpcEngine.java`）。

---

## 2.1 所有类型在进入分支前已算好的公共量

1. **`callAmount`**：`max(0, currentBetToCall - bot.bet)`。
2. **`callRatio`**：跟注额相对剩余筹码比例（用于大压力场景）。
3. **`position`**：`getTablePosition` → `EARLY` / `MIDDLE` / `LATE` / `BLINDS`。
4. **`strength`**：`estimateCurrentStrength` 直接作为决策用牌力档（无难度噪声）。
5. **`boardDanger`**：`evaluateBoardDanger(communityCards)` → `DRY` / `WET`。
6. **`mood`**：默认 **`NPC_MOOD_ENABLED == false`** 时恒视为 0；为 true 时读 `DpPlayer.getMood()`，结算可增减，影响概率与思考延迟。
7. **`StyleProfile`**：由 `getStyleByBotType(type)` 取 `NpcStyle`，再查 `STYLE_PROFILE_MAP`。分支里会用到 `preflopTightness`、`aggression`、`bluffFrequency`、`callStation`、`stealBlindFrequency`、`checkRaiseFear` 等字段。

---

## 2.2 BOT_Fish（`case DEMO`）

**设计目标**：偏被动、可预测，适合新手；仍保留一定加注与小额 bluff，避免纯跟注机器。

1. **翻前**：若 `stage == preflop` 且 **有跟注**且 **`strength == WEAK`**，用 `preflopTightness` 算基础弃牌概率，并按 `callRatio`、`mood` 微调，抽样是否 **FOLD**。
2. **翻后（有跟注）**：若 `callAmount > 0` 且非翻前，弱牌 + 高 `callRatio` + 湿牌面等会提高弃牌概率（**免费看牌不弃牌** 的约定在通用逻辑里已保证：`callAmount == 0` 不会走这类 fold）。
3. **行动类型**：大比例 **CALL_OR_CHECK**；翻后用 `aggression` 把「加注概率」从基准里拆出来。
4. **小额诈唬**：无人下注、翻后、牌力 WEAK/MEDIUM 时，按 `bluffFrequency` 抽样，以约 **1/3 pot** 尺度 **RAISE**（并吸附小盲整数倍）。
5. **价值加注**：选择加注时，用「对手下注 × 倍数」与「底池比例」综合定目标，并设最小加额，避免「只抬一点点」。

---

## 2.3 BOT_Maniac（`case MANIAC`）

**设计目标**：极凶；尽量少弃牌；大量随机加注与全下。

1. **先建 `DpUtilSmartContext`**：与其它类型不同，Maniac 在整条分支里会用到 **筹码深度**、**对手可信度**、**摊牌 bluff 倾向** 等（用于微调 all-in / 弃牌，不是完整「读牌引擎」）。
2. **`maniCommitFactor`**：按牌力档 + 湿牌面 + 深码桌弱中牌略降，得到「愿意投入总筹码的比例」，用于后面判断加注后是否 **直接 all-in**。
3. **`callAmount >= chips`（面临全下量）**：在 **all-in / fold / 极少数 call** 之间抽样，概率受对手 `ActionCredibility`、`showdownBluffiness`、`VillainRangeTier` 影响。
4. **无人下注（`callAmount == 0`）**  
   - 翻前、后位、底池仍像「仅盲注」时：按 **`stealBlindFrequency`** 偷盲 open。  
   - 翻前弱牌：极低弃牌率（仍带一点 `preflopTightness`）。  
   - 其余：高概率 **RAISE**（倍数随牌力随机），否则 check。
5. **有人下注**：极低基础弃牌率，再按对手风格微调；然后高概率 **加注**（额外 BB 倍数随机），大底池时更高概率 **ALL_IN**；若跟注成本已很高（如翻前 `callRatio > 0.3`），对强/中/弱牌分档处理 **平跟 / 弃牌 / all-in**。

---

## 2.4 BOT_Tag（`case TAG`）

**设计目标**：紧凶；翻前紧、翻后偏价值；**不做** Shark 级读牌，但会用 `SmartContext` 与 **HandPlan** 控制多街节奏。

1. **翻前**：若 `stage == preflop`，调用 **`decidePreflopForTagOrShark(room, bot, TAG, ...)`**（内部使用 `PREFLOP_TAG_PROFILE` 与手牌分类），得到 fold / call / raise / all-in。
2. **翻后**：  
   - **`buildSmartContext`**。  
   - **`initHandPlanIfNeededForPostflop`**：仅在 **flop**、且玩家身上还没有 `npcHandPlanType` 时生成 **VALUE / BLUFF / POT_CONTROL / GIVE_UP** 之一，并设 `maxBarrels`、`aggression`（与 Shark 共用同一套计划生成框架，但 TAG 不走 `DpNpcSharkExploitHandPlan` 的剥削层）。  
   - 后续逻辑在 `case TAG` 内继续：结合 `HandPlan`、牌力、赔率、压力等决定 check/call/fold/raise（具体条件见源码同一段落）。

---

## 2.5 三条通用约定（所有普通 NPC + Shark）

1. **免费看牌（`callAmount == 0`）不弃牌**：避免河牌面对方 check 仍 fold 的违和行为。
2. **`BotAction` 只描述意图**：实际扣筹码、更新底池在 **`DpRoomServiceImpl`**。
3. **随机性**：同一手牌用 `buildHandRandom(room, bot)` 固定种子，保证 **同一手内** 决策可复现、不同手之间有变化。

下一篇：[03_normal_npc_modules.md](03_normal_npc_modules.md)。
