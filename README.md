# MGDemoPlus

基于 **Spring Boot** 的 Web 演示项目，核心是 **DP 德州扑克（无限注）**：多房间、实时对局、机器人 NPC、牌谱回放、可选火山方舟大模型牌手等。前端 **`front/dp_game`** 为 **Vue 2 + Vue Router + Vuex + Element UI**，构建产物由 **`Dockerfile`** 打进后端 JAR，与 REST、WebSocket **同端口**发布。

**长说明（环境变量、WebSocket、NPC、牌谱、Docker 等）：** 中文 **[README.ch.md](README.ch.md)** · 英文 **[README.en.md](README.en.md)**（同结构对照）。

---

## 项目特性

### 游戏与对局

- **标准 NL 规则**：盲注、街、最小加注、边池等逻辑在后端 `DpRoomServiceImpl` 等模块中实现；对局页通过轮询 + WebSocket 同步房间状态。
- **多房间**：房间状态保存在进程内 **`ConcurrentHashMap`（`roomMap`）**；详见长文档中的 WebSocket 与房间说明。前端 **`/create-room`** 页设 **小盲/大盲**、**每人初始多少 BB**（筹码 = 大盲 × BB，补码同深度）与可选 **进房密码**（仅存内存，重启失效）；大厅 **`/home`** 仅列表与加入。
- **实时推送**：游戏页 WebSocket 路径形如 **`/ws/dp-game?roomId=...`**；有订阅者时才序列化推送，并与上次 JSON 去重以省流量。
- **牌谱**：观察到的对局可落库，支持按用户分页列表与详情（权限与底牌展示规则见 `docs` 下说明）。
- **大厅列表（单例版）**：`/dpRoom/publicRooms` 改为读 `dp_room_lobby` 单表（唯一数据源），返回结构保持 `{ list, total, page, pageSize }`；服务端在建房/进退房/踢人/房主变更/开局与房间销毁时同步摘要，分页查询使用 PageHelper + Redis 版本缓存（`mgdemo:cache:dpRoom:publicRooms:rev` 与 `mgdemo:cache:dpRoom:publicRooms:data:{rev}:{page}:{pageSize}`），变更时通过 `INCR rev + SCAN 删除旧 data 键` 失效，失败仅记录日志不阻断业务。带筛选的 `/dpRoom/publicRooms/query` 共用同一 `rev`，结果键为 `mgdemo:cache:dpRoom:lobbyQuery:data:{rev}:{paramHash}`（`paramHash` 为规范化查询串的 SHA-256 前 16 位十六进制），TTL 与 `mgdemoplus.cache.dp-room-public-rooms-ttl-seconds` 一致。

### 用户与账号

- **认证**：**Spring Security** + **JWT**（`Authorization: Bearer`）；白名单含登录注册、`/ws/**`、部分房间只读接口等（完整列表见 [docs/JWT.md](docs/JWT.md)）。
- **密码**：服务端对密码做 **bcrypt** 摘要后入库（详见 [docs/DpUserPassword.md](docs/DpUserPassword.md)）。
- **虚拟筹码**：演示用途，不涉及真钱。

### NPC / AI

- **规则型机器人**：多种风格与难度（如 Fish、TAG、**Shark** 等），Shark 含翻前范围、翻后计划、对手习惯记忆等（概要见 [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md)）。
- **大模型牌手（可选）**：`BOT_LLM` 通过火山方舟接口决策，需配置 `ARK_API_KEY`、`ARK_ENDPOINT_ID` 等（见 [README.en.md](README.en.md) 中大模型小节与 [docs/ENV_README.md](docs/ENV_README.md)）。

### 其它功能

- **曲库 BGM**：上传与列表接口，对局内可通过 WebSocket 同步播放状态（见长文档「曲库 BGM」）。
- **Redis**：用于曲库列表缓存、演示用 `/demo/redis/*` 等；**房间与对局 WebSocket 默认不依赖 Redis 做共享状态**（多实例需自行扩展，见 [docs/WEBSOCKET.md](docs/WEBSOCKET.md)）。

---

## 技术架构

| 层级        | 技术                                                                                                                                               |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 后端        | Java **17**、**Spring Boot 3.5**、Spring Web / Security / WebSocket、**MyBatis-Plus**、**PageHelper**、**Druid**、MySQL 驱动、**Lettuce**（Redis）、**JJWT** |
| 前端（DP 游戏） | **Vue 2**、Vue Router、**Vuex**、**Element UI**、axios（源码目录 **`front/dp_game`**）                                                                     |
| 数据        | **MySQL 8**（库名默认 **`school_db`**）、**Redis 7**                                                                                                    |
| 构建与部署     | **Maven**、**Docker** / **Docker Compose**、**Nginx**（反向代理与 WS 转发）                                                                                 |

---

## 环境要求

- **JDK 17**、**Maven 3.6+**
- 开发 DP 前端：**Node.js**（与 `front/dp_game/package.json` 中 Vue CLI 5 兼容的版本）
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

- **经 Nginx**：浏览器打开 **[http://localhost](http://localhost)**（见 [docs/NGINX.md](docs/NGINX.md)）
- **直连应用**：**[http://localhost:8088](http://localhost:8088)**（前端为 **hash 路由**，例如 **`/#/login`**）

默认 MySQL root 密码见 `docker-compose.yml` / `.env`（默认 **`mgdemo_root`**）；宿主机连接 MySQL 一般为 **`localhost:3307`**，Redis 为 **`localhost:6380`**（见 [docs/DOCKER.md](docs/DOCKER.md)）。

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

开发环境代理与接口前缀以项目内 `vue.config.js` 为准（常见为 **`/dev-api`** 转发到后端）。

**大厅与对局 UI 主题**：游戏大厅（`/home`）、房间内（`/room/:id`）、曲库上传（`/music-upload`）、历史对局列表与详情与对局页共用同一套主题选项与本地持久化键；**登录 / 注册**在 `App.vue` 中同样可选择主题（`dp-auth-shell.css`）。`document.body[data-dp-game-theme]` 由 `main.js` 在路由与主题变更时统一维护，以便 Element UI 弹层配色一致。说明见 `front/dp_game/docs/THEME_BINDING_README.md` 第 6～7 节。

---

## 仓库结构（概览）

```
MGDemoPlus/
├── src/main/java/com/example/mgdemoplus/   # Spring Boot 主工程
├── front/dp_game/                          # DP 游戏前端（Vue CLI）
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

- DP 游戏为**学习与演示**，**虚拟筹码，不涉及真实货币**。

若本短版与代码不一致，以 **[README.ch.md](README.ch.md)** / **[README.en.md](README.en.md)** 与 **[docs/README.md](docs/README.md)** 为准。

---

## English

A **Spring Boot** web demo centered on **DP Texas Hold’em (NL)**—multi-room tables, live play, NPC bots, hand history, and optional Volcengine Ark LLM players. The **`front/dp_game`** UI is **Vue 2 + Vue Router + Vuex + Element UI**; production builds are embedded into the Spring Boot JAR by **`Dockerfile`**, served on the **same port** as REST and WebSocket.

**Long-form maintenance notes:** Chinese **[README.ch.md](README.ch.md)** · English **[README.en.md](README.en.md)** (same structure).

### Features

#### Gameplay

- **NLHE rules**: Blinds, streets, min-raise, side pots, etc., implemented server-side (e.g. `DpRoomServiceImpl`); the table UI uses polling plus WebSocket for state.
- **Rooms**: In-memory **`ConcurrentHashMap` (`roomMap`)**; see long docs for WebSocket and room lifecycle.
- **Push**: Game WebSocket path pattern **`/ws/dp-game?roomId=...`**; serializes only when subscribers exist and de-duplicates JSON vs last push.

#### Accounts

- **Auth**: **Spring Security** + **JWT** (`Authorization: Bearer`); whitelist covers login/register, `/ws/**`, some read-only room APIs—full list in [docs/JWT.md](docs/JWT.md).
- **Passwords**: **bcrypt** digest stored server-side—see [docs/DpUserPassword.md](docs/DpUserPassword.md).
- **Play money only**: No real-money gambling.

#### NPC / AI

- **Rule-based bots**: Multiple styles (e.g. Fish, TAG, **Shark** with preflop/postflop logic and opponent memory)—overview in [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md).
- **LLM seat (optional)**: `BOT_LLM` via Ark APIs; set `ARK_API_KEY`, `ARK_ENDPOINT_ID`, etc. ([docs/ENV_README.md](docs/ENV_README.md), LLM section in [README.en.md](README.en.md)).

#### Other

- **Music library BGM**: Upload/list APIs; in-game playback state can sync over the room WebSocket.
- **Redis**: Used for music list cache, `/demo/redis/*` labs, etc.; **table WebSocket state is not Redis-backed by default** (scale-out notes in [docs/WEBSOCKET.md](docs/WEBSOCKET.md)).

### Stack

| Layer | Tech |
|--------|------|
| Backend | Java **17**, **Spring Boot 3.5**, Web / Security / WebSocket, **MyBatis-Plus**, **PageHelper**, **Druid**, MySQL driver, **Lettuce** (Redis), **JJWT** |
| Frontend (DP) | **Vue 2**, Vue Router, **Vuex**, **Element UI**, axios (`front/dp_game`) |
| Data | **MySQL 8** (default DB **`school_db`**), **Redis 7** |
| Build / Ops | **Maven**, **Docker** / **Compose**, **Nginx** |

### Requirements

- **JDK 17**, **Maven 3.6+**
- For DP frontend dev: **Node.js** compatible with Vue CLI 5 in `front/dp_game`
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

- Via Nginx: **http://localhost** — [docs/NGINX.md](docs/NGINX.md)
- Direct app: **http://localhost:8088** — hash routes, e.g. **`/#/login`**

MySQL on host is typically **`localhost:3307`**, Redis **`localhost:6380`** ([docs/DOCKER.md](docs/DOCKER.md)).

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

Use `vue.config.js` dev proxy (often **`/dev-api`**) to hit the API.

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

### Legal

Virtual chips only; **no real-money play**. License: see **LICENSE** if present.

If this overview disagrees with code, trust **[README.ch.md](README.ch.md)** / **[README.en.md](README.en.md)** and **[docs/README.md](docs/README.md)** / **docs/**.
