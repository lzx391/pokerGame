## MGDemoPlus

### DP游戏文档链接

- [DP游戏详细文档（规则、接口、开发与维护）](src/main/java/com/example/mgdemoplus/front/dp_game/DPGAME.md)

### NPC / AI（给不懂代码的人看的）

#### Shark（BOT_Shark）现在会“翻前按局势调范围”

从 2026-03-18 起，Shark 的翻前（preflop）不再只靠粗分类规则，而是接入了一个可复用的翻前模块：

- **入口位置**：`DpNpcEngine` 在 `case SHARK` 且 `stage == preflop` 时调用 `DpNpcPreflopStrategy.decideForShark(...)`
- **核心思路**：
  - **底层**：把起手牌映射到一个“手牌分组”`HandGroup`（近似 13×13 的强度分层）
  - **上层**：把 **人数 / 位置 / 有效筹码深度 / 松紧风格 / 情绪** 合成一个 `rangeLevel(1~8)`
  - 再用 `rangeLevel` 决定：
    - **无人加注**：要不要 open（以及 open 多大）
    - **面对 open**：call / 3bet / fold
    - **面对 3bet**：call / 4bet / fold
    - **面对 4bet+**：收敛到 all-in / call / fold（避免“每轮只抬一点点”）

#### 相关文件

- `src/main/java/com/example/mgdemoplus/service/studentImpl/DpNpcPreflopStrategy.java`
- `src/main/java/com/example/mgdemoplus/service/studentImpl/DpNpcEngine.java`

#### Shark 对手习惯跨房间记忆（2026-03-21）

桌上存在 `BOT_Shark` 时：

- **持久化表**：执行 `src/main/resources/db/dp_shark_opponent_profile.sql` 建表 `dp_shark_opponent_profile`（主键为玩家昵称）。
- **存什么**：`PlayerStats`（累计入池/加注/摊牌等 + 最近 10 手窗口）与 `DpNpcSharkLearningLab` 的全部旋钮及分桶样本；**不按房间 ID**，只按昵称，适配随机房间号。
- **何时写入**：每手正常结算后，`DpSharkOpponentMemoryService.persistOpponentsAfterHand` 在 `onHandSettled` 之后执行。
- **何时读入**：玩家 `joinRoom` 上桌时、以及每手 `newHand` 盲注就绪后，若 `playerStatsMap` 尚无该昵称则 `hydrate` 从 DB 加载。
- **代码入口**：`DpSharkOpponentMemoryService`、`DpNpcSharkLearningLab`（旋钮键已改为仅对手昵称）。
