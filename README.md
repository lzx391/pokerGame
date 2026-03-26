## MGDemoPlus

### DP游戏文档链接

- [DP游戏详细文档（规则、接口、开发与维护）](docs/DPGAME.md)

### 游戏对局 WebSocket（无 Redis）

- **用途**：仅 **游戏页** `front/dp_game` 使用；服务端在内存里按 `roomId` 维护连接，与 `ConcurrentHashMap` 房间数据同进程，**不需要 Redis**。
- **对局页座位列表（2026-03-23）**：`game.vue` 中玩家网格按 **本机视角** 旋转展示——**自己的座位固定排在第一行**，其余人保持原有相对顺序；`seatIndex` 与后端一致，行动高亮与庄位动画不受影响。说明见 `docs/RoomUi.md`。
- **对局页全宽（2026-03-23）**：原先 `#app` 全局 `padding` + `.dp-game-root` `max-width:800px` 居中，宽屏两侧会露出浅灰底。现登录/注册区 `padding` 仅加在 `.app-container`，对局根节点横向 `width:100%` 铺满视口。
- **原生全屏 + 弹窗（2026-03-23）**：手机/浏览器对 `gameRoot` 做 **Fullscreen API** 时，只有全屏子树会渲染；Element UI 的 `$confirm` / `$message` 默认挂在 `body` 上会看不见。`game.vue` 在原生全屏状态下把这些层移入 `.dp-game-root`（伪全屏不涉及 API，不受影响）。
- **节能模式（2026-03-23）**：对局页 `game.vue` 主题行可勾选 **节能模式**（偏好存 `localStorage` 键 `dp_game_eco_mode`），不依赖手机/电脑判断；开启后根节点带 `data-dp-eco-mode="true"`，由 `dp-game-eco-mode.css` 关闭扫光、飞入、毛玻璃模糊等，并与系统「减少动态效果」一样让 `GamePlayerCard` / `GameCommunityCards` 跳过发牌/弃牌位移动画逻辑。**2026-03-26**：摊牌时手牌区若本地算「最佳五张」（`dpGameHandRank.js`），同一组 7 张牌曾被牌型名、说明、`pickShowdownLeader` 等重复评估；已对评估结果做缓存，减轻主线程压力（节能模式帧率更低时此前更容易感觉「算一会儿」）。**同日**：翻后成牌的 **牌型名**（`handRankName`）与 **说明**（`handRankDetail`）改由 `DpRoomServiceImpl#getAllRooms` 与 `bestHandCards` 一并填充（`DpUtilHandEvaluator`），前端 `GamePlayerCard` 优先展示接口字段、缺省时仍用本地 `dpGameHandRank` 兜底。
- **桌面行动倒计时（2026-03-25）**：下注街（非摊牌/结算）时，圆桌中央公共牌上方显示 **当前行动者昵称 + 30 秒圆环倒计时**，观众与对手都能看见；与下方操作区 `GameActionPanel` 内倒计时共用同一 `timeLeft`，换行动者或新一手才重置，轮询/推送重复同一状态不会把秒数打回 30。**关闭节能模式** 时台呢区有额外静态质感（围边与明暗层次），倒计时条为毛玻璃 + 最后几秒脉冲提示。
- **牌面 UI（2026-03-22）**：`dp-poker-cards.css`、`dp-game-community-cards.css` 中为扑克牌提供渐变高光、金边阴影、周期性扫光（牌型说明弹窗内小牌会关闭扫光以免干扰阅读）；公共牌会先 **从桌面下方飞入公共区**（`GameCommunityCards`），再依次翻转；`game.vue` 里翻牌 `setTimeout` 的前置时间（约 520ms）与飞入时长对齐，`communityCardsFlipComplete` 仍按最后一翻 + 翻转时长计算；系统开启「减少动态效果」时会跳过飞入并降级其它动画。**2026-03-25**：牌型说明、观众席等居中弹窗使用 `dp-game-modals.css` 的 `--dp-panel-*` / `--dp-text-*` 与底栏抽屉一致的标题栏与关闭钮，随对局主题切换外观。**2026-03-26**：庄位锚点绑在本人内联手牌区时，窄屏/全屏下该区域被 `display:none`，`getBoundingClientRect` 为 0 会误把发牌起点当成视口左上角；`front/dp_game/src/utils/dpGameDealerAnchor.js` 在锚点不可见时回退为 **视口底部居中略偏上**（本人视角），`GamePlayerCard` / `GameCommunityCards` 共用。
- **摊牌与弃牌动画（2026-03-23）**：摊牌/结算阶段他人手牌 **同时翻开**（不再沿用首圈发牌的座位错开延迟）；弃牌时手牌飞向桌面中心 **弃牌堆小图标**（`data-dp-muck-anchor`），动画结束后该座位手牌区收起；**他人紧凑位**在底牌行已隐藏（翻后）弃牌时，从座位底部 **临时两张背面** 飞出完成同一动画。
- **对局布局（2026-03-23）**：圆桌区域按视口 **自适应**（`dvh` / `clamp` 等），**不再提供浏览器全屏按钮**；入座玩家 **本人完整卡片在圆桌下方**，桌上该方位仅保留空锚点。**2026-03-25**：本人手牌区与 **右侧固定槽位** 并排（`dp-game-hero-action-row`）：两列 **等高**（flex `stretch`）；轮到本人显示 `GameActionPanel`，否则右侧为与主题底相同的 **占位块**（`dp-game-action-slot-cover`）盖住空槽；宽度 ≤360px 时改为上下叠放以免误触。**2026-03-25（晚）**：**房间聊天输入**与手牌区、行动槽 **同一行**（宽屏在 `dp-game-hero-action-row` 最左；窄屏/全屏与底栏「查看手牌」「行动」同一条固定栏）；纯观众等无底栏时聊天仍在原独立条。**顶栏**：`GameTopBar` **单行 + 横滑**（左信息、右设置与按钮）；`.dp-game-root` **上边距仅 `env(safe-area-inset-top)`**（无刘海为 0）。有刘海时根节点会在顶部留出安全区空白，顶栏若随文档流下移会与「摊牌赢家牌区」等贴顶元素不齐；**2026-03-26**：`dp-game-shell.css` 对 `.dp-top-bar` 使用 **负 `margin-top` 抵消根节点 `padding-top`，并把 `safe-area-inset-top` 加回顶栏自身 `padding-top`**，使顶栏背景与屏顶对齐、文字仍在安全区内。**勿**给顶栏加过高 **`z-index`** 盖住座位牌。**同日（紧凑）**：窄屏与矮视口横屏下收紧顶栏与圆桌间距等。**同日（横屏大屏机）**：部分手机横屏宽度 **>900px**（如 Pro Max），原先 `(max-width:900px)` 的紧凑样式不生效，顶栏仍为大内边距；已改为 **`max-width:1024px` + `max-height:560px`**，并减小顶栏上下 `padding`、行 `gap`、圆桌与顶栏间距，使牌桌上移、底部座位区更易一屏见全。**同日（修正）**：`getPlayerRoundTableStyle` 在 **≤600px** 对上侧座位加大 **`top` 下移**（`cos` 分档），避免上家被顶栏挡住；**撤销**窄屏/全屏下「圆桌 `order:-1` 置顶」——恢复 **顶栏 → 圆桌 → 底区** 顺序，避免桌面段与顶栏叠在同一视窗逻辑里。旁观时「下一局加入对局」等与同排按钮一起排列。**查看手牌抽屉**：与桌上发牌动画解耦，手牌 **瞬时展示**（无逐张翻）。确认类操作仍用 Element UI 的 `$confirm` / `$alert`，避免原生对话框干扰流程。
- **地址**：`ws://<后端主机>:<端口>/ws/dp-game?roomId=房间号`（本地开发前端里默认连 `ws://localhost:8088`）。
- **数据**：每条消息 JSON 与 `GET /dpRoom/getNowRoom` 一致；房间不存在时推送 `{"_ws":"roomClosed"}`。
- **推送节奏**：与后端原有 1 秒定时任务对齐，仅当该房间 **至少有一个 WebSocket 订阅者** 时才序列化；**与上次成功下发的 JSON 相同则不再往客户端发**，减少流量（`DpGameRoomPushService` 内去重；`lastHeartBeat` 不参与 JSON，避免机器人心跳刷新把内容“刷变”）。
- **房间聊天（短时气泡）**：与同一 WS 连接收发；**每人只在对应座位卡片上方显示一条**，新发顶掉旧文案；**有手牌/行动底栏时输入栏与该栏同一行**；观众消息在操作区上方条带展示；协议见 `docs/WEBSOCKET.md`。
- **行动面板布局（2026-03-25）**：窄屏采用常见扑克客户端结构：**第一行** 倒计时 + 跟注/过牌 + 加注额 +「加注」；**第二行** 全宽两列 **All-In | 弃牌**（约 44px 触控高），避免横滑把弃牌挡在屏外或安全区外。**宽屏（≥680px）** 两行并为一行，底行两钮仍跟在主控件右侧，主区可横向滑动。
- **标准 NL 最小加注 + 操作区（2026-03-25）**：后端 `DpRoom.lastRaiseIncrement` 与 `DpRoomServiceImpl.bet` 校验：再加注总注须 ≥ `currentBetToCall + lastRaiseIncrement`（**不足最小加注的全下**仍允许；**短全下**不抬高最小增量）。每新街重置增量为大盲。前端 `GameActionPanel` 展示「最少抬到的总注」、**⅓～1½ 池**快捷按钮与**滑动条**（筹码量取「跟注 + round((底池+跟注)×比例)」的近似，与常见线上桌一致）。说明见 `docs/DPGAME.md`。
- **相关代码**：`DpGameRoomPushService`、`DpGameRoomWebSocketHandler`、`WebSocketGameRoomConfig`；`DpRoomServiceImpl` 定时循环末尾调用 `broadcastIfSubscribed`。

### NPC / AI（给不懂代码的人看的）

#### 酒馆档强度（BOT_Fish，2026-03-25）

- **背景**：原先 `BOT_Fish` 使用 `NpcDifficulty.EASY`，读牌/底池赔率噪声很高（约 35%/40%），容易“看错牌力”，整体比《大镖客 2》酒馆 NPC 更弱、更好欺负。
- **调整**：`BOT_Fish` 改为 **`MEDIUM` 难度**（并略收紧 `MEDIUM`/`HARD` 的噪声），`LOOSE_FUN` 风格提高激进度与诈唬频率、略收跟注站倾向；`DEMO` 分支翻后略提高加注倾向。仍弱于 `BOT_Shark`（PRO），但更接近单机酒馆桌的压迫感。若需要 **更菜的演示鱼**，可再把 `decideBotAction` 里 `case DEMO` 的难度改回 `EASY`。

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

#### Shark 策略 v2（2026-03-25）

- **目标**：少「赔率还行却弃」、少对跟注型对手多街送诈唬、多价值与河牌薄价值；`BOT_Fish` 从第一手起按 **跟注站剧本** 归类（无需等统计样本）。
- **参数中枢**：`DpNpcEngine.SharkStrategyProfile.DEFAULT` + `SharkConfig`（含 `CBET_*`、`RIVER_BLOCK_*`）；翻前 `DpNpcPreflopStrategy` 对 Shark **整体放宽一档**；翻后尺度由配置驱动，见 `DpNpcSharkStrategy`。
- **剥削**：`DpNpcSharkExploitHandPlan.tuneAfterBasePlan` 增加主对手昵称，用于识别内置鱼机器人。

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

### Docker 部署

- **详细说明**（Git 与打包关系、`WebConfig` / 驱动 / 前端生产地址、DBeaver 连接串等）：见 [docs/DOCKER.md](docs/DOCKER.md)。
- **一键（MySQL + 应用）**：仓库根目录执行 `docker compose up --build`，浏览器打开 `http://localhost:8088`（前端为 hash 路由，形如 `/#/login`）。默认 MySQL root 密码为 `mgdemo_root`，可通过环境变量 `MYSQL_ROOT_PASSWORD` 修改。容器内 MySQL 对 **宿主机** 映射为 **`localhost:3307`**（避免与本机已占用的 **3306** 冲突）；应用容器仍通过内部网络访问 `mysql:3306`。
- **仅构建后端镜像**：`docker build -t mgdemoplus .`，运行示例：  
  `docker run -p 8088:8088 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/school_db?useSSL=false -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=你的密码 -e MGDEMOPLUS_IMAGES_FILE_LOCATION=file:/data/mgdemo-files/ -v 本机目录:/data/mgdemo-files mgdemoplus`
- **说明**：`Dockerfile` 会编译 `front/dp_game` 并把 `dist` 打进 jar 的 `static`，与 API、WebSocket 同端口；`/images/**` 上传文件目录在容器内默认为 `/data/mgdemo-files`（compose 已映射到 `./docker-data/uploads`）。首次使用需在 `school_db` 中建好业务表；可将初始化 `.sql` 放入 `./docker-data/mysql-init`（仅 MySQL 第一次初始化数据卷时执行）。**勿把含真实密钥的 `application.properties` 依赖进镜像**；LLM 等密钥请用环境变量 `ARK_API_KEY`、`ARK_ENDPOINT_ID` 等在 compose 中注入。
