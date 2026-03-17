## 德扑 NPC / AI 相关类调用关系简图（面向非程序员说明）

> 你可以把整套 NPC 系统想象成：  
> 房间服务（游戏主持人） → 找 NPC 引擎（机器人大脑） → 大脑先收集情报 → 决定这手牌怎么打。

---

### 1. 顶层调用链：谁先找谁？

- **真人 / 前端玩家**
  - 通过接口操作房间（加人、准备、下注、弃牌等）。

- **`DpRoomServiceImpl`（房间服务 / 游戏主持人）**
  - 负责：
    - 管理所有房间 `DpRoom`；
    - 定时心跳检查玩家是否在线；
    - 控制“轮到谁行动”和“是否超时”；
    - 结算一手牌、开新的一手牌。
  - 在“轮到某个玩家行动”的定时任务里：
    - 先从 `roomMap` 里拿到当前房间 `DpRoom`；
    - 根据 `room.getCurrentActorIndex()` 取出当前行动玩家 `DpPlayer p`；
    - **如果 `DpNpcEngine.isBotPlayer(p)` 为真**（说明是机器人）：
      - 调用 `DpNpcEngine.decideActionIfReady(room, p)` 让 NPC 引擎决定动作；
      - 得到一个 `BotAction`，再根据 `BotAction.Type` 调用房间自己的方法：
        - `FOLD` → 调 `fold(roomId, nickname)`；
        - `CALL_OR_CHECK` → 算出需要跟多少，再调 `bet(roomId, nickname, callAmount)`；
        - `RAISE` / `ALL_IN` → 调 `bet(roomId, nickname, action.getAmount())`。

- **`DpNpcEngine`（NPC 引擎 / 机器人大脑）**
  - 只负责两大类事：
    - **识别谁是机器人**：`isBotPlayer(DpPlayer)`；
    - **在需要行动时做决策**：`decideActionIfReady(DpRoom room, DpPlayer hero)`。
  - 不直接改房间里的筹码、底池等，只是返回“建议动作”给 `DpRoomServiceImpl`。

---

### 2. `DpNpcEngine` 内部主要调用关系（简化版）

当 `DpRoomServiceImpl` 调用 `DpNpcEngine.decideActionIfReady(room, hero)` 时，内部大致流程是：

1. **检查思考时间：**
   - 读取 `hero`（`DpPlayer`）上的 `nextBotActionTime` 字段：
     - 如果是 0 → 说明第一次轮到他：  
       - 引擎根据牌力、情绪、机器人类型，生成一个“思考多久”的随机时间，写入 `nextBotActionTime`；  
       - 本次直接返回 `null`，让房间服务“看到机器人还在想”。
     - 如果当前时间还没到 `nextBotActionTime` → 继续返回 `null`；
     - 如果已经超过 → 清空 `nextBotActionTime`，进入真正“算这次怎么打”的逻辑。

2. **构造当前这手牌的“情报包” `SmartContext`：**
   - 内部会调用若干分析函数，收集信息，例如（名字简化理解即可）：
     - 主动进攻者与其统计：`DpPlayer aggressor` + `PlayerStats aggressorStats`；
     - 对手整体风格分档：`VillainRangeTier villainTier`；
     - 当前加注动作的可信度：`ActionCredibility credibility`；
     - 近期摊牌的 bluff 倾向：`estimateShowdownBluffiness(...)` → `showdownBluffiness`；
     - 底池赔率：`computePotOdds(...)` → `potOdds`；
     - 粗略赢率：`estimateEquityBucket(...)` → `equityEst`；
     - 筹码深度：`analyzeStacks(room, hero)` → `StackContext stackCtx`；
     - 多人底池信息：`List<MultiwayVillainInfo> multiwayVillains` 及后位短码/紧玩家数量等；
     - 针对主要对手的反制建议：`analyzeVillainCounterStrategy(...)` → `CounterStrategyProfile counterStrategy`。
   - 上述结果被打包进一个 `SmartContext` 实例，仅作为“数据容器”传入后续决策逻辑中。

3. **根据机器人类型 + 风格 + 难度，选择具体决策路径：**
   - 通过昵称 → 类型枚举：`BotType getBotTypeByNickname(hero.getNickname())`：
     - `BOT_Fish` → `BotType.DEMO`；
     - `BOT_Maniac` → `BotType.MANIAC`；
     - `BOT_Tag` → `BotType.TAG`；
     - `BOT_Shark` → `BotType.SHARK`。
   - 再由 `BotType` 映射到：
     - **风格参数**：`NpcStyle` + `StyleProfile`（紧/松、aggressive 程度、bluff 频率等）；
     - **难度参数**：`NpcDifficulty` + `DifficultyProfile`（看错牌、算错赔率、随机失误的概率）。
   - 对于 SHARK / TAG 等“较聪明”机器人：
     - 会把 `SmartContext` 里收集到的信息一起塞入更高级的决策函数，比如：
       - “根据赢率 vs 底池赔率调整弃牌率”；
       - “识别短码/深码，决定加注尺度”；
       - “结合 multi-way、多玩家筹码结构来调节是否强推价值或者减少 bluff”等。

4. **输出 `BotAction`：**
   - 决策函数最终返回一个 `BotAction`：
     - 包含：动作类型（FOLD / CALL_OR_CHECK / RAISE / ALL_IN），以及需要下注的筹码数量。
   - 这个 `BotAction` 返回给调用方 `DpRoomServiceImpl`，  
     由房间服务实际去调用 `bet` / `fold` 等方法，真正修改游戏状态。

---

### 3. 关键数据类之间的关系（简化成“谁里边引用了谁”）

- **`DpRoom`**
  - 持有：
    - `List<DpPlayer>`：本房间所有玩家；
    - 当前阶段、当前该谁行动（`currentActorIndex`）、当前需要跟注的筹码（`currentBetToCall`）等；
    - 大盲注金额 `BBChips` 等配置；
    - 若干 `DpPot`（底池）信息。
  - 向下被谁用：
    - `DpRoomServiceImpl` 用它来控制对局流程；
    - `DpNpcEngine` 在决策时读取它，分析牌面、底池、筹码分布等；
    - `SmartContext` 的构造函数中部分字段来自 `DpRoom`（比如 pot、筹码深度等）。

- **`DpPlayer`**
  - 持有：
    - 玩家昵称 `nickname`（用来判断是否为机器人）；
    - 筹码 `chips`、本轮已下注 `bet`、是否弃牌 `fold` 等；
    - 心跳时间 `lastHeartBeat`；
    - 机器人思考时间控制字段：`nextBotActionTime`；
    - 情绪值 `mood` 等。
  - 向下被谁用：
    - `DpRoomServiceImpl` 用来判断当前行动者、超时处理、心跳踢人；
    - `DpNpcEngine` 用来识别机器人（`isBotPlayer`）、读取筹码/情绪等做决策；
    - `SmartContext` 中直接保存了当前这手中“主要进攻者”“英雄 hero”等玩家引用。

- **`PlayerStats`**
  - 持有：
    - 各种统计数据：VPIP、PFR、近期摊牌牌型分布、是否经常 bluff 等；
  - 向下被谁用：
    - `DpNpcEngine` 在构建 `SmartContext` 时获取对应玩家的 `PlayerStats`；
    - 决策逻辑通过 `PlayerStats` + 行为记录计算：
      - 对手是 NIT / MANIAC / TAG / 鱼；
      - 对手摊牌 bluff 倾向 `showdownBluffiness` 等。

- **`SmartContext`**
  - 只在 NPC 内部流转，不对外暴露给控制器：
    - 由 `DpNpcEngine` 内部创建；
    - 被 SHARK / TAG 等机器人决策方法使用；
    - 不会被 `DpRoomServiceImpl` 直接操作。

---

### 4. 把这一切连成一句话（便于你记）

**一句话总结：**  
`DpRoomServiceImpl` 像游戏主持人，每秒看“轮到谁行动”；  
如果轮到的是机器人，就去问 `DpNpcEngine`；  
`DpNpcEngine` 先根据 `DpRoom` + `DpPlayer` + `PlayerStats` 收集一堆情报打包成 `SmartContext`，  
再根据机器人类型和难度参数算出这一步是弃牌、跟注还是加注，  
最后把这个决定（`BotAction`）交还给 `DpRoomServiceImpl`，  
由房间服务真正执行下注或弃牌，推动整局游戏向前走。
