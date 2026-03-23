## MGDemoPlus

### DP游戏文档链接

- [DP游戏详细文档（规则、接口、开发与维护）](docs/DPGAME.md)

### 游戏对局 WebSocket（无 Redis）

- **用途**：仅 **游戏页** `front/dp_game` 使用；服务端在内存里按 `roomId` 维护连接，与 `ConcurrentHashMap` 房间数据同进程，**不需要 Redis**。
- **对局页座位列表（2026-03-23）**：`game.vue` 中玩家网格按 **本机视角** 旋转展示——**自己的座位固定排在第一行**，其余人保持原有相对顺序；`seatIndex` 与后端一致，行动高亮与庄位动画不受影响。说明见 `docs/RoomUi.md`。
- **对局页全宽（2026-03-23）**：原先 `#app` 全局 `padding` + `.dp-game-root` `max-width:800px` 居中，宽屏两侧会露出浅灰底。现登录/注册区 `padding` 仅加在 `.app-container`，对局根节点横向 `width:100%` 铺满视口。
- **节能模式（2026-03-23）**：对局页 `game.vue` 主题行可勾选 **节能模式**（偏好存 `localStorage` 键 `dp_game_eco_mode`），不依赖手机/电脑判断；开启后根节点带 `data-dp-eco-mode="true"`，由 `dp-game-eco-mode.css` 关闭扫光、飞入、毛玻璃模糊等，并与系统「减少动态效果」一样让 `GamePlayerCard` / `GameCommunityCards` 跳过发牌/弃牌位移动画逻辑。
- **牌面 UI（2026-03-22）**：`dp-poker-cards.css`、`dp-game-community-cards.css` 中为扑克牌提供渐变高光、金边阴影、周期性扫光（牌型说明弹窗内小牌会关闭扫光以免干扰阅读）；公共牌会先 **从桌面下方飞入公共区**（`GameCommunityCards`），再依次翻转；`game.vue` 里翻牌 `setTimeout` 的前置时间（约 520ms）与飞入时长对齐，`communityCardsFlipComplete` 仍按最后一翻 + 翻转时长计算；系统开启「减少动态效果」时会跳过飞入并降级其它动画。
- **摊牌与弃牌动画（2026-03-23）**：摊牌/结算阶段他人手牌 **同时翻开**（不再沿用首圈发牌的座位错开延迟）；弃牌时手牌飞向桌面中心 **弃牌堆小图标**（`data-dp-muck-anchor`），动画结束后该座位手牌区收起；**他人紧凑位**在底牌行已隐藏（翻后）弃牌时，从座位底部 **临时两张背面** 飞出完成同一动画。
- **对局布局（2026-03-23）**：圆桌区域按视口 **自适应**（`dvh` / `clamp` 等），**不再提供浏览器全屏按钮**；入座玩家 **本人完整卡片固定在圆桌下方**，桌上该方位仅保留空锚点，减少挡视野。确认类操作仍用 Element UI 的 `$confirm` / `$alert`，避免原生对话框干扰流程。
- **地址**：`ws://<后端主机>:<端口>/ws/dp-game?roomId=房间号`（本地开发前端里默认连 `ws://localhost:8088`）。
- **数据**：每条消息 JSON 与 `GET /dpRoom/getNowRoom` 一致；房间不存在时推送 `{"_ws":"roomClosed"}`。
- **推送节奏**：与后端原有 1 秒定时任务对齐，仅当该房间 **至少有一个 WebSocket 订阅者** 时才序列化并广播，避免空订阅浪费。
- **相关代码**：`DpGameRoomPushService`、`DpGameRoomWebSocketHandler`、`WebSocketGameRoomConfig`；`DpRoomServiceImpl` 定时循环末尾调用 `broadcastIfSubscribed`。

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

#### 大模型 NPC（BOT_LLM，火山方舟 / 豆包）

- **做什么**：昵称 `BOT_LLM` 仅在 `DpRoomServiceImpl` 定时器里走 `DpLlmNpcDecisionService`，与普通规则 NPC 分离；接口 `POST /dpRoom/addLlmBot?roomId=...` 可预约下一局上桌。
- **代码位置**：`npc/LlmNpc.java`、`DpLlmNpcDecisionService.java`、`DpNpcEngine.buildLlmNpcGameSnapshot` 等。
- **密钥与接入点怎么配（二选一，配置文件优先）**：
  1. **`application.properties`**（已预留项，适合本机开发）：`dp.llm.ark.api-key=`、`dp.llm.ark.endpoint-id=`（可选 `dp.llm.ark.base-url=`）。**不要把填了真密钥的文件提交到 Git。**
  2. **系统环境变量**：`ARK_API_KEY`、`ARK_ENDPOINT_ID`（可选 `ARK_BASE_URL`）。  
     - Windows：**设置 → 系统 → 关于 → 高级系统设置 → 环境变量**（用户或系统变量里新建）。  
     - 仅当前 PowerShell 会话：`$env:ARK_API_KEY="你的key"`、`$env:ARK_ENDPOINT_ID="ep-..."`。  
     - IntelliJ：**Run → Edit Configurations → 你的 Spring Boot → Environment variables**。
- **对局摘要**：`DpUtilSmartContext` 由 `buildLlmNpcGameSnapshot` 与房间状态一起打成 `LlmNpcGameContext` 再发给模型。
