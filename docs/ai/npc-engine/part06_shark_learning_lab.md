## Part 06 - Shark 动态学习（旋钮）+ 逐街动作日志（实验版）

> 本文只讲 **BOT_Shark** 的“动态变聪明”机制。  
> 目标：不改 `SmartContext`，不影响其他 NPC（TAG / MANIAC / Fish），只让 Shark 越打越会针对对手。

---

### 1. 先搞清楚三层东西：日志 / 统计 / 当下情报（SmartContext）

- **逐街动作日志（本手流水账）**：`DpNpcSharkHandActionLog`
  - 记录：这一手里每次下注/弃牌发生在什么街（preflop/flop/turn/river）、是谁、投了多少。
  - 作用：让结算时填 `PlayerStats.SingleHandStats` 更准确（尤其是 flop/turn/river 的激进标志、turn/river 放弃模式）。
  - 特点：只保存“当前这一手”的明细，**结算后会清理**（避免内存无限增长）。

- **玩家习惯统计（近 10 手窗口）**：`PlayerStats`
  - 存在：`DpRoom.playerStatsMap`（key=玩家昵称）。
  - 结算时更新：`DpRoomServiceImpl.autoSettle(...)` 会把本手行为写入 `PlayerStats.SingleHandStats`，并 `addHand(hand, 10)`。
  - 这是“短期历史记忆”，供 Shark 推断对手紧松、诈唬倾向等。

- **本手情报快照（当下）**：`SmartContext`
  - 由 `DpNpcEngine.buildSmartContext(...)` 在每次决策时临时构造。
  - 它会读取 `PlayerStats`、桌面状态、底池赔率等，算出例如：
    - `showdownBluffiness`（摊牌诈唬倾向）
    - `counterStrategy`（对该对手的反制建议）
    - `potOdds / equityEst / stackCtx / multiway 信息`
  - 这是“眼睛”，用来判断**这一手现在局面是什么**，不是长期存储。

---

### 2. 旋钮（LearningLab）是什么？为什么它更像“机器学习”

我们新增了一个只给 Shark 用的“长期记忆旋钮”模块：`DpNpcSharkLearningLab`。

- 它不保存每一手的 SmartContext 明细（太重）。
- 它只保存“压缩后的长期结论”，例如目前实现的一个旋钮：
  - `foldAdjustment`：对某个对手整体更愿意弃牌还是更愿意跟注/抓诈唬。

**更新方式（关键点）**：
- 每手结算后（`autoSettle`）根据最新统计，慢慢调整旋钮（learning rate 很小）。
- 下一次遇到这个人时，Shark 决策会把旋钮叠加到本手的 `baseFold` 上，产生“越打越克制”的效果。

---

### 3. 代码接入点（你只要记这 3 个地方）

#### 3.1 新一手开局：初始化动作日志
- 位置：`DpRoomServiceImpl.newHand(...)`
- 行为：`DpNpcSharkHandActionLog.beginHand(r)`
- 同时记录 SB/BB 盲注事件（仅在桌上有 `BOT_Shark` 时启用）

#### 3.2 对局进行中：记录 bet / fold
- 位置：`DpRoomServiceImpl.bet(...)` 和 `DpRoomServiceImpl.fold(...)`
- 行为：
  - bet：记录 check/call/bet/raise/all-in
  - fold：记录弃牌发生在哪一街

#### 3.3 结算：用日志填统计 + 更新旋钮 + 清理日志
- 位置：`DpRoomServiceImpl.autoSettle(...)`
- 行为：
  - `snapshot` 当前手的日志，填充 `SingleHandStats` 的逐街字段
  - `DpNpcSharkLearningLab.onHandSettled(r)` 更新旋钮
  - `DpNpcSharkHandActionLog.clearHand(r)` 清理这一手日志

---

### 4. Shark 决策时怎么“用”旋钮

位置：`DpNpcSharkStrategy.decide(...)`

逻辑：在算 `baseFold` 的过程中，面对某个 aggressor 且需要付费跟注时：
- 读取 `DpNpcSharkLearningLab.getAdjust(room, bot, aggressor)`
- 把 `foldAdjustment` 叠加到 `baseFold`

