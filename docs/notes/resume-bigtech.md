# 大厂投递版 · 多人在线策略对战 Web 应用

---

## 30秒口述版

> 我独立做了一套实时多人策略牌局平台：后端 Spring Boot 单服务，对局态放在 JVM 内存房间，WebSocket 做房间级推送，MySQL 持久化牌谱、Redis 做登录会话和大厅缓存。核心难点是**多房间并发下的状态一致性**——牌局引擎在服务端跑完整状态机，玩家行动带合法性校验，WebSocket 按连接做 JSON 去重避免无效广播。另外做了快匹配协调器（锁顺序 + 超时清理 + 单测）、JWT+JTI 单会话、三端 OAuth、RBAC 权限，以及 6 种规则 AI + LLM 异步兜底。已 Docker 多阶段构建，GitHub Actions 推镜像并 SSH 自动部署到演示站。

---

## 面试追问预备

**Q1：为什么对局房间放内存而不放 Redis？**  
A：对局是高频读写 + 强一致的单手牌状态机（行动顺序、主池/边池、阶段流转），放内存用 `synchronized(room)` 即可保证单房原子性，延迟最低、实现简单。Redis 适合跨实例共享的**低频元数据**（登录 JTI、大厅列表 rev 缓存、RBAC 权限），不适合每 tick 序列化整桌状态。当前是单 JVM 部署，内存房间是诚实选型；若水平扩展需引入房间分片或分布式状态，那是下一阶段架构演进。

**Q2：JWT 为什么 subject 只存 nickname 而不存 userId？**  
A：历史设计以 nickname 作为 WebSocket 路由与牌桌身份标识（进房、行动、推送快照均按昵称关联），JWT subject 与 WS 鉴权链路保持一致，过滤器里 `claims.getSubject()` 直接查 `dp_user`。改 userId 需同步改 WS Handler、房间玩家映射和前端 token 解析，收益有限。权限校验走 `@PreAuthorize` + `hasPermi()`，内部 nickname → userId → Redis 权限缓存，不依赖 JWT 携带权限列表。

**Q3：大厅 Redis 缓存怎么保证和 DB/内存房间一致？**  
A：采用 **rev 版本号 + 写后失效** 策略：`dp_room_lobby` 变更时先落库，再 `INCR rev` 使旧分页缓存键自然失效；定时任务 `DpRoomLobbyReconcileScheduler` 对齐 DB 与内存房间，清理幽灵房。读路径 miss 时回源 MySQL，写入带 TTL 的新缓存页。这是典型的 cache-aside + 版本戳模式，避免并发写导致脏读。

---

## 简历正文

多人在线策略对战 Web 应用    个人项目 · 全栈（偏后端）    2026.02 - 至今

**项目描述：**  
面向策略牌局爱好者的实时多人在线对战平台，核心挑战在于**单实例多房间并发下的状态一致性与低延迟同步**——牌局逻辑必须在服务端权威执行，客户端仅接收快照。架构为 Spring Boot 3 单服务 + JVM 内存房间（`ConcurrentHashMap`）+ WebSocket 房间推送 + MySQL 牌谱持久化 + Redis 会话/缓存；生产环境 Docker 多阶段同源部署（8088 同端口 REST / WS / 静态资源）。当前为**单 JVM 多房间**架构，完整实现从 preflop 到 showdown 的牌局状态机，非分布式集群。  
演示：https://catandppoker.asia  
代码：https://github.com/lzx391/pokerGame.git

**个人职责：**

- 独立负责后端整体架构与核心模块（房间引擎、WebSocket 推送、快匹配、安全鉴权、AI 决策），按功能域分包（room / lobby / npc / quickmatch / rbac 等）落地分层约定。
- 完成 Vue 2 前端联调与 WebSocket 断线重连、牌谱回放等交互；开发期前后端分离，生产 Docker 同源 JAR 部署。
- 搭建 Flyway schema 演进（V1–V28）、Docker Compose 全栈编排与 GitHub Actions CI/CD 自动构建推送 + SSH 线上部署链路。

**核心亮点：**

- **实时同步（WebSocket 房间推送）**：挑战是多房并发订阅下避免无效广播与断线态不一致 → 按房间维护会话表，全局 1s tick 仅在有订阅者时序列化快照，连接级 JSON 去重（payload 不变则 skip），前端对局/快匹 WS 指数退避重连 → 降低带宽与 CPU 序列化开销，断线后可恢复对局视图。
- **牌局引擎（状态机 + 主池边池）**：挑战是服务端权威校验防止作弊与状态漂移 → 自研完整牌局流程（preflop→river→settle），行动合法性判定（最小加注、行动顺序）、`DpPotCalculator` 主池/边池结算，牌谱 payload V2 关联参与者入库（Flyway V18）→ 支持 REST 分页查询与局内/独立页回放，前后端渲染口径一致。
- **并发与匹配（快匹协调器）**：挑战是队列配对与进房写操作并发易死锁 → 设计 `DpQuickMatchPairingCoordinator` 单入口：优先灌入未满公开房，队列≥2 人批量开新房；强制锁顺序（房间监视器→队列锁），等待超时清理 + 嵌套深度防重入；`JoinableQuickMatchRoomIndex` 与配对核心路径单元测试覆盖 → 配对逻辑可回归、锁顺序可审计。
- **性能与缓存（大厅 Redis rev）**：挑战是高频刷大厅列表造成 DB 重复查询 → 公开房/筛选分页采用 rev 版本号 + 参数指纹缓存键，房间状态变更先写库再 `INCR rev` 失效旧页；定时任务对齐 DB 与内存房间清理幽灵房 → 读多写少场景下显著减少 MySQL 回源，保证缓存与内存/DB 最终一致。
- **安全体系（JWT + RBAC）**：挑战是多端登录与会话劫持、细粒度游戏权限 → JWT（HMAC-SHA256）subject 存 nickname，JTI + Redis 单会话（新登录踢旧）；口令 BCrypt 存储；GitHub/Gitee/钉钉 OAuth 绑定本地用户（`dp_social_auth`）；RBAC 角色权限表（Flyway V26–28）+ `@PreAuthorize` + `hasPermi()` + Redis 权限缓存（TTL 300s），覆盖看牌、实验排牌、NPC 追踪等能力 → 接口级鉴权与单点登录可控。
- **AI 双轨（规则 NPC + LLM 兜底）**：挑战是 AI 对手需多样风格且 LLM 延迟不可阻塞 tick → 6 种规则引擎（Fish/TAG/LAG/NIT/Call/Maniac）+ 房主自定义 Bot 六维参数；OpenAI 兼容 Client 接火山方舟 LLM Bot，`CompletableFuture` 异步决策，快照过期或模型无响应时本地规则兜底；NPC 决策追踪 REST（RBAC 门控）可复盘 → 对局 tick 不被 LLM 阻塞，Bot 风格可配置可观测。
- **工程化（Flyway + Docker + CI/CD）**：挑战是个人项目需可重复部署与 schema 可追溯 → Flyway V1–V28 管理全部 DDL（成就、OAuth、RBAC 等）；Docker 多阶段（Node 编前端→Maven 打 JAR→JRE 运行）；Compose 编排 MySQL + Redis + Nginx；GitHub Actions push 触发双镜像构建推 Docker Hub 并 SSH `docker compose pull && up -d` → 演示站与代码仓库同源可追溯。

**技术栈：**  
后端 Java 17 · Spring Boot 3.5 · WebSocket · SSE · Spring Security · JWT · OAuth2/JustAuth · MySQL · Druid · MyBatis-Plus · Redis · Flyway · Docker · Nginx · GitHub Actions ｜ 前端 Vue 2 · Vuex · Axios · WebSocket

---

## [待确认]

1. **演示站** https://catandppoker.asia 当前是否可访问、是否为最新镜像（CI 分支 `desensitization-pre`，镜像 tag `v1.0.1`）。
2. **GitHub 仓库** https://github.com/lzx391/pokerGame.git 是否与本地 MGDemoPlus 代码同步（本地目录名不同）。
3. **Electron 桌面客户端**（`electron-client/`）是否作为投递亮点单独列出（可选，非 Web 主线）。
