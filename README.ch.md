# MGDemoPlus

基于 **Spring Boot** 的 Web 演示项目，核心是 **DP 德州扑克（无限注）**：多房间、实时对局、机器人 NPC、牌谱回放、可选火山方舟大模型牌手等。前端 `**front/dp_game`** 为 **Vue 2 + Vue Router + Vuex + Element UI**，构建产物由 `**Dockerfile`** 打进后端 JAR，与 REST、WebSocket **同端口**发布。

---

## 项目特性

### 游戏与对局

- **标准 NL 规则**：盲注、街、最小加注、边池等逻辑在后端 `DpRoomServiceImpl` 等模块中实现；对局页通过轮询 + WebSocket 同步房间状态。
- **多房间**：房间状态保存在进程内 `**ConcurrentHashMap`（`roomMap`）**；详见主文档中的 WebSocket 与房间说明。
- **实时推送**：游戏页 WebSocket 路径形如 `**/ws/dp-game?roomId=...`**；有订阅者时才序列化推送，并与上次 JSON 去重以省流量。
- **牌谱**：观察到的对局可落库，支持按用户分页列表与详情（权限与底牌展示规则见 `docs` 下说明）。

### 用户与账号

- **认证**：**Spring Security** + **JWT**（`Authorization: Bearer`）；白名单含登录注册、`/ws/`**、部分房间只读接口等（完整列表见 [docs/JWT.md](docs/JWT.md)）。
- **密码**：服务端对密码做 **MD5(UTF-8)** 摘要后入库（详见 [docs/DpUserPassword.md](docs/DpUserPassword.md)）。
- **虚拟筹码**：演示用途，不涉及真钱。

### NPC / AI

- **规则型机器人**：多种风格与难度（如 Fish、TAG、**Shark** 等），Shark 含翻前范围、翻后计划、对手习惯记忆等（概要见 [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md)）。
- **大模型牌手（可选）**：`BOT_LLM` 通过火山方舟接口决策，需配置 `ARK_API_KEY`、`ARK_ENDPOINT_ID` 等（见 [readme.md](readme.md) 中大模型小节与 [docs/ENV_README.md](docs/ENV_README.md)）。

### 其它功能

- **曲库 BGM**：上传与列表接口，对局内可通过 WebSocket 同步播放状态（见主文档「曲库 BGM」）。
- **Redis**：用于曲库列表缓存、演示用 `/demo/redis/`* 等；**房间与对局 WebSocket 默认不依赖 Redis 做共享状态**（多实例需自行扩展，见 [docs/WEBSOCKET.md](docs/WEBSOCKET.md)）。

---

## 技术架构


| 层级        | 技术                                                                                                                                               |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 后端        | Java **17**、**Spring Boot 3.5**、Spring Web / Security / WebSocket、**MyBatis-Plus**、**PageHelper**、**Druid**、MySQL 驱动、**Lettuce**（Redis）、**JJWT** |
| 前端（DP 游戏） | **Vue 2**、Vue Router、**Vuex**、**Element UI**、axios（源码目录 `**front/dp_game`**）                                                                     |
| 数据        | **MySQL 8**（库名默认 `**school_db`**）、**Redis 7**                                                                                                    |
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

- **经 Nginx**：浏览器打开 **[http://localhost](http://localhost)**（见 [docs/NGINX.md](docs/NGINX.md)）
- **直连应用**：**[http://localhost:8088](http://localhost:8088)**（前端为 **hash 路由**，例如 `**/#/login`**）

默认 MySQL root 密码见 `docker-compose.yml` / `.env`（默认 `**mgdemo_root`**）；宿主机连接 MySQL 一般为 `**localhost:3307**`，Redis 为 `**localhost:6380**`（见 [docs/DOCKER.md](docs/DOCKER.md)）。

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
│   ├── MgDemoPlusApplication.java
│   ├── config/                             # Security、WebSocket、CORS 等
│   ├── controller/dp/                      # DP 房间、用户、牌谱、曲库等 API
│   ├── controller/demo/                    # 示例：Redis、上传等
│   ├── service/ ... /serviceImpl/dp/       # 房间逻辑、NPC、牌谱等
│   ├── websocket/                          # WebSocket 处理
│   ├── security/                           # JWT 过滤器等
│   └── mapper/                             # MyBatis Mapper
├── src/main/resources/
│   ├── application.properties
│   └── db/                                 # 建表 SQL（Docker 初始化会引用）
├── front/dp_game/                          # DP 游戏前端（Vue CLI）
├── docker/                                 # Nginx 等配置
├── docker-data/                            # 本地数据卷挂载（mysql-init、uploads 等）
├── docker-compose.yml
├── docker-compose.hub.yml                  # 仅拉镜像部署（见文档）
├── Dockerfile / Dockerfile.mysql / Dockerfile.nginx
├── pom.xml
├── readme.md                               # 主文档（变更记录、细项说明）
├── README.ch.md                            # 本文件（中文概览）
└── docs/                                   # 专题文档（JWT、Docker、WebSocket、DP 规则等）
```

---

## 文档索引

- **环境与变量**：[docs/ENV_README.md](docs/ENV_README.md)
- **Docker / 镜像部署**：[docs/DOCKER.md](docs/DOCKER.md)
- **Nginx**：[docs/NGINX.md](docs/NGINX.md)
- **JWT**：[docs/JWT.md](docs/JWT.md)
- **DP 游戏规则与接口**：[docs/DPGAME.md](docs/DPGAME.md)
- **WebSocket**：[docs/WEBSOCKET.md](docs/WEBSOCKET.md)
- **维护级总说明（极长）**：[readme.md](readme.md) — 含各模块迭代说明、NPC 细节、牌谱与 Redis 等

---

## 部署说明（摘要）

- **全套源码部署**：`git clone` 仓库后使用根目录 `**docker compose up -d --build`**（勿用「只拉镜像」代替拉代码）。
- **仅镜像部署**：使用 `**docker-compose.hub.yml`**，并预先构建/推送 `**dpgame`、`dpgame-mysql`、`dpgame-nginx`** 等镜像；细节见 [docs/DOCKER.md](docs/DOCKER.md)。
- **数据库初始化**：首次创建 MySQL 数据卷时会执行 `docker-data/mysql-init` 与 `src/main/resources/db` 下的脚本；若旧卷导致表缺失，需按 [readme.md](readme.md) 中 Docker 小节处理。

---

## 许可证与声明

- DP 游戏为**学习与演示**，**虚拟筹码，不涉及真实货币**。

若你发现本概览与代码不一致，以 `**readme.md`** 与 `**docs/`** 下专题文档为准。