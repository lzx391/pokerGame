# MGDemoPlus：`docs/` 专题文档总索引

本页是仓库 **`docs/`** 目录的**总导航**：按主题归类，并简要说明每篇文档解决什么问题、适合谁读。更偏「产品 + 运维 + 长变更记录」的说明见 **[README.ch.md](../README.ch.md)**（中文）与 **[README.en.md](../README.en.md)**（英文，同结构）；英文快速入门见 **[README.md](../README.md)**。

---

## 根目录 README 怎么选

| 文档 | 内容侧重 |
|------|----------|
| **[README.md](../README.md)** | **英文短版**：特性、技术栈、快速开始（**入门首选**） |
| **[README.ch.md](../README.ch.md)** | **中文长说明**：环境变量、Docker、WebSocket、对局 UI 迭代、NPC、牌谱、曲库、Redis 等（按功能/时间线） |
| **[README.en.md](../README.en.md)** | **English long-form**（与 README.ch 同内容，英文） |

---

## 环境与配置

| 文档 | 简介 |
|------|------|
| **[ENV_README.md](ENV_README.md)** | `application.properties`、根目录 `.env`、`LocalDotenvLoader`、JWT 与方舟 LLM 配置的读取顺序与变量含义。 |

---

## 部署与网关

| 文档 | 简介 |
|------|------|
| **[DOCKER.md](DOCKER.md)** | `docker compose` 全套部署、仅镜像部署（`docker-compose.hub.yml`）、端口映射（MySQL 3307、Redis 6380）、数据卷与初始化 SQL、构建推送脚本等。 |
| **[NGINX.md](NGINX.md)** | Compose 内 Nginx 反向代理、静态资源与 WebSocket 转发要点。 |

---

## 安全与用户

| 文档 | 简介 |
|------|------|
| **[JWT.md](JWT.md)** | `JwtTokenService`、`SecurityConfig`、白名单、`JwtAuthenticationFilter`、前端 axios 带 token 与 401 处理。 |
| **[DpUserPassword.md](DpUserPassword.md)** | 前后端密码分工、MD5(UTF-8) 存储、校验与找回密码现状。 |

---

## DP 游戏：规则、接口与房间

| 文档 | 简介 |
|------|------|
| **[DPGAME.md](DPGAME.md)** | 后端对游戏流程、主要 REST 接口与房间状态的理解笔记（维护向，含创建房间、行动、结算等）。 |
| **[RoomUi.md](RoomUi.md)** | 对局页座位列表「本机视角旋转」等 UI 与 `seatIndex` 约定。 |
| **[WEBSOCKET.md](WEBSOCKET.md)** | 对局页 WebSocket 地址、与轮询/心跳分工、推送内容、聊天与音乐同步协议、多实例扩展注意点。 |
| **[DpMusicWebPath.md](DpMusicWebPath.md)** | 曲库 `webPath`、磁盘目录、`/music/**` 与开发代理试听流程。 |
| **[dp-quick-match-concurrency.md](dp-quick-match-concurrency.md)** | 大厅快速匹配里 `synchronized(房间)` 在解决什么问题、谁先谁后、单机与多实例注意点（零基础向）。 |
| **[dp_friend_mailbox_mvp.md](dp_friend_mailbox_mvp.md)** | **好友 + 邮箱邀请（REST）**：`/dp/*` 双向申请、房主 60 秒进房邀约、观众进房免密；已知限制（超员/反骚扰/仅大厅可见前端约定）。 |

## 数据持久化与牌谱

| 文档 | 简介 |
|------|------|
| **[DP_PERSISTENCE_README.md](DP_PERSISTENCE_README.md)** | 牌谱落库、参与者表、`user_id` 与昵称补全思路、与 Shark 记忆表的关系。 |

---

## Redis

| 文档 | 简介 |
|------|------|
| **[Redis.md](Redis.md)** | 曲库列表缓存键与 TTL；文内另含 JWT+Redis 踢下线等扩展思路笔记。 |

---

## AI / NPC

| 文档 | 简介 |
|------|------|
| **[ai/npc-engine/README.md](ai/npc-engine/README.md)** | NPC 引擎总入口与分册导航（Fish/Maniac/TAG、Shark 等）。 |
| **[ai/npc-preflop-unified-decision-flow.md](ai/npc-preflop-unified-decision-flow.md)** | 统一翻前策略数据流向（入口、`Spot`、`lateFactor`、`rangeLevel`、分场景、加注尺度与 G1–G8 说明）。 |
| **[ai/npc-engine/01_overview_and_entry.md](ai/npc-engine/01_overview_and_entry.md)** | 总览与调用入口。 |
| **[ai/npc-engine/02_normal_npc_implementation.md](ai/npc-engine/02_normal_npc_implementation.md)** | 普通 NPC 实现要点。 |
| **[ai/npc-engine/03_normal_npc_modules.md](ai/npc-engine/03_normal_npc_modules.md)** | 普通 NPC 模块拆分。 |
| **[ai/npc-engine/04_shark_implementation.md](ai/npc-engine/04_shark_implementation.md)** | Shark 实现要点。 |
| **[ai/npc-engine/05_shark_modules.md](ai/npc-engine/05_shark_modules.md)** | Shark 模块与策略相关说明。 |
| **[ai/npc-llm/part01_类间关系以及调用.md](ai/npc-llm/part01_类间关系以及调用.md)** | 大模型 NPC（`BOT_LLM`）相关类关系与调用链。 |

---

## 学习与备忘（通用）

| 文档 | 简介 |
|------|------|
| **[BACKEND_INTERVIEW_QUESTIONS.md](BACKEND_INTERVIEW_QUESTIONS.md)** | 结合本仓库技术栈的后端面试题清单。 |
| **[Json-map-serialization.md](Json-map-serialization.md)** | JSON、`Map`、序列化/反序列化与接口数据备忘。 |
| **[Java对象引用与浅拷贝深拷贝备忘.md](Java对象引用与浅拷贝深拷贝备忘.md)** | Java 引用、`roomMap` 与拷贝相关备忘。 |
| **[Spring依赖注入复习笔记.md](Spring依赖注入复习笔记.md)** | Spring DI 中文复习笔记。 |
| **[SPRING_DI_NOTES.md](SPRING_DI_NOTES.md)** | Spring DI 英文/对照笔记（与上一篇主题相近，选读其一即可）。 |
| **[SpringScheduling.md](SpringScheduling.md)** | Spring `@Scheduled` 与 `Timer` 对照、`DpRoomLobbyReconcileScheduler` 与配置、在本项目新增定时任务的步骤与多实例注意。 |

---

## 前端 DP 游戏专题（`front/dp_game/docs`）

对局页布局、主题、CSS 变量等见 **[front/dp_game/docs/README.md](../front/dp_game/docs/README.md)**（前端文档索引，与本页互补）。

---

## 文件顶部一级标题（`#`）说明

本篇各表格内的简介已与对应 `.md` 文件开头的**单行 Markdown 一级标题**一致；从 IDE 大纲跳转时请以该 `# …` 为准（文件名保持不变）。
