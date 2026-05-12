## MGDemoPlus

### 文档导航（总入口）

| 入口 | 说明 |
|------|------|
| **[docs/README.md](docs/README.md)** | **全仓库专题文档索引**：按「环境 / 部署 / 安全 / 游戏 / WebSocket / 持久化 / Redis / AI / 学习备忘」分类，每篇附一句话简介；**查找某主题从这一页开始**。 |
| **[README.md](README.md)** | **英文短版**：项目特性、技术栈、快速开始；适合第一次打开仓库（英文为主）。 |
| **[README.ch.md](README.ch.md)** | **本文件**：中文 **运行与维护长说明**（与 [README.en.md](README.en.md) 同结构）。 |
| **[README.en.md](README.en.md)** | **English** 长说明（本文件的英文翻译）。 |
| **[front/dp_game/docs/README.md](front/dp_game/docs/README.md)** | **DP 前端专题**：对局页布局调参、主题与 `--dp-*` 绑定、与后端 `docs` 的对应关系。 |

**本文件（`README.ch.md`）**：偏 **运行与维护长说明**——环境变量、WebSocket 与对局 UI 迭代、NPC、牌谱与用户、曲库 BGM、Redis、Docker 等，按时间线或功能块记录；与 `docs/` 中专题文档**互为补充**（细节以 `docs/*.md` 为准时，会在此交叉引用）。英文版见 **[README.en.md](README.en.md)**。

### 中文概览

- 英文快速入门（特性、栈、快速开始）见 **[README.md](README.md)**；**完整文档地图**见 **[docs/README.md](docs/README.md)**；本文件为中文长说明。

### 环境变量（`.env`）

- 仓库根目录提供 **`.env.example`**：列出 Docker Compose 与本机运行时常用变量（MySQL / Redis / JWT / 方舟 LLM 等）。复制为 **`.env`** 后按需填写；**`.env` 已被 Git 忽略**，不要提交含真实密码的文件。
- **`docker compose`** 会自动读取与 `docker-compose.yml` 同目录的 `.env`，用于镜像标签、口令及可选的 `JWT_SECRET`、`ARK_*` 等注入应用容器。
- **本机直接跑 Spring Boot**（IDEA / `mvn spring-boot:run`）：**`MgDemoPlusApplication.main`** 在 **`SpringApplication.run` 之前**调用 **`LocalDotenvLoader.load()`**（`dotenv-java`），将根目录 **`.env`** 写入系统属性；请把运行工作目录设为**仓库根目录**，否则找不到文件。若已在系统或 IDE 里配置了同名环境变量，以环境变量为准。
- **默认值**：数据库、Redis、上传路径、方舟等已在 **`application.properties`** 里用 `${环境变量:默认值}` 写好；不配 `.env` 也能按默认值启动。要覆盖时写根目录 `.env` 或 IDE 环境变量，变量名见 **`.env.example`**。
- **详细说明**（`application.properties` 与 `.env`、JWT、方舟 LLM 的读取关系）：见 [docs/ENV_README.md](docs/ENV_README.md)。

### DP游戏文档链接

**完整分类与每篇简介**见 **[docs/README.md](docs/README.md)**；下列为常用直达。

- [后端面试题清单（结合本仓库技术栈）](docs/BACKEND_INTERVIEW_QUESTIONS.md)
- [JSON、Map、序列化/反序列化与接口数据（备忘）](docs/Json-map-serialization.md)
- [DP游戏详细文档（规则、接口、开发与维护）](docs/DPGAME.md)
- [DP NPC 引擎笔记（Fish/Maniac/TAG 与 Shark 分册）](docs/ai/npc-engine/README.md)
- [对局页布局调参（顶栏 / 圆桌 / 底栏间距，`dp-game-shell.css`）](front/dp_game/docs/GAME_LAYOUT_TUNING_README.md)
- [DP 曲库：`webPath`、磁盘目录与试听代理流程](docs/DpMusicWebPath.md)
- [DP 用户密码：前后端分工、校验方式、找回密码现状](docs/DpUserPassword.md)
- [Java：对象引用、`roomMap` 与浅拷贝 / 深拷贝（备忘）](docs/Java对象引用与浅拷贝深拷贝备忘.md)
- [WebSocket：对局页长连接、后端 Handler/PushService 与前端 `game.vue` 串接说明](docs/WEBSOCKET.md)

### 游戏对局 WebSocket（无 Redis）

- **用途**：仅 **游戏页** `front/dp_game` 使用；服务端在内存里按 `roomId` 维护连接，与 `ConcurrentHashMap` 房间数据同进程，**不需要 Redis**。
- **对局页座位列表（2026-03-23）**：`game.vue` 中玩家网格按 **本机视角** 旋转展示——**自己的座位固定排在第一行**，其余人保持原有相对顺序；`seatIndex` 与后端一致，行动高亮与庄位动画不受影响。说明见 `docs/RoomUi.md`。
- **对局页子组件（2026-04-02）**：从 `game.vue` 抽出 **`GameRoomChatBar`**、**`GameBottomSheet`**、**`GameTableActionTimer`**、**`GameRoundTable`**；几何与发牌顺序辅助在 **`front/dp_game/src/utils/dpGameRoundTableLayout.js`**，玩家卡片容器样式在 **`dpGamePlayerBoxStyle.js`**。
- **对局页再拆（2026-04-02）**：**`GameHeroDockFooter`**（宽屏底栏 + 窄屏底栏 + 观众聊天条）与 **`GameDpFloatingModals`** / **`GameDpGameSheets`**（居中弹层、底部抽屉）通过 **`inject: ['dpGameView']`** 访问根实例，减少超长 props；**`mixins/dpGameFullscreenMixin.js`**（全屏 + Element 弹层挪入根）、**`mixins/dpGameActionCountdownMixin.js`**（行动/准备倒计时）；`game.vue` 保留路由、WS、HTTP、`provide`、`<audio ref="roomBgm">` 与核心业务方法。
- **Vuex（2026-04-02）**：对局页使用 **Vue 3 以下兼容的 `vuex@3`**；房间与 UI 状态集中在 **`front/dp_game/src/store/modules/dpGame.js`**（命名空间 `dpGame`），`game.vue` 用 `mapState` / `mapGetters` 订阅；子组件内对原 `data` 的赋值已改为 **`$store.commit('dpGame/…')`**（如弹窗、聊天草稿、加注额、移动端抽屉、房主工具 `.sync` 等），避免对只读 computed 赋值失效。
- **圆桌分区线、小牌面与轨道倒计时（2026-04-02）**：牌尺寸默认在 **`dp-game-shell.css`** 的 **`--dp-card-base-*`**（略小于早期 44×62）；**`GameRoundTable`** 内 **SVG 分区线**为「公共牌区内沿 → 台呢外沿」，中央无线；**行动倒计时** 默认 **`orbitActionTimer`**（仅圆环 + 秒数）：沿桌心—座位射线，用 **`actionTimerOrbitRoundTableStyle`** 比 **`seatFeltMarkerRoundTableStyle`** 更靠桌心，顺序为 **桌心 → 计时器 → 庄/盲标 → 玩家卡**。中央仅叠公共牌；射线描边用 **`--dp-accent`**。**同日（筹码王光效）**：**`DpRoom.chipLeaderNicknames`** 在 **`DpRoomServiceImpl#autoSettle`** 末尾按结算后筹码写入（并列最高全列入），经 WS/轮询进 **`dpGame` → `GameRoundTable`**；前端只比对昵称，**不在本地算最大筹码**。第一局开局前该列表为空，避免「全员同分」误亮。原 **连胜台呢标** 仍注释。
- **对局页全宽（2026-03-23）**：原先 `#app` 全局 `padding` + `.dp-game-root` `max-width:800px` 居中，宽屏两侧会露出浅灰底。现登录/注册区 `padding` 仅加在 `.app-container`，对局根节点横向 `width:100%` 铺满视口。
- **原生全屏 + 弹窗（2026-03-23）**：手机/浏览器对 `gameRoot` 做 **Fullscreen API** 时，只有全屏子树会渲染；Element UI 的 `$confirm` / `$message` 默认挂在 `body` 上会看不见。`game.vue` 在原生全屏状态下把这些层移入 `.dp-game-root`（伪全屏不涉及 API，不受影响）。
- **对局主题（2026-03-29）**：在 `front/dp_game/src/constants/dpGameThemes.js` 登记主题 id 与中文名；在 `dp-game-themes.css` 为 `.dp-game-root[data-dp-game-theme='…']` 写同一套 `--dp-*` 变量即可。**组件绑定与新增主题自检问卷**见 [front/dp_game/docs/THEME_BINDING_README.md](front/dp_game/docs/THEME_BINDING_README.md)。新增 **草莓甜心**（`strawberry`）、**绵云轻柔**（`cotton`）粉嫩童话系；与 **节能模式** 正交——节能由 `data-dp-eco-mode` + `dp-game-eco-mode.css` 统一降级动画/毛玻璃，不按主题重复写两套。**圆桌台呢**颜色在 `dp-game-shell.css` 中由 `--dp-table-felt-depth` / `--dp-table-felt-spot` 等与面板混色；默认混 `#0a1620` 做暗边，浅色童话主题在 `dp-game-themes.css` 中覆盖为粉/紫系，避免桌面发灰黑。**Element UI 与 body 主题（2026-03-29）**：`$confirm` / `$message` / `MessageBox` 等默认挂在 `body` 上，无法继承 `.dp-game-root` 的 `--dp-*`。对局页 `game.vue` 在 `created` / `watch gameUiTheme` 时把 `data-dp-game-theme` 同步到 `document.body`，`dp-game-themes.css` 中每个主题选择器为 `.dp-game-root[…], body[…]` 双写变量；`dp-game-element-ui.css` 覆盖 `.el-message-box`、`.el-message`、`.v-modal` 及弹窗内按钮。离开对局 `beforeDestroy` 时移除 body 上的属性。

**牌背 / 手牌灰底 / 弃牌堆**：与暗黑哥特一样走「全局变量」——在 `.dp-game-root` 上定义 `--dp-card-back-*`、`--dp-muck-*` 等（`dp-game-shell.css` 默认值 + `strawberry`/`cotton` 在 `dp-game-themes.css` 覆盖），`dp-poker-cards.css` / `dp-game-community-cards.css` 引用变量；`--dp-panel-bg` / `--dp-player-card-bg` 须保持**可被 color-mix 的纯色**，勿用渐变。
- **节能模式（2026-03-23）**：对局页 `game.vue` 主题行可勾选 **节能模式**（偏好存 `localStorage` 键 `dp_game_eco_mode`），不依赖手机/电脑判断；开启后根节点带 `data-dp-eco-mode="true"`，由 `dp-game-eco-mode.css` 关闭扫光、飞入、毛玻璃模糊等，并与系统「减少动态效果」一样让 `GamePlayerCard` / `GameCommunityCards` 跳过发牌/弃牌位移动画逻辑。**2026-03-26**：摊牌时手牌区若本地算「最佳五张」（`dpGameHandRank.js`），同一组 7 张牌曾被牌型名、说明、`pickShowdownLeader` 等重复评估；已对评估结果做缓存，减轻主线程压力（节能模式帧率更低时此前更容易感觉「算一会儿」）。**同日**：翻后成牌的 **牌型名**（`handRankName`）与 **说明**（`handRankDetail`）改由 `DpRoomServiceImpl#getAllRooms` 与 `bestHandCards` 一并填充（`DpUtilHandEvaluator`），前端 `GamePlayerCard` 优先展示接口字段、缺省时仍用本地 `dpGameHandRank` 兜底。
- **桌面行动倒计时（2026-03-25）**：下注街（非摊牌/结算）时显示 **当前行动者昵称 + 30 秒圆环倒计时**，观众与对手都能看见；与下方操作区 `GameActionPanel` 内倒计时共用同一 `timeLeft`，换行动者或新一手才重置，轮询/推送重复同一状态不会把秒数打回 30。**关闭节能模式** 时台呢区有额外静态质感（围边与明暗层次），倒计时条为毛玻璃 + 最后几秒脉冲提示。**2026-04-01（晚）**：庄盲标更靠桌心（`nudgeSeatTowardTableCenter` 约 0.48）；桌面行动倒计时恢复为 **公共牌上方** 的圆环 + 昵称 +「思考中」胶囊条，**`dp-game-table-action-timer--compact`** 整体缩小、`center-stack` 间距收紧，减轻对公共牌区的纵向挤压。**2026-04-01（再调）**：**摊牌/结算** 时弃牌堆锚点改到桌面 **右侧偏下**（约 81%/56%），不再占桌心；下注阶段仍随庄位。**2026-04-01**：庄/盲/庄位台呢标 **减小向桌心偏移**（`getSeatFeltMarkerRoundTableStyle`：≤600px 约 0.16、≤900px 约 0.24、否则约 0.34），避免手机端与公共牌重叠。**2026-04-01（本人手牌）**：本人内联手牌区（`heroHandDock`）首圈飞入 **落点** 对准 **「查看手牌」** 按钮（`data-dp-hero-deal-target`），与庄位给自己发或自己给自己发相同；飞入结束后短过渡回手牌槽；起点仍为弃牌堆/庄位锚点。**宽屏非房主** 无该按钮时落点与原先一致。
- **牌面 UI（2026-03-22）**：`dp-poker-cards.css`、`dp-game-community-cards.css` 中为扑克牌提供渐变高光、金边阴影、周期性扫光（牌型说明弹窗内小牌会关闭扫光以免干扰阅读）；公共牌会先 **从桌面下方飞入公共区**（`GameCommunityCards`），再依次翻转；`game.vue` 里翻牌 `setTimeout` 的前置时间（约 520ms）与飞入时长对齐，`communityCardsFlipComplete` 仍按最后一翻 + 翻转时长计算；系统开启「减少动态效果」时会跳过飞入并降级其它动画。**2026-03-25**：牌型说明、观众席等居中弹窗使用 `dp-game-modals.css` 的 `--dp-panel-*` / `--dp-text-*` 与底栏抽屉一致的标题栏与关闭钮，随对局主题切换外观。**2026-03-26**：庄位锚点绑在本人内联手牌区时，窄屏/全屏下该区域被 `display:none`，`getBoundingClientRect` 为 0 会误把发牌起点当成视口左上角；`front/dp_game/src/utils/dpGameDealerAnchor.js` 在锚点不可见时回退为 **视口底部居中略偏上**（本人视角），`GamePlayerCard` / `GameCommunityCards` 共用。**2026-04-01**：本人内联手牌区（`GamePlayerCard` 的 `heroHandDock`）仅保留昵称、底牌、成牌牌型；后手/本轮移至 `GameActionPanel`；庄位 / 盲注 / 连胜标贴在圆桌 **本人 6 点空位**（`data-dp-dealer-anchor` 改绑该处 D 徽标，避免与精简手牌区重复）。**同日（修正）**：`syncCommunityCardsFlipState` 不再在每次房间推送时无条件 `clearTimeout`「翻完」计时器；否则公共牌张数未变时计时器被清掉且不重建，`communityCardsFlipComplete` 可长时间为 false，成牌牌型延迟数秒～十余秒才出现。**2026-04-01（晚）**：庄/盲/连胜标改在 **台呢层**（`game.vue` 的 `dp-game-table__felt-markers`），不再叠在玩家卡片上；**弃牌堆**随庄位绕桌（`dp-game-muck-pile--orbit`），手牌与公共牌飞入起点优先取弃牌堆（`data-dp-muck-anchor`）；`dpGameDealerAnchor.js` 先查弃牌堆再查 `data-dp-dealer-anchor`。全屏/伪全屏下根节点与圆桌段 **flex + overflow:hidden**，尽量一屏展示顶栏、圆桌与底栏。
- **摊牌与弃牌动画（2026-03-23）**：摊牌/结算阶段他人手牌 **同时翻开**（不再沿用首圈发牌的座位错开延迟）；弃牌时手牌飞向桌面中心 **弃牌堆小图标**（`data-dp-muck-anchor`），动画结束后该座位手牌区收起；**他人紧凑位**在底牌行已隐藏（翻后）弃牌时，从座位底部 **临时两张背面** 飞出完成同一动画。
- **对局布局（2026-04-02）**：宽屏（≥901px）与窄屏一致采用根节点 **视口封顶 + `.dp-game-layout__main` 内纵向滚动**；`game.vue` 根节点增加 **`data-dp-stage`**，摊牌/结算阶段为主区增加 **顶内边距**，避免 12 点方向座位牌型/最佳五张被 `overflow` 裁切且无法负向滚动。`App.vue` 对 **`/game`** 路由为 **`#app` → `.full-page` → `.dp-game-root` flex 链** 铺满动态视口，减轻平板/安全区下露浅灰底；宽屏下圆桌在 **`main` 内 `margin:auto` 垂直居中**（原生全屏/布局全屏/伪全屏下用更具体选择器覆盖原圆桌固定上下 `margin`）。
- **对局布局（2026-03-23）**：圆桌区域按视口 **自适应**（`dvh` / `clamp` 等），**不再提供浏览器全屏按钮**；入座玩家 **本人完整卡片在圆桌下方**，桌上该方位仅保留空锚点。**2026-04-01（修正）**：**翻牌前发牌**时本人与其它座位一样在 **圆桌 6 点** 显示紧凑座位牌（`rivalMini`）；发牌飞入结束后该处收起，**整街翻牌前**屏幕下方不显示内联手牌区，仅 **「查看手牌」** 打开抽屉看牌；**进入翻牌圈**后恢复下方内联手牌区。**2026-03-25**：本人手牌区与 **右侧固定槽位** 并排（`dp-game-hero-action-row`）：两列 **等高**（flex `stretch`）；轮到本人显示 `GameActionPanel`，否则右侧为与主题底相同的 **占位块**（`dp-game-action-slot-cover`）盖住空槽；宽度 ≤360px 时改为上下叠放以免误触。**2026-03-25（晚）**：**房间聊天输入**与手牌区、行动槽 **同一行**（宽屏在 `dp-game-hero-action-row` 最左；窄屏/全屏与底栏「查看手牌」「行动」同一条固定栏）；纯观众等无底栏时聊天仍在原独立条。**顶栏**：`GameTopBar` **单行 + 横滑**（左信息、右设置与按钮）；`.dp-game-root` **上边距仅 `env(safe-area-inset-top)`**（无刘海为 0）。有刘海时根节点会在顶部留出安全区空白，顶栏若随文档流下移会与「摊牌赢家牌区」等贴顶元素不齐；**2026-03-26**：`dp-game-shell.css` 对 `.dp-top-bar` 使用 **负 `margin-top` 抵消根节点 `padding-top`，并把 `safe-area-inset-top` 加回顶栏自身 `padding-top`**，使顶栏背景与屏顶对齐、文字仍在安全区内。**勿**给顶栏加过高 **`z-index`** 盖住座位牌。**同日（紧凑）**：窄屏与矮视口横屏下收紧顶栏与圆桌间距等。**同日（横屏大屏机）**：部分手机横屏宽度 **>900px**（如 Pro Max），原先 `(max-width:900px)` 的紧凑样式不生效，顶栏仍为大内边距；已改为 **`max-width:1024px` + `max-height:560px`**，并减小顶栏上下 `padding`、行 `gap`、圆桌与顶栏间距，使牌桌上移、底部座位区更易一屏见全。**同日（修正）**：`getPlayerRoundTableStyle` 在 **≤600px** 对上侧座位加大 **`top` 下移**（`cos` 分档），避免上家被顶栏挡住；**撤销**窄屏/全屏下「圆桌 `order:-1` 置顶」——恢复 **顶栏 → 圆桌 → 底区** 顺序，避免桌面段与顶栏叠在同一视窗逻辑里。旁观时「下一局加入对局」等与同排按钮一起排列。**查看手牌抽屉**：与桌上发牌飞入解耦（`skipHoleDealAnimation`）；可设 `dealRevealStaggerSec` 逐张翻面。**2026-04-01**：修正 `skipHoleDealAnimation` 与翻牌前 intro 隐藏逻辑叠加导致抽屉内底牌区不渲染的问题。**同日**：`holeCards` 若晚于 `currentHandSeed` 到达，须在 `player.holeCards` 上补触发 `tryStartHoleDealFly`，且 intro 不得在尚无手牌时结束，否则 6 点飞牌从不播放。确认类操作仍用 Element UI 的 `$confirm` / `$alert`，避免原生对话框干扰流程。
- **地址**：`ws://<后端主机>:<端口>/ws/dp-game?roomId=房间号`（本地开发前端里默认连 `ws://localhost:8088`）。
- **对局内历史对局（2026-03-31）**：顶栏「历史对局」打开居中弹层（`GameHandHistoryModal`），复用 `HandHistory` / `HandHistoryDetail` 的 `embedded` 模式，不离开 `/game/:roomId`；弹层与嵌入页使用对局当前 `data-dp-game-theme` 的 `--dp-*` 变量（与顶栏主题一致）；大厅独立路由 `/hand-history` 仍为原样浅色整页样式。
- **数据**：每条消息 JSON 与 `GET /dpRoom/getNowRoom` 一致；房间不存在时推送 `{"_ws":"roomClosed"}`。
- **推送节奏**：与后端原有 1 秒定时任务对齐，仅当该房间 **至少有一个 WebSocket 订阅者** 时才序列化；**与上次成功下发的 JSON 相同则不再往客户端发**，减少流量（`DpGameRoomPushService` 内去重；`lastHeartBeat` 不参与 JSON，避免机器人心跳刷新把内容“刷变”）。
- **房间聊天（短时气泡）**：与同一 WS 连接收发；**每人只在对应座位卡片上方显示一条**，新发顶掉旧文案；**有手牌/行动底栏时输入栏与该栏同一行**；观众消息在操作区上方条带展示；协议见 `docs/WEBSOCKET.md`。
- **行动面板布局（2026-03-25）**：窄屏采用常见扑克客户端结构：**第一行** 倒计时 + 跟注/过牌 + 加注额 +「加注」；**第二行** 全宽两列 **All-In | 弃牌**（约 44px 触控高），避免横滑把弃牌挡在屏外或安全区外。**宽屏（≥680px）** 两行并为一行，底行两钮仍跟在主控件右侧，主区可横向滑动。
- **标准 NL 最小加注 + 操作区（2026-03-25）**：后端 `DpRoom.lastRaiseIncrement` 与 `DpRoomServiceImpl.bet` 校验：再加注总注须 ≥ `currentBetToCall + lastRaiseIncrement`（**不足最小加注的全下**仍允许；**短全下**不抬高最小增量）。每新街重置增量为大盲。前端 `GameActionPanel` 展示「最少抬到的总注」、**⅓～1½ 池**快捷按钮与**滑动条**（筹码量取「跟注 + round((底池+跟注)×比例)」的近似，与常见线上桌一致）。说明见 `docs/DPGAME.md`。**2026-04-01**：无人跟注时合法加注总注下限为 **一个大盲**（不再出现 1 筹码开池）；底池比例与输入/滑条对齐到 **小盲整数倍**（比例结果先算再向下取档，若低于最小加注则取不低于最小加注的最小小盲倍数）。
- **相关代码**：`DpGameRoomPushService`、`DpGameRoomWebSocketHandler`、`WebSocketGameRoomConfig`；`DpRoomServiceImpl` 定时循环末尾调用 `broadcastIfSubscribed`。

### NPC / AI（给不懂代码的人看的）

#### 酒馆档强度（BOT_Fish，2026-03-25）

- **规则型 NPC 读牌/赔率**：已不再按「难度档」对牌力或底池赔率加人为噪声；`estimateCurrentStrength` 与 `computePotOdds` 一律用真值，强弱差异主要来自 `NpcStyle`/`StyleProfile` 与各 bot 分支逻辑。**情绪 `mood`**：默认关闭（`DpNpcEngine.NPC_MOOD_ENABLED = false`），决策按 mood=0，结算也不再改写机器人 mood；若要恢复，将该常量改为 `true`。
- **决策概率抖动**：`applySoftNoise` 由 `DpNpcEngine.NPC_SOFT_NOISE_ENABLED` 控制（默认 `false`，已关闭 ±`PROB_NOISE_DELTA`）；与发牌/洗牌无关。
- **机器人决策随机**：`NPC_HAND_SEED_FOR_DECISIONS` 默认 `false`，每次行动 `new Random()`；房间的 `currentHandSeed` 仍用于前端动画 key、牌谱等，**不是发牌种子**，洗牌在 `newDeck()` 里单独 `Collections.shuffle`。

#### Shark（BOT_Shark）现在会“翻前按局势调范围”

从 2026-03-18 起，Shark 的翻前（preflop）不再只靠粗分类规则，而是接入了一个可复用的翻前模块：

- **入口位置**：`DpNpcEngine` 在 `case SHARK` 且 `stage == preflop` 时调用 `DpNpcSharkPreflopStrategy.decideForShark(...)`
- **核心思路**：
  - **底层**：把起手牌映射到一个“手牌分组”`HandGroup`（近似 13×13 的强度分层）
  - **上层**：把 **人数 / 位置 / 有效筹码深度 / 松紧风格 / 情绪** 合成一个 `rangeLevel(1~8)`
  - **风格（2026-03-21）**：Shark 的 `BotType → NpcStyle` 已改为 **松凶 `LOOSE_AGGRO`**（`preflopTightness`≈0.30），不再与 TAG 共用紧凶表（≈0.85），避免「有人翻前加注就大量弃牌」的观感。TAG 仍为紧凶。历史上若用「×2 扣档 + 面对 open 再减 2 档」叠高紧度也会加剧该问题；当前策略在 `DpNpcSharkPreflopStrategy` 中已用较温和扣档 + 面对 open 放宽。
  - 再用 `rangeLevel` 决定：
    - **无人加注**：要不要 open（以及 open 多大）
    - **面对 open**：call / 3bet / fold
    - **面对 3bet**：call / 4bet / fold
    - **面对 4bet+**：收敛到 all-in / call / fold（避免“每轮只抬一点点”）

#### 相关文件

- `src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpNpcSharkPreflopStrategy.java`
- `src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpNpcEngine.java`
- 整理后的说明文档：`docs/ai/npc-engine/README.md`

#### Shark 对手习惯跨房间记忆（2026-03-21）

桌上存在 `BOT_Shark` 时：

- **持久化表**：`dp_shark_opponent_profile` 定义于 Flyway `src/main/resources/db/migration/V1__init_schema.sql`（启动时自动迁移；主键为玩家昵称）。
- **存什么**：`PlayerStats`（累计入池/加注/摊牌等 + 最近 10 手窗口）与 `DpNpcSharkLearningLab` 的全部旋钮及分桶样本；**不按房间 ID**，只按昵称，适配随机房间号。
- **何时写入**：每手正常结算后，`DpNpcSharkOpponentMemoryService.persistOpponentsAfterHand` 在 `onHandSettled` 之后执行。
- **何时读入**：玩家 `joinRoom` 上桌时、以及每手 `newHand` 盲注就绪后，若 `playerStatsMap` 尚无该昵称则 `hydrate` 从 DB 加载。
- **代码入口**：`DpNpcSharkOpponentMemoryService`、`DpNpcSharkLearningLab`（旋钮键已改为仅对手昵称）。

#### Shark 策略 v2（2026-03-25）

- **目标**：少「赔率还行却弃」、少对跟注型对手多街送诈唬、多价值与河牌薄价值；`BOT_Fish` 从第一手起按 **跟注站剧本** 归类（无需等统计样本）。
- **参数中枢**：`DpNpcEngine.SharkStrategyProfile.DEFAULT` + `SharkConfig`（含 `CBET_*`、`RIVER_BLOCK_*`）；翻前 `DpNpcSharkPreflopStrategy` 对 Shark **整体放宽一档**；翻后尺度由配置驱动，见 `DpNpcSharkStrategy`。
- **剥削**：`DpNpcSharkExploitHandPlan.tuneAfterBasePlan` 增加主对手昵称，用于识别内置鱼机器人。

#### Shark 翻后：剥削剧本驱动 HandPlan（2026-03-21）

- **说明**：下面这段是**说明书**，不是“待开启功能”。只要运行的是当前工程编译出的服务、桌上有 `BOT_Shark`，翻后就会走这套逻辑。
- **做什么**：在 flop 首次生成整手计划（VALUE / BLUFF / POT_CONTROL / GIVE_UP 与 barrels、激进度）之后，根据**对手 `PlayerStats` + `LearningLab` 旋钮**归类成粗剧本（跟注站 / 紧弱 / 松凶等），再**改线路、加减压枪数、调激进度**；turn/river 强牌纠正为价值线时，对「跟注站」会多给 1 枪额度。
- **代码**：`DpNpcSharkExploitHandPlan`；接入点在 `DpNpcEngine.initHandPlanIfNeededForPostflop`（仅 Shark）与 `updateHandPlanForLaterStreetIfNeeded`。
- **数据库**：**不需要**为剥削剧本单独加表或加字段；沿用 `dp_shark_opponent_profile` 里已有的统计与旋钮 JSON 即可。

#### 大模型 NPC（BOT_LLM，火山方舟 / 豆包）

- **做什么**：昵称 `BOT_LLM` 仅在 `DpRoomServiceImpl` 定时器里走 `DpLlmNpcDecisionService`，与普通规则 NPC 分离；接口 `POST /dpRoom/addLlmBot?roomId=...` 可预约下一局上桌。
- **代码位置**：`npc/LlmNpc.java`、`DpLlmNpcDecisionService.java`、`DpNpcEngine.buildLlmNpcGameSnapshot` 等。
- **密钥与接入点怎么配（二选一，配置文件优先）**：
  1. **`application.properties`**（已预留项，适合本机开发）：`dp.llm.ark.api-key=`、`dp.llm.ark.endpoint-id=`（可选 `dp.llm.ark.base-url=`、`dp.llm.ark.reasoning-effort=`、`dp.llm.ark.thinking-type=`）。**不要把填了真密钥的文件提交到 Git。**
  2. **系统环境变量**：`ARK_API_KEY`、`ARK_ENDPOINT_ID`（可选 `ARK_BASE_URL`、`ARK_REASONING_EFFORT`、`ARK_THINKING_TYPE`）。
- **响应偏慢（深度思考 / 推理）**：方舟文档里两层独立控制：① `thinking.type`（`enabled` / `disabled` / `auto`）——是否走深度思考、是否返回思维链；若接入点为 Seed 等且**不传**，服务端对许多型号**默认为 `enabled`**，会明显变慢。② `reasoning_effort`（`minimal`～`high`）——在允许思考时调节思维链长度。**注意**：`thinking.type=disabled` 与部分 `reasoning_effort` 组合会被方舟拒绝（如 `low + disabled` → HTTP 400）。`LlmNpc` 在 `disabled` 时会自动不传 `reasoning_effort`，避免该错误；若需显式调推理强度，请用 `enabled`/`auto` 或去掉 `thinking-type` 让服务端默认。  
     - Windows：**设置 → 系统 → 关于 → 高级系统设置 → 环境变量**（用户或系统变量里新建）。  
     - 仅当前 PowerShell 会话：`$env:ARK_API_KEY="你的key"`、`$env:ARK_ENDPOINT_ID="ep-..."`。  
     - IntelliJ：**Run → Edit Configurations → 你的 Spring Boot → Environment variables**。
- **对局摘要**：`DpUtilSmartContext` 由 `buildLlmNpcGameSnapshot` 与房间状态一起打成 `LlmNpcGameContext` 再发给模型。

### 牌谱与用户关联（2026-03-30）

- **落库思路（牌谱 / 参与者 / Shark 记忆表）**：[docs/DP_PERSISTENCE_README.md](docs/DP_PERSISTENCE_README.md)。
- **表**：`dp_observed_hand_participant` 定义于 `src/main/resources/db/migration/V1__init_schema.sql`（多对多拆解；**不设数据库外键**）。
- **user_id**：列上**可为 NULL**；入库时优先用内存 `dpUserId`，否则按 **昵称** 查 `dp_user`（昵称全局唯一时可补全）；仍无账号则只存 `nickname_snapshot`。机器人不占行。
- **前端**：`GET /dpUser/loginProfile`、`POST /dpUser/registerUser` 返回统一 `ResultUtil`（`success` / `code` / `message` / `data`）；登录成功时 `data` 含 `userId`、`nickname`、`token`。前端用 `userId` 写入 `localStorage`；**创建房间 / 加入房间 / 观众「下一局加入」** 可带可选 `userId`（与昵称须与 `dp_user` 一致才采纳），界面只展示昵称。
- **JWT 全局鉴权（2026-04-07）**：引入 `spring-boot-starter-security`，`SecurityConfig` + `JwtAuthenticationFilter` 对除白名单外的请求要求 `Authorization: Bearer`；未登录返回 HTTP 401 JSON。白名单含：登录/注册、`/ws/**`、静态资源、`GET /dpRoom/getNowRoom`、`GET /dpRoom/getAllRooms2`、`GET /dpMusic/list`（旁观/分享链接与曲库列表）；详见 [docs/JWT.md](docs/JWT.md)。前端 `main.js`：axios **请求**拦截器自动带 token（登录/注册请求除外）；**响应**拦截器对 HTTP 401 弹窗提示、清 `userInfo` 并跳转登录页。
- **用户密码加密（2026-04-07）**：`DpUserServiceImpl` 在 `registerUser`、`loginUser`、`loginProfile`（经 `loginUserOrNull`）与 `updateUserInfo` 中统一经 **`CryptoUtil.md5HexUtf8`** 使用 **MD5(UTF-8)** 处理密码，数据库 `dp_user.password` 存储 32 位小写 MD5 字符串。**前端传明文、后端摘要**；校验与找回密码说明见 [docs/DpUserPassword.md](docs/DpUserPassword.md)。
- **会话令牌**：`JwtTokenService` 使用 **JWT**，签名算法为 **HMAC-SHA256**（JJWT 默认与 `Keys.hmacShaKeyFor` 一致）；与口令摘要工具类分离。
- **加入房间（JWT）**：`POST /dpRoom/joinRoom2` 在全局鉴权通过后校验 `SecurityContext` 中昵称与参数 `nickname` 一致；大厅「加入」走该接口。旧 `POST /dpRoom/joinRoom` 同样需带 token。旧缓存仅有 `userId` 无 `token` 时，`ensureDpUserIdInStorage` 会再调 `loginProfile` 补 `token`。
- **历史查询（列表/详情）**：仅按 **`userId`**（`dp_user.id` + 参与者表 `user_id`）；不再支持仅昵称查询。
- **列表接口（分页，PageHelper）**：`GET /dpHandHistory/list?userId=必填&page=1&pageSize=10` → 仅按参与者表 `user_id`；`pageSize` 默认 10、最大 100。前端在打开历史页/对局页前用 `GET /dpUser/loginProfile` 把 `userId` 写入 `localStorage`（兼容旧缓存仅有昵称密码的情况）。
- **详情接口**：`GET /dpHandHistory/detail?handHistoryId=必填&userId=必填` → `DpHandHistoryDetailDTO`。仅 **参与者（该 user_id）** 可读。前端 `HandHistoryDetail`：**本人**始终可看自己的底牌（含自己弃牌后）；**他人**弃牌则不展示其底牌，未弃牌仍按街展示。**结算** 页另有终局公共牌。
- **牌谱详情页 UI（2026-03-30）**：`HandHistoryDetail.vue` 使用浅灰渐变底、卡片式元信息、分段街导航（胶囊底 + 高亮当前街）、深绿「台呢」公共牌区与斑马纹表格；庄/小盲/大盲标签分色；边池以卡片列表展示。牌面仍复用 `dp-poker-cards.css`，详情页内关闭扫光动画以免干扰阅读。
- **牌谱本手盈亏（2026-03-30）**：`DpNpcSharkObservedHandHistory#finalizeHand` 原先用「盲注后筹码」作基准，未把已下盲注算进本手盈亏（例如仅输掉大盲时显示 0）。修复为「盲注前」基准：`beforeHand = chipsAfterBlinds + 该座位小盲/大盲额`，`net = 终局筹码 - beforeHand`。历史库里已写入的 `payload_json` / `net_chips` 不会自动重算，仅新产生的牌谱正确。
- **牌谱主池统计（2026-04-01）**：入库字段 `mainPotBeforeSettlement` 使用「有效底池」——对结算前各分层池，仅累加 **至少两名玩家有资格赢取** 的池金额；短码全下后深码多出的单边池不计，避免虚高（例如 A=20、B=40 全下时有效为 40 而非双方总筹码 60）。`potsBeforeSettlement` 仍保留完整分层供详情展示。

### 曲库 BGM（2026-04-01）

- **表**：`dp_music_track` 由 Flyway `V1__init_schema.sql` 在 `school_db` 中建表。
- **文件目录**：默认 `P:/javaworkspace/DPGameFiles/music/`（`mgdemoplus.music.file-location`），HTTP 映射 `/music/**`；Docker 可用环境变量 `MGDEMOPLUS_MUSIC_FILE_LOCATION`（见 `docker-compose.yml`）。
- **接口**：`POST /dpMusic/upload`（multipart：`file` 必填；可选 `displayName`、`sortOrder`、`userId`）写入磁盘并入库；`GET /dpMusic/list` 返回已上架曲目（供对局音乐盒拉列表）。
- **前端**：登录后大厅点「曲库上传」进入 `/#/music-upload`，可试听已入库曲目（开发环境经 `/dev-api` 代理访问 `/music/...`）。
- **对局音乐盒（2026-04-01）**：对局页顶栏「音乐盒」打开曲库列表；**播放/暂停/停止** 经 **同一房间 WebSocket** 广播，服务端校验发送者昵称属于本桌玩家或观众后，向该房间所有连接推送 `{"_ws":"roomMusic",...}`（并记住最后一帧，**新进入房间者**在首包房间快照后会再收到一帧音乐状态）。摊牌/结算阶段自动暂停曲库 BGM，避免与既有结算短 BGM 叠播；离开结算后若状态仍为 `play` 会恢复播放。客户端上行：`{"_ws":"roomMusicSync","nickname":"…","action":"play|pause|stop","trackId":…,"webPath":"/music/…","displayName":"…"}`（`play` 时 `trackId`/`webPath` 必填；路径须为 `/music/` 下安全文件名）。

### Redis 接入

- **依赖**：已加入 `spring-boot-starter-data-redis`（客户端为 **Lettuce**），连接参数在 **`application.properties`** 的 `spring.data.redis.*`（默认 `127.0.0.1:6379`、库 `0`）。
- **环境变量覆盖**（部署时常用）：`SPRING_DATA_REDIS_HOST`、`SPRING_DATA_REDIS_PORT`、`SPRING_DATA_REDIS_PASSWORD`。**`docker compose`** 已包含 **Redis** 服务，应用容器内指向 `redis:6379`，默认口令与 compose 中 **`REDIS_PASSWORD`**（默认 `mgdemo_redis`）一致；宿主机调试 Redis 用 **`localhost:6380`**（见 [docs/DOCKER.md](docs/DOCKER.md)）。
- **在代码里用**：Spring 会自动装配 **`StringRedisTemplate`**、**`RedisTemplate`**、**`RedisConnectionFactory`**，按需 `@Autowired` 或构造器注入即可（例如 `stringRedisTemplate.opsForValue().set("k","v")`）。
- **本机小实验（跑起后端即可）**：`RedisLabController` 提供 **`/demo/redis/*`**（已加入 JWT 白名单，浏览器可直接访问）。例：`GET http://localhost:8088/demo/redis/ping`；存取见 `RedisLabController` 注释。键会自动加前缀 `mgdemo:lab:`。
- **曲库列表缓存**：`DpRedisListCacheService` 将 **`GET /dpMusic/list`** 的 JSON 缓存在 Redis（键 `mgdemo:cache:dpMusic:listEnabled`），默认 TTL `mgdemoplus.cache.music-list-ttl-seconds`（默认 300）；**曲库上传成功**后删键以便立刻回源。大厅 **`GET /dpRoom/getAllRooms2`** 仍直接读内存 `roomMap`，不经 Redis。Redis 异常时曲库列表自动回源，不阻断接口。
- **说明**：当前对局 **WebSocket 与房间状态仍在单机内存**；若要多实例共房间或跨机广播，需在业务层自行用 Redis（Pub/Sub、缓存会话映射等）扩展，见 `docs/WEBSOCKET.md` 中「多实例」相关段落。

### Docker 部署

- **云服务器要「全套」和本机一致**：用 **`git clone`** 拉**整个仓库**（含 `docker-compose.yml` 等），再执行 `docker compose up -d --build`；**Docker Hub 不能代替 Git 拉代码**。步骤与说明见 [docs/DOCKER.md](docs/DOCKER.md) 中的「云服务器和本机一样「全套」」。
- **仅拉镜像、不 clone 仓库**：使用 **`docker-compose.hub.yml`**，并预先在 Hub 上推送 **`dpgame`、`dpgame-nginx`**；**MySQL 使用官方 `mysql:8.0`**，表结构由应用启动时 **Flyway** 迁移（见 [docs/DOCKER.md](docs/DOCKER.md)「仅镜像部署」）。服务器上只需该 YAML + `docker compose -f docker-compose.hub.yml pull` 与 `docker compose -f docker-compose.hub.yml up -d`（**不要**加 `--build`）。
- **Docker Hub：什么时候推镜像、什么时候用 `docker-compose.hub.yml`（速览）**
  - **什么时候要推到 Hub**：在**有完整源码**的机器上，只要你改了需要打进镜像的内容（根目录 **`Dockerfile`**、**`Dockerfile.nginx`**、应用或前端代码、`docker/nginx` 配置、**`src/main/resources/db/migration/`** 下 Flyway 脚本等），并希望**另一台只拉镜像、不拉 Git 的机器**跑新版本，就需要在该机器上 **重新构建并推送** `dpgame` 与 `dpgame-nginx`（**版本标签一致**）。
  - **怎么推**：先执行 **`docker login`**。Windows 在仓库根目录运行 **`.\build-push-hub.ps1`**（可用 **`-Tag v1.0.1`** 等自定义标签）；Linux / macOS 用 [docs/DOCKER.md](docs/DOCKER.md)「仅镜像部署」里与 `REG` / `TAG` 对应的 **`docker build`** / **`docker push`** 命令。部署端 `.env` 里的 **`IMAGE_TAG`**（或 compose 默认值）须与本次推送的**标签一致**。
  - **什么时候在服务器上用 `docker-compose.hub.yml`**：目标环境**不** `git clone` 仓库、只安装 Docker 与 Compose、**只从 Hub 拉镜像**部署时，把 **`docker-compose.hub.yml`** 放到任意目录（可从本仓库拷贝或下载单文件），同目录可选 **`.env`** 配置 `DOCKER_REGISTRY`、`IMAGE_TAG`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD` 等，再 **`pull` + `up -d`**。编排里只有 **`image:`**，没有 **`build:`**，因此服务器上**不需要**源码目录。
  - **和默认 `docker-compose.yml` 的区别**：默认文件在**仓库根目录**用 **`build:`** 本地构建并常挂载 `./docker-data/uploads`；**Hub 方式**把 Nginx 与应用打进镜像（**DDL 随应用 JAR 内 Flyway**），MySQL 为官方镜像，适合「只拷一个 YAML + `.env` 就部署」。
- **详细说明**（Git 与打包关系、`WebConfig` / 驱动 / 前端生产地址、DBeaver 连接串等）：见 [docs/DOCKER.md](docs/DOCKER.md)。
- **Nginx 反向代理**（Compose 内 `nginx` 服务、80 端口、WebSocket 转发）：见 [docs/NGINX.md](docs/NGINX.md)。
- **一键（MySQL + Redis + 应用 + Nginx）**：仓库根目录执行 `docker compose up --build`；浏览器可打开 **`http://localhost`**（经 Nginx）或 **`http://localhost:8088`**（直连应用；前端为 hash 路由，形如 `/#/login`）。默认 MySQL root 密码为 `mgdemo_root`，可通过环境变量 `MYSQL_ROOT_PASSWORD` 修改。容器内 MySQL 对 **宿主机** 映射为 **`localhost:3307`**（避免与本机已占用的 **3306** 冲突）；应用容器仍通过内部网络访问 `mysql:3306`。**Redis**：Compose 内服务名 `redis`，数据卷 `redis_data`；默认口令 **`mgdemo_redis`**（可用环境变量 **`REDIS_PASSWORD`** 覆盖，须与 `SPRING_DATA_REDIS_PASSWORD` 一致）。宿主机连接容器内 Redis 用 **`localhost:6380`**（映射到容器 6379，避免与本机独立 Redis 的 6379 冲突）。仅 `docker run` 单容器时需自行提供 Redis 或设置 `SPRING_DATA_REDIS_*` 指向已有实例。
- **仅构建后端镜像**：`docker build -t mgdemoplus .`，运行示例：  
  `docker run -p 8088:8088 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/school_db?useSSL=false -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=你的密码 -e MGDEMOPLUS_IMAGES_FILE_LOCATION=file:/data/mgdemo-files/ -v 本机目录:/data/mgdemo-files mgdemoplus`
- **说明**：`Dockerfile` 会编译 `front/dp_game` 并把 `dist` 打进 jar 的 `static`，与 API、WebSocket 同端口；本机开发默认上传目录为 `P:/javaworkspace/DPGameFiles/`（配置项 `mgdemoplus.images.file-location`）。`/images/**` 在容器内默认为 `/data/mgdemo-files`（compose 已映射到 `./docker-data/uploads`）。**数据库**：MySQL 命名卷仅存数据；**`school_db` 内建表改表全部由应用 Flyway**（`classpath:db/migration`）在启动时执行。若 **`mysql_data` 曾为旧 Docker init 所建**，升级后 Flyway **V1** 可能与已有表冲突，开发机可用 `docker compose down -v` 删卷重来（**会清空库**）；生产须自备备份与迁移策略。**勿把含真实密钥的 `application.properties` 依赖进镜像**；LLM 等密钥请用环境变量 `ARK_API_KEY`、`ARK_ENDPOINT_ID` 等在 compose 中注入。
