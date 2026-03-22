## MGDemoPlus

### DP游戏文档链接

- [DP游戏详细文档（规则、接口、开发与维护）](front/dp_game/DPGAME.md)

### NPC / AI（给不懂代码的人看的）

#### Shark（BOT_Shark）现在会“翻前按局势调范围”

从 2026-03-18 起，Shark 的翻前（preflop）不再只靠粗分类规则，而是接入了一个可复用的翻前模块：

- **入口位置**：`DpNpcEngine` 在 `case SHARK` 且 `stage == preflop` 时调用 `DpNpcPreflopStrategy.decideForShark(...)`
- **核心思路**：
  - **底层**：把起手牌映射到一个“手牌分组”`HandGroup`（近似 13×13 的强度分层）
  - **上层**：把 **人数 / 位置 / 有效筹码深度 / 松紧风格 / 情绪** 合成一个 `rangeLevel(1~8)`
  - **风格（2026-03-21）**：Shark 的 `BotType → NpcStyle` 已改为 **松凶 `LOOSE_AGGRO`**（`preflopTightness`≈0.30），不再与 TAG 共用紧凶表（≈0.85），避免「有人翻前加注就大量弃牌」的观感。TAG 仍为紧凶。历史上若用「×2 扣档 + 面对 open 再减 2 档」叠高紧度也会加剧该问题；当前策略在 `DpNpcPreflopStrategy` 中已用较温和扣档 + 面对 open 放宽。
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

#### Shark 翻后：剥削剧本驱动 HandPlan（2026-03-21）

- **说明**：下面这段是**说明书**，不是“待开启功能”。只要运行的是当前工程编译出的服务、桌上有 `BOT_Shark`，翻后就会走这套逻辑。
- **做什么**：在 flop 首次生成整手计划（VALUE / BLUFF / POT_CONTROL / GIVE_UP 与 barrels、激进度）之后，根据**对手 `PlayerStats` + `LearningLab` 旋钮**归类成粗剧本（跟注站 / 紧弱 / 松凶等），再**改线路、加减压枪数、调激进度**；turn/river 强牌纠正为价值线时，对「跟注站」会多给 1 枪额度。
- **代码**：`DpSharkExploitHandPlan`；接入点在 `DpNpcEngine.initHandPlanIfNeededForPostflop`（仅 Shark）与 `updateHandPlanForLaterStreetIfNeeded`。
- **数据库**：**不需要**为剥削剧本单独加表或加字段；沿用 `dp_shark_opponent_profile` 里已有的统计与旋钮 JSON 即可。
