# DP 游戏 - NPC / AI 模块说明（后端实现视角）

> 本文件专门记录德州扑克机器人（NPC）相关的后端实现，方便以后调参和维护。  
> 游戏整体流程、接口说明仍然以 `DPGAME.md` 为主，这里只关注 AI 细节。

---

## 1. 文件与结构总览

- **核心文件**
  - `DpNpcEngine`：NPC 行为引擎（决策、牌面评估、思考时间等全在这里）。
  - `DpHandEvaluator`：通用牌型评估工具（7 选 5、HandStrength 等）。
  - `DpRoomServiceImpl`：房间与对局总控，只做“调用 NPC 引擎”和“执行 bet/fold”，不再直接写 NPC 算法。

- **与房间服务的关系**
  - `DpRoomServiceImpl` 内部判断当前行动玩家是否为机器人：
    - 使用 `DpNpcEngine.isBotPlayer(DpPlayer)`；
  - 如果是机器人：
    - 调 `DpNpcEngine.decideActionIfReady(DpRoom room, DpPlayer bot)` 拿到一个 `BotAction`；
    - 再根据 `BotAction` 调用自身的 `bet(roomId, nickname, amount)` 或 `fold(roomId, nickname)`。

---

## 2. 机器人识别与类型

### 2.1 昵称约定

`DpNpcEngine` 中定义了多个机器人昵称常量：

- `BOT_Fish` → 简单鱼式机器人（原 BOT_Demo，范围较宽、偏被动，适合新手与流程演示）；
- `BOT_Maniac` → 疯子风格机器人；
- `BOT_Tag` → 紧凶型机器人（Tight-Aggressive）；
- `BOT_Shark` → 聪明型机器人。

```java
public static final String DEMO_BOT_NICKNAME = "BOT_Fish";
public static final String MANIAC_BOT_NICKNAME = "BOT_Maniac";
public static final String TAG_BOT_NICKNAME   = "BOT_Tag";
public static final String SHARK_BOT_NICKNAME = "BOT_Shark";
```

判断是否为机器人：

```java
public static boolean isBotPlayer(DpPlayer p) {
    if (p == null) return false;
    String name = p.getNickname();
    return DEMO_BOT_NICKNAME.equals(name)
            || MANIAC_BOT_NICKNAME.equals(name)
            || SHARK_BOT_NICKNAME.equals(name)
            || TAG_BOT_NICKNAME.equals(name);
}
```

类型枚举：

```java
private enum BotType {
    DEMO,
    MANIAC,
    SHARK,
    TAG
}
```

昵称 → 类型映射：

```java
public static BotType getBotTypeByNickname(String nickname) {
    if (DEMO_BOT_NICKNAME.equals(nickname)) {
        return BotType.DEMO;
    }
    if (MANIAC_BOT_NICKNAME.equals(nickname)) {
        return BotType.MANIAC;
    }
    if (SHARK_BOT_NICKNAME.equals(nickname)) {
        return BotType.SHARK;
    }
    if (TAG_BOT_NICKNAME.equals(nickname)) {
        return BotType.TAG;
    }
    return null;
}
```

---

## 3. 房间服务如何调用 NPC

### 3.1 定时器中的调用链

在 `DpRoomServiceImpl` 的定时任务中，当轮到某个玩家行动时：

1. 如果当前玩家是 NPC（`DpNpcEngine.isBotPlayer(p)` 返回 true）：
2. 调用 `DpNpcEngine.decideActionIfReady(room, p)`：
   - 如果还在“思考时间窗口内”，返回 `null`，本次 tick 不动；
   - 如果已经到预定出手时间，则返回一个 `BotAction`（类型 + 金额）。
3. 服务类根据 `BotAction` 的类型调用自身已有接口：
   - `FOLD` → `fold(roomId, nickname)`；
   - `CALL_OR_CHECK` → 计算需要跟注金额 `callAmount`，调用 `bet(roomId, nickname, callAmount)`；
   - `RAISE/ALL_IN` → `bet(roomId, nickname, action.getAmount())`。

简化示意（伪代码）：

```java
if (DpNpcEngine.isBotPlayer(p)) {
    DpNpcEngine.BotAction action = DpNpcEngine.decideActionIfReady(room, p);
    if (action != null) {
        switch (action.getType()) {
            case FOLD: ...
            case CALL_OR_CHECK: ...
            case RAISE:
            case ALL_IN: ...
        }
    }
}
```

### 3.2 结算后情绪更新

结算 (`autoSettle`) 后，`DpRoomServiceImpl` 会根据机器人本手盈亏调整 `DpPlayer.mood`：

- 赢了钱 → `mood += 0.2`；
- 输了钱 → `mood -= 0.2`；
- 范围被裁剪在 `[-1.0, 1.0]`。

`DpNpcEngine` 在决定行动和思考时间时会参考此情绪值：

- 情绪高（正数） → 更凶、更快出手；
- 情绪低（负数） → 更保守、思考时间偏长。

---

## 4. 决策要素与牌力评估

### 4.1 粗粒度牌力 `SimpleStrength`

`DpHandEvaluator` 负责从若干张牌中评估出一个 `HandStrength`，然后 `DpNpcEngine` 将其压缩成四档：

- `WEAK`：高牌、很弱的一对等；
- `MEDIUM`：普通一对、两对、三条；
- `STRONG`：顺子、同花；
- `MONSTER`：葫芦、四条、同花顺以上。

preflop 阶段，如果公共牌不足 3 张，就直接根据手牌做一个简单判定（大对、高连牌等）。

### 4.2 公共牌危险度 `BoardDanger`

根据公共牌判断当前牌面是：

- `DRY`（干燥）：不会很容易凑出顺子/同花；
- `WET`（湿润）：存在明显的听同花/听顺子结构。

逻辑大致是：

- 某花色数量 ≥ 3 → 视为同花危险；
- 连续点数结构长度 ≥ 3 → 视为顺子危险。

### 4.3 位置 `TablePosition`

根据当前机器人在 `players` 列表中的位置、庄家位置、盲注位置，粗略分类为：

- `EARLY`：早位；
- `MIDDLE`：中位；
- `LATE`：庄家及其右手（后位）；
- `BLINDS`：小盲/大盲位。

---

## 5. 风格与难度参数

### 5.1 风格 `NpcStyle` → `StyleProfile`

内部枚举：

- `TIGHT_AGGRO`：紧凶；
- `LOOSE_AGGRO`：松凶；
- `TIGHT_PASSIVE`：紧弱；
- `LOOSE_FUN`：松散娱乐型。

每种风格有一组参数（简化理解即可）：

- `preflopTightness`：翻前范围紧/松；
- `aggression`：下注/加注意愿；
- `bluffFrequency`：诈唬频率；
- `callStation`：爱不爱跟到底；
- `stealBlindFrequency`：偷盲频率；
- `checkRaiseFear`：怕不怕被 check-raise。

目前这组参数主要用于未来扩展，当前 DEMO/MANIAC/SHARK 逻辑是显式在代码里区分，但可以继续往“参数驱动”方向演进。

### 5.2 难度 `NpcDifficulty` → `DifficultyProfile`

难度枚举：

- `EASY`、`MEDIUM`、`HARD`、`PRO`。

参数控制：

- `strengthNoise`：牌力评估时的“看错概率”；
- `potOddsNoise`：赔率计算误差；
- `ignorePositionProb`：忽视位置的概率；
- `ignorePotOddsProb`：忽视赔率的概率；
- `missBluffCatchProb`：错过抓诈唬机会的概率；
- `randomSpewProb`：偶尔完全乱来一次的概率。

当前约定（内部通过 `BotType` → `NpcDifficulty` 映射实现）：

- `BOT_Fish`（DEMO 类型）：使用 `EASY` 设置，更容易出现简单错误，整体偏弱，主要适合新手和流程演示；
- `BOT_Maniac`：使用 `MEDIUM` 设置，整体偏松凶，但仍然会出现明显失误；
- `BOT_Tag`：使用 `HARD` 设置，范围更紧、更偏价值下注，基本不额外引入“难度削弱噪声”，但不做复杂“读对手”调整；
- `BOT_Shark`：使用 **PRO** 设置：在现有策略逻辑下关闭所有“难度削弱噪声”（不再额外看错牌、算错赔率、忽视位置/赔率、随机乱来等），尽量发挥策略上限；
- 若未来需要一个略弱一点但仍然“聪明”的 Shark，可以改回使用 `HARD` 档，重新引入少量噪声。

---

## 6. 机器人“思考时间”与节奏

为了让机器人看起来更像真人，`DpPlayer` 中增加了字段：

- `nextBotActionTime`：下一次允许自动行动的时间戳（毫秒）。

流程：

1. 轮到机器人行动时，`decideActionIfReady` 首先检查 `nextBotActionTime`：
   - 如果为 0：根据当前牌力、类型、情绪计算一个延迟（例如弱牌 0.2~1.5 秒、强牌 3~8 秒等），写入 `nextBotActionTime`，本次不真正行动；
   - 如果当前时间尚未到达 `nextBotActionTime`：直接返回 `null`，表示“还在想”；
   - 如果当前时间超过 `nextBotActionTime`：清零并真正计算一次动作。

2. 思考时间配置通过 `BotThinkProfile` 针对不同类型单独设置：
   - `THINK_DEMO`、`THINK_MANIAC`、`THINK_SHARK` 三套参数；
   - 不同类型可以调成：
     - 疯子基本秒出；
     - Shark 强牌更爱长考。

3. 情绪会略微放大/缩短思考时间：
   - 连赢 → 平均思考更快；
   - 连输 → 稍微拖长。

---

## 7. 各机器人类型的总体性格

### 7.1 BOT_Fish（简单鱼式玩家，原 BOT_Demo）

- 翻前 / 翻后逻辑较简单：
  - 弱牌 + 高跟注成本 + 危险牌面 → 偏向弃牌；
  - 大多数时间选择跟注/过牌；
  - 小概率加注，强牌加注力度略大。
- 难度：简单（EASY），适合作为“基础机器人”用于流程演示和初学者对战。

### 7.2 BOT_Maniac（疯子）

- 非常不爱弃牌：
  - 只有在弱牌+巨大压力+危险牌面时，有一定概率弃牌；
  - 其余情况大量选择加注或 all-in。
- 没有跟注成本时（可以 free check）：
  - 占多数情况主动下注（2~4 个大盲），利用位置和 fold equity；
  - 少量情况选择 check 装弱。
- 有跟注成本时：
  - 少量只是跟注；
  - 大部分加注（在需要跟注基础上额外加多个大盲）；
  - 一定比例直接 all-in（体现疯子风格）。

### 7.3 BOT_Shark（聪明型）

- 综合考虑：
  - 自己的粗粒度牌力 `SimpleStrength`；
  - 牌面危险度 `BoardDanger`；
  - 对手近期 / 长期统计 `PlayerStats`（VPIP、PFR 等）；
  - 当前对手是否偏石头 / 疯狗；
  - 对手动作可信度 `estimateActionCredibility`（石头/紧型加注更信、疯狗/松型更不信，会调弃牌率）；
  - 底池赔率 `computePotOdds`（跟注成本相对底池大时略增弃牌率，赔率好时略减）；
  - 当前自己情绪。
- 决策上：
  - **免费看牌（对手过牌、无需跟注）时绝不弃牌**，至少过牌看摊牌；只有存在跟注额时才会考虑弃牌。
  - 遇到石头型对手加注 → 更相信对方是强牌，弃牌概率提升；
  - 遇到疯狗型对手 → 减少弃牌概率，更愿意跟注或轻量反击；
  - 超强牌在早期街会留一些慢打空间，河牌阶段再打大一点。
- 控制台会输出简单的决策日志（方便调参）：
  - `[BOT_Shark] 决策=FOLD / CALL_OR_CHECK / RAISE / CALL_OR_CHECK(默认)`，附带房间号、阶段、牌力档、筹码、跟注额、情绪等。
  - `[BOT_Shark-Explain]` 还会额外打印本手中其他玩家的筹码深度信息，比如 `minStackBB` / `avgStackBB`，方便观察是否因为桌上有短码/深码而调整了下注量。

### 7.4 BOT_Tag（紧凶型 TAG）

- 整体思路：
  - **信息来源**：只使用自身牌力 `SimpleStrength`、牌面危险度 `BoardDanger`、当前阶段、跟注成本占筹码比例 `callRatio` 和简单赔率 `computePotOdds`；  
    不会像 Shark 一样综合 `PlayerStats`、筹码深度、多玩家牌局结构做复杂“读对手”与筹码压制。
  - **风格**：起手选择相对紧（弱牌在高压力或湿牌面更容易弃牌），拿到 STRONG/MONSTER 牌时偏向价值加注，整体更像一个稳健的 TAG 常客。
- 典型行为：
  - 免费看牌（`callAmount == 0`）时：
    - 强牌/怪兽牌在 flop/turn/river 有较高概率下注获取价值（0.5~0.7 区间）；  
    - 其余情况大多选择过牌控制底池。
  - 有跟注成本时：
    - 弱牌 + 高 `callRatio` + 湿牌面 → 明显倾向弃牌；  
    - 中等牌在高压力 + 湿牌面时也会有一定弃牌概率；  
    - 简单用 `potOdds` 做“赔率好/差”的修正，但不做基于对手历史的风格识别。
  - 价值加注：
    - STRONG/MONSTER 时，根据阶段在若干 BB 区间内随机加注，河牌阶段强牌会略微放大加注倍数；  
    - MEDIUM 牌更多是小到中等加注或仅跟注，避免在早期街就把底池打得过大。
  - 不会主动引入多余“乱来”噪声，所有随机性仅用来在「一小段合理范围内」分布下注尺度和概率。

#### 7.3.1 后手筹码（Stack）意识与隐含赔率/压迫力

- 为了让 SHARK 在翻后下注时更像一个“懂筹码深度”的常客，引入了一个简化的 **后手筹码分析**：
  - 在 `DpNpcEngine` 内部新增 `StackContext`：
    - `minStack / maxStack / avgStack`：本手牌中，除自己以外、未弃牌且未离桌玩家的剩余筹码；
    - `minStackBB / maxStackBB / avgStackBB`：上述筹码按大盲 `BB` 换算成的倍数。
  - 通过 `analyzeStacks(DpRoom room, DpPlayer hero)` 计算得到 `StackContext`：
    - 遍历 `room.getPlayers()`，忽略 `null`、`fold`、`leftThisHand` 以及 hero 自己；
    - 统计 min / max / avg，并用当前 `DpRoom.getBBChips()` 换算为 BB 倍数；
    - 无其他有效玩家时返回全 0，保证调用安全。
- 约定上的筹码深度阈值（只是启发式规则，不是 GTO）：
  - `minStackBB <= 20`：视为桌上存在 **短码病人 short stack**；
  - `minStackBB > 60 && avgStackBB > 80`：视为整体是 **深码桌 deep stack table**。

##### (a) 无人下注时（`callAmount == 0`）的调整

- 仍然以原有逻辑为基础：
  - 先根据牌力 `SimpleStrength` 选择一个 `baseFactor`（约 0.45~0.8），再乘一个 0.9~1.2 的 `randomFactor`：
    - `target ≈ pot × baseFactor × randomFactor`；
  - 然后做一些启发式修正：
    - **短码 + 强牌/怪兽牌**：  
      - 条件：`stackCtx.minStackBB > 0 && stackCtx.minStackBB <= 20 && st ∈ {STRONG, MONSTER}`；
      - 使用 `effectiveBase = min(pot, minStack)` 作为基准池；
      - 在 40%~80% 区间内随机一个系数，得到 `shortTarget ≈ effectiveBase × [0.4, 0.8]`；
      - 若 `shortTarget > target`，就提升到 `shortTarget`。  
      - 直观含义：桌上有典型短码且自己有强牌时，把下注量调到“短码一跟就接近 all‑in”的区间，提高从短码身上获取隐含价值。
    - **整体深码 + 弱牌，在 flop/turn 的 c‑bet**：  
      - 条件：`stackCtx.avgStackBB >= 80 && st == WEAK && stage ∈ {flop, turn}`；
      - 将 `target` 乘以 0.8，偏向较小的 1/3~1/2 pot 下注；
      - 直观含义：大家都很深码而自己只是在 flop/turn 有中等/弱牌时，用更小的 c‑bet 维护范围，避免在很早的街就把底池抬得过大。
- 然后仍按原来的规则：
  - 保证 `target >= 2 * BB`；
  - `raiseAmount = min(chips, target)`；
  - 最终统一吸附到“小盲 `SB` 的整数倍”，避免出现 78 这种奇怪金额。

##### (b) 已有人下注时（`callAmount > 0`）的调整

- 原有逻辑：  
  - 先根据牌力 + 当前街道选择一个 `extraMin` / `extraMax`（若干个 BB），在区间内随机一个 `extra`；
  - 得到 `target = callAmount + extra`；
  - 再截断到不超过自己筹码，然后吸附为 `SB` 的整数倍。
- 新增的筹码深度意识主要体现在“主要加注者 stack”的判断上：
  - 利用已有的 `aggressor`，取 `aggressor.getChips()` 作为对手后手筹码；
  - 计算 `aggressorStackBB = aggressorStack / BB`。
- 规则化调整：
  - **对短码（≤ 20BB）的主要对手**：
    - 若 SHARK 拿的是 **强牌/怪兽牌**：
      - 在对手后手筹码的 60%~100% 区间随机一个 `pressureFactor`；
      - 计算 `desired = callAmount + aggressorStack × pressureFactor`；
      - 如果 `desired > target`，则把 `target` 拉高到 `desired`；  
      - 直观含义：用更大、接近对手 all‑in 的加注量，让短码一跟就“入 all‑in 区间”，最大化价值。
    - 若 SHARK 只是 **弱牌 bluff**：
      - 设定 `maxBluffExtra = 3 * BB`，`bluffCap = callAmount + maxBluffExtra`；
      - 如果原来的 `target > bluffCap`，则压回到 `bluffCap`；  
      - 直观含义：不要在短码身上做太过激的 bluff，加注量控制在几个大盲之内，防止被短码宽范围轻易跟 all‑in。
  - **整体深码桌（`minStackBB > 60 && avgStackBB > 80`）**：
    - 若 SHARK 为 **强牌/怪兽牌**：`target *= 1.15`，略微放大额外加注，体现“深码可以多街玩、河牌可以打大一点”的倾向；
    - 若 SHARK 为 **弱牌 bluff**：`target *= 0.9`，轻微缩小 bluff 尺度，避免在深码对手面前做过大的单街 bluff。
- 最终流程保持不变：
  - `raiseAmount = min(chips, target)`；
  - `raiseAmount` 再被吸附为 `SB` 的整数倍，不会超过自己筹码。

---

## 8. 行为修正与未用方法整合说明

### 8.0 免费看牌不弃牌（已修正）

- **问题**：此前在“对手河牌过牌、无需跟注”时，SHARK 仍可能因对手被识别为“石头型”而提高弃牌率并弃牌（例如拿着 QQ、牌面干燥却弃牌），不符合常理——至少应过牌免费看摊牌。
- **修正**：所有机器人（DEMO / MANIAC / SHARK）在 **跟注额为 0（免费看牌）时一律不选择 FOLD**，只会 CALL_OR_CHECK 或 RAISE。SHARK 的“对手风格调整弃牌率”和“底池赔率调整”也仅在 `callAmount > 0` 时生效。

### 8.1 已整合的辅助方法

- **`computePotOdds`**：原未在决策中使用，现已在 SHARK 的弃牌逻辑中整合。当存在跟注额时，计算底池赔率；赔率差（potOdds > 0.5）时略增弃牌率，赔率好（potOdds < 0.25）时略减弃牌率。
- **`estimateActionCredibility`**：原未在决策中使用，现已在 SHARK 中整合。根据对手类型（NIT/TIGHT → 高可信度、MANIAC/LOOSE → 低可信度）调整弃牌率：对手偏诈唬（LOW）时 baseFold × 0.6，对手偏价值（HIGH）时 baseFold × 1.2。
- **`StackContext` + `analyzeStacks`**：新增的后手筹码分析工具，用于：
  - 统计本手中其他未弃牌玩家的 `minStack` / `maxStack` / `avgStack` 及其 BB 倍数；
  - 在 SHARK 的下注/加注逻辑中区分“短码病人”（minStackBB ≤ 20）和“整体深码桌”（minStackBB > 60 & avgStackBB > 80）；
  - 根据这些信息微调 pot × 系数 和 callAmount + 若干 BB 的目标金额，体现隐含赔率和筹码压迫力。

---

## 9. Shark 2.0 决策要素补充（赢率 / 赔率 / 筹码 / 位置 / 摊牌 bluff）

### 9.1 粗略赢率桶 vs 底池赔率

- 在 SHARK 的弃牌逻辑中，引入了一个非常简单的“赢率估计桶”：
  - `estimateEquityBucket(SimpleStrength st, String stage)`：
    - preflop：MONSTER≈0.75，STRONG≈0.60，MEDIUM≈0.40，WEAK≈0.25；
    - 翻后（flop/turn/river）：MONSTER≈0.85，STRONG≈0.65，MEDIUM≈0.45，WEAK≈0.20。
- 对有跟注成本的场景（`callAmount > 0`）：
  - 先算底池赔率 `potOdds = call / (pot + call)`；
  - 再比较 `equity` vs `potOdds`：
    - 若 `equity + EQUITY_POTODDS_EPS < potOdds` → 在原有 `baseFold` 基础上 +0.15，明显不划算时更倾向弃牌；
    - 若 `equity > potOdds + EQUITY_POTODDS_MARGIN` → `baseFold *= 0.8`，明显划算时更不容易被吓走。
- 大额跟注保护：
  - 当 `callRatio = callAmount / chips > 0.5` 且 `potOdds > 0.45` 且 `牌力 == WEAK` 时：
    - `baseFold` 至少提升到 ≈0.85，弱牌面对大锅有更强烈的弃牌倾向。

### 9.2 筹码深度与筹码比压制

- 关键常量集中在 `DpNpcEngine` 顶部：
  - `SHORT_STACK_BB = 20.0`：典型短码；
  - `DEEP_STACK_MIN_BB = 60.0`：单个玩家深码下限；
  - `DEEP_TABLE_AVG_BB = 80.0`：整桌平均深码阈值。
- 辅助函数：
  - `StackContext analyzeStacks(...)`：如前文；
  - `computeHeroVsVillainStackRatio(DpPlayer hero, DpPlayer villain)`：返回 heroChips / villainChips。
- 使用策略概览：
  - **短码 vs 强牌/怪兽**：
    - 无人下注时（free bet）：若存在 `minStackBB <= SHORT_STACK_BB` 且 SHARK 为 STRONG/MONSTER：
      - 目标下注 `target` 会被拉近“短码一跟就接近 all-in”的区间（约对方后手 40%~80%）。
    - 已有人下注时：
      - 主要对手后手 ≤ 20BB 且 SHARK 为 STRONG/MONSTER 时：
        - 在 `60%~100%` 对手后手之间随机一个压力系数，提升 `target`，让短码一跟基本进入 all-in 区。
      - 若是 WEAK 牌 bluff 面对短码：
        - 将加注额上限限制在 `callAmount + 3*BB` 左右，避免在短码面前做过头的 bluff。
  - **整体深码桌（minStackBB>60 && avgStackBB>80）**：
    - 强牌/怪兽：
      - 有人下注时，`target` 约乘以 1.15，体现深码局更敢打大一点的 value bet；
      - turn/river 且 `heroVsVillainStackRatio >= 1.5` 且 `villainStackBB ∈ [25,60]` 时：
        - 再对 `target` 做一次轻微放大（约 1.15），表现为“筹码优势下的价值压制”，而不是盲目 bluff。
    - 弱牌 bluff：
      - 在深码桌时略微缩小 bluff 尺度（约乘 0.9），避免深码对手轻松跟大注。

### 9.3 位置与翻后 c-bet / 防守

- 位置枚举仍然是 `TablePosition`（EARLY/MIDDLE/LATE/BLINDS）。
- 晚位无人下注时的 c‑bet（更像常客）：
  - 条件：`callAmount == 0`、`stage ∈ {flop, turn}`、`position == LATE`、`BoardDanger == DRY`；
  - 若牌力 `WEAK/MEDIUM`：
    - 计算一个 `baseCbetProb`（约 0.45~0.55），根据对手风格和整体深码情况略微 +/‑：
      - 松凶/疯狗或短码 → 概率略减；
      - 紧弱/整体深码 → 概率略增；
      - 情绪高时再略微放大；
    - 若命中该概率：直接在 1/3~1/2 pot 区间随机一个下注量，吸附到 SB 整数倍后做一次小额 c‑bet。
- 早位 + 湿牌面 + 弱牌的防守：
  - 条件：`position == EARLY && BoardDanger == WET && SimpleStrength == WEAK`；
  - 有人下注时：
    - 在原有 `callProb` 基础上略微下调（相当于提升 `baseFold`），让 SHARK 在 bad board 上更愿意提前放弃；
  - 无人下注时：
    - 不额外鼓励主动下注，更多选择 check/back，避免在早位湿牌面上做轻率 c‑bet。

### 9.4 摊牌 bluff 倾向（showdown bluffiness）

- `PlayerStats.SingleHandStats` 新增字段：
  - `finalRankCategory`：摊牌时的最终牌型大类（同 `HandStrength.rankCategory`）。
- 结算处（`autoSettle`）会在为每位玩家更新统计时写入：
  - 仅在 `wentToShowdown == true` 且存在牌力评估结果时设置此字段。
- 新增辅助：
  - `estimateShowdownBluffiness(PlayerStats stats)`：
    - 只看最近 N 手（目前 10 手）中 `wentToShowdown == true && raised == true` 的局；
    - 统计其中 `finalRankCategory <= 4`（高牌/一对/两对/三条）的比例；
    - 返回 [0,1]，越高说明“经常拿一般牌/空气牌打到河牌摊牌”，bluff 倾向越强。
- 接入 SHARK 的决策：
  - 在已有 `estimateActionCredibility` 的基础上，再用 `showdownBluffiness` 调整 `baseFold`：
    - 若 `bluffiness > 0.4`：`baseFold *= 0.7`，在大尺度上更不轻易相信对手强牌叙事；
    - 若 `bluffiness < 0.1`：`baseFold *≈ 1.2`，对摊牌大多是强牌的“诚实玩家”提升整体弃牌率。
  - 日志增强：
    - 在所有 `[BOT_Shark-Explain]` 输出中追加：`showdownBluffiness=0.xx`，方便观测调参。

---

## 9. 以后如果要扩展 / 修改

### 9.1 新增一个机器人类型

例如你想要 `BOT_Rock`（石头人）：

1. 在 `DpNpcEngine` 中新增昵称常量 + BotType 枚举值；
2. 在 `getBotTypeByNickname` 中增加对应分支；
3. 在 `decideBotAction` 的 switch 里增加一个 `case ROCK:`，写一套适合石头人的逻辑（几乎只玩超好牌，其他大多弃牌或跟注）。

前端只要在房主工具里提供一个“添加 BOT_Rock” 的按钮，通过 `readyNextHand(roomId, "BOT_Rock")` 把它报名到下一局即可。

### 9.2 调整难度 / 风格

如果某个机器人太强或太弱：

- 调整对应 `NpcDifficulty` 的参数（看错牌、忽视赔率、随机乱来的概率等）；
- 或者在 `decideBotAction` 中直接微调概率：
  - 比如给 DEMO 增加一点诈唬；
  - 给 Maniac 降低 all-in 的频率；
  - 给 Shark 增加一些“装疯卖傻”的随机行为。

### 9.3 调整日志输出

目前只对 `BOT_Shark` 打印了相对详细的控制台日志。  
如果需要，可以类似地给 DEMO / MANIAC 在 `decideBotAction` 内部加 `System.out.println`，打印你关心的字段即可。

---

## 10. 安全边界与注意事项

- NPC 只通过已有的 `bet` / `fold` 接口操作游戏状态，不会直接改底层字段；
- 思考时间上限有限（默认不超过 12 秒，兜底 20 秒），不会妨碍原有的 30 秒超时弃牌规则；
- 所有 NPC 逻辑都封装在服务层，不影响 Controller 接口与前端协议；
- 随时可以通过关闭“添加机器人”的入口来回到纯真人对局模式。

