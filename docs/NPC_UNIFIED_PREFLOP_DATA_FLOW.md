# 规则 NPC 统一翻前：参数、流程与数据源（对齐 `BOT_Fish/Maniac/Tag/Shark`，不含 `BOT_LLM`）

本文描述 **`BOT_Fish` / `BOT_Maniac` / `BOT_Tag` / `BOT_Shark`** 在 **`preflop`** 阶段的端到端数据通路、参与字段与方法分支。**`BOT_LLM`** 不走此链路（见 `DpLlmNpcDecisionService`）。

---

## 1. 总览：从定时任务到落地动作

| 步骤 | 位置 | 作用 |
|------|------|------|
| ① 谁在行动 | `DpRoomServiceImpl`（主循环） | `currentActorIndex` 指向的玩家若为 NPC 且非 `BOT_LLM`，调用 `DpNpcEngine.decideActionIfReady` |
| ② 门卫校验 | `DpNpcEngine.decideActionIfReady` | 校验轮到座、`bot` 未弃牌/未全下；`getBotTypeByNickname` → `BotType` |
| ③ 决策分发 | `DpNpcEngine.decideBotAction` | 组装 `callAmount`、`callRatio`、`style`（六旋钮）、`mood`、`random`；若 `stage == preflop` 优先走统一翻前 |
| ④ 统一翻前 | `DpNpcUnifiedPreflopStrategy.decide` | G1–G8、`rangeLevel`、有效位置、`PreflopSpot` → `fold` / `call` / `raise` / `all-in` |
| ⑤ 执行 | `DpRoomServiceImpl.NpcAction` | 将 `BotAction` 写入局面 |

**注意：** `decideBotAction` 内仍会计算 `getTablePosition`（相对庄家的粗位置），但翻前在返回统一策略结果前即结束，**统一翻前不使用该方法**，而使用 **`computePreflopLateFactor` → `pseudoTablePosition`**。

---

## 2. `decideBotAction` 传入统一翻前的参数

调用位置：`DpNpcEngine.decideBotAction` → `DpNpcUnifiedPreflopStrategy.decide(room, bot, callAmount, callRatio, vpip, pfr, callStation, foldToPressure, mood, random, type)`。

| 参数 | 来源 |
|------|------|
| `room` | `DpRoomBO`：阶段、盲注、`raiseLevel`、`currentBetToCall`、`players`、`playerStatsMap` 等 |
| `hero` | 当前行动的 `DpPlayer`：`holeCards`、`chips`、`bet`、`blind` |
| `callAmount` | `max(0, room.getCurrentBetToCall() - hero.getBet())` |
| `callRatio` | `callAmount / chips`（筹码不足跟满则为 `1`） |
| `vpip, pfr, callStation, foldToPressure` | `StyleProfile` **原始字段**（不经 `preflopTightness()` 等兼容 getter） |
| `mood` | `NPC_MOOD_ENABLED ? bot.getMood() : 0` |
| `random` | `buildHandRandom(room, bot)`（受 `NPC_HAND_SEED_FOR_DECISIONS` 影响） |
| `type` | `BotType`：仅用于 **`rangeLevelBonus`**（Shark +1、Maniac +2，TAG/Fish 为 0） |

**风格映射：** `STYLE_PROFILE_MAP.get(getStyleByBotType(type))`。例如 **TAG** → `NpcStyle.TIGHT_AGGRO` → `presetTightAggro()`。

---

## 3. TAG 预设六旋钮（当前默认值）

定义于 `DpNpcEngine.StyleProfile.presetTightAggro()`：

- `vpip = 0.24`
- `pfr = 0.76`
- `cbetFreq / bluffFreq`：**翻前不进** `decide`，翻后在其它分支使用
- `callStation = 0.18`
- `foldToPressure = 0.22`

**说明：** 工程里 `pfr` 与 `vpip` 作独立旋钮使用（翻前用 `pfr` 缩放 3bet/4bet **概率**）；若按真实 HUD 语义应有 `pfr ≤ vpip`，此处以代码为准。

---

## 4. `DpNpcUnifiedPreflopStrategy.decide` 内部流水线（按执行顺序）

### 4.1 守卫与基础字段

- `room.getCurrentStage()` 必须为 `"preflop"`；`bb > 0`
- `parseHole(hero.getHoleCards())` → 无效则 **`CALL_OR_CHECK, 0`**
- `countActivePlayers(room)`：未弃牌且未 `leftThisHand`
- `findAggressor(room, hero)` → `aggressorStats` → `estimateVillainTier` → **`VillainTier`**（影响 3bet bluff / 4bet bluff 等）
- `heroStackBB`、`effStackBB`（有 aggressor 时取双方筹码 **min**）
- `raiseLevel = room.getRaiseLevel()`，与 `callAmount` 共同决定 **`PreflopSpot`**

### 4.2 `PreflopSpot`（`spotBy`）

| 条件 | Spot |
|------|------|
| `raiseLevel >= 3` | `FACING_4BET` |
| `raiseLevel == 2` | `FACING_3BET` |
| `raiseLevel == 1 && callAmount > 0` | `FACING_OPEN` |
| `callAmount == 0` | `UNOPENED` |
| 其余 | `UNKNOWN` → 默认 `CALL_OR_CHECK` |

### 4.3 有效位置（与固定昵称无关）

1. **`lateFactor = computePreflopLateFactor(room, hero)`**  
   - UTG 索引 `(dealerIndex + 3) % N`（与 `DpRoomServiceImpl` 翻前首位行动一致）  
   - 仅保留未弃牌玩家在圆周顺序中的列表，`rank` 从 1 起  
   - `lateFactor = (rank - 1) / (active - 1)` ∈ [0, 1]  
   - **不读 `raiseLevel`**，弃牌会改变 `active` 从而改变后续座次的 `lateFactor`

2. **`position = pseudoTablePosition(lateFactor, hero.getBlind())`**  
   - `blind == 1 或 2` → **`BLINDS`**  
   - 否则：`lateFactor < 0.34` → EARLY；`< 0.67` → MIDDLE；否则 LATE  

### 4.4 `rangeLevel`（1～8，越大越松）

`computeRangeLevel(activePlayers, effStackBB, vpip, callStation, foldToPressure, mood)`：

- 基准 `base = 4`
- `activePlayers ≤ 3` → +2；`≤ 5` → +1（**6 人全员存活不加此项**）
- `effStackBB ≥ 50` → +1；`≤ 16` → -1
- `round((vpip - 0.30) * 5)`、`round(callStation)`、`-round(foldToPressure * 0.8)`、`round(mood)`
- `clampInt(1, 8)`，再 **`rangeLevel += rangeLevelBonus(botType)`**，再 clamp

**位置松紧**主要由 **`thresholdsFor(position, rangeLevel)`** 承担，不在此处按「相对庄家」粗分。

### 4.5 `RangeThreshold`（五个 HandGroup 上限）

由 **`thresholdsFor`** 从 `rangeLevel` 与 **`pseudoTablePosition`** 导出：

- `openMaxGroup` — UNOPENED 是否 open  
- `vsOpenContinueMaxGroup` — 面对 open 是否继续  
- `vsOpen3BetValueMaxGroup` — 面对 open 价值 3bet 资格 + 概率分支  
- `vs3BetContinueMaxGroup` — 面对 3bet 是否继续  
- `vs3Bet4BetValueMaxGroup` — 面对 3bet 价值 4bet 分支  

EARLY 对 `vsOpen3BetValue` 会再收紧一档（见源码）。

### 4.6 手牌桶 `HandGroup`（G1～G8）

- **`g = groupOf(hole)`**：按 **点数 2～14**、对子、Ax、同花、broadway、**gap**（连张/一隔）等规则归类（见 `DpNpcUnifiedPreflopStrategy.groupOf`）。
- **判定是否在范围内：** `isGroupAtMost(g, maxAllowed)`（ordinal 越小越强）。

**注意：** G1–G8 是 **翻前抽象桶**，与翻后 `SimpleStrength` / `DpUtilHandEvaluator` **不是同一套刻度**；存在 **同桶内 equity 不同** 的失真，属设计取舍。

### 4.7 分支方法（按 Spot）

- **`FACING_4BET`** → `decideFacing4Bet`（`callRatio`、`effStackBB`、`tier`、`mood`、`g` 等）
- **`UNOPENED`** → `decideUnopened`（`openMaxGroup`、`baseOpenSizeBB` → `RAISE` 或 `CALL_OR_CHECK`）
- **`FACING_OPEN`** → `decideFacingOpen`（继续范围 → **3bet 概率**：含 **`pfrAggressionScale(pfr)`**、`tier`、位置、mood、random）
- **`FACING_3BET`** → `decideFacing3Bet`（`vs3BetContinue`；**`callRatio ≥ 0.35`** 且非价值 4bet 时有高压支路；否则 **4bet 概率** × `pfrAggressionScale(pfr)` 等）

---

## 5. 六人桌示例（便于对照「第几席」）

约定：`players` 长度 6，庄家在 **`players[0]`**，则 SB=1、BB=2，**UTG = (0+3)%6 = 3**。翻前行动顺序：**3 → 4 → 5 → 0 → 1 → 2**。

- **TAG 坐在下标 `4`**、全员存活：**rank = 2**，`lateFactor = 1/5 = 0.2` → **EARLY**（非盲）。
- **TAG 坐在下标 `3`**（列表「第四席」若从 1 数）：**UTG**，`lateFactor = 0` → **EARLY**。

具体 `rangeLevel`、`th` 的五个 Group 需代入当日 **`effStackBB`、`activePlayers`、TAG 六旋钮** 按源码计算。

---

## 6. 加注「数额」与六旋钮、抖动

- **六旋钮**主要影响 **动不动手**（范围档位 + 3bet/4bet **概率**）；**未**在统一翻前把 `vpip/pfr` 直接乘进 open/3bet/4bet 的筹码倍数。
- **Open 尺度：** `baseOpenSizeBB(position, activePlayers, effStackBB, mood, random)` → 约 **2～4BB** 整数档 + **`jitter`**（random ±0.2 与阈值触发 ±1BB）+ **`mood * 0.1`**。
- **3bet：** `compute3BetAmount` — IP/OOP 倍数、码深、`random` 在倍数上 **±0.2**，最小加注约束，`roundToSB`。
- **4bet：** `compute4BetAmount` — 倍数约 **2.2～2.6×**（含 random），再约束与 `roundToSB`。

翻前统一策略 **不使用** `DpNpcEngine.applySoftNoise`；抖动来自上述 **`Random`** 与取整。

---

## 7. 相关开关（非翻前核心，便于联查）

| 常量 | 文件 | 含义 |
|------|------|------|
| `NPC_MOOD_ENABLED` | `DpNpcEngine` | 为 false 时 `mood` 恒 0（open jitter 里仍参与公式，但项为 0） |
| `NPC_HAND_SEED_FOR_DECISIONS` | `DpNpcEngine` | 决策用 Random 是否绑定手牌种子 |
| `NPC_SHARK_OPPONENT_DB_ENABLED` | `DpNpcEngine` | 为 false 时不读写 `dp_shark_opponent_profile`（对手画像持久化） |
| `NPC_SOFT_NOISE_ENABLED` | `DpNpcEngine` | 主要影响翻后等路径的概率抖动，**不**用于本统一翻前 |

**牌谱入库：** `DpHandHistoryObservedImpl` / `DpHandHistoryPersistServiceImpl`，与是否有 Shark **解耦**（有人上桌即可记观测牌谱）。

---

## 8. 源码索引

| 主题 | 文件 / 符号 |
|------|-------------|
| 定时任务入口 NPC | `DpRoomServiceImpl` |
| `decideActionIfReady` / `decideBotAction` | `DpNpcEngine` |
| 统一翻前 | `DpNpcUnifiedPreflopStrategy`（`decide`、`spotBy`、`computePreflopLateFactor`、`pseudoTablePosition`、`computeRangeLevel`、`thresholdsFor`、`groupOf`、`decideUnopened`、`decideFacingOpen`、`decideFacing3Bet`、`decideFacing4Bet`） |
| 风格预设与 Bot→风格 | `DpNpcEngine.StyleProfile`、`getStyleByBotType`、`STYLE_PROFILE_MAP` |

---

*文档版本：与当前仓库统一翻前实现一致；若改 `spotBy` / `rangeLevel` / `thresholdsFor` 公式，请同步更新本节。*
