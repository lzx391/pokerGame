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

`DpNpcEngine` 中定义了三个机器人昵称常量：

- `BOT_Demo` → 演示/普通风格机器人；
- `BOT_Maniac` → 疯子风格机器人；
- `BOT_Shark` → 聪明型机器人。

```java
public static final String DEMO_BOT_NICKNAME = "BOT_Demo";
public static final String MANIAC_BOT_NICKNAME = "BOT_Maniac";
public static final String SHARK_BOT_NICKNAME = "BOT_Shark";
```

判断是否为机器人：

```java
public static boolean isBotPlayer(DpPlayer p) {
    if (p == null) return false;
    String name = p.getNickname();
    return DEMO_BOT_NICKNAME.equals(name)
            || MANIAC_BOT_NICKNAME.equals(name)
            || SHARK_BOT_NICKNAME.equals(name);
}
```

类型枚举：

```java
private enum BotType {
    DEMO,
    MANIAC,
    SHARK
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

- `EASY`、`MEDIUM`、`HARD`。

参数控制：

- `strengthNoise`：牌力评估时的“看错概率”；
- `potOddsNoise`：赔率计算误差；
- `ignorePositionProb`：忽视位置的概率；
- `ignorePotOddsProb`：忽视赔率的概率；
- `missBluffCatchProb`：错过抓诈唬机会的概率；
- `randomSpewProb`：偶尔完全乱来一次的概率。

当前约定：

- `BOT_Demo` / `BOT_Maniac` 大致属于 MEDIUM；
- `BOT_Shark` 使用 HARD 设置，决策更稳定、更少低级错误。

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

### 7.1 BOT_Demo（普通玩家）

- 翻前 / 翻后逻辑较简单：
  - 弱牌 + 高跟注成本 + 危险牌面 → 偏向弃牌；
  - 大多数时间选择跟注/过牌；
  - 小概率加注，强牌加注力度略大。
- 难度：中等，适合作为“基础机器人”用于流程演示和初学者对战。

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

