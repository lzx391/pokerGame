# 规则 NPC 统一翻前决策 · 数据流向（精细版）

本文描述 **`DpNpcUnifiedPreflopStrategy`** 与 **`DpNpcEngine.decideBotAction`** 在 **`stage == preflop`** 时的完整调用链、参与字段与方法分支。**不包含** `BOT_LLM`（走 `DpLlmNpcDecisionService`）。

---

## 1. 总入口与调用顺序

| 步骤 | 位置 | 输入 | 输出 |
|------|------|------|------|
| ① 谁在行动 | `DpRoomServiceImpl` 定时逻辑 | `room.getCurrentActorIndex()`，`players.get(actor)` | 确认为 NPC |
| ② LLM 分流 | 同上 | `nickname.equals(BOT_LLM)` | LLM 不进本文链路 |
| ③ 规则 NPC | `DpNpcEngine.decideActionIfReady(room, bot)` | `room`、`bot`；且当前行动位必须是该 `bot` | `BotAction` |
| ④ 翻前统一 | `DpNpcEngine.decideBotAction` → `DpNpcUnifiedPreflopStrategy.decide` | 见 §3 | `BotAction` |
| ⑤ 落地 | `DpRoomServiceImpl.NpcAction(room, p, action)` | `BotAction` | 更新下注/弃牌并推进局面 |

**门卫（`decideActionIfReady`）**：校验 `room.isPlaying()`、actor 索引、`bot` 未弃牌/未全下/未离桌；`bot.getNickname()` → `getBotTypeByNickname` → `BotType`（如 TAG）。

---

## 2. 六人桌座位约定（便于对照「第几席」）

以下与 `DpRoomServiceImpl` 发牌后一致：`players` 长度 **6**，下标 **0～5**。

- **庄家**：`players[dealerIndex]`，常为开局轮转后的 `dealer`。
- **小盲 / 大盲**：`(dealer+1)%6`、`(dealer+2)%6`。
- **翻前首位行动 UTG**：`(dealer+3)%6`。
- **行动顺序（一整圈）**：`UTG → … → BTN → SB → BB`，即从下标 `(dealer+3)%6` 开始顺时针绕一桌。

**「第四位」**：若指列表第 4 个座位 → 下标 **`3`**，在庄家为 **`0`** 时正好是 **UTG**（若全员在局，则最先表态）；若指行动次序第 4 个 → 对应顺序里的第四个座位（依当下弃牌会变）。

---

## 3. `decideBotAction`：翻前参数组装

方法：`DpNpcEngine.decideBotAction(room, bot, type)`

### 3.1 始终计算的量（翻前也会算，但翻前不依赖 `SimpleStrength`）

| 变量 | 来源 |
|------|------|
| `chips` | `bot.getChips()` |
| `callAmount` | `max(0, room.getCurrentBetToCall() - bot.getBet())` |
| `callRatio` | `callAmount / chips`（跟满或超出则为 `1.0`） |
| `stageForNpc` | `room.getCurrentStage()` |
| `random` | `buildHandRandom(room, bot)`（受 `NPC_HAND_SEED_FOR_DECISIONS` 影响） |
| `boardDanger` | `evaluateBoardDanger(...)` — **翻前在统一策略返回后不再使用** |
| `mood` | `NPC_MOOD_ENABLED ? bot.getMood() : 0` |
| `style` | `STYLE_PROFILE_MAP.get(getStyleByBotType(type))` |

**注意**：`getTablePosition(room, bot)` 仍会执行，但翻前在 **`prefUnified != null` 时直接 `return`**，**不参与**统一翻前；统一翻前位置使用 **`computePreflopLateFactor` + `pseudoTablePosition`**（见 §5）。

### 3.2 风格映射（示例：TAG）

- `BotType.TAG` → `NpcStyle.TIGHT_AGGRO` → `StyleProfile.presetTightAggro()`  
- 传入 `DpNpcUnifiedPreflopStrategy.decide` 的字段：**`vpip, pfr, callStation, foldToPressure`**（以及引擎侧的 `mood, random, type`）。  
- **`cbetFreq` / `bluffFreq`**：翻前不进 `decide`，翻后分支使用。

### 3.3 翻前唯一出口

当 `stageForNpc` 为 `"preflop"` 时：

```text
DpNpcUnifiedPreflopStrategy.decide(
    room, bot,
    callAmount, callRatio,
    style.vpip, style.pfr, style.callStation, style.foldToPressure,
    mood, random, type)
```

非 `null` 则 **`return`**，不再进入 `switch (type)` 的翻前逻辑。

---

## 4. `DpNpcUnifiedPreflopStrategy.decide`：内部流水线

方法签名：`decide(room, hero, callAmount, callRatio, vpip, pfr, callStation, foldToPressure, mood, random, botType)`

### 4.1 守卫与基础数据

| 步骤 | 方法/字段 | 说明 |
|------|-----------|------|
| 空指针/阶段 | `room`, `hero`, `random`；`currentStage == "preflop"` | 不满足则 `null` 或无效 |
| 盲注 | `bb`, `sb` | `bb <= 0` → `null` |
| 手牌 | `parseHole` → `HoleInfo` | 非法 → **`CALL_OR_CHECK, 0`** |
| 存活人数 | `countActivePlayers(room)` | 未弃牌且未 `leftThisHand` |
| 激进方 | `findAggressor(room, hero)` | 当前圈最高 `bet` 且非 hero |
| 对手统计 | `playerStatsMap.get(aggressor.nickname)` | 可 `null` |
| 对手松紧桶 | `estimateVillainTier(aggressorStats)` | 影响 3bet/4bet bluff 等 |
| 码深 | `heroStackBB`，`effStackBB` | 有 aggressor 时 eff 取双方筹码 **min** |
| 加注层级 | `room.getRaiseLevel()` | 与 `callAmount` 决定 **Spot** |

### 4.2 `PreflopSpot`（`spotBy(raiseLevel, callAmount)`）

| 条件 | Spot |
|------|------|
| `raiseLevel >= 3` | `FACING_4BET` |
| `raiseLevel == 2` | `FACING_3BET` |
| `raiseLevel == 1 && callAmount > 0` | `FACING_OPEN` |
| `callAmount == 0` | `UNOPENED` |
| 其余 | `UNKNOWN` → 末尾 **`CALL_OR_CHECK`** |

### 4.3 位置：`lateFactor` → 伪 `TablePosition`

1. **`computePreflopLateFactor(room, hero)`**  
   - UTG 起点：`(dealerIndex + 3) % N`（与房间一致）。  
   - 仅保留未弃牌、未离桌玩家，按该顺序列表得到 **`rank`（1-based）**。  
   - **`lateFactor = (rank - 1) / (active - 1)`**，∈ [0,1]；每手每次决策重算，弃牌会改变 `active` 与 `rank`。  
   - **不读** `raiseLevel` / 谁在加注；粗粒度几何 proxy。

2. **`pseudoTablePosition(lateFactor, hero.getBlind())`**  
   - `blind == 1 或 2` → **`BLINDS`**（含 HU 时 BTN/SB）。  
   - 否则三分位：`lateFactor < 0.34` → EARLY；`< 0.67` → MIDDLE；否则 LATE。

### 4.4 `rangeLevel`（1～8，越大越松）

`computeRangeLevel(activePlayers, effStackBB, vpip, callStation, foldToPressure, mood)`：

- 基准 `base = 4`；  
- `activePlayers <= 3` → +2；`<= 5` → +1（**6 人全员在局不加此项**）；  
- `effStackBB >= 50` → +1；`<= 16` → -1；  
- `round((vpip - 0.30) * 5)`、`round(callStation)`、`-round(foldToPressure * 0.8)`、`round(mood)`；  
- `clampInt(1, 8)`；再 **`rangeLevel += rangeLevelBonus(botType)`**（TAG=0，Shark+1，Maniac+2），再 clamp。

### 4.5 阈值 `RangeThreshold`

`thresholdsFor(position, rangeLevel)` 产出五个 **`HandGroup` 上限**：

- `openMaxGroup` — UNOPENED 是否 open  
- `vsOpenContinueMaxGroup` — FACING_OPEN 是否继续  
- `vsOpen3BetValueMaxGroup` — FACING_OPEN 价值 3bet 资格与概率分支  
- `vs3BetContinueMaxGroup` — FACING_3BET 是否继续  
- `vs3Bet4BetValueMaxGroup` — FACING_3BET 价值 4bet 分支  

EARLY 会对 `vsOpen3BetValue` 再收紧一档（见源码）。

### 4.6 手牌桶 `HandGroup`（G1～G8）

- **`g = groupOf(hole)`**：由两张牌的 **数值 rank（2～14）**、**对子**、**同花**、**间隔 gap** 等规则归类（非翻后 `SimpleStrength`）。  
- **`isGroupAtMost(g, maxAllowed)`**：`g.ordinal() <= maxAllowed.ordinal` 表示在允许范围内。

**失真说明**：同一桶内组合真实胜率不同（如部分同结构连张），这是 **8 桶抽象**的代价；细则见源码 `groupOf`。

---

## 5. 分场景子流程（动作层）

以下假定 **`th`**、`rangeLevel`、`spot` 已由上文算完。

### 5.1 `FACING_4BET` → `decideFacing4Bet`

输入侧重：`callRatio`、`effStackBB`、`g`、`tier`、`mood`、`random`；输出 **FOLD / CALL_OR_CHECK / RAISE / ALL_IN**（含便宜跟注、强牌 jam 等分支）。

### 5.2 `UNOPENED` → `decideUnopened`

- `canOpen = isGroupAtMost(g, th.openMaxGroup)`；否 → **`CALL_OR_CHECK, 0`**。  
- 是 → `openSizeBB = baseOpenSizeBB(position, activePlayers, effStackBB, mood, random)`（**2～4BB** 档位 + jitter），再 `raiseAmount = min(chips, openSizeBB * bb)`，`roundToSB` → **`RAISE`**。

**尺度与六旋钮**：open **倍数/档位**不直接乘 `vpip/pfr`；`mood` 参与 `baseOpenSizeBB` 的 jitter。

### 5.3 `FACING_OPEN` → `decideFacingOpen`

1. `canContinue` vs `vsOpenContinueMaxGroup` → 否 **FOLD**。  
2. `can3BetValue` / `can3BetBluff`（bluff 需 `tier == LOOSE_OR_AGGRO` 等）。  
3. `base3betProb`：价值 **0.62**，bluff **0.16**，否则 **0**；盲注 **+0.06**；**+ mood×0.04**；再 **× pfrAggressionScale(pfr)**（`0.35 + 0.65*pfr`），`clamp01`。  
4. `random < base3betProb` → **`compute3BetAmount`** → **RAISE**；否则 **CALL_OR_CHECK**。

### 5.4 `FACING_3BET` → `decideFacing3Bet`

1. `canContinue` vs `vs3BetContinueMaxGroup` → 否 **FOLD**。  
2. `value4bet` / `bluff4bet`（bluff 对 `tier` 与 `effStackBB` 有要求）。  
3. 若 **`callRatio >= 0.35` 且非 value4bet**：高压支路（深码小概率 call，否则 fold 概率与 `callRatio`、`mood`）→ **FOLD / CALL**。  
4. 否则 **`fourBetProb`** × **mood/位置** × **`pfrAggressionScale(pfr)`**，抽样 **4bet**；价值浅码可能 **ALL_IN**；否则 **CALL**。

### 5.5 `UNKNOWN`

→ **`CALL_OR_CHECK, 0`**（兜底）。

---

## 6. 加注数额 vs 风格参数

- **六旋钮**：主要影响 **rangeLevel / 阈值**（动不动）与 **3bet/4bet 概率**（**`pfr`** → `pfrAggressionScale`）。  
- **数额**：`baseOpenSizeBB`、`compute3BetAmount`、`compute4BetAmount` 使用 **位置桶、人数、effStackBB、对手 `currentBetToCall`、`bb/sb`、`random` 抖动**；**不**把 `vpip` 直接乘进 open 大小。  
- **抖动**：3bet/4bet 倍数带 `random`；open 为 **2～4BB** 整数档 + jitter；翻前路径 **不使用** `DpNpcEngine.applySoftNoise`。

---

## 7. 相关源码文件（速查）

| 文件 | 职责 |
|------|------|
| `DpRoomServiceImpl.java` | 定时任务 → `decideActionIfReady` / `NpcAction` |
| `DpNpcEngine.java` | `decideActionIfReady`、`decideBotAction`、`StyleProfile` / `BotType` / `getStyleByBotType` |
| `DpNpcUnifiedPreflopStrategy.java` | 统一翻前：`decide`、`spotBy`、`computePreflopLateFactor`、`computeRangeLevel`、`thresholdsFor`、`groupOf`、各 `decideFacing*` |

---

## 8. 修订记录

- 文档 initial：与当前仓库统一翻前实现一致；若后续改 `spotBy`、阈值或 `groupOf`，请同步更新本节与 §4。
