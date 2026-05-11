# MGDemoPlus(此版本为npc前瞻版)

基于 **Spring Boot** 的 Web 演示项目，核心是**多人实时在线卡牌对战 / 多人联机策略卡牌演示**：多房间、实时对战、AI 智能玩家、对局回放、可选火山方舟大模型决策玩家等。前端 `**front/dp_game`** 为 **Vue 2 + Vue Router + Vuex + Element UI**，构建产物由 `**Dockerfile`** 打进后端 JAR，与 REST、WebSocket **同端口**发布。

**长说明（环境变量、WebSocket、AI 玩家、对局回放、Docker 等）：** 中文 **[README.ch.md](README.ch.md)** · 英文 **[README.en.md](README.en.md)**（同结构对照）。

---

## 项目特性

### 游戏与对局

- **标准卡牌对战规则**：回合流程、最小操作、多玩家结算逻辑等在后端 `DpRoomServiceImpl` 等模块中实现；对局页通过轮询 + WebSocket 同步房间状态。房主神器「踢出至观众席」支持多选批量：`POST /dpRoom/kickPlayersBatch`（昵称英文逗号分隔，服务端去重后单次同步大厅）；单人仍用 `POST /dpRoom/kickPlayer`。**对局页顶栏**在「观众席」旁提供「等待名单」按钮（与观众席同为青色强调样式），点开可查看已报名「下一局加入对局」的玩家及候补顺序（与房间状态中的 `waitNextHand` 一致）。
- **大厅快速匹配（含自动建房）**：`POST /dpRoom/quickMatch2` 先尝试进入已有**无密码公开房**（逻辑同原快匹）；若无空位则写入**本机内存 FIFO 队列**（小盲 5、大盲 10、初始 50 倍、最多 9 人、无密码），队列中每**满两人**自动建新桌：队首当房主并已准备，第二位 `joinRoom` 后同样准备，并由服务端**立即调用** `startGame`（等同创建房间页的「建好即开局」），直接进入首手。**排队中**由 `WebSocket` 路径 **`/ws/dp-quick-match`**（`?nickname=&token=`）推送 `WAITING` / `MATCHED` / `IDLE`；离开队列可 **`POST /dpRoom/quickMatchCancel2`**；单条等待约 **3 分钟**未配上会被周期性任务移出队列（间隔 `mgdemoplus.dp-quick-match-prune-ms`，默认 30s）。**并发**：`joinRoom` / 候补 / `exitRoom` / 开局前准备等按 `DpRoomBO` 监视器串行化同一房间内的写操作；快匹扫房 + 默认队列「拉两人建房」另用 `dpQuickMatchAssignmentLock` 与单房锁配合，减轻同时点匹配时「明明有空位却失败 / 准备失败」的竞态（仍仅保证单 JVM 内一致，多实例部署需另行设计）。
- **多房间**：房间状态保存在进程内 `**ConcurrentHashMap`（`roomMap`）**；详见长文档中的 WebSocket 与房间说明。前端 `**/create-room`** 页可设置**房间基础积分**、**每人初始积分**与可选**进房密码**（仅存内存，重启失效），提交后会**自动调用开局并进入对局页**（房主可先单人上桌，他人从大厅加入）；大厅 `**/home`** 仅列表与加入；**`/room/:id`** 仍用于未开局时的加入/等人场景，若房间已在进行中则会自动转入 **`/game/:id`**。
- **实时推送**：游戏页 WebSocket 路径形如 `**/ws/dp-game?roomId=...`**；有订阅者时才序列化推送，并与上次 JSON 去重以省流量。
- **对局回放**：完整游戏对局记录可落库，支持按用户分页列表与详情（权限与卡牌信息展示规则见 `docs` 下说明）。
- **大厅列表（单例版）**：`/dpRoom/publicRooms` 改为读 `dp_room_lobby` 单表（唯一数据源），返回结构保持 `{ list, total, page, pageSize }`；服务端在建房/进退房/踢人/房主变更/开局与房间销毁时同步摘要，分页查询使用 PageHelper + Redis 版本缓存（`mgdemo:cache:dpRoom:publicRooms:rev` 与 `mgdemo:cache:dpRoom:publicRooms:data:{rev}:{page}:{pageSize}`），变更时通过 `INCR rev + SCAN 删除旧 data 键` 失效，失败仅记录日志不阻断业务。带筛选的 `/dpRoom/publicRooms/query` 共用同一 `rev`，结果键为 `mgdemo:cache:dpRoom:lobbyQuery:data:{rev}:{paramHash}`（`paramHash` 为规范化查询串的 SHA-256 前 16 位十六进制），TTL 与 `mgdemoplus.cache.dp-room-public-rooms-ttl-seconds` 一致。

### 用户与账号

- **认证**：**Spring Security** + **JWT**（`Authorization: Bearer`）；白名单含登录注册、`/ws/`**、部分房间只读接口等（完整列表见 [docs/JWT.md](docs/JWT.md)）。
- **密码**：服务端对密码做 **bcrypt** 摘要后入库（详见 [docs/DpUserPassword.md](docs/DpUserPassword.md)）。
- **虚拟积分**：演示用途，不涉及真实货币。

### AI 智能玩家

- **规则型策略机器人**：多种风格（无分级「难度噪声」），包含回合策略、行为逻辑、对手习惯记忆等（概要见 [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md)）。**翻前**仅由 `DpNpcUnifiedPreflopStrategy` 处理：起手牌语义仍为 G1–G8，`rangeLevel`、简化位置、`PreflopSpot` 对应局面在运行时查 **预计算的 13×13 表**（对角=对子，行&gt;列为杂色、行&lt;列为同色，值为 1/0）；`groupOf` 仍用于填表逻辑与少量诈唬分支；另有 `StyleProfile`（vpip、pfr、callStation、foldToPressure、mood）、`lateFactor`、`raiseLevel` 与 BotType 小档偏移等；**各 BotType 策略类不再单独处理翻前**，若统一翻前返回 null（异常局面）则引擎兜底过牌/跟注。**细数据流**见 [docs/ai/npc-preflop-unified-decision-flow.md](docs/ai/npc-preflop-unified-decision-flow.md)。**翻后**由 `DpNpcEngine#decideBotAction` 组装 `DpNpcRuleDecisionParams` 后按 `BotType` 进入 `npc` 包六个类：`DpNpcFishStrategy`、`DpNpcCallStrategy`、`DpNpcLagStrategy`、`DpNpcManiacStrategy`、`DpNpcTagStrategy`、`DpNpcNitStrategy`（各文件自包含，便于按风格独立演进）；`BOT_LLM` 仍单独走局面包与方舟接口。
- **大模型玩家（可选）**：`BOT_LLM` 通过兼容 OpenAI 的 Chat 接口实现 AI 决策，需配置 `ARK_API_KEY`、`ARK_ENDPOINT_ID` 等（见 [README.en.md](README.en.md) 中大模型小节与 [docs/ENV_README.md](docs/ENV_README.md)）。默认开启 `**ARK_RESPONSE_JSON_OBJECT`**（`response_format=json_object`）以压缩正文；模型须返回含 **action / chips_to_add / brief_reason** 的 JSON（**brief_reason** 为 1～2 句决策理由，控制台会单独打印）。在 **thinking 仍开启** 的前提下，将 `**ARK_REASONING_EFFORT`** 从 `**high**` 改为 `**medium**` 或 `**low**` 可明显缩短服务端思维链耗时。**日志**：控制台中的「只看模型思考」用 Spring `**logging.level`**，`application.yml` 里已注释示例：`com.example.mgdemoplus.dp.BotLlmReasoning` 专打 reasoning，其它可把 `DpLlmNpcDecisionService` 或 `root` 调低。喂给模型的 user 包顺序为 **M→T→H→E**：`M` 含盲注、`rl`（加注层级，非 ante）及 `**SBseat`/`BBseat`**；`T` 为座位序下的昵称、剩余筹码、本街已下；`**H` 的 `pot`/`call`/`stack` 为服务端真值**（键名 `hero`、`stage`、`stack` 等）；`**E`** 为可读键名的对手摘要；`**rk=**` 与 `**hsl=**` 仍为成牌相关真值说明。`**rk**` 对「公牌已成对、手牌未中该对、仅拼踢脚」的情形会在 `DpUtilHandEvaluator.toSimpleStrength` 中封顶为 **MEDIUM/WEAK**，避免误标成 STRONG。

### 其它功能

- **曲库 BGM**：上传与列表接口，对局内可通过 WebSocket 同步播放状态（见长文档「曲库 BGM」）。
- **Redis**：用于曲库列表缓存、演示用 `/demo/redis/*` 等；**房间与对局 WebSocket 默认不依赖 Redis 做共享状态**（多实例需自行扩展，见 [docs/WEBSOCKET.md](docs/WEBSOCKET.md)）。

---

## 技术架构


| 层级       | 技术                                                                                                                                               |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 后端       | Java **17**、**Spring Boot 3.5**、Spring Web / Security / WebSocket、**MyBatis-Plus**、**PageHelper**、**Druid**、MySQL 驱动、**Lettuce**（Redis）、**JJWT** |
| 前端（卡牌游戏） | **Vue 2**、Vue Router、**Vuex**、**Element UI**、axios（源码目录 `**front/dp_game`**）                                                                     |
| 数据       | **MySQL 8**（库名默认 `**school_db`**）、**Redis 7**                                                                                                    |
| 构建与部署    | **Maven**、**Docker** / **Docker Compose**、**Nginx**（反向代理与 WS 转发）                                                                                 |


### 前端「猫咪派对」展示包装（仅文案与 UI）

- 登录成功后会请求在大厅展示**玩法说明**弹窗（`CatTutorialDialog.vue`，`append-to-body`）；样式与当前**界面主题**一致：`body[data-dp-game-theme]` 上的 `--dp-*` 与 `**dp-game-element-ui.css`** 中对 `el-dialog` 的覆盖（避免暗色主题下浅色字叠在白底上）。可选「不再自动弹出」，仍可通过大厅 **玩法说明** 打开。
- **界面主题**：预设多套 + 可选「**自定义**」（选一预设作底 + **强调色**，派生若干 `--dp-*` 覆盖；`dp_game_ui_theme` + `dp_game_custom_theme` 存 **localStorage**，非数据库）。自定义模式下配色编辑区 **默认收起**，用 **「展开配色」** 打开、**「收起配色」** 合上。
- 术语与本地存储键名集中在 `**front/dp_game/src/constants/dpCatThemeCopy.js`**（如：发牌猫、小猫 SC / 大猫 BC、小鱼干；对局阶段展示为翻前圈 / 翻后圈 / 半决赛 / 决赛圈 / 结算阶段）；**不改变**后端 API、请求参数或 WebSocket 载荷字段名。

---

## 环境要求

- **JDK 17**、**Maven 3.6+**
- 开发前端：**Node.js**（与 `front/dp_game/package.json` 中 Vue CLI 5 兼容的版本）
- 可选：**Docker 20+**、**Docker Compose 2+**（一键起 MySQL + Redis + 应用 + Nginx）

---

## 快速开始

### 1. 克隆与配置

```bash
git clone <你的仓库地址> MGDemoPlus
cd MGDemoPlus
copy .env.example .env
# 编辑 .env：MYSQL_ROOT_PASSWORD、REDIS_PASSWORD、JWT_SECRET、ARK_* 等（勿提交含真实密钥的 .env）
```

环境变量与 `application.properties` 的关系见 **[docs/ENV_README.md](docs/ENV_README.md)**。

### 2. Docker 一键（推荐）

在**仓库根目录**执行（记得修改脚本信息）：

```bash
.\build-push-hub.ps1
docker compose -f docker-compose.hub.yml up -d
```

或本机构建：

```bash
docker compose up --build
```

**GitHub 自动构建镜像（可选）**：推送分支 `**desensitization`** 时由 GitHub Actions 构建并推送 `1933886418/dpgame*:v1.0.1` 三镜像（与 `build-push-hub.ps1`、默认 `docker-compose.hub.yml` 的 `**IMAGE_TAG=v1.0.1**` 一致，**不**另打 `latest`）；亦可在 **Actions** 里手动运行。仓库 **Secrets** 中配置 `**DOCKERHUB_TOKEN`** 即可；工作流见 `[.github/workflows/docker-publish.yml](.github/workflows/docker-publish.yml)`。

- **经 Nginx**：浏览器打开 **[http://localhost](http://localhost)**（见 [docs/NGINX.md](docs/NGINX.md)）
- **直连应用**：**[http://localhost:8088](http://localhost:8088)**（前端为 **hash 路由**，例如 `**/#/login`**）

默认 MySQL root 密码见 `docker-compose.yml` / `.env`（默认 `**mgdemo_root**`）；宿主机连接 MySQL 一般为 `**localhost:3307**`，Redis 为 `**localhost:6380**`（见 [docs/DOCKER.md](docs/DOCKER.md)）。

### 3. 本机开发（后端 + 前端分离）

**后端**（工作目录设为**仓库根目录**，以便加载根目录 `.env`）：

```bash
mvn spring-boot:run
# 或
mvn -q -DskipTests package && java -jar target/MGDemoPlus-0.0.1-SNAPSHOT.jar
```

**前端**（修改 `front/dp_game` 时）：

```bash
cd front/dp_game
npm install
npm run dev
```

开发环境代理与接口前缀以项目内 `vue.config.js` 为准（常见为 `**/dev-api**` 转发到后端）。

**大厅与对局 UI 主题**：游戏大厅（`/home`）、房间内（`/room/:id`）、曲库上传（`/music-upload`）、历史对局列表与详情与对局页共用同一套主题选项与本地持久化键；**登录 / 注册**在 `App.vue` 中同样可选择主题（`dp-auth-shell.css`）。`document.body[data-dp-game-theme]` 由 `main.js` 在路由与主题变更时统一维护，以便 Element UI 弹层配色一致。说明见 `front/dp_game/docs/THEME_BINDING_README.md` 第 6～7 节。

---

## 仓库结构（概览）

```
MGDemoPlus/
├── src/main/java/com/example/mgdemoplus/   # Spring Boot 主工程
├── front/dp_game/                          # 游戏前端（Vue CLI）
├── docker/                                 # Nginx 等配置
├── docker-data/
├── docker-compose.yml
├── docker-compose.hub.yml
├── Dockerfile / Dockerfile.mysql / Dockerfile.nginx
├── pom.xml
├── README.md                               # 本文件：中文 + 英文短版
├── README.ch.md                            # 中文长说明（运维与迭代记录）
├── README.en.md                            # 英文长说明（与 README.ch 对照）
└── docs/                                   # 专题文档；总索引见 docs/README.md
```

---

## 文档索引

- **文档总导航（推荐）**：[docs/README.md](docs/README.md)
- **环境与变量**：[docs/ENV_README.md](docs/ENV_README.md)
- **Docker**：[docs/DOCKER.md](docs/DOCKER.md)
- **维护级长说明**：[README.ch.md](README.ch.md)（中文）· [README.en.md](README.en.md)（英文）

---

## 许可证与声明

**本项目仅为计算机技术学习与演示使用，采用虚拟积分，不涉及真实货币交易，不包含任何赌博相关功能。**

若本短版与代码不一致，以 **[README.ch.md](README.ch.md)** / **[README.en.md](README.en.md)** 与 **[docs/README.md](docs/README.md)** 为准。

---

## English

A **Spring Boot** web demo centered on **multiplayer online strategy card play**—multi-room sessions, real-time battle, AI players, session replay, and optional Volcengine Ark LLM-based players. The `**front/dp_game`** UI is **Vue 2 + Vue Router + Vuex + Element UI**; production builds are embedded into the Spring Boot JAR by `**Dockerfile`**, served on the **same port** as REST and WebSocket.

**Long-form maintenance notes:** Chinese **[README.ch.md](README.ch.md)** · English **[README.en.md](README.en.md)** (same structure).

### Features

#### Gameplay

- **Card battle rules**: Turn flow, minimum actions, and multiplayer settlement logic are implemented server-side (e.g. `DpRoomServiceImpl`); the room UI uses polling plus WebSocket for state.
- **Rooms**: In-memory `**ConcurrentHashMap` (`roomMap`)**; see long docs for WebSocket and room lifecycle.
- **Push**: Game WebSocket path pattern `**/ws/dp-game?roomId=...`**; serializes only when subscribers exist and de-duplicates JSON vs last push.
- **Session replay**: Full match records can be persisted; user-scoped paged list and detail (permissions and card-info display rules in `docs`).
- **Public lobby (single-table source)**: `/dpRoom/publicRooms` reads `dp_room_lobby` as the single source of truth, response shape `{ list, total, page, pageSize }`; summaries sync on create/join/leave/kick/owner change/start/destroy; paging uses PageHelper + Redis revision cache (`mgdemo:cache:dpRoom:publicRooms:rev`, `mgdemo:cache:dpRoom:publicRooms:data:{rev}:{page}:{pageSize}`), invalidated via `INCR rev` + `SCAN` to drop old data keys; failures are logged only. Filtered `/dpRoom/publicRooms/query` shares the same `rev`; keys `mgdemo:cache:dpRoom:lobbyQuery:data:{rev}:{paramHash}` (`paramHash` = first 16 hex chars of SHA-256 of normalized query string), TTL matches `mgdemoplus.cache.dp-room-public-rooms-ttl-seconds`.

#### Accounts

- **Auth**: **Spring Security** + **JWT** (`Authorization: Bearer`); whitelist covers login/register, `/ws/`**, some read-only room APIs—full list in [docs/JWT.md](docs/JWT.md).
- **Passwords**: **bcrypt** digest stored server-side—see [docs/DpUserPassword.md](docs/DpUserPassword.md).
- **Virtual points only**: Demo use; no real currency.

#### AI players

- **Rule-based bots**: Multiple styles and difficulty levels with strategy logic and opponent memory—overview in [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md).
- **LLM player (optional)**: `BOT_LLM` via Ark APIs; set `ARK_API_KEY`, `ARK_ENDPOINT_ID`, etc. ([docs/ENV_README.md](docs/ENV_README.md), LLM section in [README.en.md](README.en.md)).

#### Other

- **Music library BGM**: Upload/list APIs; in-game playback state can sync over the room WebSocket.
- **Redis**: Used for music list cache, `/demo/redis/*` labs, etc.; **room WebSocket state is not Redis-backed by default** (scale-out notes in [docs/WEBSOCKET.md](docs/WEBSOCKET.md)).

### Stack


| Layer                | Tech                                                                                                                                                   |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Backend              | Java **17**, **Spring Boot 3.5**, Web / Security / WebSocket, **MyBatis-Plus**, **PageHelper**, **Druid**, MySQL driver, **Lettuce** (Redis), **JJWT** |
| Frontend (card game) | **Vue 2**, Vue Router, **Vuex**, **Element UI**, axios (`front/dp_game`)                                                                               |
| Data                 | **MySQL 8** (default DB `**school_db`**), **Redis 7**                                                                                                  |
| Build / Ops          | **Maven**, **Docker** / **Compose**, **Nginx**                                                                                                         |


### Requirements

- **JDK 17**, **Maven 3.6+**
- For frontend dev: **Node.js** compatible with Vue CLI 5 in `front/dp_game`
- Optional: **Docker 20+**, **Compose 2+**

### Quick start

#### 1. Clone and env

```bash
git clone <your-repo-url> MGDemoPlus
cd MGDemoPlus
cp .env.example .env
# Edit .env — do not commit secrets
```

See [docs/ENV_README.md](docs/ENV_README.md) for variable mapping.

#### 2. Docker (recommended)

From repo root:

```bash
docker compose up --build
```

**GitHub Actions (optional)** — On push to `**desensitization`**, workflows build/push `**1933886418/dpgame*:v1.0.1**` only (matches `build-push-hub.ps1` / default `**IMAGE_TAG=v1.0.1**`; no `**latest**` tag). Configure `**DOCKERHUB_TOKEN**`. See `[.github/workflows/docker-publish.yml](.github/workflows/docker-publish.yml)`.

- Via Nginx: **[http://localhost](http://localhost)** — [docs/NGINX.md](docs/NGINX.md)
- Direct app: **[http://localhost:8088](http://localhost:8088)** — hash routes, e.g. `**/#/login`**

MySQL on host is typically `**localhost:3307**`, Redis `**localhost:6380**` ([docs/DOCKER.md](docs/DOCKER.md)).

#### 3. Local dev (split)

Backend (run **from repo root** so `.env` loads):

```bash
mvn spring-boot:run
```

Frontend:

```bash
cd front/dp_game
npm install
npm run dev
```

Use `vue.config.js` dev proxy (often `**/dev-api**`) to hit the API.

### Repo layout (overview)

```
MGDemoPlus/
├── src/main/java/com/example/mgdemoplus/
├── front/dp_game/
├── docker/
├── docker-data/
├── docker-compose.yml
├── docker-compose.hub.yml
├── Dockerfile
├── pom.xml
├── README.md              # This file — Chinese + English short overview
├── README.ch.md           # Chinese long-form maintenance notes
├── README.en.md           # English long-form (same structure as README.ch)
└── docs/                  # Topic docs; see docs/README.md
```

### Documentation

- **[docs/README.md](docs/README.md)** — full index of `docs/`
- [docs/ENV_README.md](docs/ENV_README.md)
- [docs/DOCKER.md](docs/DOCKER.md)
- [docs/NGINX.md](docs/NGINX.md)
- [docs/JWT.md](docs/JWT.md)
- [docs/DPGAME.md](docs/DPGAME.md)
- [docs/WEBSOCKET.md](docs/WEBSOCKET.md)
- [front/dp_game/docs/README.md](front/dp_game/docs/README.md)
- [README.ch.md](README.ch.md) · [README.en.md](README.en.md) — long-form maintenance notes

### Statement

**This project is for technical learning and demonstration only. It uses virtual points, involves no real currency, and does not provide gambling-related functionality.**

If this overview disagrees with code, trust **[README.ch.md](README.ch.md)** / **[README.en.md](README.en.md)** and **[docs/README.md](docs/README.md)** / **docs/**.