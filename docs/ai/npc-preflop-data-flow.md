# 规则 NPC 翻前：统一策略下的数据流水线（G1–G8、`PreflopSpot`）

本文描述 **`DpNpcUnifiedPreflopStrategy`** 与 **`DpNpcEngine`** 在翻前阶段的调用链、参与字段、局面分型与手牌分桶（G1–G8），便于对照代码与调参。

---

## 1. 总入口（规则 NPC，非 BOT_LLM）

| 步骤 | 位置 | 说明 |
|------|------|------|
| 调度 | `DpRoomServiceImpl` 定时逻辑 | `currentActorIndex` 指向 NPC 时，若昵称非 `BOT_LLM`，调用 `DpNpcEngine.decideActionIfReady` |
| 门卫 | `DpNpcEngine.decideActionIfReady` | 校验轮到该 bot、未弃牌/未离桌；`getBotTypeByNickname` → `BotType` |
| 核心 | `DpNpcEngine.decideBotAction` | 组装 `callAmount`、`callRatio`、`style`、`mood`、`random` 等 |
| 翻前早退 | 同上，`stage == preflop` | 调用 `DpNpcUnifiedPreflopStrategy.decide(...)`，非 null 则 **直接 return**，**不**走 `SimpleStrength` 与 `switch(type)` 翻前分支 |
| 落地 | `DpRoomServiceImpl.NpcAction` | 将 `BotAction` 写入下注/弃牌等 |

**注意：** `decideBotAction` 里仍会计算 `getTablePosition`，但翻前在统一策略返回后已结束；翻前位置以 **`computePreflopLateFactor` + `pseudoTablePosition`** 为准。

---

## 2. `decideBotAction` 传入统一翻前的参数

| 参数 | 来源 |
|------|------|
| `room` | 当前房间 |
| `bot` | 当前行动英雄 |
| `callAmount` | `max(0, currentBetToCall - bot.bet)` |
| `callRatio` | `callAmount / chips`（跟满则为 1） |
| `vpip, pfr, callStation, foldToPressure` | `StyleProfile`（由 `NpcStyle` 预设，`BotType` → `getStyleByBotType`） |
| `mood` | `NPC_MOOD_ENABLED ? bot.getMood() : 0` |
| `random` | `buildHandRandom`（受 `NPC_HAND_SEED_FOR_DECISIONS` 影响） |
| `type` | `BotType`（仅 **`rangeLevelBonus`**：Shark +1、Maniac +2，TAG/DEMO 为 0） |

**风格解耦：** 预设挂在 `STYLE_PROFILE_MAP`（`TIGHT_AGGRO` / `LOOSE_AGGRO` / `LOOSE_FUN` 等），Bot 昵称只映射到 `NpcStyle`，多个 Bot 可共用同一套六旋钮。

---

## 3. `DpNpcUnifiedPreflopStrategy.decide` 内部流水线

### 3.1 守卫与基础数据

- `room.getCurrentStage()` 必须为 `"preflop"`；`bb > 0`。
- `parseHole(holeCards)` → 非法则 **`CALL_OR_CHECK, 0`**。
- `countActivePlayers(room)`：未弃牌且未 `leftThisHand`。
- `findAggressor(room, hero)`：`bet` 最大且非 hero；`playerStatsMap` 取 **`VillainTier`**（影响 3bet/4bet bluff 条件）。
- `heroStackBB`、`effStackBB`（有 aggressor 时 eff = min(hero, aggressor) 筹码 / bb）。
- `raiseLevel = room.getRaiseLevel()`，与 `callAmount` 一起决定 **`PreflopSpot`**。

### 3.2 局面分型 `spotBy(raiseLevel, callAmount)`

| 条件 | `PreflopSpot` |
|------|----------------|
| `raiseLevel >= 3` | `FACING_4BET` |
| `raiseLevel == 2` | `FACING_3BET` |
| `raiseLevel == 1 && callAmount > 0` | `FACING_OPEN` |
| `callAmount == 0` | `UNOPENED` |
| 其它 | `UNKNOWN` → 默认 `CALL_OR_CHECK` |

### 3.3 有效位置 `lateFactor` → 伪 `TablePosition`

- 与发牌一致：**UTG = (dealerIndex + 3) % N**，顺时针一圈；只保留未弃牌玩家顺序。
- `rank`：英雄在该顺序中 1-based；`lateFactor = (rank - 1) / (active - 1)` ∈ [0,1]。
- **`pseudoTablePosition(lateFactor, blind)`**：`blind == 1|2` → **BLINDS**；否则 `<0.34` EARLY，`<0.67` MIDDLE，否则 LATE。
- **多注/弃牌：** 每次决策重算；**不读** `raiseLevel` 以外的「细 squeeze」，属粗粒度几何 proxy。

### 3.4 `rangeLevel`（1～8，越大越松）

- `computeRangeLevel(activePlayers, effStackBB, vpip, callStation, foldToPressure, mood)`：基准 4，按人数、码深、六旋钮中上述四项加减，再 `clampInt(1,8)`。
- `rangeLevel += rangeLevelBonus(botType)`（Shark +1、Maniac +2）。
- **位置松紧**主要由 **`thresholdsFor(position, rangeLevel)`** 承担，不在 `computeRangeLevel` 里按旧 `TablePosition` 粗分。

### 3.5 阈值 `RangeThreshold`

由 `thresholdsFor` 生成五档 **`HandGroup` 上限**：`openMaxGroup`、`vsOpenContinueMaxGroup`、`vsOpen3BetValueMaxGroup`、`vs3BetContinueMaxGroup`、`vs3Bet4BetValueMaxGroup`。  
比较时用 **`isGroupAtMost(g, max)`**（G1 最强，ordinal 最小）。

### 3.6 手牌桶 `groupOf` → G1–G8

- 牌串格式 `花色_牌面`（如 `s_A`）；`RANK`：2–10、J=11、Q=12、K=13、A=14。
- **非**翻后 `SimpleStrength`；仅为翻前抽象，同一桶内组合真实胜率可能差异大（有意粗糙）。

### 3.7 分场景子流程

| Spot | 方法 | 要点 |
|------|------|------|
| `UNOPENED` | `decideUnopened` | `openMaxGroup`；open 尺寸 `baseOpenSizeBB`（2–4BB + jitter + mood），`roundToSB` |
| `FACING_OPEN` | `decideFacingOpen` | `vsOpenContinue` / fold；3bet 概率含 `pfrAggressionScale(pfr)`、`tier`、盲注位加成；`compute3BetAmount` |
| `FACING_3BET` | `decideFacing3Bet` | `vs3BetContinue`；`callRatio` 高压支路；`fourBetProb` × `pfrAggressionScale(pfr)`；`compute4BetAmount` / 浅码 ALL_IN |
| `FACING_4BET` | `decideFacing4Bet` | pot odds、`tier`、`mood`、`callRatio`、牌组 → fold/call/all-in |

---

## 4. 六旋钮与「加注金额」的关系

- **动手概率：** `vpip` / `callStation` / `foldToPressure` → `rangeLevel`；**`pfr`** → **`pfrAggressionScale`** 缩放 **3bet/4bet 概率**。
- **加注数额（chips）：** **`baseOpenSizeBB` / `compute3BetAmount` / `compute4BetAmount`** 主要依赖 **位置桶、人数、`effStackBB`、对手 `currentBetToCall`、`bb/sb`**，以及 **内置 `random` 抖动**；**不**把 `vpip/pfr` 直接乘进倍数。
- **引擎级 `NPC_SOFT_NOISE`：** 作用于 `DpNpcEngine` 部分翻后概率等；**统一翻前概率**不经过 `applySoftNoise`。

---

## 5. 六人桌示例（便于对照「第几席」）

- `players[0]` = 庄家；SB=`(0+1)%6`，BB=`(0+2)%6`；**UTG = `(0+3)%6 = 3`**。
- 翻前行动顺序：**3 → 4 → 5 → 0 → 1 → 2**（一整圈）。
- TAG 若坐在下标 **4**：在 6 人全在局时 **rank=2**，`lateFactor = 1/5 = 0.2` → **EARLY**（非盲）。

---

## 6. 相关文件（快速跳转）

| 文件 | 职责 |
|------|------|
| `DpRoomServiceImpl.java` | 调用 `decideActionIfReady` / `NpcAction`；UTG = dealer+3 |
| `DpNpcEngine.java` | `decideActionIfReady`、`decideBotAction`、`StyleProfile` 预设、`NPC_MOOD_ENABLED`、`NPC_SHARK_OPPONENT_DB_ENABLED`、`getStyleByBotType` |
| `DpNpcUnifiedPreflopStrategy.java` | 翻前统一：`decide`、`computePreflopLateFactor`、`computeRangeLevel`、`thresholdsFor`、`groupOf`、各 spot 决策与 sizing |
| `DpUtilHandEvaluator.java` | 翻后/展示用牌力；**与 G1–G8 不同体系** |

---

## 7. Shark 对手画像持久化（可选关闭）

- `DpNpcEngine.NPC_SHARK_OPPONENT_DB_ENABLED`（默认 `false`）：为 false 时 **`DpNpcSharkOpponentMemoryService`** 不写读 **`dp_shark_opponent_profile`**。
- **牌谱入库**由 `DpHandHistoryObservedImpl` / `DpHandHistoryPersistServiceImpl` 等负责，与上述开关无关。

---

*文档版本：与当前主分支 `DpNpcUnifiedPreflopStrategy` / `DpNpcEngine` 翻前早退逻辑对齐；若改 `spotBy` 或预设映射，请同步更新本节。*
